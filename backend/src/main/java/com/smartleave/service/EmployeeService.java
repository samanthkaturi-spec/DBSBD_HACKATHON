package com.smartleave.service;

import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.dto.EmployeeDto.DashboardResponse;
import com.smartleave.dto.EmployeeDto.LeaveBalanceDto;
import com.smartleave.dto.EmployeeDto.NotificationDto;
import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.entity.LeaveBalance;
import com.smartleave.entity.LeaveStatus;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.LeaveApplicationRepository;
import com.smartleave.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Employee self-service: dashboard, profile, balances and notifications. */
@Service
public class EmployeeService {

    private final UserRepository userRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final NotificationService notificationService;
    private final LeaveService leaveService;

    public EmployeeService(UserRepository userRepository,
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

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long userId) {
        requireActive(userId);
        int year = LocalDate.now().getYear();

        leaveBalanceService.ensureYearBalances(current(userId), year);
        List<LeaveBalanceDto> balances = leaveBalanceService.balancesForYear(userId, year).stream()
                .map(EmployeeService::toBalanceDto)
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal available = BigDecimal.ZERO;
        for (LeaveBalanceDto dto : balances) {
            total = total.add(dto.totalDays());
            used = used.add(dto.usedDays());
            available = available.add(dto.availableDays());
        }

        long pending = leaveApplicationRepository.countByUserIdAndStatus(userId, LeaveStatus.PENDING);
        long approved = leaveApplicationRepository.countByUserIdAndStatus(userId, LeaveStatus.APPROVED);
        long rejected = leaveApplicationRepository.countByUserIdAndStatus(userId, LeaveStatus.REJECTED);

        List<ApplicationResponse> recent = leaveService.myRequests(userId).stream().limit(5).toList();
        long unread = notificationService.unreadCount(userId);

        return new DashboardResponse(total, available, used, pending, approved, rejected,
                unread, balances, recent);
    }

    @Transactional(readOnly = true)
    public UserSummary profile(Long userId) {
        User user = current(userId);
        return UserSummary.from(user);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> balances(Long userId, Integer year) {
        requireActive(userId);
        int effectiveYear = year == null ? LocalDate.now().getYear() : year;
        return leaveBalanceService.balancesForYear(userId, effectiveYear).stream()
                .map(EmployeeService::toBalanceDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> notifications(Long userId) {
        return notificationService.latest(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationService.unreadCount(userId);
    }

    @Transactional
    public int markNotificationsRead(Long userId) {
        return notificationService.markAllRead(userId);
    }

    private User current(Long userId) {
        return userRepository.findWithDepartmentById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }

    private void requireActive(Long userId) {
        if (!current(userId).isActive()) {
            throw ApiException.forbidden("Your account is deactivated");
        }
    }

    private static LeaveBalanceDto toBalanceDto(LeaveBalance balance) {
        return new LeaveBalanceDto(
                balance.getId(),
                balance.getLeaveType().getId(),
                balance.getLeaveType().getLeaveName(),
                balance.getYear(),
                balance.getTotalDays(),
                balance.getUsedDays(),
                balance.getAvailableDays());
    }
}
