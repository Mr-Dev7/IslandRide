package com.islandride.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.security.JwtTokenProvider;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.core.security.UserPrincipal;
import com.islandride.identityaccess.domain.Role;
import com.islandride.identityaccess.domain.RoleType;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.repository.RoleRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.dto.PartnerRegisterRequest;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.partner.service.PartnerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthorizationAndTenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private PartnerStaffRepository partnerStaffRepository;

    @Autowired
    private PartnerService partnerService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TenantSecurityService tenantSecurityService;

    @Test
    @DisplayName("Unauthenticated requests to protected endpoints return 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tourist/profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/partners/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tourist cannot access Partner or Admin endpoints (Role Lockdown)")
    void testTouristRoleLockdown() throws Exception {
        // Create Tourist user
        User tourist = createTestUser("tourist_rbac_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com", RoleType.ROLE_TOURIST);
        UserPrincipal principal = UserPrincipal.create(tourist, null);
        String token = jwtTokenProvider.generateAccessToken(principal);

        // Accessing Partner endpoint should return 403 Forbidden
        mockMvc.perform(get("/api/v1/partners/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Accessing Admin endpoint should return 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Partner Onboarding: Active user account does not authorize inventory until business is APPROVED")
    void testPartnerOnboardingSeparationAndApproval() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        PartnerRegisterRequest regReq = new PartnerRegisterRequest(
                "partner_" + uniqueSuffix + "@test.com",
                "Password123!",
                "Ramesh",
                "Patel",
                "+9198" + uniqueSuffix.replaceAll("[^0-9]", "0") + "1234",
                "Andaman Island Rentals Private Limited",
                "Island Wheels",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/partners/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("DRAFT"));

        User partnerUser = userRepository.findByEmail("partner_" + uniqueSuffix + "@test.com").orElseThrow();
        assertThat(partnerUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        Business business = businessRepository.findByOwnerUserId(partnerUser.getId()).orElseThrow();
        assertThat(business.getVerificationStatus()).isEqualTo(VerificationStatus.DRAFT);

        // Security check: Verify that active user is NOT authorized for inventory while status is DRAFT
        UserPrincipal partnerPrincipal = UserPrincipal.create(partnerUser, business.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(partnerPrincipal, null, partnerPrincipal.getAuthorities())
        );

        // User has business access (is the owner)
        assertThat(tenantSecurityService.hasBusinessAccess(business.getId())).isTrue();
        // BUT business is not approved yet
        assertThat(tenantSecurityService.isBusinessApproved(business.getId())).isFalse();
        // Therefore, inventory management is strictly rejected!
        assertThat(tenantSecurityService.isAuthorizedForBusinessInventory(business.getId())).isFalse();

        // Simulate admin approval
        business.setVerificationStatus(VerificationStatus.APPROVED);
        businessRepository.save(business);

        // Now inventory authorization succeeds
        assertThat(tenantSecurityService.isAuthorizedForBusinessInventory(business.getId())).isTrue();
    }

    @Test
    @DisplayName("Revoked partner membership immediately terminates access even if token carries contextual businessId")
    void testRevokedMembershipBlocksAccess() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        User owner = createTestUser("owner_" + uniqueSuffix + "@test.com", RoleType.ROLE_PARTNER_OWNER);
        Business business = new Business();
        business.setOwnerUser(owner);
        business.setLegalName("Havlock Bikes");
        business.setTradeName("Havlock Bikes");
        business.setVerificationStatus(VerificationStatus.APPROVED);
        Business savedBusiness = businessRepository.save(business);

        User staffUser = createTestUser("staff_" + uniqueSuffix + "@test.com", RoleType.ROLE_PARTNER_STAFF);

        PartnerStaff staff = new PartnerStaff();
        staff.setBusiness(savedBusiness);
        staff.setUser(staffUser);
        staff.setRoleInBusiness("STAFF");
        staff.setActive(true);
        partnerStaffRepository.save(staff);

        // Staff has active access
        UserPrincipal staffPrincipal = UserPrincipal.create(staffUser, savedBusiness.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staffPrincipal, null, staffPrincipal.getAuthorities())
        );
        assertThat(tenantSecurityService.hasBusinessAccess(savedBusiness.getId())).isTrue();

        // Deactivate (revoke) staff membership in database
        staff.setActive(false);
        partnerStaffRepository.save(staff);

        // Backend check immediately denies access! Token claims cannot bypass database authorization
        assertThat(tenantSecurityService.hasBusinessAccess(savedBusiness.getId())).isFalse();
    }

    @Test
    @DisplayName("Multi-Tenant Isolation: Partner A cannot access or modify Partner B's business resources")
    void testPartnerTenantIsolationCrossAccessPrevented() throws Exception {
        String suffixA = UUID.randomUUID().toString().substring(0, 6);
        String suffixB = UUID.randomUUID().toString().substring(0, 6);

        // 1. Register Partner A
        PartnerRegisterRequest regReqA = new PartnerRegisterRequest(
                "partner_a_" + suffixA + "@test.com",
                "Password123!",
                "PartnerA",
                "Owner",
                "+9198" + suffixA.replaceAll("[^0-9]", "1") + "1111",
                "Legal Firm A",
                "Rentals A",
                null,
                null
        );
        mockMvc.perform(post("/api/v1/partners/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReqA)))
                .andExpect(status().isCreated());

        User partnerUserA = userRepository.findByEmail("partner_a_" + suffixA + "@test.com").orElseThrow();
        Business businessA = businessRepository.findByOwnerUserId(partnerUserA.getId()).orElseThrow();

        // 2. Register Partner B
        PartnerRegisterRequest regReqB = new PartnerRegisterRequest(
                "partner_b_" + suffixB + "@test.com",
                "Password123!",
                "PartnerB",
                "Owner",
                "+9198" + suffixB.replaceAll("[^0-9]", "2") + "2222",
                "Legal Firm B",
                "Rentals B",
                null,
                null
        );
        mockMvc.perform(post("/api/v1/partners/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReqB)))
                .andExpect(status().isCreated());

        User partnerUserB = userRepository.findByEmail("partner_b_" + suffixB + "@test.com").orElseThrow();
        Business businessB = businessRepository.findByOwnerUserId(partnerUserB.getId()).orElseThrow();

        // 3. Establish security context as Partner A
        UserPrincipal principalA = UserPrincipal.create(partnerUserA, businessA.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities())
        );

        // TenantSecurityService checks:
        // Partner A has access to Business A
        assertThat(tenantSecurityService.hasBusinessAccess(businessA.getId())).isTrue();
        // Partner A STRICTLY has NO access to Business B
        assertThat(tenantSecurityService.hasBusinessAccess(businessB.getId())).isFalse();
        // Partner A cannot manage Business B's inventory
        assertThat(tenantSecurityService.isAuthorizedForBusinessInventory(businessB.getId())).isFalse();

        // 4. API endpoint verification: Calling /api/v1/partners/me with Partner A's token
        String tokenA = jwtTokenProvider.generateAccessToken(principalA);
        mockMvc.perform(get("/api/v1/partners/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("Rentals A"))
                .andExpect(jsonPath("$.legalName").value("Legal Firm A"));
    }

    @Test
    @DisplayName("Role Enforcement: SUPPORT_AGENT can view users but cannot verify or approve partner businesses")
    void testSupportAgentCannotVerifyPartner() throws Exception {
        User supportUser = createTestUser("support_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com", RoleType.ROLE_SUPPORT_AGENT);
        UserPrincipal supportPrincipal = UserPrincipal.create(supportUser, null);
        String supportToken = jwtTokenProvider.generateAccessToken(supportPrincipal);

        // Support agent CAN list users (read-only for support triage)
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());

        // Support agent CANNOT verify partner businesses (restricted to SUPER_ADMIN / OPERATIONS_ADMIN)
        mockMvc.perform(post("/api/v1/admin/partners/1/verify")
                        .header("Authorization", "Bearer " + supportToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    private User createTestUser(String email, RoleType roleType) {
        Role role = roleRepository.findByName(roleType.name()).orElseThrow();
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash("hashed_dummy_pw");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(role);
        return userRepository.save(user);
    }
}
