package com.islandride.partner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PartnerRegisterRequest(
        @NotBlank(message = "Owner email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        String password,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+0-9]{10,15}$", message = "Phone must be a valid phone number")
        String phone,

        @NotBlank(message = "Business legal name is required")
        String legalName,

        @NotBlank(message = "Business trade name is required")
        String tradeName,

        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$", message = "Invalid GSTIN format")
        String gstin,

        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN number format")
        String panNumber
) {}
