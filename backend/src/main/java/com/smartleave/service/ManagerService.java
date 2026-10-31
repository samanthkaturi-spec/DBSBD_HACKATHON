package com.smartleave.service;

import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.dto.ManagerDto.DashboardResponse;
import com.smartleave.dto.ManagerDto.TeamMemberSummary;
import com.smartleave.entity.LeaveApplication;
import com.smartleave.entity.LeaveStatus;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.LeaveApplicationRepository;
import com.smartleave.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager workflow: view team requests, approve or reject them.
 *
 * <p>Approve/reject are fully transactional. The application row is locked
 * first (prevents a double submit from deducting twice), then the balance row
 * is locked, then status, approver and remarks are written and a notification
 * is created. Any rule violation throws, which rolls everything back.</p>
 */
@Service
public class ManagerService {

    private final UserRepository userRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final NotificationService notificationService;
    private final LeaveService leaveService;

    public ManagerService(UserRepository userRepository,
                          LeaveApplicationRepository leaveApplicationRepository,
                          LeaveBalanceService leaveBalanceService,
                          NotificationService notificationService,
                          LeaveService leaveService) {
        this.userRepository = userRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.notificationService = notificationService;
        this.leaveService = leaveService;
    }

    // ------------------------------------------------------------ dashboard
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long managerId) {
        User manager = requireManager(managerId);

        long teamSize = userRepository.countByManagerId(managerId);
        long pending = leaveApplicationRepository.countByUser_Manager_IdAndStatus(managerId, LeaveStatus.PENDING);
        long approved = leaveApplicationRepository.countByUser_Manager_IdAndStatus(managerId, LeaveStatus.APPROVED);
        long rejected = leaveApplicationRepository.countByUser_Manager_IdAndStatus(managerId, LeaveStatus.REJECTED);
        long onLeaveToday = leaveApplicationRepository.countTeamOnLeave(managerId, LocalDate.now());

        List<ApplicationResponse> recent = leaveApplicationRepository.findByManager(managerId).stream()
                .limit(5)
                .map(leaveService::toResponse)
                .toList();

        return new DashboardResponse(teamSize, pending, approved, rejected, onLeaveToday, recent, teamOverview(managerId));
    }

    @Transactional(readOnly = true)
    public List<TeamMemberSummary> teamOverview(Long managerId) {
        List<User> members = userRepository.findByManagerIdOrderByFullNameAsc(managerId);
        Map<Long, Map<LeaveStatus, Long>> counts = new HashMap<>();
        for (Object[] row : leaveApplicationRepository.teamStatusCounts(managerId)) {
            Long userId = (Long) row[0];
            LeaveStatus status = row[1] instanceof LeaveStatus ls ? ls : LeaveStatus.valueOf(row[1].toString());
            Long count = (Long) row[2];
            counts.computeIfAbsent(userId, k -> new EnumMap<>(LeaveStatus.class)).put(status, count);
        }
        List<TeamMemberSummary> overview = new ArrayList<>();
        for (User member : members) {
            Map<LeaveStatus, Long> memberCounts = counts.getOrDefault(member.getId(), Map.of());
            overview.add(new TeamMemberSummary(
                    UserSummary.from(member),
                    memberCounts.getOrDefault(LeaveStatus.PENDING, 0L),
                    memberCounts.getOrDefault(LeaveStatus.APPROVED, 0L),
                    memberCounts.getOrDefault(LeaveStatus.REJECTED, 0L)));
        }
        return overview;
    }

    // ------------------------------------------------------------- requests
    @Transactional(readOnly = true)
    public Page<ApplicationResponse> requests(Long managerId, String status, int page, int size) {
        requireManager(managerId);

        LeaveStatus parsed = parseStatus(status);
        Specification<LeaveApplication> specification =
                (root, query, cb) -> cb.equal(root.get("user").get("manager").get("id"), managerId);
        if (parsed != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "appliedAt"));
        return leaveApplicationRepository.findAll(specification, pageable).map(leaveService::toResponse);
    }

    // -------------------------------------------------------------- approve
    @Transactional
    public ApplicationResponse approve(Long managerId, Long applicationId, String remarks) {
        requireManager(managerId);
        LeaveApplication application = lockApplication(applicationId);
        User employee = application.getUser();

        assertInsideTeam(managerId, employee);
        assertNotSelf(managerId, employee);
        assertPending(application);

        // Rule 8: insufficient balance aborts the transaction.
        leaveBalanceService.deduct(employee.getId(), application.getLeaveType(),
                application.getStartDate().getYear(), application.getTotalDays());

        application.setStatus(LeaveStatus.APPROVED);
        application.setApprovedBy(userRepository.getReferenceById(managerId));
        application.setManagerRemarks(remarks == null || remarks.isBlank() ? "Approved" : remarks.trim());
        leaveApplicationRepository.save(application);

        notificationService.notify(employee.getId(),
                "Your " + application.getLeaveType().getLeaveName() + " leave ("
                        + application.getStartDate() + " to " + application.getEndDate()
                        + ") was APPROVED");

        return leaveService.toResponse(application);
    }

    // --------------------------------------------------------------- reject
    @Transactional
    public ApplicationResponse reject(Long managerId, Long applicationId, String remarks) {
        requireManager(managerId);
        if (remarks == null || remarks.isBlank()) {
            throw ApiException.badRequest("Rejection remarks are required");
        }
        LeaveApplication application = lockApplication(applicationId);
        User employee = application.getUser();

        assertInsideTeam(managerId, employee);
        assertNotSelf(managerId, employee);
        assertPending(application);

        // Rule 10: rejecting never touches the balance.
        application.setStatus(LeaveStatus.REJECTED);
        application.setApprovedBy(userRepository.getReferenceById(managerId));
        application.setManagerRemarks(remarks.trim());
        leaveApplicationRepository.save(application);

        notificationService.notify(employee.getId(),
                "Your " + application.getLeaveType().getLeaveName() + " leave ("
                        + application.getStartDate() + " to " + application.getEndDate()
                        + ") was REJECTED: " + remarks.trim());

        return leaveService.toResponse(application);
    }

    // -------------------------------------------------------------- helpers
    private LeaveApplication lockApplication(Long id) {
        return leaveApplicationRepository.findForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Leave request " + id + " not found"));
    }

    private void assertInsideTeam(Long managerId, User employee) {
        if (employee == null || employee.getManager() == null
                || !managerId.equals(employee.getManager().getId())) {
            throw ApiException.forbidden("You can only process leave requests from your own team");
        }
    }

    private void assertNotSelf(Long managerId, User employee) {
        if (employee != null && managerId.equals(employee.getId())) {
            throw ApiException.forbidden("You cannot approve your own leave request");
        }
    }

    private void assertPending(LeaveApplication application) {
        if (application.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict("Request " + application.getId() + " is already "
                    + application.getStatus() + " - repeated decisions are not allowed");
        }
    }

    private User requireManager(Long managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (!manager.isActive()) {
            throw ApiException.forbidden("Your account is deactivated");
        }
        return manager;
    }

    private LeaveStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return LeaveStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status '" + status + "'. Use PENDING, APPROVED, REJECTED or CANCELLED");
        }
    }
}
