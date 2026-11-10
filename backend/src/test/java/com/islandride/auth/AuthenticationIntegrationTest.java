package com.islandride.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.security.RefreshTokenService;
import com.islandride.identityaccess.domain.RefreshToken;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.dto.LoginRequest;
import com.islandride.identityaccess.dto.RegisterRequest;
import com.islandride.identityaccess.repository.RefreshTokenRepository;
import com.islandride.identityaccess.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@org.springframework.transaction.annotation.Transactional
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String testEmail;
    private String testPhone;

    @BeforeEach
    void setUp() {
        testEmail = "tourist_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testPhone = "+9198" + UUID.randomUUID().toString().replaceAll("[^0-9]", "1").substring(0, 8);
    }

    @Test
    @DisplayName("Complete Tourist Auth Lifecycle: Register, Login, Refresh, and Logout")
    void testTouristAuthLifecycle() throws Exception {
        // 1. Register Tourist
        RegisterRequest registerReq = new RegisterRequest(
                testEmail,
                "Password123!",
                "Anand",
                "Sharma",
                testPhone
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.firstName").value("Anand"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_TOURIST"));

        // 2. Duplicate Registration Rejection
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isUnprocessableEntity());

        // 3. Login
        LoginRequest loginReq = new LoginRequest(testEmail, "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        String rawRefreshToken = refreshCookie.getValue();

        // Verify Refresh Token is hashed in MySQL
        String tokenHash = RefreshTokenService.hashToken(rawRefreshToken);
        Optional<RefreshToken> storedToken = refreshTokenRepository.findByTokenHash(tokenHash);
        assertThat(storedToken).isPresent();
        assertThat(storedToken.get().isRevoked()).isFalse();
        assertThat(storedToken.get().getFamilyId()).isNotEmpty();

        // 4. Token Rotation (/refresh)
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertThat(rotatedRefreshCookie).isNotNull();
        assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(rawRefreshToken);

        // Verify Old Token is Revoked in DB
        Optional<RefreshToken> oldTokenAfterRotation = refreshTokenRepository.findByTokenHash(tokenHash);
        assertThat(oldTokenAfterRotation).isPresent();
        assertThat(oldTokenAfterRotation.get().isRevoked()).isTrue();

        // 5. Replay Attack Detection: Presenting the already rotated (revoked) token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie) // presenting the OLD raw token
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // Entire family must now be revoked
        String newHash = RefreshTokenService.hashToken(rotatedRefreshCookie.getValue());
        Optional<RefreshToken> newTokenAfterReplay = refreshTokenRepository.findByTokenHash(newHash);
        assertThat(newTokenAfterReplay).isPresent();
        assertThat(newTokenAfterReplay.get().isRevoked()).isTrue(); // Family revoked!

        // 6. Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    @DisplayName("Login rejects invalid credentials")
    void testLoginInvalidCredentials() throws Exception {
        LoginRequest badPassword = new LoginRequest("nonexistent@example.com", "WrongPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login rejects locked user accounts")
    void testLockedUserAccountLogin() throws Exception {
        // Register user
        RegisterRequest registerReq = new RegisterRequest(
                testEmail,
                "Password123!",
                "Locked",
                "User",
                null
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Lock the user in DB
        User user = userRepository.findByEmail(testEmail).orElseThrow();
        user.setStatus(UserStatus.LOCKED);
        userRepository.save(user);

        // Attempt login
        LoginRequest loginReq = new LoginRequest(testEmail, "Password123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }
}
