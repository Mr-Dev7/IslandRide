package com.islandride.usermanagement.service;

import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.usermanagement.domain.TouristProfile;
import com.islandride.usermanagement.dto.TouristProfileDto;
import com.islandride.usermanagement.repository.TouristProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TouristProfileService {

    private final TouristProfileRepository touristProfileRepository;

    public TouristProfileService(TouristProfileRepository touristProfileRepository) {
        this.touristProfileRepository = touristProfileRepository;
    }

    @Transactional(readOnly = true)
    public TouristProfileDto getProfile(Long userId) {
        return touristProfileRepository.findByUserId(userId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Tourist profile not found for user: " + userId));
    }

    @Transactional
    public TouristProfileDto updateProfile(Long userId, TouristProfileDto dto) {
        TouristProfile profile = touristProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tourist profile not found for user: " + userId));

        if (dto.drivingLicenseNumber() != null) {
            profile.setDrivingLicenseNumber(dto.drivingLicenseNumber().trim());
        }
        if (dto.drivingLicenseExpiry() != null) {
            profile.setDrivingLicenseExpiry(dto.drivingLicenseExpiry());
        }
        if (dto.emergencyContactName() != null) {
            profile.setEmergencyContactName(dto.emergencyContactName().trim());
        }
        if (dto.emergencyContactPhone() != null) {
            profile.setEmergencyContactPhone(dto.emergencyContactPhone().trim());
        }
        if (dto.nationality() != null) {
            profile.setNationality(dto.nationality().trim());
        }

        TouristProfile saved = touristProfileRepository.save(profile);
        return toDto(saved);
    }

    private TouristProfileDto toDto(TouristProfile profile) {
        return new TouristProfileDto(
                profile.getDrivingLicenseNumber(),
                profile.getDrivingLicenseExpiry(),
                profile.getEmergencyContactName(),
                profile.getEmergencyContactPhone(),
                profile.getNationality(),
                profile.getVerificationStatus()
        );
    }
}
