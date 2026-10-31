package com.smartleave.service;

import com.smartleave.dto.CommonDto.LeaveTypeSummary;
import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.dto.LeaveDto.ApplyRequest;
import com.smartleave.entity.LeaveApplication;
import com.smartleave.entity.LeaveStatus;
import com.smartleave.entity.LeaveType;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.LeaveApplicationRepository;
import com.smartleave.repository.LeaveTypeRepository;
import com.smartleave.repository.UserRepository;
import com.smartleave.util.WorkingDaysCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Employee side of the leave workflow: submit, list and cancel.
 *
 * <p>Submitting an application never touches the balance - balances are only
 * deducted after a manager approves (Phase 9, rules 6 and 7).</p>
 */
@Service
public class LeaveService {

    private final UserRepository userRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final NotificationService notificationService;
    private final boolean allowCancellationAfterStart;

    public LeaveService(UserRepository userRepository,
                        LeaveApplicationRepository leaveApplicationRepository,
                        LeaveTypeRepository leaveTypeRepository,
                        LeaveBalanceService leaveBalanceService,
                        NotificationService notificationService,
                        @Value("${smartleave.cancellation.allow-after-start:false}") boolean allowCancellationAfterStart) {
        this.userRepository = userRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.notificationService = notificationService;
        this.allowCancellationAfterStart = allowCancellationAfterStart;
    }

    // ---------------------------------------------------------------- apply
    @Transactional
    public ApplicationResponse apply(Long userId, ApplyRequest request) {
        User user = userRepository.findWithDepartmentById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (!user.isActive()) {
            throw ApiException.forbidden("Your account is deactivated");
        }

        LocalDate today = LocalDate.now();
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();

        // Rule 1: start date cannot be in the past
        if (start.isBefore(today)) {
            throw ApiException.badRequest("Start date cannot be in the past");
        }
        // Rule 2: end date cannot be before start date
        if (end.isBefore(start)) {
            throw ApiException.badRequest("End date cannot be before start date");
        }

        BigDecimal days = WorkingDaysCalculator.calculate(start, end);
        // Rule 3: duration must be greater than zero (weekends are not counted)
        if (days.signum() <= 0) {
            throw ApiException.badRequest(
                    "Leave duration must be greater than zero - the selected range only contains weekends");
        }

        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .filter(LeaveType::isActive)
                .orElseThrow(() -> ApiException.badRequest("Unknown or inactive leave category"));

        // Rule 9: no overlapping PENDING or APPROVED requests
        List<LeaveApplication> overlaps = leaveApplicationRepository
                .findOverlapping(userId, start, end, null);
        if (!overlaps.isEmpty()) {
            LeaveApplication clash = overlaps.get(0);
            throw ApiException.conflict(
                    "Overlapping leave already exists (id " + clash.getId() + ", status "
                            + clash.getStatus() + ", " + clash.getStartDate() + " to " + clash.getEndDate() + ")");
        }

        leaveBalanceService.ensureYearBalances(user, start.getYear());

        LeaveApplication application = new LeaveApplication();
        application.setUser(user);
        application.setLeaveType(leaveType);
        application.setStartDate(start);
        application.setEndDate(end);
        application.setTotalDays(days);
        application.setReason(request.reason().trim());
        application.setStatus(LeaveStatus.PENDING);
        application = leaveApplicationRepository.save(application);

        // Rule 6: no deduction happens here - only after approval.
        if (user.getManager() != null) {
            notificationService.notify(user.getManager().getId(),
                    user.getFullName() + " requested " + leaveType.getLeaveName()
                            + " from " + start + " to " + end + " (" + days.toPlainString() + " day(s))");
        }
        notificationService.notify(user.getId(),
                "Your " + leaveType.getLeaveName() + " request (" + start + " to " + end + ") is pending approval");

        return toResponse(application);
    }

    // ---------------------------------------------------------------- read
    @Transactional(readOnly = true)
    public List<ApplicationResponse> myRequests(Long userId) {
        return leaveApplicationRepository.findByUser(userId).stream().map(this::toResponse).toList();
    }

    /** Owners only: an employee can never read somebody else's request (rule 16). */
    @Transactional(readOnly = true)
    public ApplicationResponse get(Long id, Long userId) {
        LeaveApplication application = requireOwnApplication(id, userId);
        return toResponse(application);
    }

    // -------------------------------------------------------------- cancel
    @Transactional
    public ApplicationResponse cancel(Long id, Long userId, String remarks) {
        LeaveApplication application = leaveApplicationRepository.findForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Leave request " + id + " not found"));

        if (application.getUser() == null || !userId.equals(application.getUser().getId())) {
            throw ApiException.forbidden("You can only cancel your own leave requests");
        }

        switch (application.getStatus()) {
            case CANCELLED -> throw ApiException.conflict("This request has already been cancelled");
            case REJECTED -> throw ApiException.conflict("Rejected requests cannot be cancelled");
            case PENDING -> {
                application.setStatus(LeaveStatus.CANCELLED);
                application.setManagerRemarks(remarks == null || remarks.isBlank()
                        ? "Cancelled by employee" : "Cancelled by employee: " + remarks.trim());
            }
            case APPROVED -> {
                // Rule 13: approved leave can only be cancelled inside the policy window.
                if (LocalDate.now().isAfter(application.getStartDate()) && !allowCancellationAfterStart) {
                    throw ApiException.conflict(
                            "Approved leave that has already started can no longer be cancelled. Contact HR.");
                }
                leaveBalanceService.restore(application.getUser().getId(), application.getLeaveType(),
                        application.getStartDate().getYear(), application.getTotalDays());
                application.setStatus(LeaveStatus.CANCELLED);
                application.setManagerRemarks(remarks == null || remarks.isBlank()
                        ? "Cancelled by employee (balance restored)" : "Cancelled by employee: " + remarks.trim());
            }
        }

        application = leaveApplicationRepository.save(application);

        if (application.getUser().getManager() != null) {
            notificationService.notify(application.getUser().getManager().getId(),
                    application.getUser().getFullName() + " cancelled leave request " + application.getId());
        }
        notificationService.notify(userId,
                "Leave request " + application.getId() + " is now CANCELLED");

        return toResponse(application);
    }

    // -------------------------------------------------------------- helpers
    private LeaveApplication requireOwnApplication(Long id, Long userId) {
        LeaveApplication application = leaveApplicationRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> ApiException.notFound("Leave request " + id + " not found"));
        return application;
    }

    public ApplicationResponse toResponse(LeaveApplication application) {
        User employee = application.getUser();
        return new ApplicationResponse(
                application.getId(),
                UserSummary.from(employee),
                new LeaveTypeSummary(
                        application.getLeaveType().getId(),
                        application.getLeaveType().getLeaveName(),
                        application.getLeaveType().getAnnualEntitlement(),
                        application.getLeaveType().getDescription(),
                        application.getLeaveType().isActive()),
                application.getStartDate(),
                application.getEndDate(),
                application.getTotalDays(),
                application.getReason(),
                application.getStatus().name(),
                application.getManagerRemarks(),
                application.getApprovedBy() == null ? null : application.getApprovedBy().getId(),
                application.getApprovedBy() == null ? null : application.getApprovedBy().getFullName(),
                application.getAppliedAt(),
                application.getUpdatedAt());
    }
}
