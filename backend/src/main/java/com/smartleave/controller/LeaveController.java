package com.smartleave.controller;

import com.smartleave.dto.CommonDto.LeaveTypeSummary;
import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.dto.LeaveDto.ApplyRequest;
import com.smartleave.dto.LeaveDto.RemarksRequest;
import com.smartleave.entity.LeaveType;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.LeaveTypeRepository;
import com.smartleave.security.LoggedInUser;
import com.smartleave.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Leave application endpoints. Ownership is always taken from the principal,
 * never from the URL or body, so an employee can only read or cancel their
 * own requests.
 */
@RestController
@RequestMapping("/api")
public class LeaveController extends BaseController {

    private final LeaveService leaveService;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveController(LeaveService leaveService, LeaveTypeRepository leaveTypeRepository) {
        this.leaveService = leaveService;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @GetMapping("/leave-types")
    public List<LeaveTypeSummary> leaveTypes() {
        return leaveTypeRepository.findByActiveTrueOrderByLeaveNameAsc().stream()
                .map(LeaveController::toSummary)
                .toList();
    }

    @PostMapping("/leaves/apply")
    public ResponseEntity<ApplicationResponse> apply(@AuthenticationPrincipal LoggedInUser principal,
                                                      @Valid @RequestBody ApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveService.apply(requireUserId(principal), request));
    }

    @GetMapping("/leaves/my-requests")
    public List<ApplicationResponse> myRequests(@AuthenticationPrincipal LoggedInUser principal) {
        return leaveService.myRequests(requireUserId(principal));
    }

    @GetMapping("/leaves/{id}")
    public ApplicationResponse get(@AuthenticationPrincipal LoggedInUser principal,
                                   @PathVariable Long id) {
        return leaveService.get(id, requireUserId(principal));
    }

    @PutMapping("/leaves/{id}/cancel")
    public ApplicationResponse cancel(@AuthenticationPrincipal LoggedInUser principal,
                                      @PathVariable Long id,
                                      @RequestBody(required = false) RemarksRequest request) {
        String remarks = request == null ? null : request.remarks();
        return leaveService.cancel(id, requireUserId(principal), remarks);
    }

    private static LeaveTypeSummary toSummary(LeaveType type) {
        return new LeaveTypeSummary(type.getId(), type.getLeaveName(), type.getAnnualEntitlement(),
                type.getDescription(), type.isActive());
    }
}
