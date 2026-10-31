package com.smartleave.service;

import com.smartleave.dto.AuthDto.AuthResponse;
import com.smartleave.dto.AuthDto.LoginRequest;
import com.smartleave.dto.AuthDto.RegisterRequest;
import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.entity.Department;
import com.smartleave.entity.Role;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.DepartmentRepository;
import com.smartleave.repository.UserRepository;
import com.smartleave.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication: BCrypt verification, JWT issuance and employee-only
 * self-registration.
 *
 * <p>Public registration can never assign MANAGER or ADMIN: the role is
 * hard coded to {@link Role#EMPLOYEE}. Administrative accounts are created
 * from seed data or through the protected admin API.</p>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LeaveBalanceService leaveBalanceService;
    private final boolean registrationEnabled;

    public AuthService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LeaveBalanceService leaveBalanceService,
                       @Value("${smartleave.registration.enabled:true}") boolean registrationEnabled) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.leaveBalanceService = leaveBalanceService;
        this.registrationEnabled = registrationEnabled;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        // Same message for unknown email and wrong password: no account enumeration.
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "This account has been deactivated. Contact your administrator.");
        }
        return issueToken(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!registrationEnabled) {
            throw ApiException.forbidden("Public registration is disabled on this server");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        if (request.departmentId() == null) {
            throw ApiException.badRequest("Department is required");
        }
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.badRequest("Unknown department id " + request.departmentId()));

        User user = new User();
        user.setEmployeeId(generateEmployeeId());
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.EMPLOYEE);              // never taken from the request
        user.setDepartment(department);
        user.setDesignation(request.designation() == null || request.designation().isBlank()
                ? "Executive" : request.designation().trim());
        user.setActive(true);
        user = userRepository.save(user);

        leaveBalanceService.ensureYearBalances(user, java.time.LocalDate.now().getYear());
        return issueToken(user);
    }

    /** Builds the login payload for an already authenticated user. */
    @Transactional(readOnly = true)
    public AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole(), user.getFullName());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), UserSummary.from(user));
    }

    /** Profile of the currently authenticated principal (never from request input). */
    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        return userRepository.findWithDepartmentById(userId)
                .map(UserSummary::from)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }

    private String generateEmployeeId() {
        for (int attempt = 0; attempt < 50; attempt++) {
            int number = 1000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9000);
            String candidate = "EMP" + number;
            if (!userRepository.existsByEmployeeIdIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return "EMP" + System.currentTimeMillis() % 100000;
    }
}
