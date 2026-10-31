package com.smartleave.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security matrix: 401 without (or with an invalid) token, 403 when the role is
 * wrong, object level isolation between employees, and the cookie based SPA
 * session including logout.
 */
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("Anonymous requests are rejected with 401 and a JSON body")
    void anonymousRequestsAreRejected() throws Exception {
        String[] protectedEndpoints = {
                "/api/employee/dashboard",
                "/api/employee/profile",
                "/api/employee/leave-balance",
                "/api/leaves/my-requests",
                "/api/manager/dashboard",
                "/api/manager/requests",
                "/api/admin/dashboard",
                "/api/admin/employees",
                "/api/admin/reports"
        };
        for (String endpoint : protectedEndpoints) {
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    @DisplayName("A malformed or forged token is rejected with 401")
    void invalidTokenRejected() throws Exception {
        mockMvc.perform(get("/api/employee/dashboard")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Role based access: employee and manager cannot reach admin APIs")
    void roleMatrix() throws Exception {
        String employee = tokenFor("employee1@smartleave.com", "Employee@123");
        String manager = tokenFor("manager1@smartleave.com", "Manager@123");
        String admin = tokenFor("admin@smartleave.com", "Admin@123");

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/employees").header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/manager/requests").header("Authorization", "Bearer " + employee))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDepartments").value(3));
        mockMvc.perform(get("/api/manager/requests").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/employee/dashboard").header("Authorization", "Bearer " + employee))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An employee cannot read another employee's leave request")
    void employeeCannotReadOthersRequest() throws Exception {
        String employeeOne = tokenFor("employee1@smartleave.com", "Employee@123");
        String employeeTwo = tokenFor("employee2@smartleave.com", "Employee@123");

        // request 1 belongs to employee1
        mockMvc.perform(get("/api/leaves/1").header("Authorization", "Bearer " + employeeOne))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.email").value("employee1@smartleave.com"));

        // employee2 must not see it (404 - existence is not leaked)
        mockMvc.perform(get("/api/leaves/1").header("Authorization", "Bearer " + employeeTwo))
                .andExpect(status().isNotFound());

        // and cannot cancel it either
        mockMvc.perform(put("/api/leaves/1/cancel")
                        .header("Authorization", "Bearer " + employeeTwo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarks\":\"not mine\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Login failures are indistinguishable and never leak account existence")
    void loginFailures() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@smartleave.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@smartleave.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Registration can never assign MANAGER or ADMIN")
    void registrationAlwaysCreatesEmployee() throws Exception {
        String body = "{\"fullName\":\"Role Shooter\",\"email\":\"role.shooter."
                + System.nanoTime() + "@smartleave.com\",\"password\":\"Password@1\","
                + "\"departmentId\":1,\"designation\":\"Intern\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("Cookie session works for the SPA and is invalidated by logout")
    void cookieSessionLifecycle() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"employee1@smartleave.com\",\"password\":\"Employee@123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("SLM_TOKEN"))
                .andExpect(cookie().httpOnly("SLM_TOKEN", true))
                .andReturn();

        String cookieValue = login.getResponse().getCookie("SLM_TOKEN").getValue();
        assertThat(cookieValue).isNotBlank();

        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("SLM_TOKEN", cookieValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("employee1@smartleave.com"));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("SLM_TOKEN", 0));

        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("SLM_TOKEN", "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS only allows the configured React origin")
    void corsPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        MvcResult foreign = mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn();
        assertThat(foreign.getResponse().getHeader("Access-Control-Allow-Origin"))
                .as("foreign origins must not be allowed")
                .isNull();
    }
}
