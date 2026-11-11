package com.islandride.inventory.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.inventory.domain.VehicleDocumentType;
import com.islandride.inventory.dto.SaveVehicleDocumentTypeRequest;
import com.islandride.inventory.dto.VehicleDocumentTypeDto;
import com.islandride.inventory.repository.VehicleDocumentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VehicleDocumentTypeService {

    private final VehicleDocumentTypeRepository documentTypeRepository;
    private final AuditLogService auditLogService;

    public VehicleDocumentTypeService(VehicleDocumentTypeRepository documentTypeRepository,
                                      AuditLogService auditLogService) {
        this.documentTypeRepository = documentTypeRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<VehicleDocumentTypeDto> listActive() {
        return documentTypeRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(VehicleDocumentTypeService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleDocumentTypeDto> listAllAdmin() {
        return documentTypeRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(VehicleDocumentTypeService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleDocumentType requireActiveByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("Document type code is required.");
        }
        VehicleDocumentType type = documentTypeRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BusinessRuleException("Unsupported vehicle document type: " + code));
        if (!type.isActive()) {
            throw new BusinessRuleException("Vehicle document type '" + type.getCode() + "' is inactive.");
        }
        return type;
    }

    @Transactional
    public VehicleDocumentTypeDto create(SaveVehicleDocumentTypeRequest req, Long actorUserId) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (documentTypeRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Document type code already exists: " + code);
        }
        VehicleDocumentType type = new VehicleDocumentType();
        apply(type, req, code);
        VehicleDocumentType saved = documentTypeRepository.save(type);
        auditLogService.record(actorUserId, "VEHICLE_DOCUMENT_TYPE_CREATED", "VehicleDocumentType", saved.getId().toString(),
                null, Map.of("code", saved.getCode(), "mandatory", saved.isMandatory()));
        return toDto(saved);
    }

    @Transactional
    public VehicleDocumentTypeDto update(Long id, SaveVehicleDocumentTypeRequest req, Long actorUserId) {
        VehicleDocumentType type = documentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle document type not found: " + id));
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        documentTypeRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ConflictException("Document type code already exists: " + code); });

        apply(type, req, code);
        VehicleDocumentType saved = documentTypeRepository.save(type);
        auditLogService.record(actorUserId, "VEHICLE_DOCUMENT_TYPE_UPDATED", "VehicleDocumentType", saved.getId().toString(),
                null, Map.of("code", saved.getCode(), "active", saved.isActive()));
        return toDto(saved);
    }

    private void apply(VehicleDocumentType type, SaveVehicleDocumentTypeRequest req, String code) {
        type.setCode(code);
        type.setName(req.name().trim());
        type.setDescription(req.description());
        if (req.mandatory() != null) {
            type.setMandatory(req.mandatory());
        }
        if (req.requiresExpiryDate() != null) {
            type.setRequiresExpiryDate(req.requiresExpiryDate());
        }
        if (req.applicableVehicleTypes() != null && !req.applicableVehicleTypes().isBlank()) {
            type.setApplicableVehicleTypes(req.applicableVehicleTypes().trim().toUpperCase(Locale.ROOT));
        }
        if (req.active() != null) {
            type.setActive(req.active());
        }
        if (req.displayOrder() != null) {
            type.setDisplayOrder(req.displayOrder());
        }
    }

    public static VehicleDocumentTypeDto toDto(VehicleDocumentType type) {
        return new VehicleDocumentTypeDto(
                type.getId(),
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.isMandatory(),
                type.isRequiresExpiryDate(),
                type.getApplicableVehicleTypes(),
                type.isActive(),
                type.getDisplayOrder()
        );
    }
}

