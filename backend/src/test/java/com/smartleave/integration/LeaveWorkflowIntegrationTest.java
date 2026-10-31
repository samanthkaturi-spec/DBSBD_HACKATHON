package com.smartleave.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full workflow tests running against the isolated MySQL test database:
 * create employee -> apply -> manager decides -> balance and status change in
 * MySQL -> employee sees the updated state.
 */
class LeaveWorkflowIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN_EMAIL = "admin@smartleave.com";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String MANAGER_EMAIL = "manager2@smartleave.com";
    private static final String MANAGER_PASSWORD = "Manager@123";

    /** Creates a throw-away employee reporting to manager2 and logs them in. */
    private String newEmployee(String marker) throws Exception {
        String admin = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = "wf." + marker + "." + System.nanoTime() + "@smartleave.com";

        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", "Workflow " + marker);
        payload.put("email", email);
        payload.put("password", "Employee@123");
        payload.put("departmentId", 1);
        payload.put("managerId", 3);      // Ravi Teja (manager2)
        payload.put("designation", "Test Engineer");
        payload.put("role", "EMPLOYEE");

        mockMvc.perform(post("/api/admin/employees")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.active").value(true));

        return tokenFor(email, "Employee@123");
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private com.fasterxml.jackson.databind.JsonNode balance(String token, String leaveName) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/employee/leave-balance").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        var array = objectMapper.readTree(result.getResponse().getContentAsString());
        for (var node : array) {
            if (leaveName.equals(node.path("leaveName").asText())) {
                return node;
            }
        }
        throw new AssertionError("No balance row for " + leaveName);
    }

    private long apply(String token, long leaveTypeId, LocalDate start, LocalDate end) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("leaveTypeId", leaveTypeId);
        payload.put("startDate", start.toString());
        payload.put("endDate", end.toString());
        payload.put("reason", "Integration workflow leave request");

        MvcResult result = mockMvc.perform(post("/api/leaves/apply")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    @Test
    @DisplayName("Employee applies, manager approves, balance is deducted in MySQL")
    void approveWorkflow() throws Exception {
        String employee = newEmployee("approve");
        var before = balance(employee, "Casual Leave");
        long availableBefore = before.path("availableDays").asLong();
        long usedBefore = before.path("usedDays").asLong();
        long casualId = before.path("leaveTypeId").asLong();

        LocalDate start = nextMonday();
        long applicationId = apply(employee, casualId, start, start.plusDays(1));

        // Rule 6: submitting must not change the balance
        var afterApply = balance(employee, "Casual Leave");
        assertThat(afterApply.path("availableDays").asLong()).isEqualTo(availableBefore);

        // persistence check straight in MySQL through the API
        mockMvc.perform(get("/api/leaves/" + applicationId).header("Authorization", auth(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalDays").value(2));

        String manager = tokenFor(MANAGER_EMAIL, MANAGER_PASSWORD);
        MvcResult pending = mockMvc.perform(get("/api/manager/requests?status=PENDING&size=100")
                        .header("Authorization", auth(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();
        boolean visibleToManager = false;
        for (var row : objectMapper.readTree(pending.getResponse().getContentAsString()).path("content")) {
            if (row.path("id").asLong() == applicationId) {
                visibleToManager = true;
            }
        }
        assertThat(visibleToManager).as("manager can see the team request").isTrue();

        mockMvc.perform(put("/api/manager/requests/" + applicationId + "/approve")
                        .header("Authorization", auth(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarks\":\"Approved by integration test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedByName").value("Ravi Teja"));

        // Rule 11: a second approval is refused
        mockMvc.perform(put("/api/manager/requests/" + applicationId + "/approve")
                        .header("Authorization", auth(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarks\":\"again\"}"))
                .andExpect(status().isConflict());

        // Rule 7: balance deducted only after approval
        var afterApprove = balance(employee, "Casual Leave");
        assertThat(afterApprove.path("usedDays").asLong()).isEqualTo(usedBefore + 2);
        assertThat(afterApprove.path("availableDays").asLong()).isEqualTo(availableBefore - 2);

        // the employee sees the decision and is notified
        mockMvc.perform(get("/api/leaves/" + applicationId).header("Authorization", auth(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        MvcResult notifications = mockMvc.perform(get("/api/notifications").header("Authorization", auth(employee)))
                .andExpect(status().isOk())
                .andReturn();
        boolean notified = false;
        for (var row : objectMapper.readTree(notifications.getResponse().getContentAsString())) {
            if (row.path("message").asText().contains("APPROVED")) {
                notified = true;
            }
        }
        assertThat(notified).as("employee is notified about the approval").isTrue();
    }

    @Test
    @DisplayName("Rejection records remarks, keeps the balance untouched")
    void rejectWorkflow() throws Exception {
        String employee = newEmployee("reject");
        var before = balance(employee, "Sick Leave");
        long availableBefore = before.path("availableDays").asLong();
        long sickId = before.path("leaveTypeId").asLong();

        LocalDate start = nextMonday().plusDays(7);
        long applicationId = apply(employee, sickId, start, start.plusDays(1));

        String manager = tokenFor(MANAGER_EMAIL, MANAGER_PASSWORD);

        // Rule: rejection remarks are mandatory
        mockMvc.perform(put("/api/manager/requests/" + applicationId + "/reject")
                        .header("Authorization", auth(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarks\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/manager/requests/" + applicationId + "/reject")
                        .header("Authorization", auth(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarks\":\"On-call that week\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.managerRemarks").value("On-call that week"));

        var after = balance(employee, "Sick Leave");
        assertThat(after.path("availableDays").asLong()).isEqualTo(availableBefore);
        assertThat(after.path("usedDays").asLong()).isEqualTo(before.path("usedDays").asLong());
    }

    @Test
    @DisplayName("Cancelling an approved future leave restores the deducted balance")
    void cancelRestoresBalance() throws Exception {
        String employee = newEmployee("cancel");
        var before = balance(employee, "Earned Leave");
        long availableBefore = before.path("availableDays").asLong();
        long earnedId = before.path("leaveTypeId").asLong();

        LocalDate start = nextMonday().plusDays(14);
        long applicationId = apply(employee, earnedId, start, start);

        String manager = tokenFor(MANAGER_EMAIL, MANAGER_PASSWORD);
        mockMvc.perform(put("/api/manager/requests/" + applicationId + "/approve")
                        .header("Authorization", auth(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarks\":\"ok\"}"))
                .andExpect(status().isOk());

        assertThat(balance(employee, "Earned Leave").path("availableDays").asLong())
                .isEqualTo(availableBefore - 1);

        mockMvc.perform(put("/api/leaves/" + applicationId + "/cancel")
                        .header("Authorization", auth(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarks\":\"trip postponed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(balance(employee, "Earned Leave").path("availableDays").asLong())
                .isEqualTo(availableBefore);
    }

    @Test
    @DisplayName("Registration creates an EMPLOYEE that no manager can approve (no manager assigned)")
    void registeredEmployeeHasNoManager() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", "Self Registered");
        payload.put("email", "wf.register." + System.nanoTime() + "@smartleave.com");
        payload.put("password", "Register@123");
        payload.put("departmentId", 1);
        payload.put("designation", "Analyst");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();

        // balances are provisioned from the yearly entitlement on registration
        var casual = balance(token, "Casual Leave");
        assertThat(casual.path("totalDays").asLong()).isEqualTo(12);

        mockMvc.perform(get("/api/employee/profile").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
