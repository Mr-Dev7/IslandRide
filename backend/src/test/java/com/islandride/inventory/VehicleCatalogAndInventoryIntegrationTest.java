package com.islandride.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.identityaccess.domain.User;
import com.islandride.inventory.domain.*;
import com.islandride.inventory.dto.SaveVehicleRequest;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.repository.VehicleStatusHistoryRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestPropertySource(properties = "islandride.storage.local-root=target/test-storage")
class VehicleCatalogAndInventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryTestSupport fixtures;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleStatusHistoryRepository historyRepository;
    @Autowired
    private BookingRepository bookingRepository;

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
    void partnerLifecycle_tenantIsolation_andStaffPermissions() throws Exception {
        User ownerA = fixtures.createUser("ROLE_PARTNER_OWNER", "ownerA");
        Business bizA = fixtures.createBusiness(ownerA, "Andaman Rides A", VerificationStatus.APPROVED);
        User managerA = fixtures.createUser("ROLE_PARTNER_MANAGER", "mgrA");
        fixtures.attachStaff(bizA, managerA, "MANAGER");
        User staffA = fixtures.createUser("ROLE_PARTNER_STAFF", "staffA");
        fixtures.attachStaff(bizA, staffA, "STAFF");

        User ownerB = fixtures.createUser("ROLE_PARTNER_OWNER", "ownerB");
        Business bizB = fixtures.createBusiness(ownerB, "Swaraj Fleet B", VerificationStatus.APPROVED);

        User pendingOwner = fixtures.createUser("ROLE_PARTNER_OWNER", "pendingOwn");
        Business pendingBiz = fixtures.createBusiness(pendingOwner, "Unapproved Rentals", VerificationStatus.UNDER_REVIEW);

        User tourist = fixtures.createUser("ROLE_TOURIST", "tourist");
        User opsAdmin = fixtures.createUser("ROLE_OPERATIONS_ADMIN", "ops");

        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Island hvl = fixtures.requireIslandByCode("HAVELOCK");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        Location hvlHub = fixtures.firstActiveHubForIsland(hvl.getId());
        VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");

        SaveVehicleRequest createReq = fixtures.buildVehicleRequest(
                bizA.getPublicId(), pb, pbHub, pbHub, false, scooter, "Honda", "Activa 6G", new BigDecimal("500.00")
        );

        // 1. Unauthorized creates rejected
        mockMvc.perform(post("/api/v1/partners/vehicles").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(createReq)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/partners/vehicles").with(csrf())
                        .header("Authorization", fixtures.bearerToken(tourist, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/partners/vehicles").with(csrf())
                        .header("Authorization", fixtures.bearerToken(staffA, bizA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // Unapproved business cannot manage inventory even with PARTNER_OWNER role
        SaveVehicleRequest unapprovedReq = fixtures.buildVehicleRequest(
                pendingBiz.getPublicId(), pb, pbHub, pbHub, false, scooter, "TVS", "Jupiter", new BigDecimal("450.00")
        );
        mockMvc.perform(post("/api/v1/partners/vehicles").with(csrf())
                        .header("Authorization", fixtures.bearerToken(pendingOwner, pendingBiz))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(unapprovedReq)))
                .andExpect(status().isForbidden());

        // Cross-island hub transfer blocked
        SaveVehicleRequest interIslandReq = fixtures.buildVehicleRequest(
                bizA.getPublicId(), pb, pbHub, hvlHub, true, scooter, "TVS", "NTorq", new BigDecimal("550.00")
        );
        mockMvc.perform(post("/api/v1/partners/vehicles").with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerA, bizA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(interIslandReq)))
                .andExpect(status().is(422));

        // 2. Authorized create by Partner A owner -> forced DRAFT status
        MvcResult createResult = mockMvc.perform(post("/api/v1/partners/vehicles").with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerA, bizA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.businessPublicId").value(bizA.getPublicId()))
                .andReturn();
        String vehPublicId = InventoryTestSupport.JSON.readTree(createResult.getResponse().getContentAsString())
                .get("publicId").asText();

        // Duplicate registration number -> 409 Conflict
        mockMvc.perform(post("/api/v1/partners/vehicles").with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerA, bizA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(createReq)))
                .andExpect(status().isConflict());

        // 3. Multi-tenant isolation: Partner B denied on GET, PUT, submit, delete
        mockMvc.perform(get("/api/v1/partners/vehicles/" + vehPublicId)
                        .header("Authorization", fixtures.bearerToken(ownerB, bizB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehPublicId).with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerB, bizB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/partners/vehicles")
                        .param("businessPublicId", bizA.getPublicId())
                        .header("Authorization", fixtures.bearerToken(ownerB, bizB)))
                .andExpect(status().isForbidden());

        // 4. PARTNER_STAFF can read summary, list, detail, pricing, availability, locations
        mockMvc.perform(get("/api/v1/partners/vehicles/summary")
                        .header("Authorization", fixtures.bearerToken(staffA, bizA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftVehicles").value(1));

        mockMvc.perform(get("/api/v1/partners/vehicles/" + vehPublicId)
                        .header("Authorization", fixtures.bearerToken(staffA, bizA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(vehPublicId));

        // PARTNER_STAFF cannot pause/submit/delete
        mockMvc.perform(post("/api/v1/partners/vehicles/" + vehPublicId + "/submit").with(csrf())
                        .header("Authorization", fixtures.bearerToken(staffA, bizA)))
                .andExpect(status().isForbidden());

        // 5. Partner Manager submits -> PENDING_REVIEW; still hidden from public catalog
        mockMvc.perform(post("/api/v1/partners/vehicles/" + vehPublicId + "/submit").with(csrf())
                        .header("Authorization", fixtures.bearerToken(managerA, bizA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mockMvc.perform(get("/api/v1/vehicles/" + vehPublicId))
                .andExpect(status().isNotFound());

        // Ops admin approves -> ACTIVE
        mockMvc.perform(post("/api/v1/admin/vehicles/" + vehPublicId + "/approve").with(csrf())
                        .header("Authorization", fixtures.bearerToken(opsAdmin, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 6. Sensitive update while ACTIVE triggers automatic PENDING_REVIEW re-approval
        SaveVehicleRequest changedModelReq = new SaveVehicleRequest(
                bizA.getPublicId(), pb.getId(), pbHub.getPublicId(), pbHub.getPublicId(), false,
                scooter.getId(), "Honda Dio 2024", "Honda", "Dio 125", 2024,
                "Updated vehicle", createReq.registrationNumber(), "Blue",
                TransmissionType.AUTOMATIC, FuelType.PETROL, 2,
                new BigDecimal("45.00"), 125, VehicleCondition.EXCELLENT,
                "Helmets included.", new BigDecimal("600.00"), new BigDecimal("2000.00"), 60,
                BookingMode.INSTANT, List.of("ABS")
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehPublicId).with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerA, bizA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(changedModelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        Vehicle reReviewed = vehicleRepository.findByPublicId(vehPublicId).orElseThrow();
        assertThat(historyRepository.findByVehicleIdOrderByCreatedAtDesc(reReviewed.getId()))
                .anyMatch(h -> h.getFromStatus() == VehicleStatus.ACTIVE && h.getToStatus() == VehicleStatus.PENDING_REVIEW);

        // 7. Safe archival blocked when an active booking exists
        Booking activeBooking = new Booking();
        activeBooking.setBookingReference("BKG" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        activeBooking.setUser(tourist);
        activeBooking.setVehicle(reReviewed);
        activeBooking.setBusiness(bizA);
        activeBooking.setPickupLocation(pbHub);
        activeBooking.setReturnLocation(pbHub);
        activeBooking.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        activeBooking.setEndTime(Instant.now().plus(4, ChronoUnit.DAYS));
        activeBooking.setTotalDays(2);
        activeBooking.setDailyRate(new BigDecimal("600.00"));
        activeBooking.setBaseRentalAmount(new BigDecimal("1200.00"));
        activeBooking.setTotalPayableAmount(new BigDecimal("1200.00"));
        activeBooking.setStatus(BookingStatus.CONFIRMED);
        activeBooking = bookingRepository.save(activeBooking);

        mockMvc.perform(delete("/api/v1/partners/vehicles/" + vehPublicId).with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerA, bizA)))
                .andExpect(status().isConflict());

        // Cancel booking -> archive succeeds -> subsequent edits blocked
        activeBooking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(activeBooking);

        mockMvc.perform(delete("/api/v1/partners/vehicles/" + vehPublicId).with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerA, bizA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(put("/api/v1/partners/vehicles/" + vehPublicId).with(csrf())
                        .header("Authorization", fixtures.bearerToken(ownerA, bizA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(changedModelReq)))
                .andExpect(status().is(422));
    }

    @Test
    void publicCatalog_visibilityFilteringSortingAndSensitiveDataExclusion() throws Exception {
        User approvedOwner = fixtures.createUser("ROLE_PARTNER_OWNER", "pubOwner");
        Business approvedBiz = fixtures.createBusiness(approvedOwner, "Andaman Public Fleet", VerificationStatus.APPROVED);

        User suspendedOwner = fixtures.createUser("ROLE_PARTNER_OWNER", "suspOwner");
        Business unapprovedBiz = fixtures.createBusiness(suspendedOwner, "Hidden Unapproved Co", VerificationStatus.SUSPENDED);

        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Island hvl = fixtures.requireIslandByCode("HAVELOCK");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        Location hvlHub = fixtures.firstActiveHubForIsland(hvl.getId());
        VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");
        VehicleCategory suv = fixtures.requireCategoryByCode("SUV");

        // Published vehicles
        Vehicle lowScooter = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.ACTIVE, new BigDecimal("450.00"));
        lowScooter.setMake("Honda");
        lowScooter.setModel("Dio");
        vehicleRepository.save(lowScooter);

        Vehicle highSuv = fixtures.saveDirectVehicle(approvedBiz, hvl, hvlHub, suv, VehicleStatus.ACTIVE, new BigDecimal("2800.00"));
        highSuv.setMake("Mahindra");
        highSuv.setModel("Thar");
        highSuv.setFuelType(FuelType.DIESEL);
        vehicleRepository.save(highSuv);

        // Non-public states (MUST remain invisible)
        Vehicle draftV = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.DRAFT, new BigDecimal("300.00"));
        Vehicle pendingV = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.PENDING_REVIEW, new BigDecimal("300.00"));
        Vehicle pausedV = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.PAUSED, new BigDecimal("300.00"));
        Vehicle maintV = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.MAINTENANCE, new BigDecimal("300.00"));
        Vehicle suspV = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.SUSPENDED, new BigDecimal("300.00"));
        Vehicle archV = fixtures.saveDirectVehicle(approvedBiz, pb, pbHub, scooter, VehicleStatus.ARCHIVED, new BigDecimal("300.00"));
        Vehicle hiddenBizActive = fixtures.saveDirectVehicle(unapprovedBiz, pb, pbHub, scooter, VehicleStatus.ACTIVE, new BigDecimal("350.00"));

        String catalogTag = "VisTag" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        for (Vehicle v : List.of(lowScooter, highSuv, draftV, pendingV, pausedV, maintV, suspV, archV, hiddenBizActive)) {
            v.setTitle(catalogTag + " " + v.getTitle());
            vehicleRepository.save(v);
        }

        // 1. Non-active vehicles & unapproved business vehicles return 404 directly
        for (Vehicle hidden : List.of(draftV, pendingV, pausedV, maintV, suspV, archV, hiddenBizActive)) {
            mockMvc.perform(get("/api/v1/vehicles/" + hidden.getPublicId()))
                    .andExpect(status().isNotFound());
        }

        // 2. Public search returns ONLY the 2 valid ACTIVE vehicles (sorted by price asc)
        MvcResult searchRes = mockMvc.perform(get("/api/v1/vehicles")
                        .param("q", catalogTag)
                        .param("sortBy", "price_asc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].publicId").value(lowScooter.getPublicId()))
                .andExpect(jsonPath("$.content[1].publicId").value(highSuv.getPublicId()))
                .andReturn();

        String searchJson = searchRes.getResponse().getContentAsString();
        assertThat(searchJson).doesNotContain(lowScooter.getRegistrationNumber());
        assertThat(searchJson).doesNotContain(highSuv.getRegistrationNumber());
        assertThat(searchJson).doesNotContain("35ABCDE1234F1Z5"); // GSTIN
        assertThat(searchJson).doesNotContain("ABCDE1234F");      // PAN
        assertThat(searchJson).doesNotContain("Private Limited"); // legalName

        // 3. Public detail also excludes sensitive registration, documents, tax/legal fields
        MvcResult detailRes = mockMvc.perform(get("/api/v1/vehicles/" + highSuv.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(highSuv.getPublicId()))
                .andExpect(jsonPath("$.partnerTradeName").value("Andaman Public Fleet"))
                .andExpect(jsonPath("$.verifiedPartner").value(true))
                .andReturn();

        JsonNode detailNode = InventoryTestSupport.JSON.readTree(detailRes.getResponse().getContentAsString());
        assertThat(detailNode.has("registrationNumber")).isFalse();
        assertThat(detailNode.has("documents")).isFalse();
        assertThat(detailNode.has("businessLegalName")).isFalse();

        // 4. Filter by islandCode=HAVELOCK + fuelType=DIESEL + sortBy=price_desc
        mockMvc.perform(get("/api/v1/vehicles")
                        .param("islandCode", "HAVELOCK")
                        .param("fuelType", "DIESEL")
                        .param("sortBy", "price_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(highSuv.getPublicId()));
    }
}
