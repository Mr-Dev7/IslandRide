package com.islandride.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.security.JwtTokenProvider;
import com.islandride.core.security.UserPrincipal;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.IslandRepository;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.Role;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.repository.RoleRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.*;
import com.islandride.inventory.dto.SaveVehicleRequest;
import com.islandride.inventory.repository.VehicleCategoryRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
class InventoryTestSupport {

    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    BusinessRepository businessRepository;
    @Autowired
    PartnerStaffRepository partnerStaffRepository;
    @Autowired
    IslandRepository islandRepository;
    @Autowired
    LocationRepository locationRepository;
    @Autowired
    VehicleCategoryRepository categoryRepository;
    @Autowired
    VehicleRepository vehicleRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtTokenProvider jwtTokenProvider;

    User createUser(String roleName, String prefix) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail(prefix + "_" + tag + "@islandride.test");
        user.setPhone("+9194" + tag.replaceAll("[^0-9]", "7").substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("StrongPass123!"));
        user.setFirstName(prefix);
        user.setLastName("Test");
        user.setStatus(UserStatus.ACTIVE);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Missing seeded role: " + roleName));
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    Business createBusiness(User owner, String tradeName, VerificationStatus status) {
        Business b = new Business();
        b.setOwnerUser(owner);
        b.setTradeName(tradeName);
        b.setLegalName(tradeName + " Private Limited");
        b.setGstin("35ABCDE1234F1Z5");
        b.setPanNumber("ABCDE1234F");
        b.setVerificationStatus(status);
        Business saved = businessRepository.save(b);

        PartnerStaff staff = new PartnerStaff();
        staff.setBusiness(saved);
        staff.setUser(owner);
        staff.setRoleInBusiness("OWNER");
        staff.setActive(true);
        partnerStaffRepository.save(staff);

        return saved;
    }

    PartnerStaff attachStaff(Business business, User user, String roleInBusiness) {
        PartnerStaff staff = new PartnerStaff();
        staff.setBusiness(business);
        staff.setUser(user);
        staff.setRoleInBusiness(roleInBusiness);
        staff.setActive(true);
        return partnerStaffRepository.save(staff);
    }

    String bearerToken(User user, Business business) {
        UserPrincipal principal = UserPrincipal.create(user, business != null ? business.getId() : null);
        return "Bearer " + jwtTokenProvider.generateAccessToken(principal);
    }

    Island requireIslandByCode(String code) {
        return islandRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing island: " + code));
    }

    Location firstActiveHubForIsland(Long islandId) {
        return locationRepository.findByIslandIdAndActiveTrue(islandId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hub on island " + islandId));
    }

    VehicleCategory requireCategoryByCode(String code) {
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing category: " + code));
    }

    SaveVehicleRequest buildVehicleRequest(
            String businessPublicId,
            Island island,
            Location pickup,
            Location drop,
            boolean allowDiffReturn,
            VehicleCategory category,
            String make,
            String model,
            BigDecimal dailyRate
    ) {
        String regSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return new SaveVehicleRequest(
                businessPublicId,
                island.getId(),
                pickup != null ? pickup.getPublicId() : null,
                drop != null ? drop.getPublicId() : null,
                allowDiffReturn,
                category.getId(),
                make + " " + model + " 2024",
                make,
                model,
                2024,
                "Well maintained island rental vehicle",
                "AN01T" + regSuffix,
                "Pearl White",
                TransmissionType.AUTOMATIC,
                FuelType.PETROL,
                category.getVehicleType() == VehicleType.TWO_WHEELER ? 2 : 5,
                new BigDecimal("42.50"),
                category.getVehicleType() == VehicleType.TWO_WHEELER ? 125 : 1498,
                VehicleCondition.EXCELLENT,
                "Bring original driving licence at pickup.",
                dailyRate,
                new BigDecimal("2000.00"),
                60,
                BookingMode.INSTANT,
                List.of("Helmet Included", "USB Charger")
        );
    }

    Vehicle saveDirectVehicle(Business business, Island island, Location pickup, VehicleCategory category, VehicleStatus status, BigDecimal rate) {
        Vehicle v = new Vehicle();
        v.setBusiness(business);
        v.setIsland(island);
        v.setPickupLocation(pickup);
        v.setReturnLocation(pickup);
        v.setCategory(category);
        v.setTitle("Test " + category.getName() + " " + UUID.randomUUID().toString().substring(0, 4));
        v.setMake("Honda");
        v.setModel("Activa 6G");
        v.setModelYear(2024);
        v.setRegistrationNumber("AN01Z" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        v.setTransmissionType(TransmissionType.AUTOMATIC);
        v.setFuelType(FuelType.PETROL);
        v.setSeatingCapacity(category.getVehicleType() == VehicleType.TWO_WHEELER ? 2 : 5);
        v.setDailyRate(rate);
        v.setSecurityDeposit(new BigDecimal("1500.00"));
        v.setStatus(status);
        return vehicleRepository.save(v);
    }

    static MockMultipartFile validPngFile(String fieldName, String filename) {
        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x01};
        return new MockMultipartFile(fieldName, filename, "image/png", pngHeader);
    }

    static MockMultipartFile validJpegFile(String fieldName, String filename) {
        byte[] jpgHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        return new MockMultipartFile(fieldName, filename, "image/jpeg", jpgHeader);
    }

    static MockMultipartFile validPdfFile(String fieldName, String filename) {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile(fieldName, filename, "application/pdf", pdfBytes);
    }
}

