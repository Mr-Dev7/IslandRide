package com.islandride.inventory.api;

import com.islandride.core.security.UserPrincipal;
import com.islandride.inventory.dto.SaveVehicleCategoryRequest;
import com.islandride.inventory.dto.SaveVehicleDocumentTypeRequest;
import com.islandride.inventory.dto.VehicleCategoryDto;
import com.islandride.inventory.dto.VehicleDocumentTypeDto;
import com.islandride.inventory.service.VehicleCategoryService;
import com.islandride.inventory.service.VehicleDocumentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
@Tag(name = "Admin Catalog Configuration", description = "Configure database-driven vehicle categories and document types")
public class AdminCatalogConfigController {

    private final VehicleCategoryService categoryService;
    private final VehicleDocumentTypeService documentTypeService;

    public AdminCatalogConfigController(VehicleCategoryService categoryService,
                                        VehicleDocumentTypeService documentTypeService) {
        this.categoryService = categoryService;
        this.documentTypeService = documentTypeService;
    }

    @GetMapping("/categories")
    @Operation(summary = "List all vehicle categories including inactive")
    public ResponseEntity<List<VehicleCategoryDto>> listAllCategories() {
        return ResponseEntity.ok(categoryService.listAllCategoriesAdmin());
    }

    @PostMapping("/categories")
    @Operation(summary = "Create a vehicle category")
    public ResponseEntity<VehicleCategoryDto> createCategory(@AuthenticationPrincipal UserPrincipal principal,
                                                             @Valid @RequestBody SaveVehicleCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(req, principal.getId()));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update a vehicle category")
    public ResponseEntity<VehicleCategoryDto> updateCategory(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserPrincipal principal,
                                                             @Valid @RequestBody SaveVehicleCategoryRequest req) {
        return ResponseEntity.ok(categoryService.updateCategory(id, req, principal.getId()));
    }

    @PatchMapping("/categories/{id}/status")
    @Operation(summary = "Activate or deactivate a vehicle category")
    public ResponseEntity<VehicleCategoryDto> toggleCategoryStatus(@PathVariable Long id,
                                                                   @RequestParam boolean active,
                                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(categoryService.updateCategoryStatus(id, active, principal.getId()));
    }

    @GetMapping("/document-types")
    @Operation(summary = "List all vehicle document types")
    public ResponseEntity<List<VehicleDocumentTypeDto>> listDocumentTypes() {
        return ResponseEntity.ok(documentTypeService.listAllAdmin());
    }

    @PostMapping("/document-types")
    @Operation(summary = "Create a configurable vehicle document type")
    public ResponseEntity<VehicleDocumentTypeDto> createDocumentType(@AuthenticationPrincipal UserPrincipal principal,
                                                                     @Valid @RequestBody SaveVehicleDocumentTypeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentTypeService.create(req, principal.getId()));
    }

    @PutMapping("/document-types/{id}")
    @Operation(summary = "Update a configurable vehicle document type")
    public ResponseEntity<VehicleDocumentTypeDto> updateDocumentType(@PathVariable Long id,
                                                                     @AuthenticationPrincipal UserPrincipal principal,
                                                                     @Valid @RequestBody SaveVehicleDocumentTypeRequest req) {
        return ResponseEntity.ok(documentTypeService.update(id, req, principal.getId()));
    }
}

