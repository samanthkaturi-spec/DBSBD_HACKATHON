package com.smartleave.service;

import com.smartleave.dto.LeaveDto.ApplicationResponse;
import com.smartleave.dto.LeaveDto.ApplyRequest;
import com.smartleave.entity.Department;
import com.smartleave.entity.LeaveApplication;
import com.smartleave.entity.LeaveStatus;
import com.smartleave.entity.LeaveType;
import com.smartleave.entity.Role;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.LeaveApplicationRepository;
import com.smartleave.repository.LeaveTypeRepository;
import com.smartleave.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Business rules for submitting and cancelling a leave request (Phase 9).
 * No balance is ever touched while an application is only submitted.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveBalanceService leaveBalanceService;
    @Mock
    private NotificationService notificationService;

    private LeaveService leaveService;
    private User employee;
    private LeaveType casual;

    @BeforeEach
    void setUp() {
        leaveService = new LeaveService(userRepository, leaveApplicationRepository,
                leaveTypeRepository, leaveBalanceService, notificationService, false);

        Department department = new Department("Engineering", "dev");
        department.setId(1L);

        User manager = new User();
        manager.setId(2L);
        manager.setFullName("Priya Sharma");
        manager.setRole(Role.MANAGER);

        employee = new User();
        employee.setId(10L);
        employee.setEmployeeId("EMP1004");
        employee.setFullName("Rahul Verma");
        employee.setEmail("employee1@smartleave.com");
        employee.setRole(Role.EMPLOYEE);
        employee.setDepartment(department);
        employee.setManager(manager);
        employee.setActive(true);

        casual = new LeaveType("Casual Leave", new BigDecimal("12.0"), "personal");
        casual.setId(1L);
    }

    private static LocalDate nextMonday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private void stubEmployee() {
        when(userRepository.findWithDepartmentById(10L)).thenReturn(Optional.of(employee));
    }

    @Test
    @DisplayName("Submitting a valid request stores it as PENDING without deducting balance")
    void applyStoresPendingWithoutDeduction() {
        stubEmployee();
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(casual));
        when(leaveApplicationRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(leaveApplicationRepository.save(any(LeaveApplication.class)))
                .thenAnswer(invocation -> {
                    LeaveApplication saved = invocation.getArgument(0);
                    saved.setId(99L);
                    return saved;
                });

        LocalDate monday = nextMonday();
        ApplicationResponse response = leaveService.apply(10L,
                new ApplyRequest(1L, monday, monday.plusDays(2), "Family function"));

        ArgumentCaptor<LeaveApplication> captor = ArgumentCaptor.forClass(LeaveApplication.class);
        verify(leaveApplicationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(captor.getValue().getTotalDays()).isEqualByComparingTo("3");
        assertThat(response.status()).isEqualTo("PENDING");

        verify(leaveBalanceService, never()).deduct(anyLong(), any(), anyInt(), any());
        verify(leaveBalanceService).ensureYearBalances(any(User.class), anyInt());
        verify(notificationService, atLeastOnce()).notify(anyLong(), anyString());
    }

    @Test
    @DisplayName("Start date in the past is rejected")
    void rejectsPastStartDate() {
        stubEmployee();
        LocalDate past = LocalDate.now().minusDays(3);
        assertThatThrownBy(() -> leaveService.apply(10L, new ApplyRequest(1L, past, past, "reason text")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("past");
    }

    @Test
    @DisplayName("End date before start date is rejected")
    void rejectsEndBeforeStart() {
        stubEmployee();
        LocalDate monday = nextMonday();
        assertThatThrownBy(() -> leaveService.apply(10L,
                new ApplyRequest(1L, monday, monday.minusDays(1), "backwards")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("End date cannot be before start date");
    }

    @Test
    @DisplayName("A weekend-only range has zero duration and is rejected")
    void rejectsWeekendOnlyRange() {
        stubEmployee();
        LocalDate saturday = nextMonday().minusDays(2);
        assertThatThrownBy(() -> leaveService.apply(10L,
                new ApplyRequest(1L, saturday, saturday.plusDays(1), "weekend plans")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than zero");
        verify(leaveApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("An overlapping PENDING/APPROVED request is rejected with 409")
    void rejectsOverlappingRequest() {
        stubEmployee();
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(casual));

        LeaveApplication existing = new LeaveApplication();
        existing.setId(5L);
        existing.setStatus(LeaveStatus.APPROVED);
        when(leaveApplicationRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of(existing));

        LocalDate monday = nextMonday();
        assertThatThrownBy(() -> leaveService.apply(10L,
                new ApplyRequest(1L, monday, monday, "double booking")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Overlapping");
        verify(leaveApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Employee cannot read another employee's request")
    void cannotReadSomeoneElsesRequest() {
        when(leaveApplicationRepository.findByIdAndUser_Id(5L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> leaveService.get(5L, 10L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Cancelling a PENDING request marks it CANCELLED without touching balance")
    void cancelsPendingRequest() {
        LeaveApplication application = new LeaveApplication();
        application.setId(7L);
        application.setUser(employee);
        application.setLeaveType(casual);
        application.setStartDate(nextMonday());
        application.setEndDate(nextMonday());
        application.setTotalDays(new BigDecimal("1"));
        application.setStatus(LeaveStatus.PENDING);
        when(leaveApplicationRepository.findForUpdate(7L)).thenReturn(Optional.of(application));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));

        ApplicationResponse response = leaveService.cancel(7L, 10L, "plans changed");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.managerRemarks()).contains("plans changed");
        verify(leaveBalanceService, never()).restore(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("An employee cannot cancel somebody else's application")
    void cannotCancelOthersApplication() {
        LeaveApplication application = new LeaveApplication();
        application.setId(7L);
        application.setUser(employee);
        application.setStatus(LeaveStatus.PENDING);
        when(leaveApplicationRepository.findForUpdate(7L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveService.cancel(7L, 99L, "not mine"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("your own");
        verify(leaveApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancelling an approved future leave restores the balance")
    void cancellingApprovedRestoresBalance() {
        LeaveApplication application = new LeaveApplication();
        application.setId(8L);
        application.setUser(employee);
        application.setLeaveType(casual);
        application.setStartDate(nextMonday());
        application.setEndDate(nextMonday().plusDays(1));
        application.setTotalDays(new BigDecimal("2"));
        application.setStatus(LeaveStatus.APPROVED);
        when(leaveApplicationRepository.findForUpdate(8L)).thenReturn(Optional.of(application));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));

        ApplicationResponse response = leaveService.cancel(8L, 10L, "");

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(leaveBalanceService).restore(anyLong(), any(LeaveType.class), anyInt(),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("2")));
    }

    @Test
    @DisplayName("Approved leave that already started cannot be cancelled (policy)")
    void cannotCancelStartedApprovedLeave() {
        LeaveApplication application = new LeaveApplication();
        application.setId(9L);
        application.setUser(employee);
        application.setLeaveType(casual);
        application.setStartDate(LocalDate.now().minusDays(2));
        application.setEndDate(LocalDate.now().minusDays(1));
        application.setTotalDays(new BigDecimal("2"));
        application.setStatus(LeaveStatus.APPROVED);
        when(leaveApplicationRepository.findForUpdate(9L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveService.cancel(9L, 10L, "too late"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already started");
        verify(leaveBalanceService, never()).restore(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("A rejected application cannot be cancelled")
    void cannotCancelRejected() {
        LeaveApplication application = new LeaveApplication();
        application.setId(11L);
        application.setUser(employee);
        application.setStatus(LeaveStatus.REJECTED);
        when(leaveApplicationRepository.findForUpdate(11L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveService.cancel(11L, 10L, "x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Rejected");
    }
}
