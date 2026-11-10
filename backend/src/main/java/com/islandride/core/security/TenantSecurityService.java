package com.islandride.core.security;

import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for multi-tenant authorization and business resource scoping.
 * Enforces live database membership checks and separates account status from business approval.
 */
@Service("tenantSecurity")
public class TenantSecurityService {

    private final PartnerStaffRepository partnerStaffRepository;
    private final BusinessRepository businessRepository;

    public TenantSecurityService(PartnerStaffRepository partnerStaffRepository,
                                 BusinessRepository businessRepository) {
        this.partnerStaffRepository = partnerStaffRepository;
        this.businessRepository = businessRepository;
    }

    /**
     * Verifies that the currently authenticated user has an ACTIVE membership in the requested business.
     * Tokens are NEVER trusted as the sole source of authorization; revoked memberships are rejected immediately.
     */
    @Transactional(readOnly = true)
    public boolean hasBusinessAccess(Long businessId) {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null || businessId == null) {
            return false;
        }

        // Platform administrators have universal oversight
        if (principal.hasRole("ROLE_SUPER_ADMIN") || principal.hasRole("ROLE_OPERATIONS_ADMIN")) {
            return true;
        }

        // Check if user is the direct business owner
        Optional<Business> businessOpt = businessRepository.findById(businessId);
        if (businessOpt.isPresent() && businessOpt.get().getOwnerUser().getId().equals(principal.getId())) {
            return true;
        }

        // Check active partner staff record in database
        Optional<PartnerStaff> staffOpt = partnerStaffRepository.findByBusinessIdAndUserId(businessId, principal.getId());
        return staffOpt.map(PartnerStaff::isActive).orElse(false);
    }

    /**
     * Verifies that the business has completed onboarding and has been verified & approved.
     * Prevents pending or unapproved partners from executing operations requiring active approval.
     */
    @Transactional(readOnly = true)
    public boolean isBusinessApproved(Long businessId) {
        if (businessId == null) {
            return false;
        }
        return businessRepository.findById(businessId)
                .map(b -> b.getVerificationStatus() == VerificationStatus.APPROVED)
                .orElse(false);
    }

    /**
     * Verifies that the partner is authorized AND the business is fully approved.
     * Enforces requirement: An active partner account must not automatically authorize inventory management
     * for a pending or unapproved business.
     */
    @Transactional(readOnly = true)
    public boolean isAuthorizedForBusinessInventory(Long businessId) {
        return hasBusinessAccess(businessId) && isBusinessApproved(businessId);
    }

    /**
     * Checks if current user matches the target user ID or has administrative oversight.
     */
    public boolean isSelfOrAdmin(Long targetUserId) {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null || targetUserId == null) {
            return false;
        }
        if (principal.hasRole("ROLE_SUPER_ADMIN") || principal.hasRole("ROLE_OPERATIONS_ADMIN") || principal.hasRole("ROLE_SUPPORT_AGENT")) {
            return true;
        }
        return principal.getId().equals(targetUserId);
    }

    /**
     * Retrieves all business IDs for which the authenticated user currently holds active membership.
     * Supports multi-business partner staff/owners.
     */
    @Transactional(readOnly = true)
    public List<Long> getAuthorizedBusinessIds() {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        businessRepository.findByOwnerUserId(principal.getId()).ifPresent(b -> ids.add(b.getId()));

        List<PartnerStaff> activeStaff = partnerStaffRepository.findAllByUserIdAndActiveTrue(principal.getId());
        for (PartnerStaff staff : activeStaff) {
            if (!ids.contains(staff.getBusiness().getId())) {
                ids.add(staff.getBusiness().getId());
            }
        }
        return ids;
    }

    private UserPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}

