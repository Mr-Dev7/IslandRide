package com.islandride.administration.service;

import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.dto.UserDto;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.identityaccess.service.AuthService;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.dto.BusinessDto;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.service.PartnerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminManagementService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PartnerService partnerService;

    public AdminManagementService(UserRepository userRepository,
                                  BusinessRepository businessRepository,
                                  PartnerService partnerService) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.partnerService = partnerService;
    }

    @Transactional(readOnly = true)
    public List<UserDto> listAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> AuthService.toUserDto(user, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto updateUserStatus(Long userId, UserStatus newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setStatus(newStatus);
        User saved = userRepository.save(user);
        return AuthService.toUserDto(saved, null);
    }

    @Transactional
    public BusinessDto verifyPartnerBusiness(Long businessId, VerificationStatus newStatus, String rejectionReason) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));

        business.setVerificationStatus(newStatus);
        if (newStatus == VerificationStatus.REJECTED) {
            business.setRejectionReason(rejectionReason);
        } else {
            business.setRejectionReason(null);
        }

        Business saved = businessRepository.save(business);
        return partnerService.toBusinessDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BusinessDto> listBusinessesByStatus(VerificationStatus status) {
        List<Business> businesses = (status != null) 
                ? businessRepository.findByVerificationStatus(status)
                : businessRepository.findAll();

        return businesses.stream()
                .map(partnerService::toBusinessDto)
                .collect(Collectors.toList());
    }
}
