package com.smartleave.controller;

import com.smartleave.dto.AdminDto.CreateDepartmentRequest;
import com.smartleave.dto.AdminDto.CreateEmployeeRequest;
import com.smartleave.dto.AdminDto.DashboardResponse;
import com.smartleave.dto.AdminDto.DepartmentDto;
import com.smartleave.dto.AdminDto.ReportResponse;
import com.smartleave.dto.AdminDto.UpdateEmployeeRequest;
import com.smartleave.dto.AdminDto.UpdateBalanceRequest;
import com.smartleave.dto.CommonDto.MessageResponse;
import com.smartleave.dto.CommonDto.PageResponse;
import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.dto.EmployeeDto.LeaveBalanceDto;
import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.security.LoggedInUser;
import com.smartleave.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administration / HR endpoints - ROLE_ADMIN only (enforced by the filter chain). */
@RestController
@RequestMapping("/api/admin")
public class AdminController extends BaseController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return adminService.dashboard();
    }

    // ------------------------------------------------------------ employees
    @GetMapping("/employees")
    public PageResponse<UserSummary> employees(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Long departmentId,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return adminService.employees(q, departmentId, active, page(page), size(size, 10));
    }

    @PostMapping("/employees")
    public ResponseEntity<UserSummary> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createEmployee(request));
    }

    @PutMapping("/employees/{id}")
    public UserSummary updateEmployee(@PathVariable Long id,
                                      @Valid @RequestBody UpdateEmployeeRequest request) {
        return adminService.updateEmployee(id, request);
    }

    @DeleteMapping("/employees/{id}")
    public UserSummary deactivateEmployee(@PathVariable Long id,
                                          @AuthenticationPrincipal LoggedInUser principal) {
        return adminService.deactivateEmployee(id, requireUserId(principal));
    }

    @GetMapping("/employees/{id}/leave-balances")
    public List<LeaveBalanceDto> employeeBalances(@PathVariable Long id) {
        return adminService.employeeBalances(id);
    }

    @PutMapping("/leave-balances/{id}")
    public LeaveBalanceDto updateBalance(@PathVariable Long id,
                                         @Valid @RequestBody UpdateBalanceRequest request) {
        if (!id.equals(request.id())) {
            throw com.smartleave.exception.ApiException.badRequest("Balance id in the path and body must match");
        }
        return adminService.updateBalance(request);
    }

    // ---------------------------------------------------------- departments
    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return adminService.departments();
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createDepartment(request));
    }

    // --------------------------------------------------------- applications
    @GetMapping("/applications")
    public PageResponse<ApplicationResponse> applications(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return adminService.allApplications(status, page(page), size(size, 10));
    }

    @GetMapping("/reports")
    public ReportResponse reports() {
        return adminService.reports();
    }

    @GetMapping("/ping")
    public MessageResponse ping() {
        return new MessageResponse("admin api ok");
    }
}
