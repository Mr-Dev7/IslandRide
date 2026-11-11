package com.islandride.inventory.api;

import com.islandride.inventory.service.VehicleMediaDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
public class PublicMediaController {

    private final VehicleMediaDocumentService mediaDocumentService;

    public PublicMediaController(VehicleMediaDocumentService mediaDocumentService) {
        this.mediaDocumentService = mediaDocumentService;
    }

    @GetMapping("/vehicle-images/{imagePublicId}")
    public ResponseEntity<byte[]> getPublicVehicleImage(@PathVariable String imagePublicId) {
        return MediaResponseHelper.buildMediaResponse(
                mediaDocumentService.loadPublicImageContent(imagePublicId),
                true,
                false
        );
    }
}

