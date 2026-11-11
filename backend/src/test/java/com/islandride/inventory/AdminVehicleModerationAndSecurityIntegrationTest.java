package com.islandride.inventory;

import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.dto.SaveIslandRequest;
import com.islandride.geography.dto.SaveLocationRequest;
import com.islandride.identityaccess.domain.User;
import com.islandride.inventory.domain.DocumentVerificationStatus;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleCategory;
import com.islandride.inventory.domain.VehicleDocument;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.domain.VehicleType;
import com.islandride.inventory.dto.SaveVehicleCategoryRequest;
import com.islandride.inventory.dto.SaveVehicleDocumentTypeRequest;
import com.islandride.inventory.dto.VehicleModerationRequest;
import com.islandride.inventory.dto.VerifyVehicleDocumentRequest;
import com.islandride.inventory.repository.VehicleDocumentRepository;
import com.islandride.inventory.service.VehicleDocumentTypeService;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestPropertySource(properties = "islandride.storage.local-root=target/test-storage")
class AdminVehicleModerationAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryTestSupport fixtures;
    @Autowired
    private VehicleDocumentRepository documentRepository;
    @Autowired
    private VehicleDocumentTypeService documentTypeService;

    @Test
    void adminModerationWorkflow_docVerification_expiredDocBlock_andRbac() throws Exception {
        User opsAdmin = fixtures.createUser("ROLE_OPERATIONS_ADMIN", "modOps");
        User supportAgent = fixtures.createUser("ROLE_SUPPORT_AGENT", "modSupport");
        User financeAdmin = fixtures.createUser("ROLE_FINANCE_ADMIN", "modFinance");
        User partnerOwner = fixtures.createUser("ROLE_PARTNER_OWNER", "modOwner");
        Business approvedBiz = fixtures.createBusiness(partnerOwner, "Verified Boats & Bikes", VerificationStatus.APPROVED);

        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");
        Vehicle v = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.PENDING_REVIEW, new BigDecimal("520.00"));

        VehicleDocument rcDoc = new VehicleDocument();
        rcDoc.setVehicle(v);
        rcDoc.setDocumentTypeEntity(documentTypeService.requireActiveByCode("RC_BOOK"));
        rcDoc.setDocumentType("RC_BOOK");
        rcDoc.setDocumentUrl("/private/test");
        rcDoc.setStorageKey("vehicle-docs/fake/rc.pdf");
        rcDoc.setExpiryDate(LocalDate.now().plusYears(2));
        rcDoc = documentRepository.save(rcDoc);

        String opsToken = fixtures.bearerToken(opsAdmin, null);

        // 1. RBAC: SUPPORT_AGENT, FINANCE_ADMIN, and PARTNER_OWNER are denied access to Admin moderation & catalog config
        for (User nonOps : List.of(supportAgent, financeAdmin, partnerOwner)) {
            String tok = fixtures.bearerToken(nonOps, null);
            mockMvc.perform(get("/api/v1/admin/vehicles").header("Authorization", tok))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/vehicles/" + v.getPublicId() + "/approve").with(csrf())
                            .header("Authorization", tok))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/categories").header("Authorization", tok))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/geography/islands").header("Authorization", tok))
                    .andExpect(status().isForbidden());
        }

        // 2. Reject requires non-blank reason; valid reject records reason & history
        mockMvc.perform(post("/api/v1/admin/vehicles/" + v.getPublicId() + "/reject").with(csrf())
                        .header("Authorization", opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/vehicles/" + v.getPublicId() + "/reject").with(csrf())
                        .header("Authorization", opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(new VehicleModerationRequest("Blurry RC scan"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Blurry RC scan"));

        // Partner resubmits -> PENDING_REVIEW
        mockMvc.perform(post("/api/v1/partners/vehicles/" + v.getPublicId() + "/submit").with(csrf())
                        .header("Authorization", fixtures.bearerToken(partnerOwner, approvedBiz)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        // 3. Admin verifies document, approves vehicle -> ACTIVE, and can suspend / unsuspend
        mockMvc.perform(patch("/api/v1/admin/vehicles/" + v.getPublicId() + "/documents/" + rcDoc.getPublicId() + "/verify")
                        .with(csrf())
                        .header("Authorization", opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(
                                new VerifyVehicleDocumentRequest(DocumentVerificationStatus.VERIFIED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        mockMvc.perform(post("/api/v1/admin/vehicles/" + v.getPublicId() + "/approve").with(csrf())
                        .header("Authorization", opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/admin/vehicles/" + v.getPublicId() + "/suspend").with(csrf())
                        .header("Authorization", opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(new VehicleModerationRequest("Safety check needed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("Safety check needed"));

        // 4. Inspect full Admin vehicle detail (audit logs + status history present)
        mockMvc.perform(get("/api/v1/admin/vehicles/" + v.getPublicId())
                        .header("Authorization", opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory").isNotEmpty())
                .andExpect(jsonPath("$.auditLogs").isNotEmpty());

        // 5. Expired compliance document blocks unsuspend / approval
        rcDoc.setExpiryDate(LocalDate.now().minusDays(2));
        documentRepository.save(rcDoc);

        mockMvc.perform(post("/api/v1/admin/vehicles/" + v.getPublicId() + "/unsuspend").with(csrf())
                        .header("Authorization", opsToken))
                .andExpect(status().is(422));
    }

    @Test
    void adminConfigCrud_categoriesDocumentTypesAndGeography() throws Exception {
        User superAdmin = fixtures.createUser("ROLE_SUPER_ADMIN", "cfgAdmin");
        String adminToken = fixtures.bearerToken(superAdmin, null);

        // Create Category -> visible on public GET /api/v1/categories
        SaveVehicleCategoryRequest catReq = new SaveVehicleCategoryRequest(
                "E-Bicycle", "EBICYCLE", "e-bicycle", VehicleType.TWO_WHEELER,
                "Pedal assist beach cruisers", "bike-electric", List.of("batteryRangeKm"), 9, true
        );
        mockMvc.perform(post("/api/v1/admin/categories").with(csrf())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(catReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("EBICYCLE"));

        // Create Document Type
        SaveVehicleDocumentTypeRequest docTypeReq = new SaveVehicleDocumentTypeRequest(
                "PASSENGER_INSURANCE", "Passenger Liability Policy", "Additional tourist cover",
                false, true, "FOUR_WHEELER", true, 10
        );
        mockMvc.perform(post("/api/v1/admin/document-types").with(csrf())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(docTypeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PASSENGER_INSURANCE"));

        // Create Island & Hub
        SaveIslandRequest islandReq = new SaveIslandRequest(
                "Baratang Island", "Baratang Hub", "BTG", "Mud volcano & limestone caves", 6, true
        );
        var islandRes = mockMvc.perform(post("/api/v1/admin/geography/islands").with(csrf())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(islandReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayAlias").value("Baratang Hub"))
                .andReturn();
        long islandId = InventoryTestSupport.JSON.readTree(islandRes.getResponse().getContentAsString()).get("id").asLong();

        SaveLocationRequest hubReq = new SaveLocationRequest(
                islandId, "Nilambur Jetty Hub", "BTG_JETTY", "Nilambur Ferry Point, Baratang",
                new BigDecimal("12.1630"), new BigDecimal("92.7520"), "+91-3192-279000", true
        );
        mockMvc.perform(post("/api/v1/admin/geography/locations").with(csrf())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(hubReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BTG_JETTY"));

        // Public reference endpoints reflect new entries
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='EBICYCLE')]").exists());

        mockMvc.perform(get("/api/v1/document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='PASSENGER_INSURANCE')]").exists());

        mockMvc.perform(get("/api/v1/locations/hubs").param("islandCode", "BTG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BTG_JETTY"));
    }
}
