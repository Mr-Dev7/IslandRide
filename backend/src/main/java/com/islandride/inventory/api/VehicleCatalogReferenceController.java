package com.islandride.inventory.api;

import com.islandride.inventory.dto.VehicleCategoryDto;
import com.islandride.inventory.dto.VehicleDocumentTypeDto;
import com.islandride.inventory.service.VehicleCategoryService;
import com.islandride.inventory.service.VehicleDocumentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Vehicle Catalog Reference", description = "Public endpoints for active vehicle categories and configurable document types")
public class VehicleCatalogReferenceController {

    private final VehicleCategoryService categoryService;
    private final VehicleDocumentTypeService documentTypeService;

    public VehicleCatalogReferenceController(VehicleCategoryService categoryService,
                                             VehicleDocumentTypeService documentTypeService) {
        this.categoryService = categoryService;
        this.documentTypeService = documentTypeService;
    }

    @GetMapping("/categories")
    @Operation(summary = "List active configurable vehicle categories")
    public ResponseEntity<List<VehicleCategoryDto>> listCategories() {
        return ResponseEntity.ok(categoryService.listActiveCategories());
    }

    @GetMapping("/document-types")
    @Operation(summary = "List active configurable vehicle document types")
    public ResponseEntity<List<VehicleDocumentTypeDto>> listDocumentTypes() {
        return ResponseEntity.ok(documentTypeService.listActive());
    }
}

