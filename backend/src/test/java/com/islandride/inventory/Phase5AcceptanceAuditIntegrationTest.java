package com.islandride.inventory;

import com.islandride.availability.dto.CreateAvailabilityBlockRequest;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.storage.MediaStorageService;
import com.islandride.core.storage.MediaVisibility;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleCategory;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.repository.VehicleUnavailabilityRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.pricing.domain.VehiclePricingPlan;
import com.islandride.pricing.dto.SavePricingRuleRequest;
import com.islandride.pricing.dto.SaveVehiclePricingRequest;
import com.islandride.pricing.repository.VehiclePricingPlanRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "islandride.storage.local-root=target/test-storage-audit")
class Phase5AcceptanceAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryTestSupport fixtures;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehiclePricingPlanRepository pricingPlanRepository;
    @Autowired
    private VehicleUnavailabilityRepository unavailabilityRepository;
    @Autowired
    private PartnerStaffRepository partnerStaffRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MediaStorageService mediaStorageService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterAll
    static void cleanAuditTestStorage() throws IOException {
        Path root = Path.of("target/test-storage-audit");
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    @Transactional
    void availability_confirmedBooking_boundaryWindows_andDraftHiding() throws Exception {
        User owner = fixtures.createUser("ROLE_PARTNER_OWNER", "audAvailOwn");
        Business biz = fixtures.createBusiness(owner, "Audit Availability Co", VerificationStatus.APPROVED);
        User tourist = fixtures.createUser("ROLE_TOURIST", "audAvailTour");

        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");
        Vehicle activeVehicle = fixtures.saveDirectVehicle(biz, pb, pbHub, scooter, VehicleStatus.ACTIVE, new BigDecimal("550.00"));
        Vehicle draftVehicle = fixtures.saveDirectVehicle(biz, pb, pbHub, scooter, VehicleStatus.DRAFT, new BigDecimal("500.00"));
        String token = fixtures.bearerToken(owner, biz);

        // Unapproved/DRAFT vehicle public availability must not leak -> 404
        mockMvc.perform(get("/api/v1/vehicles/" + draftVehicle.getPublicId() + "/availability"))
                .andExpect(status().isNotFound());

        Instant day2 = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant day4 = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant day6 = Instant.now().plus(6, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        Booking confirmed = new Booking();
        confirmed.setBookingReference("BKA" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        confirmed.setUser(tourist);
        confirmed.setVehicle(activeVehicle);
        confirmed.setBusiness(biz);
        confirmed.setPickupLocation(pbHub);
        confirmed.setReturnLocation(pbHub);
        confirmed.setStartTime(day2);
        confirmed.setEndTime(day4);
        confirmed.setTotalDays(2);
        confirmed.setDailyRate(new BigDecimal("550.00"));
        confirmed.setBaseRentalAmount(new BigDecimal("1100.00"));
        confirmed.setTotalPayableAmount(new BigDecimal("1100.00"));
        confirmed.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(confirmed);

        // Public calendar & search exclude overlapping confirmed booking window [day2, day4]
        mockMvc.perform(get("/api/v1/vehicles/" + activeVehicle.getPublicId() + "/availability")
                        .param("from", day2.toString())
                        .param("to", day4.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableForRequestedWindow").value(false));

        mockMvc.perform(get("/api/v1/vehicles")
                        .param("startTime", day2.plus(1, ChronoUnit.HOURS).toString())
                        .param("endTime", day4.minus(1, ChronoUnit.HOURS).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Adjacent boundary [day4, day6] is free (strict half-open overlap)
        mockMvc.perform(get("/api/v1/vehicles")
                        .param("startTime", day4.toString())
                        .param("endTime", day6.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(activeVehicle.getPublicId()));

        // Partner maintenance block overlapping booking by 1 second rejected -> 409 Conflict
        CreateAvailabilityBlockRequest conflictBlock = new CreateAvailabilityBlockRequest(
                day4.minusSeconds(1), day6, "MAINTENANCE", "SERVICE", null, null, true
        );
        mockMvc.perform(post("/api/v1/partners/vehicles/" + activeVehicle.getPublicId() + "/availability/blocks")
                        .with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(conflictBlock)))
                .andExpect(status().isConflict());

        // Partner maintenance block starting at exact boundary day4 succeeds -> 201 Created
        CreateAvailabilityBlockRequest boundaryBlock = new CreateAvailabilityBlockRequest(
                day4, day6, "MAINTENANCE", "OIL_CHANGE", null, null, true
        );
        mockMvc.perform(post("/api/v1/partners/vehicles/" + activeVehicle.getPublicId() + "/availability/blocks")
                        .with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(boundaryBlock)))
                .andExpect(status().isCreated());
    }

    @Test
    @Transactional
    void pricing_effectiveDates_inactiveRecords_andPublicPricingExclusion() throws Exception {
        User owner = fixtures.createUser("ROLE_PARTNER_OWNER", "audPriceOwn");
        Business biz = fixtures.createBusiness(owner, "Audit Pricing Co", VerificationStatus.APPROVED);
        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        VehicleCategory suv = fixtures.requireCategoryByCode("SUV");

        Vehicle v = fixtures.saveDirectVehicle(biz, pb, pbHub, suv, VehicleStatus.ACTIVE, new BigDecimal("1000.00"));
        String token = fixtures.bearerToken(owner, biz);
        Instant now = Instant.now();

        // 1. Live active plan (1200/day)
        SaveVehiclePricingRequest liveReq = new SaveVehiclePricingRequest(
                "INR", null, new BigDecimal("1200.00"), null, new BigDecimal("3000.00"),
                6, 30, null, null, null, null, null, null, null, null, 30,
                now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), true
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + v.getPublicId() + "/pricing").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(liveReq)))
                .andExpect(status().isOk());

        // 2. Inactive draft plan (99/day, active=false) must NOT alter public pricing
        SaveVehiclePricingRequest inactiveReq = new SaveVehiclePricingRequest(
                "INR", null, new BigDecimal("99.00"), null, new BigDecimal("0.00"),
                6, 30, null, null, null, null, null, null, null, null, 30,
                null, null, false
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + v.getPublicId() + "/pricing").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(inactiveReq)))
                .andExpect(status().isOk());

        // 3. Future-scheduled plan (validFrom = now + 3d, 2500/day) must NOT override current public activePlan
        SaveVehiclePricingRequest futureReq = new SaveVehiclePricingRequest(
                "INR", null, new BigDecimal("2500.00"), null, new BigDecimal("5000.00"),
                6, 30, null, null, null, null, null, null, null, null, 30,
                now.plus(3, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS), true
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + v.getPublicId() + "/pricing").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(futureReq)))
                .andExpect(status().isOk());

        // 4. Already-expired active plan (validTo in the past) rejected with 422
        SaveVehiclePricingRequest expiredReq = new SaveVehiclePricingRequest(
                "INR", null, new BigDecimal("400.00"), null, new BigDecimal("500.00"),
                6, 30, null, null, null, null, null, null, null, null, 30,
                now.minus(5, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), true
        );
        mockMvc.perform(put("/api/v1/partners/vehicles/" + v.getPublicId() + "/pricing").with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(expiredReq)))
                .andExpect(status().is(422));

        // 5. Deactivated pricing rule must be excluded from public detail
        MvcResult ruleRes = mockMvc.perform(post("/api/v1/partners/vehicles/" + v.getPublicId() + "/pricing/rules")
                        .with(csrf())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InventoryTestSupport.JSON.writeValueAsString(
                                new SavePricingRuleRequest("Off-season 15%", "DURATION_DISCOUNT", 3, "PERCENTAGE", new BigDecimal("-15.00"))
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        long ruleId = InventoryTestSupport.JSON.readTree(ruleRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/v1/partners/vehicles/" + v.getPublicId() + "/pricing/rules/" + ruleId)
                        .with(csrf())
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/vehicles/" + v.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingPlan.dailyRate").value(1200.00))
                .andExpect(jsonPath("$.pricingRules").isEmpty())
                .andExpect(jsonPath("$.history").doesNotExist());

        // 6. When persisted plan's validTo has passed in DB, public detail ignores the expired plan & falls back to vehicle base rate
        List<VehiclePricingPlan> plans = pricingPlanRepository.findByVehicleIdAndActiveTrue(v.getId());
        for (VehiclePricingPlan plan : plans) {
            if (plan.getValidFrom() == null || !plan.getValidFrom().isAfter(now)) {
                plan.setValidTo(now.minusSeconds(60));
                pricingPlanRepository.save(plan);
            }
        }
        v.setDailyRate(new BigDecimal("1000.00"));
        vehicleRepository.save(v);

        mockMvc.perform(get("/api/v1/vehicles/" + v.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingPlan.dailyRate").value(1000.00))
                .andExpect(jsonPath("$.pricingPlan.publicId").isEmpty());
    }

    @Test
    @Transactional
    void mediaSecurity_primaryPromotion_privateDocIsolation_pathTraversal_andArchivedBookingRef() throws Exception {
        User owner = fixtures.createUser("ROLE_PARTNER_OWNER", "audMedOwn");
        Business biz = fixtures.createBusiness(owner, "Audit Media Fleet", VerificationStatus.APPROVED);
        User tourist = fixtures.createUser("ROLE_TOURIST", "audMedTour");
        Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
        Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
        VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");

        Vehicle v = fixtures.saveDirectVehicle(biz, pb, pbHub, scooter, VehicleStatus.ACTIVE, new BigDecimal("600.00"));
        String token = fixtures.bearerToken(owner, biz);

        // Upload 2 images -> deleting primary auto-promotes second image
        MvcResult upRes = mockMvc.perform(multipart("/api/v1/partners/vehicles/" + v.getPublicId() + "/images")
                        .file(InventoryTestSupport.validPngFile("files", "first.png"))
                        .file(InventoryTestSupport.validJpegFile("files", "second.jpg"))
                        .with(csrf())
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn();
        var imgs = InventoryTestSupport.JSON.readTree(upRes.getResponse().getContentAsString());
        String firstImg = imgs.get(0).get("publicId").asText();
        String secondImg = imgs.get(1).get("publicId").asText();

        mockMvc.perform(delete("/api/v1/partners/vehicles/" + v.getPublicId() + "/images/" + firstImg)
                        .with(csrf())
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/vehicles/" + v.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].publicId").value(secondImg))
                .andExpect(jsonPath("$.images[0].primary").value(true));

        // Upload private compliance doc -> public media endpoint MUST return 404
        MvcResult docRes = mockMvc.perform(multipart("/api/v1/partners/vehicles/" + v.getPublicId() + "/documents")
                        .file(InventoryTestSupport.validPdfFile("file", "rc.pdf"))
                        .param("documentTypeCode", "RC_BOOK")
                        .param("expiryDate", LocalDate.now().plusYears(1).toString())
                        .with(csrf())
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn();
        String docPublicId = InventoryTestSupport.JSON.readTree(docRes.getResponse().getContentAsString()).get("publicId").asText();

        mockMvc.perform(get("/api/v1/media/vehicle-images/" + docPublicId))
                .andExpect(status().isNotFound());

        // Path traversal rejected by storage service
        assertThatThrownBy(() -> mediaStorageService.loadBytes(MediaVisibility.PRIVATE, "../../etc/passwd"))
                .isInstanceOf(BusinessRuleException.class);

        // Historical completed booking preserves vehicle reference when vehicle is archived
        Booking historical = new Booking();
        historical.setBookingReference("BKH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        historical.setUser(tourist);
        historical.setVehicle(v);
        historical.setBusiness(biz);
        historical.setPickupLocation(pbHub);
        historical.setReturnLocation(pbHub);
        historical.setStartTime(Instant.now().minus(5, ChronoUnit.DAYS));
        historical.setEndTime(Instant.now().minus(3, ChronoUnit.DAYS));
        historical.setTotalDays(2);
        historical.setDailyRate(new BigDecimal("600.00"));
        historical.setBaseRentalAmount(new BigDecimal("1200.00"));
        historical.setTotalPayableAmount(new BigDecimal("1200.00"));
        historical.setStatus(BookingStatus.COMPLETED);
        historical = bookingRepository.save(historical);

        mockMvc.perform(delete("/api/v1/partners/vehicles/" + v.getPublicId())
                        .with(csrf())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Archived vehicle public media and catalog detail both hidden (404), booking FK preserved
        mockMvc.perform(get("/api/v1/media/vehicle-images/" + secondImg)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/vehicles/" + v.getPublicId())).andExpect(status().isNotFound());

        Booking reloaded = bookingRepository.findById(historical.getId()).orElseThrow();
        assertThat(reloaded.getVehicle().getPublicId()).isEqualTo(v.getPublicId());
        assertThat(reloaded.getBookingReference()).isEqualTo(historical.getBookingReference());
    }

    @Test
    void concurrentBlockCreation_preventsConflictingInventoryStates() throws Exception {
        record FixtureIds(Long userId, Long businessId, Long staffId, Long vehicleId, String vehiclePublicId, String token) {}

        FixtureIds ids = transactionTemplate.execute(status -> {
            User owner = fixtures.createUser("ROLE_PARTNER_OWNER", "concOwn");
            Business biz = fixtures.createBusiness(owner, "Concurrent Fleet", VerificationStatus.APPROVED);
            List<PartnerStaff> staffs = partnerStaffRepository.findAllByUserIdAndActiveTrue(owner.getId());
            PartnerStaff ownerStaff = staffs.stream()
                    .filter(s -> s.getBusiness().getId().equals(biz.getId()))
                    .findFirst()
                    .orElseThrow();
            Island pb = fixtures.requireIslandByCode("PORT_BLAIR");
            Location pbHub = fixtures.firstActiveHubForIsland(pb.getId());
            VehicleCategory scooter = fixtures.requireCategoryByCode("SCOOTER");
            Vehicle v = fixtures.saveDirectVehicle(biz, pb, pbHub, scooter, VehicleStatus.ACTIVE, new BigDecimal("500.00"));
            String token = fixtures.bearerToken(owner, biz);
            return new FixtureIds(owner.getId(), biz.getId(), ownerStaff.getId(), v.getId(), v.getPublicId(), token);
        });
        assertThat(ids).isNotNull();

        try {
            Instant start = Instant.now().plus(12, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(14, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
            String payload = InventoryTestSupport.JSON.writeValueAsString(
                    new CreateAvailabilityBlockRequest(start, end, "MAINTENANCE", "CONCURRENT_RACE", null, null, true)
            );

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        go.await(5, TimeUnit.SECONDS);
                        return mockMvc.perform(post("/api/v1/partners/vehicles/" + ids.vehiclePublicId() + "/availability/blocks")
                                        .with(csrf())
                                        .header("Authorization", ids.token())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }));
                }

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                go.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> future : futures) {
                    statuses.add(future.get(15, TimeUnit.SECONDS));
                }

                assertThat(statuses).containsExactlyInAnyOrder(201, 409);
                assertThat(unavailabilityRepository.findByVehicleIdOrderByStartTimeAsc(ids.vehicleId())).hasSize(1);
            } finally {
                pool.shutdownNow();
            }
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                var blocks = unavailabilityRepository.findByVehicleIdOrderByStartTimeAsc(ids.vehicleId());
                unavailabilityRepository.deleteAll(blocks);
                vehicleRepository.deleteById(ids.vehicleId());
                partnerStaffRepository.deleteById(ids.staffId());
                businessRepository.deleteById(ids.businessId());
                userRepository.deleteById(ids.userId());
            });
        }
    }
}

