package com.smartleave.dto;

import com.smartleave.dto.CommonDto.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Administration / HR payloads. */
public final class AdminDto {

    private AdminDto() {
    }

    public record DashboardResponse(long totalEmployees,
                                    long activeEmployees,
                                    long totalDepartments,
                                    long totalApplications,
                                    long pendingCount,
                                    long approvedCount,
                                    long rejectedCount) {
    }

    public record CreateEmployeeRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 100) String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid") String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,

            @NotNull(message = "Department is required") Long departmentId,

            Long managerId,

            @NotBlank(message = "Designation is required")
            @Size(max = 80) String designation,

            // ADMIN can never be created here - admin accounts come from seed data.
            @Pattern(regexp = "EMPLOYEE|MANAGER", message = "Role must be EMPLOYEE or MANAGER")
            String role,
            String employeeId) {
    }

    /** Partial update: null fields keep their current value. */
    public record UpdateEmployeeRequest(String fullName,
                                        @Email(message = "Email must be valid") String email,
                                        Long departmentId,
                                        Long managerId,
                                        String designation,
                                        @Pattern(regexp = "EMPLOYEE|MANAGER", message = "Role must be EMPLOYEE or MANAGER")
                                        String role,
                                        Boolean active,
                                        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
                                        String password) {
    }

    public record CreateDepartmentRequest(
            @NotBlank(message = "Department name is required")
            @Size(max = 100) String departmentName,
            @Size(max = 255) String description) {
    }

    public record DepartmentDto(Long id, String departmentName, String description,
                                long employeeCount, LocalDateTime createdAt) {
    }

    /** Direct adjustment of an entitlement balance (HR correction tool). */
    public record UpdateBalanceRequest(
            @NotNull(message = "Leave balance id is required") Long id,
            @NotNull(message = "Total days is required") BigDecimal totalDays) {
    }

    public record DepartmentReportRow(String departmentName,
                                      long employeeCount,
                                      long applicationCount,
                                      BigDecimal approvedDays) {
    }

    public record MonthlyReportRow(int year, int month, long applicationCount,
                                   long approvedCount, BigDecimal approvedDays) {
    }

    public record ReportResponse(List<DepartmentReportRow> byDepartment,
                                 List<MonthlyReportRow> byMonth,
                                 LocalDateTime generatedAt) {
    }
}
