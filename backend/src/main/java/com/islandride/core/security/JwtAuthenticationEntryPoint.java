package com.islandride.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.exception.ErrorResponse;
import com.islandride.core.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public JwtAuthenticationEntryPoint() {
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String correlationId = org.slf4j.MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null) {
            correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                "urn:islandride:error:unauthorized",
                "Unauthorized",
                HttpServletResponse.SC_UNAUTHORIZED,
                authException.getMessage() != null ? authException.getMessage() : "Full authentication is required to access this resource",
                request.getRequestURI(),
                correlationId
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
