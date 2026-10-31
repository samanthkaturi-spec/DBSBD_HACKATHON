package com.smartleave.dto;

import com.smartleave.entity.User;

import java.math.BigDecimal;
import java.util.List;

/** Response shapes shared by every module. JPA entities are never returned. */
public final class CommonDto {

    private CommonDto() {
    }

    /** Flat, JSON friendly projection of a user. */
    public record UserSummary(Long id,
                              String employeeId,
                              String fullName,
                              String email,
                              String role,
                              Long departmentId,
                              String departmentName,
                              Long managerId,
                              String managerName,
                              String designation,
                              boolean active) {

        public static UserSummary from(User user) {
            if (user == null) {
                return null;
            }
            return new UserSummary(
                    user.getId(),
                    user.getEmployeeId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getRole() == null ? null : user.getRole().name(),
                    user.getDepartment() == null ? null : user.getDepartment().getId(),
                    user.getDepartment() == null ? null : user.getDepartment().getDepartmentName(),
                    user.getManager() == null ? null : user.getManager().getId(),
                    user.getManager() == null ? null : user.getManager().getFullName(),
                    user.getDesignation(),
                    user.isActive());
        }
    }

    public record LeaveTypeSummary(Long id, String leaveName, BigDecimal annualEntitlement,
                                   String description, boolean active) {
    }

    public record PageResponse<T>(List<T> content,
                                  int page,
                                  int size,
                                  long totalElements,
                                  int totalPages,
                                  boolean last) {

        public static <T> PageResponse<T> of(List<T> content, int page, int size,
                                             long totalElements, int totalPages) {
            boolean last = page + 1 >= totalPages || content.isEmpty();
            return new PageResponse<>(content, page, size, totalElements, totalPages, last);
        }
    }

    public record MessageResponse(String message) {
    }
}
