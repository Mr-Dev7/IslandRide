package com.islandride.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.security.RefreshTokenService;
import com.islandride.identityaccess.domain.PasswordResetToken;
import com.islandride.identityaccess.domain.RefreshToken;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.dto.ForgotPasswordRequest;
import com.islandride.identityaccess.dto.LoginRequest;
import com.islandride.identityaccess.dto.RegisterRequest;
import com.islandride.identityaccess.dto.ResetPasswordRequest;
import com.islandride.identityaccess.repository.PasswordResetTokenRepository;
import com.islandride.identityaccess.repository.RefreshTokenRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.identityaccess.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
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
@org.springframework.test.annotation.DirtiesContext
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AuthService authService;

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
                .andExpect(cookie().path("refresh_token", "/"))
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
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(cookie().path("refresh_token", "/"));
    }

    @Test
    @DisplayName("Public registration prevents role escalation: Client cannot inject privileged roles")
    void testRoleEscalationPreventionOnRegistration() throws Exception {
        String escalationEmail = "attacker_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String escalationPhone = "+9198" + UUID.randomUUID().toString().replaceAll("[^0-9]", "1").substring(0, 8);
        String payload = """
                {
                    "email": "%s",
                    "password": "Password123!",
                    "firstName": "Attacker",
                    "lastName": "User",
                    "phone": "%s",
                    "roles": ["ROLE_SUPER_ADMIN", "ROLE_OPERATIONS_ADMIN"]
                }
                """.formatted(escalationEmail, escalationPhone);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_TOURIST"));

        User savedUser = userRepository.findByEmail(escalationEmail).orElseThrow();
        assertThat(savedUser.getRoles()).hasSize(1);
        assertThat(savedUser.getRoles().iterator().next().getName()).isEqualTo("ROLE_TOURIST");
    }

    @Test
    @DisplayName("CSRF validation rejects mutating requests lacking valid CSRF token")
    void testCsrfProtectionEnforcedOnCookieEndpoints() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                testEmail,
                "Password123!",
                "Csrf",
                "Tester",
                testPhone
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest(testEmail, "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        // Calling /refresh WITHOUT csrf() token -> must be rejected with 403 Forbidden
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Calling /refresh WITH Spring test csrf() helper -> succeeds with 200 OK
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().path("refresh_token", "/"));
    }

    @Test
    @DisplayName("Logout revokes active refresh token and prevents further token refresh")
    void testLogoutRevokesRefreshToken() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                testEmail,
                "Password123!",
                "Logout",
                "Tester",
                testPhone
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest(testEmail, "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        String rawToken = refreshCookie.getValue();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(cookie().path("refresh_token", "/"));

        // Verify token in database is marked revoked
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        Optional<RefreshToken> revokedToken = refreshTokenRepository.findByTokenHash(tokenHash);
        assertThat(revokedToken).isPresent();
        assertThat(revokedToken.get().isRevoked()).isTrue();

        // Attempting to refresh with the logged-out cookie must be rejected with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Expired and invalid access tokens are strictly rejected by security filter")
    void testExpiredAndInvalidAccessTokensRejected() throws Exception {
        // 1. Completely malformed token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.malformed.jwt.token"))
                .andExpect(status().isUnauthorized());

        // 2. Token signed with wrong key / expired
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"));
        String expiredJwt = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("userId", 999999L)
                .claim("email", "expired@test.com")
                .claim("roles", List.of("ROLE_TOURIST"))
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Password reset tokens: Single-use enforcement and replay rejection")
    void testPasswordResetTokenSingleUseAndExpiryEnforcement() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                testEmail,
                "OldPassword123!",
                "Reset",
                "Tester",
                testPhone
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(testEmail).orElseThrow();

        // Create an explicit raw token and save it to test the full endpoint
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken explicitToken = new PasswordResetToken();
        explicitToken.setUser(user);
        explicitToken.setTokenHash(RefreshTokenService.hashToken(rawToken));
        explicitToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        explicitToken.setUsed(false);
        passwordResetTokenRepository.save(explicitToken);

        // 1. Reset password with valid token
        ResetPasswordRequest resetReq = new ResetPasswordRequest(rawToken, "NewSecurePassword123!");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been successfully updated"));

        // Verify user can now log in with the new password
        LoginRequest newLoginReq = new LoginRequest(testEmail, "NewSecurePassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk());

        // 2. Token reuse / Replay attack attempt: Using the SAME token a second time must fail!
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isUnprocessableEntity());
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
