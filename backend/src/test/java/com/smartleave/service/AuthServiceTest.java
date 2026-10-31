package com.smartleave.service;

import com.smartleave.dto.AuthDto.LoginRequest;
import com.smartleave.dto.AuthDto.RegisterRequest;
import com.smartleave.dto.AuthDto.AuthResponse;
import com.smartleave.entity.Department;
import com.smartleave.entity.Role;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.DepartmentRepository;
import com.smartleave.repository.UserRepository;
import com.smartleave.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authentication rules: BCrypt verification, deactivated accounts and the
 * guarantee that self-registration can only ever create an EMPLOYEE.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private LeaveBalanceService leaveBalanceService;

    private AuthService authService;
    private User employee;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, departmentRepository, passwordEncoder,
                jwtService, leaveBalanceService, true);

        Department department = new Department("Engineering", "dev");
        department.setId(1L);

        employee = new User();
        employee.setId(10L);
        employee.setEmployeeId("EMP1004");
        employee.setFullName("Rahul Verma");
        employee.setEmail("employee1@smartleave.com");
        employee.setPasswordHash("$2a$10$hashed");
        employee.setRole(Role.EMPLOYEE);
        employee.setDepartment(department);
        employee.setDesignation("Engineer");
        employee.setActive(true);

        lenient().when(jwtService.generateToken(anyLong(), anyString(), any(), anyString()))
                .thenReturn("generated-jwt");
        lenient().when(jwtService.getExpirationMs()).thenReturn(3600_000L);
    }

    @Test
    @DisplayName("Correct password issues a JWT")
    void loginWithCorrectPassword() {
        when(userRepository.findByEmailIgnoreCase("employee1@smartleave.com"))
                .thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("Employee@123", "$2a$10$hashed")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest("employee1@smartleave.com", "Employee@123"));

        assertThat(response.token()).isEqualTo("generated-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().role()).isEqualTo("EMPLOYEE");
        assertThat(response.user().email()).isEqualTo("employee1@smartleave.com");
    }

    @Test
    @DisplayName("Wrong password is rejected with 401")
    void wrongPassword() {
        when(userRepository.findByEmailIgnoreCase("employee1@smartleave.com"))
                .thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("bad", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("employee1@smartleave.com", "bad")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email or password");
        verify(jwtService, never()).generateToken(anyLong(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Unknown email is rejected with the same message as a wrong password")
    void unknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ghost@smartleave.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@smartleave.com", "whatever")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("A deactivated employee cannot log in")
    void deactivatedAccount() {
        employee.setActive(false);
        when(userRepository.findByEmailIgnoreCase("employee1@smartleave.com"))
                .thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("Employee@123", "$2a$10$hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("employee1@smartleave.com", "Employee@123")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    @DisplayName("Public registration always creates an EMPLOYEE, never a manager or admin")
    void registrationIsAlwaysEmployee() {
        when(userRepository.existsByEmailIgnoreCase("new.joiner@smartleave.com")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(new Department("Engineering", "dev")));
        when(passwordEncoder.encode("Password@1")).thenReturn("$2a$10$encoded");
        when(userRepository.existsByEmployeeIdIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequest("New Joiner", "new.joiner@smartleave.com",
                "Password@1", 1L, "Analyst"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$encoded");
        assertThat(captor.getValue().isActive()).isTrue();
        verify(leaveBalanceService).ensureYearBalances(any(User.class), anyInt());
    }

    @Test
    @DisplayName("Registration with an existing email conflicts")
    void duplicateRegistration() {
        when(userRepository.existsByEmailIgnoreCase("employee1@smartleave.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Dup", "employee1@smartleave.com",
                "Password@1", 1L, "Analyst")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registration without a department is rejected")
    void registrationRequiresDepartment() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest("No Dept", "no.dept@smartleave.com",
                "Password@1", null, "Analyst")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Department is required");
    }

    @Test
    @DisplayName("Registration can be switched off by configuration")
    void registrationCanBeDisabled() {
        AuthService disabled = new AuthService(userRepository, departmentRepository, passwordEncoder,
                jwtService, leaveBalanceService, false);
        assertThatThrownBy(() -> disabled.register(new RegisterRequest("Blocked", "blocked@smartleave.com",
                "Password@1", 1L, "Analyst")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("disabled");
    }
}
