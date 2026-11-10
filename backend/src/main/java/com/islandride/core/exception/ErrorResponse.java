package com.islandride.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard RFC 7807 Problem Details Error Representation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId,
        List<ValidationError> invalidParams,
        Instant timestamp
) {
    public record ValidationError(String name, String reason) {}

    public static ErrorResponse of(String type, String title, int status, String detail, String instance, String correlationId) {
        return new ErrorResponse(type, title, status, detail, instance, correlationId, null, Instant.now());
    }

    public static ErrorResponse withValidation(String type, String title, int status, String detail, String instance, String correlationId, List<ValidationError> invalidParams) {
        return new ErrorResponse(type, title, status, detail, instance, correlationId, invalidParams, Instant.now());
    }
}
