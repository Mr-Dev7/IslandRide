package com.islandride.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.exception.ErrorResponse;
import com.islandride.core.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public CustomAccessDeniedHandler() {
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String correlationId = org.slf4j.MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null) {
            correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                "urn:islandride:error:forbidden",
                "Forbidden",
                HttpServletResponse.SC_FORBIDDEN,
                "Access is denied. You do not have permission to perform this action.",
                request.getRequestURI(),
                correlationId
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
