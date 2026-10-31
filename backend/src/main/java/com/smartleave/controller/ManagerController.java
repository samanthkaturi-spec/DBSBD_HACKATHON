package com.smartleave.controller;

import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.dto.LeaveDto.RemarksRequest;
import com.smartleave.dto.ManagerDto.DashboardResponse;
import com.smartleave.dto.ManagerDto.TeamMemberSummary;
import com.smartleave.dto.CommonDto.PageResponse;
import com.smartleave.security.LoggedInUser;
import com.smartleave.service.ManagerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manager endpoints. Role MANAGER is required by the filter chain, and the
 * service layer additionally checks that the target employee reports to this
 * manager and that the manager is not deciding on their own request.
 */
@RestController
@RequestMapping("/api/manager")
public class ManagerController extends BaseController {

    private final ManagerService managerService;

    public ManagerController(ManagerService managerService) {
        this.managerService = managerService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal LoggedInUser principal) {
        return managerService.dashboard(requireUserId(principal));
    }

    @GetMapping("/requests")
    public PageResponse<ApplicationResponse> requests(@AuthenticationPrincipal LoggedInUser principal,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        Page<ApplicationResponse> result = managerService.requests(
                requireUserId(principal), status, page(page), size(size, 10));
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/team")
    public List<TeamMemberSummary> team(@AuthenticationPrincipal LoggedInUser principal) {
        return managerService.teamOverview(requireUserId(principal));
    }

    @PutMapping("/requests/{id}/approve")
    public ApplicationResponse approve(@AuthenticationPrincipal LoggedInUser principal,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) RemarksRequest request) {
        String remarks = request == null ? null : request.remarks();
        return managerService.approve(requireUserId(principal), id, remarks);
    }

    @PutMapping("/requests/{id}/reject")
    public ApplicationResponse reject(@AuthenticationPrincipal LoggedInUser principal,
                                      @PathVariable Long id,
                                      @Valid @RequestBody RemarksRequest request) {
        return managerService.reject(requireUserId(principal), id, request.remarks());
    }
}
