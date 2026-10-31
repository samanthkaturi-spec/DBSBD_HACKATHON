package com.smartleave.service;

import com.smartleave.dto.AdminDto.CreateDepartmentRequest;
import com.smartleave.dto.AdminDto.CreateEmployeeRequest;
import com.smartleave.dto.AdminDto.DashboardResponse;
import com.smartleave.dto.AdminDto.DepartmentDto;
import com.smartleave.dto.AdminDto.DepartmentReportRow;
import com.smartleave.dto.AdminDto.MonthlyReportRow;
import com.smartleave.dto.AdminDto.ReportResponse;
import com.smartleave.dto.AdminDto.UpdateEmployeeRequest;
import com.smartleave.dto.AdminDto.UpdateBalanceRequest;
import com.smartleave.dto.CommonDto.PageResponse;
import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.dto.EmployeeDto.LeaveBalanceDto;
import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.entity.Department;
import com.smartleave.entity.LeaveApplication;
import com.smartleave.entity.LeaveBalance;
import com.smartleave.entity.LeaveStatus;
import com.smartleave.entity.Role;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.DepartmentRepository;
import com.smartleave.repository.LeaveApplicationRepository;
import com.smartleave.repository.LeaveBalanceRepository;
import com.smartleave.repository.LeaveTypeRepository;
import com.smartleave.repository.UserRepository;
import com.smartleave.util.WorkingDaysCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Administration / HR operations. Every method runs real MySQL queries. */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final PasswordEncoder passwordEncoder;
    private final LeaveService leaveService;

    public AdminService(UserRepository userRepository,
                        DepartmentRepository departmentRepository,
                        LeaveApplicationRepository leaveApplicationRepository,
                        LeaveBalanceRepository leaveBalanceRepository,
                        LeaveTypeRepository leaveTypeRepository,
                        LeaveBalanceService leaveBalanceService,
                        PasswordEncoder passwordEncoder,
                        LeaveService leaveService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.passwordEncoder = passwordEncoder;
        this.leaveService = leaveService;
    }

    // ------------------------------------------------------------ dashboard
    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        long totalEmployees = userRepository.count();
        long activeEmployees = userRepository.findAll().stream().filter(User::isActive).count();
        long totalApplications = leaveApplicationRepository.count();
        long pending = leaveApplicationRepository.countByStatus(LeaveStatus.PENDING);
        long approved = leaveApplicationRepository.countByStatus(LeaveStatus.APPROVED);
        long rejected = leaveApplicationRepository.countByStatus(LeaveStatus.REJECTED);
        return new DashboardResponse(totalEmployees, activeEmployees, departmentRepository.count(),
                totalApplications, pending, approved, rejected);
    }

    // ------------------------------------------------------------ employees
    @Transactional(readOnly = true)
    public PageResponse<UserSummary> employees(String query, Long departmentId, Boolean active,
                                               int page, int size) {
        Specification<User> specification = (root, cq, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase() + "%";
            specification = specification.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("employeeId")), like)));
        }
        if (departmentId != null) {
            specification = specification.and((root, cq, cb) ->
                    cb.equal(root.get("department").get("id"), departmentId));
        }
        if (active != null) {
            specification = specification.and((root, cq, cb) ->
                    cb.equal(root.get("active"), active));
        }

        Page<User> result = userRepository.findAll(specification,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.ASC, "fullName")));

        List<UserSummary> content = result.getContent().stream().map(UserSummary::from).toList();
        return PageResponse.of(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public UserSummary createEmployee(CreateEmployeeRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        String employeeId = (request.employeeId() == null || request.employeeId().isBlank())
                ? generateEmployeeId() : request.employeeId().trim();
        if (userRepository.existsByEmployeeIdIgnoreCase(employeeId)) {
            throw ApiException.conflict("Employee id " + employeeId + " is already in use");
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.badRequest("Unknown department id " + request.departmentId()));

        User employee = new User();
        employee.setEmployeeId(employeeId);
        employee.setFullName(request.fullName().trim());
        employee.setEmail(request.email().trim().toLowerCase());
        employee.setPasswordHash(passwordEncoder.encode(request.password()));
        employee.setRole(parseRole(request.role()));
        employee.setDepartment(department);
        employee.setDesignation(request.designation().trim());
        employee.setActive(true);

        if (request.managerId() != null) {
            employee.setManager(findManager(request.managerId()));
        }
        employee = userRepository.save(employee);

        leaveBalanceService.ensureYearBalances(employee, LocalDate.now().getYear());
        return UserSummary.from(employee);
    }

    @Transactional
    public UserSummary updateEmployee(Long id, UpdateEmployeeRequest request) {
        User employee = userRepository.findWithDepartmentById(id)
                .orElseThrow(() -> ApiException.notFound("Employee " + id + " not found"));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            employee.setFullName(request.fullName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(employee.getEmail())
                    && userRepository.existsByEmailIgnoreCase(email)) {
                throw ApiException.conflict("An account with this email already exists");
            }
            employee.setEmail(email);
        }
        if (request.designation() != null && !request.designation().isBlank()) {
            employee.setDesignation(request.designation().trim());
        }
        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> ApiException.badRequest("Unknown department id " + request.departmentId()));
            employee.setDepartment(department);
        }
        if (request.managerId() != null) {
            if (request.managerId() == 0L) {
                employee.setManager(null);
            } else {
                if (request.managerId().equals(id)) {
                    throw ApiException.badRequest("An employee cannot be their own manager");
                }
                employee.setManager(findManager(request.managerId()));
            }
        }
        if (request.role() != null) {
            employee.setRole(parseRole(request.role()));
        }
        if (request.password() != null && !request.password().isBlank()) {
            employee.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.active() != null) {
            if (employee.getRole() == Role.ADMIN && !request.active()) {
                throw ApiException.badRequest("The administrator account cannot be deactivated here");
            }
            employee.setActive(request.active());
        }

        employee = userRepository.save(employee);
        if (Boolean.TRUE.equals(request.active())) {
            leaveBalanceService.ensureYearBalances(employee, LocalDate.now().getYear());
        }
        return UserSummary.from(employee);
    }

    /**
     * DELETE is implemented as a deactivation: historical leave applications
     * must keep their foreign key to the employee, so rows are never removed.
     */
    @Transactional
    public UserSummary deactivateEmployee(Long id, Long currentUserId) {
        User employee = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Employee " + id + " not found"));
        if (id.equals(currentUserId)) {
            throw ApiException.badRequest("You cannot deactivate your own account");
        }
        if (employee.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("Administrator accounts cannot be deactivated");
        }
        employee.setActive(false);
        return UserSummary.from(userRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> employeeBalances(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("Employee " + userId + " not found");
        }
        leaveBalanceService.ensureYearBalances(userRepository.getReferenceById(userId), LocalDate.now().getYear());
        return leaveBalanceService.balancesForYear(userId, LocalDate.now().getYear()).stream()
                .map(b -> new LeaveBalanceDto(b.getId(), b.getLeaveType().getId(), b.getLeaveType().getLeaveName(),
                        b.getYear(), b.getTotalDays(), b.getUsedDays(), b.getAvailableDays()))
                .toList();
    }

    @Transactional
    public LeaveBalanceDto updateBalance(UpdateBalanceRequest request) {
        LeaveBalance balance = leaveBalanceRepository.findById(request.id())
                .orElseThrow(() -> ApiException.notFound("Leave balance " + request.id() + " not found"));
        if (request.totalDays().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("Total days cannot be negative");
        }
        if (balance.getUsedDays().compareTo(request.totalDays()) > 0) {
            throw ApiException.badRequest("Total days cannot be lower than the already used days ("
                    + balance.getUsedDays().stripTrailingZeros().toPlainString() + ")");
        }
        balance.setTotalDays(request.totalDays());
        balance.setAvailableDays(request.totalDays().subtract(balance.getUsedDays()));
        balance = leaveBalanceRepository.save(balance);
        return new LeaveBalanceDto(balance.getId(), balance.getLeaveType().getId(),
                balance.getLeaveType().getLeaveName(), balance.getYear(), balance.getTotalDays(),
                balance.getUsedDays(), balance.getAvailableDays());
    }

    // ---------------------------------------------------------- departments
    @Transactional(readOnly = true)
    public List<DepartmentDto> departments() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : userRepository.employeeCountsPerDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return departmentRepository.findAllByOrderByDepartmentNameAsc().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getDepartmentName(), d.getDescription(),
                        counts.getOrDefault(d.getId(), 0L), d.getCreatedAt()))
                .toList();
    }

    @Transactional
    public DepartmentDto createDepartment(CreateDepartmentRequest request) {
        if (departmentRepository.existsByDepartmentNameIgnoreCase(request.departmentName().trim())) {
            throw ApiException.conflict("Department '" + request.departmentName().trim() + "' already exists");
        }
        Department department = departmentRepository.save(
                new Department(request.departmentName().trim(), request.description()));
        return new DepartmentDto(department.getId(), department.getDepartmentName(),
                department.getDescription(), 0L, department.getCreatedAt());
    }

    // --------------------------------------------------------- applications
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> allApplications(String status, int page, int size) {
        Specification<LeaveApplication> specification = (root, query, cb) -> cb.conjunction();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            LeaveStatus parsed = parseStatus(status);
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        Page<LeaveApplication> result = leaveApplicationRepository.findAll(specification,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "appliedAt")));
        List<ApplicationResponse> content = result.getContent().stream().map(leaveService::toResponse).toList();
        return PageResponse.of(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // -------------------------------------------------------------- reports
    @Transactional(readOnly = true)
    public ReportResponse reports() {
        Map<Long, Long> employeeCounts = new HashMap<>();
        for (Object[] row : userRepository.employeeCountsPerDepartment()) {
            employeeCounts.put((Long) row[0], (Long) row[1]);
        }

        Map<Long, Long> applicationCounts = new HashMap<>();
        for (Object[] row : leaveApplicationRepository.applicationCountPerDepartment()) {
            applicationCounts.put((Long) row[0], (Long) row[1]);
        }

        Map<Long, BigDecimal> approvedDays = new HashMap<>();
        Map<Long, Long> approvedCounts = new HashMap<>();
        for (Object[] row : leaveApplicationRepository.approvedStatsPerDepartment()) {
            approvedCounts.put((Long) row[0], (Long) row[1]);
            approvedDays.put((Long) row[0], decimal(row[2]));
        }

        List<DepartmentReportRow> byDepartment = new ArrayList<>();
        for (Department department : departmentRepository.findAllByOrderByDepartmentNameAsc()) {
            Long id = department.getId();
            byDepartment.add(new DepartmentReportRow(
                    department.getDepartmentName(),
                    employeeCounts.getOrDefault(id, 0L),
                    applicationCounts.getOrDefault(id, 0L),
                    approvedDays.getOrDefault(id, BigDecimal.ZERO)));
        }

        Map<String, long[]> monthlyCounts = new HashMap<>();   // [total, approved]
        for (Object[] row : leaveApplicationRepository.applicationCountPerMonth()) {
            String key = row[0] + "-" + String.format("%02d", row[1]);
            monthlyCounts.computeIfAbsent(key, k -> new long[2])[0] = (Long) row[2];
        }
        Map<String, BigDecimal> monthlyApprovedDays = new HashMap<>();
        for (Object[] row : leaveApplicationRepository.approvedStatsPerMonth()) {
            String key = row[0] + "-" + String.format("%02d", row[1]);
            monthlyCounts.computeIfAbsent(key, k -> new long[2])[1] = (Long) row[2];
            monthlyApprovedDays.put(key, decimal(row[3]));
        }

        List<MonthlyReportRow> byMonth = monthlyCounts.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("-");
                    return new MonthlyReportRow(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            entry.getValue()[0],
                            entry.getValue()[1],
                            monthlyApprovedDays.getOrDefault(entry.getKey(), BigDecimal.ZERO));
                })
                .sorted((a, b) -> a.year() != b.year()
                        ? Integer.compare(b.year(), a.year())
                        : Integer.compare(b.month(), a.month()))
                .toList();

        return new ReportResponse(byDepartment, byMonth, LocalDateTime.now());
    }

    // -------------------------------------------------------------- helpers
    /** SUM() over a DECIMAL column comes back as BigDecimal or Double depending on the driver. */
    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return Role.EMPLOYEE;
        }
        Role parsed = Role.valueOf(role.trim().toUpperCase());
        if (parsed == Role.ADMIN) {
            throw ApiException.forbidden("Administrator accounts are created from seed data only");
        }
        return parsed;
    }

    private User findManager(Long managerId) {
        if (managerId.equals(0L)) {
            return null;
        }
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> ApiException.badRequest("Unknown manager id " + managerId));
        if (manager.getRole() == Role.EMPLOYEE) {
            throw ApiException.badRequest("Manager must have the MANAGER or ADMIN role");
        }
        return manager;
    }

    private LeaveStatus parseStatus(String status) {
        try {
            return LeaveStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status '" + status + "'");
        }
    }

    private String generateEmployeeId() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = "EMP" + (1000 + ThreadLocalRandom.current().nextInt(9000));
            if (!userRepository.existsByEmployeeIdIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return "EMP" + (System.currentTimeMillis() % 100000);
    }
}
