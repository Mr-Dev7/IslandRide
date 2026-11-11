package com.islandride.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import com.islandride.availability.dto.CreateAvailabilityBlockRequest;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.identityaccess.domain.User;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleCategory;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.dto.ReorderVehicleImagesRequest;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.pricing.dto.SavePricingRuleRequest;
import com.islandride.pricing.dto.SaveVehiclePricingRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestPropertySource(properties = "islandride.storage.local-root=target/test-storage")
class VehicleMediaAndAvailabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryTestSupport fixtures;
    @Autowired
    private InventoryHoldRepository holdRepository;

    @AfterAll
    static void cleanTestStorage() throws IOException {
        Path root = Path.of("target/test-storage");
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void pricingConfigurationAndValidation() throws Exception {
        User owner = fixtures.createUser("ROLE_PARTNER_OWNER", "prcOwn");
        Business biz = fixtures.createBusiness(owner, "Island Pricing Co", VerificationStatus.APPROVED);
        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        VehicleCategory sedan = fixtures.requireCategoryByCode("SEDAN");
        Vehicle vehicle = fixtures.saveDirectVehicle(biz, pb, pbHub, sedan, VehicleStatus.ACTIVE, new BigDecimal("1800.00"));
        String token = fixtures.bearerToken(owner, biz);

        // Invalid duration: minRentalHours (100) > maxRentalDays (2) * 24
        SaveVehiclePricingRequest invalidDur = new SaveVehiclePricingRequest(
                "INR", new BigDecimal("300.00"), new BigDecimal("1900.00"), new BigDecimal("11000.00"),
                new BigDecimal("3000.00"), 100, 2, 120,
                new BigDecimal("15.00"), new BigDecimal("250.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                null, new BigDecimal("300.00"), new BigDecimal("500.00"), 30,
                null, null, true
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/pricing").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(invalidDur)))
                .andExpect(status().is(422));

        // Invalid validity dates: validFrom >= validTo
        Instant t1 = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant t0 = Instant.now().plus(2, ChronoUnit.DAYS);
        SaveVehiclePricingRequest invalidDates = new SaveVehiclePricingRequest(
                "INR", null, new BigDecimal("1900.00"), null, new BigDecimal("3000.00"),
                6, 30, 120, null, null, null, null, null, null, null, 30,
                t1, t0, true
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/pricing").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(invalidDates)))
                .andExpect(status().is(422));

        // Valid pricing save (currently effective window) -> syncs currency INR & GST 5.00
        Instant effectiveFrom = Instant.now().minus(1, ChronoUnit.DAYS);
        SaveVehiclePricingRequest validReq = new SaveVehiclePricingRequest(
                "INR", new BigDecimal("350.00"), new BigDecimal("2150.00"), new BigDecimal("12900.00"),
                new BigDecimal("4000.00"), 6, 14, 150,
                new BigDecimal("18.00"), new BigDecimal("350.00"), new BigDecimal("150.00"), new BigDecimal("200.00"),
                "Beach sand excessive cleaning extra", new BigDecimal("400.00"), new BigDecimal("500.00"), 30,
                effectiveFrom, t1, true
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/pricing").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyRate").value(2150.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.gstPercentage").value(5.00));

        // Add PricingRule
        SavePricingRuleRequest ruleReq = new SavePricingRuleRequest(
                "Weekly 10% Discount", "DURATION_DISCOUNT", 7, "PERCENTAGE", new BigDecimal("-10.00")
        );
        MvcResult ruleRes = mockMvc.perform(post("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/pricing/rules").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(ruleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleType").value("DURATION_DISCOUNT"))
                .andReturn();
        long ruleId = InventoryTestSupport.JSON.readTree(ruleRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/pricing")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].id").value(ruleId))
                .andExpect(jsonPath("$.activePlan.dailyRate").value(2150.00));
    }

    @Test
    void mediaImagesAndPrivateComplianceDocumentsSecurityAndMagicBytes() throws Exception {
        User ownerA = fixtures.createUser("ROLE_PARTNER_OWNER", "medOwnA");
        Business bizA = fixtures.createBusiness(ownerA, "Media Fleet A", VerificationStatus.APPROVED);
        User ownerB = fixtures.createUser("ROLE_PARTNER_OWNER", "medOwnB");
        Business bizB = fixtures.createBusiness(ownerB, "Media Fleet B", VerificationStatus.APPROVED);
        User opsAdmin = fixtures.createUser("ROLE_OPERATIONS_ADMIN", "medOps");

        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");

        // Start in ACTIVE so we can test public media image serving vs DRAFT hiding
        Vehicle vehicle = fixtures.saveDirectVehicle(bizA, pb, pbHub, scooter, VehicleStatus.ACTIVE, new BigDecimal("500.00"));
        String tokenA = fixtures.bearerToken(ownerA, bizA);
        String tokenB = fixtures.bearerToken(ownerB, bizB);

        // 1. Disguised non-PNG uploaded as .png rejected by magic-byte validator
        MockMultipartFile spoofedPng = new MockMultipartFile(
                "files", "evil.png", "image/png", "not-actually-a-png-header".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/images")
                        .file(spoofedPng)
                        .with(csrf())
                        .header("Authorization", tokenA))
                .andExpect(status().is(422));

        // 2. Upload 2 real images -> 1st is auto primary
        MvcResult upRes = mockMvc.perform(multipart("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/images")
                        .file(InventoryTestSupport.validPngFile("files", "front.png"))
                        .file(InventoryTestSupport.validJpegFile("files", "side.jpg"))
                        .with(csrf())
                        .header("Authorization", tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode imgs = InventoryTestSupport.JSON.readTree(upRes.getResponse().getContentAsString());
        String img1 = imgs.get(0).get("publicId").asText();
        String img2 = imgs.get(1).get("publicId").asText();

        // Set primary & reorder
        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/images/" + img2 + "/primary").with(csrf())
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value(img2))
                .andExpect(jsonPath("$[0].primary").value(true));

        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/images/reorder").with(csrf())
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(new ReorderVehicleImagesRequest(List.of(img1, img2)))))
                .andExpect(status().isOk());

        // Public image accessible for ACTIVE vehicle
        mockMvc.perform(get("/api/v1/media/vehicle-images/" + img2))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // 3. Disguised PDF document rejected
        MockMultipartFile spoofedPdf = new MockMultipartFile(
                "file", "fake_rc.pdf", "application/pdf", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/documents")
                        .file(spoofedPdf)
                        .param("documentTypeCode", "RC_BOOK")
                        .param("expiryDate", LocalDate.now().plusDays(180).toString())
                        .with(csrf())
                        .header("Authorization", tokenA))
                .andExpect(status().is(422));

        // Category applicability: PERMIT (FOUR_WHEELER) rejected on SCOOTER
        mockMvc.perform(multipart("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/documents")
                        .file(InventoryTestSupport.validPdfFile("file", "permit.pdf"))
                        .param("documentTypeCode", "PERMIT")
                        .param("expiryDate", LocalDate.now().plusDays(180).toString())
                        .with(csrf())
                        .header("Authorization", tokenA))
                .andExpect(status().is(422));

        // Valid INSURANCE expiring in 15 days -> uploaded privately
        MvcResult docRes = mockMvc.perform(multipart("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/documents")
                        .file(InventoryTestSupport.validPdfFile("file", "insurance.pdf"))
                        .param("documentTypeCode", "INSURANCE")
                        .param("documentNumber", "INS-AN-9901")
                        .param("issueDate", LocalDate.now().minusMonths(11).toString())
                        .param("expiryDate", LocalDate.now().plusDays(15).toString())
                        .with(csrf())
                        .header("Authorization", tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiringSoon").value(true))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn();
        String docPublicId = InventoryTestSupport.JSON.readTree(docRes.getResponse().getContentAsString()).get("publicId").asText();
        assertThat(docRes.getResponse().getContentAsString()).doesNotContain("storageKey");

        // Private doc: unauthenticated -> 401; Partner B -> 403; Partner A & Admin -> 200 bytes
        String partnerDocUrl = "/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/documents/" + docPublicId + "/download";
        mockMvc.perform(get(partnerDocUrl)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(partnerDocUrl).header("Authorization", tokenB)).andExpect(status().isForbidden());

        MvcResult dlPartnerA = mockMvc.perform(get(partnerDocUrl).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(new String(dlPartnerA.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).startsWith("%PDF-1.4");

        mockMvc.perform(get("/api/v1/admin/vehicles/" + vehicle.getPublicId() + "/documents/" + docPublicId + "/download")
                        .header("Authorization", fixtures.bearerToken(opsAdmin, null)))
                .andExpect(status().isOk());

        // Expiring query lists the doc within 30 days
        mockMvc.perform(get("/api/v1/partners/documents/expiring")
                        .param("withinDays", "30")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value(docPublicId));
    }

    @Test
    void availabilityCalendar_overlapConflicts_andDateRangePublicSearch() throws Exception {
        User owner = fixtures.createUser("ROLE_PARTNER_OWNER", "availOwn");
        Business biz = fixtures.createBusiness(owner, "Availability Bikes", VerificationStatus.APPROVED);
        User tourist = fixtures.createUser("ROLE_TOURIST", "availTour");

        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");
        Vehicle v = fixtures.saveDirectVehicle(biz, pb, pbHub, scooter, VehicleStatus.ACTIVE, new BigDecimal("500.00"));
        String token = fixtures.bearerToken(owner, biz);

        Instant day2 = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant day4 = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant day3 = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant day5 = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant day8 = Instant.now().plus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant day10 = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        // 1. Create MANUAL_BLOCK [day2, day4]
        CreateAvailabilityBlockRequest blockReq = new CreateAvailabilityBlockRequest(
                day2, day4, "MANUAL_BLOCK", "SERVICING", "Internal mechanic contact: 9999999999", null, true
        );
        MvcResult blkRes = mockMvc.perform(post("/api/v1/partners/vehicles/" + v.getPublicId() + "/availability/blocks").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(blockReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String blockPublicId = InventoryTestSupport.JSON.readTree(blkRes.getResponse().getContentAsString()).get("publicId").asText();

        // 2. Overlapping block [day3, day5] returns 409 Conflict
        CreateAvailabilityBlockRequest overlapBlock = new CreateAvailabilityBlockRequest(
                day3, day5, "MAINTENANCE", "OIL_CHANGE", null, null, true
        );
        mockMvc.perform(post("/api/v1/partners/vehicles/" + v.getPublicId() + "/availability/blocks").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(overlapBlock)))
                .andExpect(status().isConflict());

        // 3. Public calendar hides partner private notes; Partner calendar includes detailedBlocks
        MvcResult pubCal = mockMvc.perform(get("/api/v1/vehicles/" + v.getPublicId() + "/availability")
                        .param("from", day2.minus(1, ChronoUnit.HOURS).toString())
                        .param("to", day4.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableForRequestedWindow").value(false))
                .andExpect(jsonPath("$.detailedBlocks").isEmpty())
                .andReturn();
        assertThat(pubCal.getResponse().getContentAsString()).doesNotContain("Internal mechanic contact");

        mockMvc.perform(get("/api/v1/partners/vehicles/" + v.getPublicId() + "/availability")
                        .param("from", day2.minus(1, ChronoUnit.HOURS).toString())
                        .param("to", day4.plus(1, ChronoUnit.HOURS).toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailedBlocks[0].notes").value("Internal mechanic contact: 9999999999"));

        // 4. Date-range public search excludes vehicle during blocked window, includes when outside
        mockMvc.perform(get("/api/v1/vehicles")
                        .param("startTime", day2.toString())
                        .param("endTime", day3.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 5. Active InventoryHold [day8, day10] also triggers 409 on overlapping block creation
        InventoryHold hold = new InventoryHold();
        hold.setVehicle(v);
        hold.setUser(tourist);
        hold.setStartTime(day8);
        hold.setEndTime(day10);
        hold.setStatus(HoldStatus.HELD);
        hold.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        holdRepository.save(hold);

        CreateAvailabilityBlockRequest holdOverlap = new CreateAvailabilityBlockRequest(
                day8, day10, "OPERATIONAL_RESTRICTION", "RESTRICTED", null, "Night curfew hub only", false
        );
        mockMvc.perform(post("/api/v1/partners/vehicles/" + v.getPublicId() + "/availability/blocks").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(holdOverlap)))
                .andExpect(status().isConflict());

        // 6. Removing removable block frees window -> public catalog returns vehicle again
        mockMvc.perform(delete("/api/v1/partners/vehicles/" + v.getPublicId() + "/availability/blocks/" + blockPublicId).with(csrf())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/vehicles")
                        .param("startTime", day2.toString())
                        .param("endTime", day3.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(v.getPublicId()));
    }
}
