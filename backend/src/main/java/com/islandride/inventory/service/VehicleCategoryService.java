package com.islandride.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.administration.service.AuditLogService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.inventory.domain.VehicleCategory;
import com.islandride.inventory.dto.SaveVehicleCategoryRequest;
import com.islandride.inventory.dto.VehicleCategoryDto;
import com.islandride.inventory.repository.VehicleCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VehicleCategoryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final VehicleCategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public VehicleCategoryService(VehicleCategoryRepository categoryRepository,
                                  AuditLogService auditLogService) {
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<VehicleCategoryDto> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(VehicleCategoryService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleCategoryDto> listAllCategoriesAdmin() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(VehicleCategoryService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleCategory requireActiveCategory(Long categoryId) {
        VehicleCategory cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle category not found: " + categoryId));
        if (!cat.isActive()) {
            throw new BusinessRuleException("Vehicle category '" + cat.getName() + "' is currently inactive.");
        }
        return cat;
    }

    @Transactional
    public VehicleCategoryDto createCategory(SaveVehicleCategoryRequest req, Long actorUserId) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        String slug = req.slug().trim().toLowerCase(Locale.ROOT);
        String name = req.name().trim();

        if (categoryRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Category code already exists: " + code);
        }
        if (categoryRepository.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("Category slug already exists: " + slug);
        }
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category name already exists: " + name);
        }

        VehicleCategory category = new VehicleCategory();
        applyFields(category, req, code, slug, name);

        VehicleCategory saved = categoryRepository.save(category);
        auditLogService.record(actorUserId, "VEHICLE_CATEGORY_CREATED", "VehicleCategory", saved.getId().toString(), null,
                Map.of("code", saved.getCode(), "slug", saved.getSlug(), "name", saved.getName()));
        return toDto(saved);
    }

    @Transactional
    public VehicleCategoryDto updateCategory(Long id, SaveVehicleCategoryRequest req, Long actorUserId) {
        VehicleCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle category not found: " + id));

        String code = req.code().trim().toUpperCase(Locale.ROOT);
        String slug = req.slug().trim().toLowerCase(Locale.ROOT);
        String name = req.name().trim();

        categoryRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ConflictException("Category code already exists: " + code); });
        categoryRepository.findBySlug(slug)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ConflictException("Category slug already exists: " + slug); });

        String oldCode = category.getCode();
        boolean oldActive = category.isActive();

        applyFields(category, req, code, slug, name);
        VehicleCategory saved = categoryRepository.save(category);

        auditLogService.record(actorUserId, "VEHICLE_CATEGORY_UPDATED", "VehicleCategory", saved.getId().toString(),
                Map.of("code", oldCode, "active", oldActive),
                Map.of("code", saved.getCode(), "slug", saved.getSlug(), "active", saved.isActive()));
        return toDto(saved);
    }

    @Transactional
    public VehicleCategoryDto updateCategoryStatus(Long id, boolean active, Long actorUserId) {
        VehicleCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle category not found: " + id));
        boolean oldActive = category.isActive();
        category.setActive(active);
        VehicleCategory saved = categoryRepository.save(category);

        auditLogService.record(actorUserId, "VEHICLE_CATEGORY_STATUS_CHANGED", "VehicleCategory", saved.getId().toString(),
                Map.of("active", oldActive), Map.of("active", active));
        return toDto(saved);
    }

    private void applyFields(VehicleCategory category, SaveVehicleCategoryRequest req, String code, String slug, String name) {
        category.setName(name);
        category.setCode(code);
        category.setSlug(slug);
        category.setVehicleType(req.vehicleType());
        category.setDescription(req.description());
        category.setIconRef(req.iconRef());
        category.setApplicableAttributesJson(serializeAttributes(req.applicableAttributes()));
        if (req.displayOrder() != null) {
            category.setDisplayOrder(req.displayOrder());
        }
        if (req.active() != null) {
            category.setActive(req.active());
        }
    }

    public static VehicleCategoryDto toDto(VehicleCategory category) {
        if (category == null) {
            return null;
        }
        return new VehicleCategoryDto(
                category.getId(),
                category.getName(),
                category.getCode(),
                category.getSlug(),
                category.getVehicleType(),
                category.getDescription(),
                category.getIconRef(),
                deserializeAttributes(category.getApplicableAttributesJson()),
                category.isActive(),
                category.getDisplayOrder()
        );
    }

    private static String serializeAttributes(List<String> attrs) {
        if (attrs == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attrs);
        } catch (JsonProcessingException ex) {
            throw new BusinessRuleException("Invalid applicableAttributes structure.");
        }
    }

    private static List<String> deserializeAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return Collections.emptyList();
        }
    }
}

