package com.islandride.partner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.domain.CommissionStatus;
import com.islandride.commission.domain.Settlement;
import com.islandride.commission.domain.SettlementStatus;
import com.islandride.commission.repository.CommissionRepository;
import com.islandride.commission.repository.SettlementRepository;
import com.islandride.core.security.JwtTokenProvider;
import com.islandride.core.security.UserPrincipal;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.IslandRepository;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.Role;
import com.islandride.identityaccess.domain.RoleType;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.repository.RoleRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.BookingMode;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleCategory;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.repository.VehicleCategoryRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.paymentfinance.domain.PartnerReceivable;
import com.islandride.paymentfinance.domain.ReceivableStatus;
import com.islandride.paymentfinance.repository.PartnerReceivableRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PartnerFinancialIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private PartnerStaffRepository partnerStaffRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleCategoryRepository categoryRepository;

    @Autowired
    private IslandRepository islandRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CommissionRepository commissionRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private PartnerReceivableRepository receivableRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private final List<Long> businessesToClean = new CopyOnWriteArrayList<>();
    private final List<Long> usersToClean = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            for (Long bizId : businessesToClean) {
                entityManager.createQuery("DELETE FROM PartnerReceivable pr WHERE pr.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Settlement s WHERE s.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Commission c WHERE c.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Booking b WHERE b.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Vehicle v WHERE v.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM PartnerStaff ps WHERE ps.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Business b WHERE b.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
            }
            for (Long uid : usersToClean) {
                entityManager.createQuery("DELETE FROM User u WHERE u.id = :uid")
                        .setParameter("uid", uid).executeUpdate();
            }
        });
        businessesToClean.clear();
        usersToClean.clear();
    }

    @Test
    @DisplayName("Financial overview returns correct aggregated financial ledger values")
    void testPartnerFinancialOverviewCalculations_Success() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 6);
        User ownerA = createTestUser("fin_owner_a_" + token + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business bizA = createTestBusiness(ownerA, "Fin Fleet A " + token);
        User tourist = createTestUser("fin_tourist_" + token + "@islandride.test", RoleType.ROLE_TOURIST);
        Vehicle vehicle = createTestVehicle(bizA, "Scooter " + token);

        // Create booking
        Booking booking = new Booking();
        booking.setUser(tourist);
        booking.setVehicle(vehicle);
        booking.setBusiness(bizA);
        booking.setPickupLocation(vehicle.getPickupLocation());
        booking.setReturnLocation(vehicle.getReturnLocation());
        booking.setBookingReference("BK-FIN-" + token);
        booking.setStartTime(Instant.now().plus(Duration.ofDays(1)));
        booking.setEndTime(Instant.now().plus(Duration.ofDays(3)));
        booking.setTotalDays(2);
        booking.setDailyRate(new BigDecimal("1000.00"));
        booking.setBaseRentalAmount(new BigDecimal("2000.00"));
        booking.setTotalPayableAmount(new BigDecimal("2000.00"));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        // Commission: gross 2000, comm 80, payout 1920, onlineCollected 2000
        Commission commission = new Commission();
        commission.setBooking(booking);
        commission.setBusiness(bizA);
        commission.setGrossBookingAmount(new BigDecimal("2000.00"));
        commission.setTaxExcludedAmount(new BigDecimal("2000.00"));
        commission.setDepositExcludedAmount(new BigDecimal("2000.00"));
        commission.setCommissionPercentage(new BigDecimal("4.00"));
        commission.setCommissionAmount(new BigDecimal("80.00"));
        commission.setPartnerPayoutAmount(new BigDecimal("1920.00"));
        commission.setOnlineCollectedAmount(new BigDecimal("2000.00"));
        commission.setOfflineCollectedAmount(BigDecimal.ZERO);
        commission.setStatus(CommissionStatus.ACCRUED);
        commission = commissionRepository.save(commission);

        // Settlement: net payout 1000, status COMPLETED
        Settlement settlement = new Settlement();
        settlement.setBusiness(bizA);
        settlement.setSettlementPeriodStart(LocalDate.now().minusDays(7));
        settlement.setSettlementPeriodEnd(LocalDate.now());
        settlement.setTotalGrossAmount(new BigDecimal("2000.00"));
        settlement.setTotalCommissionDeducted(new BigDecimal("80.00"));
        settlement.setNetPayoutAmount(new BigDecimal("1000.00"));
        settlement.setStatus(SettlementStatus.COMPLETED);
        settlement.setBankUtrReference("UTR" + token);
        settlement.setProcessedBy(ownerA);
        settlement.setProcessedAt(Instant.now());
        settlementRepository.save(settlement);

        // Partner Receivable: due 150, settled 50, remaining 100
        PartnerReceivable receivable = new PartnerReceivable();
        receivable.setBusiness(bizA);
        receivable.setBooking(booking);
        receivable.setCommission(commission);
        receivable.setAmountDueFromPartner(new BigDecimal("150.00"));
        receivable.setAmountSettled(new BigDecimal("50.00"));
        receivable.setStatus(ReceivableStatus.OPEN);
        receivableRepository.save(receivable);

        // Generate token for Owner A
        UserPrincipal principalA = UserPrincipal.create(ownerA, bizA.getId());
        String jwtTokenA = jwtTokenProvider.generateAccessToken(principalA);

        mockMvc.perform(get("/api/v1/partners/financials/overview")
                        .param("businessId", bizA.getId().toString())
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(bizA.getId()))
                .andExpect(jsonPath("$.tradeName").value("Fin Fleet A " + token))
                .andExpect(jsonPath("$.totalGrossBookings").value(2000.00))
                .andExpect(jsonPath("$.totalCommissionDeducted").value(80.00))
                .andExpect(jsonPath("$.totalNetEarnings").value(1920.00))
                .andExpect(jsonPath("$.onlineCollectedByPlatform").value(2000.00))
                .andExpect(jsonPath("$.offlineCollectedByPartner").value(0.00))
                .andExpect(jsonPath("$.totalSettledPaidOut").value(1000.00))
                .andExpect(jsonPath("$.pendingSettlementBalance").value(920.00))
                .andExpect(jsonPath("$.openReceivablesBalance").value(100.00))
                .andExpect(jsonPath("$.totalCompletedBookings").value(1))
                .andExpect(jsonPath("$.recentCommissions", hasSize(1)))
                .andExpect(jsonPath("$.recentCommissions[0].bookingReference").value("BK-FIN-" + token))
                .andExpect(jsonPath("$.recentSettlements", hasSize(1)))
                .andExpect(jsonPath("$.recentSettlements[0].netPayoutAmount").value(1000.00));
    }

    @Test
    @DisplayName("Itemized commissions endpoint returns detailed commission statement records")
    void testPartnerFinancialCommissionsList_Success() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 6);
        User ownerA = createTestUser("fin_comm_owner_" + token + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business bizA = createTestBusiness(ownerA, "Comm Fleet " + token);
        User tourist = createTestUser("fin_comm_tourist_" + token + "@islandride.test", RoleType.ROLE_TOURIST);
        Vehicle vehicle = createTestVehicle(bizA, "Bike " + token);

        Booking booking = new Booking();
        booking.setUser(tourist);
        booking.setVehicle(vehicle);
        booking.setBusiness(bizA);
        booking.setPickupLocation(vehicle.getPickupLocation());
        booking.setReturnLocation(vehicle.getReturnLocation());
        booking.setBookingReference("BK-COMM-" + token);
        booking.setStartTime(Instant.now().plus(Duration.ofDays(1)));
        booking.setEndTime(Instant.now().plus(Duration.ofDays(2)));
        booking.setTotalDays(1);
        booking.setDailyRate(new BigDecimal("1500.00"));
        booking.setBaseRentalAmount(new BigDecimal("1500.00"));
        booking.setTotalPayableAmount(new BigDecimal("1500.00"));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        Commission commission = new Commission();
        commission.setBooking(booking);
        commission.setBusiness(bizA);
        commission.setGrossBookingAmount(new BigDecimal("1500.00"));
        commission.setTaxExcludedAmount(new BigDecimal("1500.00"));
        commission.setDepositExcludedAmount(new BigDecimal("1500.00"));
        commission.setCommissionPercentage(new BigDecimal("4.00"));
        commission.setCommissionAmount(new BigDecimal("60.00"));
        commission.setPartnerPayoutAmount(new BigDecimal("1440.00"));
        commission.setOnlineCollectedAmount(new BigDecimal("1500.00"));
        commission.setOfflineCollectedAmount(BigDecimal.ZERO);
        commission.setStatus(CommissionStatus.ACCRUED);
        commissionRepository.save(commission);

        UserPrincipal principalA = UserPrincipal.create(ownerA, bizA.getId());
        String jwtTokenA = jwtTokenProvider.generateAccessToken(principalA);

        mockMvc.perform(get("/api/v1/partners/financials/commissions")
                        .param("businessId", bizA.getId().toString())
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bookingReference").value("BK-COMM-" + token))
                .andExpect(jsonPath("$[0].grossRentalAmount").value(1500.00))
                .andExpect(jsonPath("$[0].commissionPercentage").value(4.00))
                .andExpect(jsonPath("$[0].commissionAmount").value(60.00))
                .andExpect(jsonPath("$[0].partnerEarnings").value(1440.00))
                .andExpect(jsonPath("$[0].status").value("ACCRUED"));
    }

    @Test
    @DisplayName("Defaulting businessId from user session context succeeds when query param is omitted")
    void testDefaultBusinessIdFromPrincipalContext() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 6);
        User owner = createTestUser("fin_ctx_owner_" + token + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business biz = createTestBusiness(owner, "Context Fleet " + token);

        UserPrincipal principal = UserPrincipal.create(owner, biz.getId());
        String jwtToken = jwtTokenProvider.generateAccessToken(principal);

        // Call WITHOUT ?businessId parameter
        mockMvc.perform(get("/api/v1/partners/financials/overview")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(biz.getId()))
                .andExpect(jsonPath("$.tradeName").value("Context Fleet " + token));
    }

    @Test
    @DisplayName("Tenant isolation: Partner B strictly forbidden from viewing Partner A's financial overview")
    void testTenantIsolation_PartnerBCannotAccessPartnerAFinancials() throws Exception {
        String tokenA = UUID.randomUUID().toString().substring(0, 6);
        String tokenB = UUID.randomUUID().toString().substring(0, 6);

        User ownerA = createTestUser("fin_iso_a_" + tokenA + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business bizA = createTestBusiness(ownerA, "Fleet A " + tokenA);

        User ownerB = createTestUser("fin_iso_b_" + tokenB + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business bizB = createTestBusiness(ownerB, "Fleet B " + tokenB);

        UserPrincipal principalB = UserPrincipal.create(ownerB, bizB.getId());
        String jwtTokenB = jwtTokenProvider.generateAccessToken(principalB);

        // Partner B tries to read Partner A's business financials
        mockMvc.perform(get("/api/v1/partners/financials/overview")
                        .param("businessId", bizA.getId().toString())
                        .header("Authorization", "Bearer " + jwtTokenB)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role lockdown: Tourist role strictly forbidden from financial endpoints (403)")
    void testRoleLockdown_TouristCannotAccessFinancials() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 6);
        User owner = createTestUser("fin_lock_own_" + token + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business biz = createTestBusiness(owner, "Lock Fleet " + token);

        User tourist = createTestUser("fin_lock_trst_" + token + "@islandride.test", RoleType.ROLE_TOURIST);
        UserPrincipal touristPrincipal = UserPrincipal.create(tourist, null);
        String touristToken = jwtTokenProvider.generateAccessToken(touristPrincipal);

        mockMvc.perform(get("/api/v1/partners/financials/overview")
                        .param("businessId", biz.getId().toString())
                        .header("Authorization", "Bearer " + touristToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role lockdown: Regular partner staff without manager role cannot access financials (403)")
    void testRoleLockdown_StaffWithoutManagerRoleCannotAccessFinancials() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 6);
        User owner = createTestUser("fin_stf_own_" + token + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business biz = createTestBusiness(owner, "Staff Fleet " + token);

        User staff = createTestUser("fin_stf_usr_" + token + "@islandride.test", RoleType.ROLE_PARTNER_STAFF);
        PartnerStaff staffRecord = new PartnerStaff();
        staffRecord.setBusiness(biz);
        staffRecord.setUser(staff);
        staffRecord.setRoleInBusiness("STAFF");
        staffRecord.setActive(true);
        partnerStaffRepository.save(staffRecord);

        UserPrincipal staffPrincipal = UserPrincipal.create(staff, biz.getId());
        String staffToken = jwtTokenProvider.generateAccessToken(staffPrincipal);

        mockMvc.perform(get("/api/v1/partners/financials/overview")
                        .param("businessId", biz.getId().toString())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to partner financials is rejected with 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/partners/financials/overview")
                        .param("businessId", "999999"))
                .andExpect(status().isUnauthorized());
    }

    private User createTestUser(String email, RoleType roleType) {
        Role role = roleRepository.findByName(roleType.name()).orElseThrow();
        User user = new User();
        user.setEmail(email.toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPhone("+9198" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000));
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(role);
        user = userRepository.save(user);
        usersToClean.add(user.getId());
        return user;
    }

    private Business createTestBusiness(User owner, String tradeName) {
        Business biz = new Business();
        biz.setOwnerUser(owner);
        biz.setTradeName(tradeName);
        biz.setLegalName(tradeName + " Legal Entity");
        biz.setGstin("35AABCA1234F1Z0");
        biz.setPanNumber("AABCA1234F");
        biz.setVerificationStatus(VerificationStatus.APPROVED);
        biz = businessRepository.save(biz);
        businessesToClean.add(biz.getId());

        PartnerStaff ownerStaff = new PartnerStaff();
        ownerStaff.setBusiness(biz);
        ownerStaff.setUser(owner);
        ownerStaff.setRoleInBusiness("OWNER");
        ownerStaff.setActive(true);
        ownerStaff.setCanRecordHandover(true);
        ownerStaff.setCanCollectOfflinePayments(true);
        ownerStaff.setCanMarkNoShow(true);
        partnerStaffRepository.save(ownerStaff);

        return biz;
    }

    private Vehicle createTestVehicle(Business biz, String title) {
        Island island = islandRepository.findByCode("PORT_BLAIR").orElseGet(() -> islandRepository.findAll().getFirst());
        Location loc = locationRepository.findByIslandIdAndActiveTrue(island.getId()).getFirst();
        VehicleCategory cat = categoryRepository.findAll().getFirst();

        Vehicle v = new Vehicle();
        v.setBusiness(biz);
        v.setIsland(island);
        v.setCategory(cat);
        v.setPickupLocation(loc);
        v.setReturnLocation(loc);
        v.setTitle(title);
        v.setMake("Honda");
        v.setModel("Activa 6G");
        v.setModelYear(2025);
        v.setRegistrationNumber("AN01" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT));
        v.setDailyRate(new BigDecimal("800.00"));
        v.setSecurityDeposit(new BigDecimal("1500.00"));
        v.setBookingMode(BookingMode.INSTANT);
        v.setStatus(VehicleStatus.ACTIVE);
        return vehicleRepository.save(v);
    }
}
