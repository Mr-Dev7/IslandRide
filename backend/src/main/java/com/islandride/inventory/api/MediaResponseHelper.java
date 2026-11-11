package com.islandride.inventory.api;

import com.islandride.inventory.service.VehicleMediaDocumentService.MediaContentResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class MediaResponseHelper {

    private MediaResponseHelper() {}

    static ResponseEntity<byte[]> buildMediaResponse(MediaContentResponse media, boolean publicCache, boolean asAttachment) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (media.contentType() != null && !media.contentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(media.contentType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set("X-Content-Type-Options", "nosniff");
        String safeFilename = media.filename() != null ? media.filename() : "file";
        ContentDisposition disposition = (asAttachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(safeFilename, StandardCharsets.UTF_8)
                .build();
        headers.setContentDisposition(disposition);
        if (publicCache) {
            headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        } else {
            headers.setCacheControl(CacheControl.noStore());
        }

        return ResponseEntity.ok().headers(headers).body(media.bytes());
    }
}

