package com.smartleave.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application (security filter chain, JPA, transactions) against
 * the isolated MySQL test database created by {@link TestDatabase}.
 *
 * <p>Schema and demo seed are executed once per JVM before the Spring context
 * starts, so every assertion runs against real MySQL persistence - never an
 * in-memory substitute.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=validate"
})
public abstract class IntegrationTestSupport {

    static {
        TestDatabase.reset();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.URL);
        registry.add("spring.datasource.username", () -> TestDatabase.USER);
        registry.add("spring.datasource.password", () -> TestDatabase.PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected ResultActions login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("email", email, "password", password));
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected String tokenFor(String email, String password) throws Exception {
        MvcResult result = login(email, password)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    /** Next Monday strictly after today, so seeded and new ranges never go stale. */
    protected static LocalDate nextMonday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
            day = day.plusDays(1);
        }
        return day;
    }
}
