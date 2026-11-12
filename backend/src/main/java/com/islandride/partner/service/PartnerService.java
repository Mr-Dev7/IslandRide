package com.islandride.partner.service;

import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ResourceNotFoundException;
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
import com.islandride.partner.dto.BusinessDto;
import com.islandride.partner.dto.PartnerRegisterRequest;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PartnerService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BusinessRepository businessRepository;
    private final PartnerStaffRepository partnerStaffRepository;
    private final PasswordEncoder passwordEncoder;

    public PartnerService(UserRepository userRepository,
                          RoleRepository roleRepository,
                          BusinessRepository businessRepository,
                          PartnerStaffRepository partnerStaffRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.businessRepository = businessRepository;
        this.partnerStaffRepository = partnerStaffRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public BusinessDto registerPartner(PartnerRegisterRequest request) {
        if (userRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new BusinessRuleException("An account with this email address already exists");
        }

        if (userRepository.existsByPhone(request.phone().trim())) {
            throw new BusinessRuleException("An account with this phone number already exists");
        }

        Role ownerRole = roleRepository.findByName(RoleType.ROLE_PARTNER_OWNER.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_PARTNER_OWNER role not seeded"));

        // 1. Create Partner Owner User (Status ACTIVE)
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone().trim());
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(ownerRole);

        User savedUser = userRepository.save(user);

        // 2. Create Partner Business (Strict Separation: VerificationStatus DRAFT / Unapproved)
        Business business = new Business();
        business.setOwnerUser(savedUser);
        business.setLegalName(request.legalName().trim());
        business.setTradeName(request.tradeName().trim());
        business.setGstin(request.gstin() != null && !request.gstin().isBlank() ? request.gstin().trim() : null);
        business.setPanNumber(request.panNumber() != null && !request.panNumber().isBlank() ? request.panNumber().trim() : null);
        business.setVerificationStatus(VerificationStatus.DRAFT);
        business.setCommissionRate(new BigDecimal("4.00"));

        Business savedBusiness = businessRepository.save(business);

        // 3. Create PartnerStaff Record for owner with default operational permissions
        PartnerStaff staff = new PartnerStaff();
        staff.setBusiness(savedBusiness);
        staff.setUser(savedUser);
        staff.setRoleInBusiness("OWNER");
        staff.setActive(true);
        staff.setCanRecordHandover(true);
        staff.setCanCollectOfflinePayments(true);
        staff.setCanMarkNoShow(true);

        partnerStaffRepository.save(staff);

        return toBusinessDto(savedBusiness);
    }

    @Transactional(readOnly = true)
    public BusinessDto getMyBusiness(UserPrincipal principal) {
        // Find by ownership or staff membership
        return businessRepository.findByOwnerUserId(principal.getId())
                .or(() -> partnerStaffRepository.findFirstByUserIdAndActiveTrueOrderByIdAsc(principal.getId()).map(PartnerStaff::getBusiness))
                .map(this::toBusinessDto)
                .orElseThrow(() -> new ResourceNotFoundException("No active business associated with current account"));
    }

    @Transactional(readOnly = true)
    public List<BusinessDto> getMyBusinesses(UserPrincipal principal) {
        return partnerStaffRepository.findAllByUserIdAndActiveTrue(principal.getId()).stream()
                .map(PartnerStaff::getBusiness)
                .map(this::toBusinessDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public BusinessDto updateMyBusiness(UserPrincipal principal, BusinessDto updateRequest) {
        Business business = businessRepository.findByOwnerUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found or caller is not authorized owner"));

        if (updateRequest.tradeName() != null && !updateRequest.tradeName().isBlank()) {
            business.setTradeName(updateRequest.tradeName().trim());
        }
        if (updateRequest.legalName() != null && !updateRequest.legalName().isBlank()) {
            business.setLegalName(updateRequest.legalName().trim());
        }
        if (updateRequest.gstin() != null) {
            business.setGstin(updateRequest.gstin().trim());
        }
        if (updateRequest.panNumber() != null) {
            business.setPanNumber(updateRequest.panNumber().trim());
        }

        Business saved = businessRepository.save(business);
        return toBusinessDto(saved);
    }

    public BusinessDto toBusinessDto(Business business) {
        return new BusinessDto(
                business.getPublicId(),
                business.getLegalName(),
                business.getTradeName(),
                business.getGstin(),
                business.getPanNumber(),
                business.getVerificationStatus().name(),
                business.getRejectionReason(),
                business.getCommissionRate()
        );
    }
}

