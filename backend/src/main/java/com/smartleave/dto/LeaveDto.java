package com.smartleave.dto;

import com.smartleave.dto.CommonDto.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Leave application payloads (employee side + manager decisions). */
public final class LeaveDto {

    private LeaveDto() {
    }

    public record ApplyRequest(
            @NotNull(message = "Leave category is required")
            Long leaveTypeId,

            @NotNull(message = "Start date is required")
            LocalDate startDate,

            @NotNull(message = "End date is required")
            LocalDate endDate,

            @NotBlank(message = "Reason is required")
            @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
            String reason) {
    }

    /** Used by cancel (optional) and by manager reject (mandatory). */
    public record RemarksRequest(
            @Size(max = 500, message = "Remarks must be at most 500 characters")
            String remarks) {
    }

    public record ApplicationResponse(Long id,
                                      UserSummary employee,
                                      CommonDto.LeaveTypeSummary leaveType,
                                      LocalDate startDate,
                                      LocalDate endDate,
                                      BigDecimal totalDays,
                                      String reason,
                                      String status,
                                      String managerRemarks,
                                      Long approvedById,
                                      String approvedByName,
                                      LocalDateTime appliedAt,
                                      LocalDateTime updatedAt) {
    }
}
