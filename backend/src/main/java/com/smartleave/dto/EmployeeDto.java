package com.smartleave.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Employee module payloads. */
public final class EmployeeDto {

    private EmployeeDto() {
    }

    public record LeaveBalanceDto(Long id,
                                  Long leaveTypeId,
                                  String leaveName,
                                  Integer year,
                                  BigDecimal totalDays,
                                  BigDecimal usedDays,
                                  BigDecimal availableDays) {
    }

    public record NotificationDto(Long id, String message, boolean read, LocalDateTime createdAt) {
    }

    /**
     * All figures are computed from MySQL - nothing here is mocked.
     *
     * @param totalEntitlementDays sum of all configured leave types for this year
     * @param totalAvailableDays   sum of available_days across leave types
     * @param totalUsedDays        sum of used_days across leave types
     */
    public record DashboardResponse(BigDecimal totalEntitlementDays,
                                    BigDecimal totalAvailableDays,
                                    BigDecimal totalUsedDays,
                                    long pendingCount,
                                    long approvedCount,
                                    long rejectedCount,
                                    long unreadNotifications,
                                    List<LeaveBalanceDto> balances,
                                    List<LeaveDto.ApplicationResponse> recentApplications) {
    }
}
