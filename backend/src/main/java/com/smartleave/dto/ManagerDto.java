package com.smartleave.dto;

import com.smartleave.dto.CommonDto.UserSummary;

import java.util.List;

/** Manager module payloads. */
public final class ManagerDto {

    private ManagerDto() {
    }

    /** One row of the team overview grid. */
    public record TeamMemberSummary(UserSummary employee,
                                    long pendingCount,
                                    long approvedCount,
                                    long rejectedCount) {
    }

    /**
     * @param teamSize       employees whose manager is the current manager
     * @param pendingCount   requests waiting for a decision
     * @param approvedCount  approved requests from the team
     * @param rejectedCount  rejected requests from the team
     * @param onLeaveToday   approved requests whose date range covers today
     */
    public record DashboardResponse(long teamSize,
                                    long pendingCount,
                                    long approvedCount,
                                    long rejectedCount,
                                    long onLeaveToday,
                                    List<LeaveDto.ApplicationResponse> recentApplications,
                                    List<TeamMemberSummary> team) {
    }
}
