package com.islandride.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.identityaccess.dto.LoginRequest;
import com.islandride.identityaccess.dto.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "islandride.security.cookie.name=__Host-refresh_token",
        "islandride.security.cookie.secure=true",
        "islandride.security.cookie.same-site=Strict",
        "islandride.security.cookie.path=/"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@org.springframework.test.annotation.DirtiesContext
class ProductionHostCookieSpecificationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private String testEmail;
    private String testPhone;

    @BeforeEach
    void setUp() {
        testEmail = "hostcookie_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testPhone = "+9198" + UUID.randomUUID().toString().replaceAll("[^0-9]", "3").substring(0, 8);
    }

    @Test
    @DisplayName("Verify Production Set-Cookie Header Conforms to RFC 6265bis __Host- Specification")
    void testProductionHostCookieSpecificationCompliance() throws Exception {
        // 1. Register User
        RegisterRequest registerReq = new RegisterRequest(
                testEmail,
                "Password123!",
                "HostCookie",
                "Tester",
                testPhone
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // 2. Login -> inspect raw Set-Cookie header
        LoginRequest loginReq = new LoginRequest(testEmail, "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookieHeaders = loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotEmpty();

        String hostCookieHeader = setCookieHeaders.stream()
                .filter(header -> header.startsWith("__Host-refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing __Host-refresh_token in Set-Cookie headers: " + setCookieHeaders));

        // RFC 6265bis __Host- prefix requirements verification:
        // 1. Name starts with __Host-
        assertThat(hostCookieHeader).startsWith("__Host-refresh_token=");

        // 2. Secure attribute must be present
        assertThat(hostCookieHeader).contains("; Secure");

        // 3. Path attribute must be exactly '/'
        assertThat(hostCookieHeader).contains("; Path=/");

        // 4. HttpOnly attribute must be present
        assertThat(hostCookieHeader).contains("; HttpOnly");

        // 5. Must NOT contain a Domain attribute
        assertThat(hostCookieHeader.toLowerCase()).doesNotContain("domain=");

        // 6. SameSite attribute should be Strict
        assertThat(hostCookieHeader).contains("; SameSite=Strict");

        // 3. Verify Logout also strictly clears the cookie complying with __Host- rules
        Cookie refreshCookie = loginResult.getResponse().getCookie("__Host-refresh_token");
        assertThat(refreshCookie).isNotNull();

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        List<String> logoutSetCookies = logoutResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String logoutCookieHeader = logoutSetCookies.stream()
                .filter(header -> header.startsWith("__Host-refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing __Host-refresh_token in logout Set-Cookie headers"));

        assertThat(logoutCookieHeader).startsWith("__Host-refresh_token=");
        assertThat(logoutCookieHeader).contains("; Path=/");
        assertThat(logoutCookieHeader).contains("; Max-Age=0");
        assertThat(logoutCookieHeader).contains("; Secure");
        assertThat(logoutCookieHeader).contains("; HttpOnly");
        assertThat(logoutCookieHeader).contains("; SameSite=Strict");
        assertThat(logoutCookieHeader.toLowerCase()).doesNotContain("domain=");
    }
}

