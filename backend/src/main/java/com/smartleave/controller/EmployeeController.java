package com.smartleave.controller;

import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.dto.EmployeeDto.DashboardResponse;
import com.smartleave.dto.EmployeeDto.LeaveBalanceDto;
import com.smartleave.dto.EmployeeDto.NotificationDto;
import com.smartleave.dto.CommonDto.MessageResponse;
import com.smartleave.security.LoggedInUser;
import com.smartleave.service.EmployeeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Employee self-service endpoints (ROLE_EMPLOYEE, MANAGER or ADMIN). */
@RestController
@RequestMapping("/api")
public class EmployeeController extends BaseController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/employee/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal LoggedInUser principal) {
        return employeeService.dashboard(requireUserId(principal));
    }

    @GetMapping("/employee/profile")
    public UserSummary profile(@AuthenticationPrincipal LoggedInUser principal) {
        return employeeService.profile(requireUserId(principal));
    }

    @GetMapping("/employee/leave-balance")
    public List<LeaveBalanceDto> leaveBalance(@AuthenticationPrincipal LoggedInUser principal,
                                              @RequestParam(required = false) Integer year) {
        return employeeService.balances(requireUserId(principal), year);
    }

    @GetMapping("/notifications")
    public List<NotificationDto> notifications(@AuthenticationPrincipal LoggedInUser principal) {
        return employeeService.notifications(requireUserId(principal));
    }

    @GetMapping("/notifications/unread-count")
    public MessageResponse unreadCount(@AuthenticationPrincipal LoggedInUser principal) {
        return new MessageResponse(String.valueOf(employeeService.unreadCount(requireUserId(principal))));
    }

    @PostMapping("/notifications/read-all")
    public MessageResponse markAllRead(@AuthenticationPrincipal LoggedInUser principal) {
        int updated = employeeService.markNotificationsRead(requireUserId(principal));
        return new MessageResponse(updated + " notification(s) marked as read");
    }
}
