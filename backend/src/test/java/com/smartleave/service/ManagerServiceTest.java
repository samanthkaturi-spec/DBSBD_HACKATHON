package com.smartleave.service;

import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.entity.Department;
import com.smartleave.entity.LeaveApplication;
import com.smartleave.entity.LeaveStatus;
import com.smartleave.entity.LeaveType;
import com.smartleave.entity.Role;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.LeaveApplicationRepository;
import com.smartleave.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Manager decision rules: team scoping, self-approval ban, single decision per
 * request, balance deduction only on approval, mandatory rejection remarks.
 */
@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;
    @Mock
    private LeaveBalanceService leaveBalanceService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private LeaveService leaveService;

    private ManagerService managerService;
    private User manager;
    private User employee;
    private LeaveType casual;

    @BeforeEach
    void setUp() {
        managerService = new ManagerService(userRepository, leaveApplicationRepository,
                leaveBalanceService, notificationService, leaveService);

        Department department = new Department("Engineering", "dev");
        department.setId(1L);

        manager = new User();
        manager.setId(2L);
        manager.setEmployeeId("EMP1002");
        manager.setEmail("manager1@smartleave.com");
        manager.setFullName("Priya Sharma");
        manager.setRole(Role.MANAGER);
        manager.setActive(true);
        manager.setDepartment(department);

        employee = new User();
        employee.setId(10L);
        employee.setEmployeeId("EMP1004");
        employee.setFullName("Rahul Verma");
        employee.setRole(Role.EMPLOYEE);
        employee.setDepartment(department);
        employee.setManager(manager);
        employee.setActive(true);

        casual = new LeaveType("Casual Leave", new BigDecimal("12.0"), "personal");
        casual.setId(1L);

        // LeaveService is only used as a mapper here; build a response from the
        // entity under test so assertions reflect the real state.
        lenient().when(leaveService.toResponse(any(LeaveApplication.class))).thenAnswer(invocation -> {
            LeaveApplication application = invocation.getArgument(0);
            return new ApplicationResponse(application.getId(), null, null,
                    application.getStartDate(), application.getEndDate(), application.getTotalDays(),
                    application.getReason(), application.getStatus().name(), application.getManagerRemarks(),
                    null, null, application.getAppliedAt(), application.getUpdatedAt());
        });
    }

    private LeaveApplication pendingApplication() {
        LeaveApplication application = new LeaveApplication();
        application.setId(55L);
        application.setUser(employee);
        application.setLeaveType(casual);
        application.setStartDate(nextMonday());
        application.setEndDate(nextMonday().plusDays(1));
        application.setTotalDays(new BigDecimal("2"));
        application.setStatus(LeaveStatus.PENDING);
        return application;
    }

    private static LocalDate nextMonday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    @Test
    @DisplayName("Approval deducts the balance, records the approver and notifies the employee")
    void approvesAndDeductsBalance() {
        LeaveApplication application = pendingApplication();
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveApplicationRepository.findForUpdate(55L)).thenReturn(Optional.of(application));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.getReferenceById(2L)).thenReturn(manager);

        ApplicationResponse response = managerService.approve(2L, 55L, "Enjoy");

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(application.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(application.getApprovedBy()).isEqualTo(manager);
        assertThat(application.getManagerRemarks()).isEqualTo("Enjoy");
        verify(leaveBalanceService).deduct(eq(10L), eq(casual), eq(nextMonday().getYear()),
                eq(new BigDecimal("2")));
        verify(notificationService).notify(eq(10L), anyString());
    }

    @Test
    @DisplayName("Insufficient balance aborts the approval")
    void insufficientBalanceAbortsWithoutApproving() {
        LeaveApplication application = pendingApplication();
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveApplicationRepository.findForUpdate(55L)).thenReturn(Optional.of(application));
        doThrow(ApiException.badRequest("Insufficient leave balance for Casual Leave"))
                .when(leaveBalanceService).deduct(anyLong(), any(), anyInt(), any());

        assertThatThrownBy(() -> managerService.approve(2L, 55L, "ok"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient leave balance");

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING);
        verify(notificationService, never()).notify(anyLong(), anyString());
    }

    @Test
    @DisplayName("A request that is no longer PENDING cannot be decided twice")
    void rejectsRepeatedDecision() {
        LeaveApplication application = pendingApplication();
        application.setStatus(LeaveStatus.APPROVED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveApplicationRepository.findForUpdate(55L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> managerService.approve(2L, 55L, "again"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already");
        verify(leaveBalanceService, never()).deduct(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("A manager cannot decide on a request outside their team")
    void blocksOutOfTeamDecision() {
        employee.setManager(null);
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveApplicationRepository.findForUpdate(55L)).thenReturn(Optional.of(pendingApplication()));

        assertThatThrownBy(() -> managerService.approve(2L, 55L, "not mine"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own team");
        verify(leaveBalanceService, never()).deduct(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("A manager cannot approve their own request")
    void blocksSelfApproval() {
        User selfManaged = new User();
        selfManaged.setId(2L);
        selfManaged.setRole(Role.MANAGER);
        selfManaged.setManager(manager);
        selfManaged.setDepartment(new Department("Engineering", "dev"));

        LeaveApplication own = pendingApplication();
        own.setUser(selfManaged);

        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveApplicationRepository.findForUpdate(55L)).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> managerService.approve(2L, 55L, "self"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own leave request");
    }

    @Test
    @DisplayName("Rejection without remarks is rejected")
    void rejectsWithoutRemarks() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        assertThatThrownBy(() -> managerService.reject(2L, 55L, "   "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Rejection remarks are required");
        verify(leaveApplicationRepository, never()).findForUpdate(anyLong());
    }

    @Test
    @DisplayName("Rejection records the decision but never changes the balance")
    void rejectDoesNotTouchBalance() {
        LeaveApplication application = pendingApplication();
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveApplicationRepository.findForUpdate(55L)).thenReturn(Optional.of(application));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.getReferenceById(2L)).thenReturn(manager);

        ApplicationResponse response = managerService.reject(2L, 55L, "Release week");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(application.getManagerRemarks()).isEqualTo("Release week");
        verify(leaveBalanceService, never()).deduct(anyLong(), any(), anyInt(), any());
        verify(leaveBalanceService, never()).restore(anyLong(), any(), anyInt(), any());
        verify(notificationService).notify(eq(10L), anyString());
    }

    @Test
    @DisplayName("Unknown request id returns 404")
    void unknownRequest() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveApplicationRepository.findForUpdate(4242L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> managerService.approve(2L, 4242L, "x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Unknown status filter is rejected with a helpful message")
    void unknownStatusFilter() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        assertThatThrownBy(() -> managerService.requests(2L, "MAYBE", 0, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown status");
    }
}
