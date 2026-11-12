package com.islandride.core.security;

import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        // Platform administrators have legitimate oversight across existing non-deleted businesses
        if (principal.hasRole("ROLE_SUPER_ADMIN") || principal.hasRole("ROLE_OPERATIONS_ADMIN")) {
            return businessRepository.findById(businessId)
                    .map(b -> !b.isDeleted())
                    .orElse(false);
        }

        // Check if user is the direct business owner
        Optional<Business> businessOpt = businessRepository.findById(businessId);
        if (businessOpt.isPresent()
                && !businessOpt.get().isDeleted()
                && businessOpt.get().getOwnerUser().getId().equals(principal.getId())) {
            return true;
        }

        // Check active partner staff record in database
        Optional<PartnerStaff> staffOpt = partnerStaffRepository.findByBusinessIdAndUserId(businessId, principal.getId());
        return businessOpt.isPresent()
                && !businessOpt.get().isDeleted()
                && staffOpt.map(PartnerStaff::isActive).orElse(false);
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
                .map(b -> !b.isDeleted() && b.getVerificationStatus() == VerificationStatus.APPROVED)
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
     * Verifies that the authenticated user possesses active management authority (OWNER or MANAGER,
     * never read/handover-only STAFF) for inventory write operations on an APPROVED business.
     */
    @Transactional(readOnly = true)
    public boolean isAuthorizedForBusinessInventoryWrite(Long businessId) {
        return hasBusinessManagementAccess(businessId) && isBusinessApproved(businessId);
    }

    @Transactional(readOnly = true)
    public boolean hasBusinessManagementAccess(Long businessId) {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null || businessId == null) {
            return false;
        }
        if (principal.hasRole("ROLE_SUPER_ADMIN") || principal.hasRole("ROLE_OPERATIONS_ADMIN")) {
            return businessRepository.findById(businessId)
                    .map(b -> !b.isDeleted())
                    .orElse(false);
        }
        Optional<Business> businessOpt = businessRepository.findById(businessId);
        if (businessOpt.isEmpty() || businessOpt.get().isDeleted()) {
            return false;
        }
        if (businessOpt.get().getOwnerUser().getId().equals(principal.getId())
                && (principal.hasRole("ROLE_PARTNER_OWNER") || principal.hasRole("ROLE_PARTNER_MANAGER"))) {
            return true;
        }
        if (!(principal.hasRole("ROLE_PARTNER_OWNER") || principal.hasRole("ROLE_PARTNER_MANAGER"))) {
            return false;
        }
        Optional<PartnerStaff> staffOpt = partnerStaffRepository.findByBusinessIdAndUserId(businessId, principal.getId());
        return staffOpt.filter(PartnerStaff::isActive)
                .map(s -> "OWNER".equalsIgnoreCase(s.getRoleInBusiness()) || "MANAGER".equalsIgnoreCase(s.getRoleInBusiness()))
                .orElse(false);
    }

    /**
     * Resolves authorized active businesses from the database for the current partner user.
     * Supports single and multiple business memberships cleanly without trusting client claims.
     */
    @Transactional(readOnly = true)
    public List<Business> getAuthorizedBusinesses() {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null) {
            return List.of();
        }
        Map<Long, Business> result = new LinkedHashMap<>();
        for (Business b : businessRepository.findAllByOwnerUserId(principal.getId())) {
            if (!b.isDeleted()) {
                result.put(b.getId(), b);
            }
        }
        List<PartnerStaff> activeStaff = partnerStaffRepository.findAllByUserIdAndActiveTrue(principal.getId());
        for (PartnerStaff staff : activeStaff) {
            Business b = staff.getBusiness();
            if (b != null && !b.isDeleted()) {
                result.putIfAbsent(b.getId(), b);
            }
        }
        return new ArrayList<>(result.values());
    }

    /**
     * Resolves a single target business for partner operations strictly via DB membership.
     */
    @Transactional(readOnly = true)
    public Business resolvePartnerBusinessForRead(String optionalBusinessPublicId) {
        List<Business> authorized = getAuthorizedBusinesses();
        if (authorized.isEmpty()) {
            throw new AccessDeniedException("No active partner business membership found.");
        }
        if (optionalBusinessPublicId == null || optionalBusinessPublicId.isBlank()) {
            return authorized.getFirst();
        }
        return authorized.stream()
                .filter(b -> optionalBusinessPublicId.equals(b.getPublicId()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not authorized to access the specified business."));
    }

    /**
     * Resolves and verifies management write access + APPROVED business status before allowing inventory mutations.
     */
    @Transactional(readOnly = true)
    public Business resolvePartnerBusinessForInventoryWrite(String optionalBusinessPublicId) {
        Business business = resolvePartnerBusinessForRead(optionalBusinessPublicId);
        if (!hasBusinessManagementAccess(business.getId())) {
            throw new AccessDeniedException("Your partner role does not permit modifying vehicle inventory, pricing, or availability.");
        }
        if (!isBusinessApproved(business.getId())) {
            throw new AccessDeniedException("Your rental business must be verified and APPROVED before managing vehicle inventory.");
        }
        return business;
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
        return getAuthorizedBusinesses().stream().map(Business::getId).toList();
    }

    public Long getCurrentUserIdOrNull() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal != null ? principal.getId() : null;
    }

    @Transactional(readOnly = true)
    public void requireBusinessReadAccess(Long businessId) {
        if (!hasBusinessAccess(businessId)) {
            throw new AccessDeniedException("You are not authorized to access this rental business resource.");
        }
    }

    @Transactional(readOnly = true)
    public void requireBusinessInventoryWriteAccess(Long businessId) {
        if (!hasBusinessManagementAccess(businessId)) {
            throw new AccessDeniedException("Your partner role does not permit modifying vehicle inventory, pricing, or availability.");
        }
        if (!isBusinessApproved(businessId)) {
            throw new AccessDeniedException("Your rental business must be verified and APPROVED before managing vehicle inventory.");
        }
    }

    @Transactional(readOnly = true)
    public boolean canApproveOrRejectBookings(Long businessId) {
        return hasBusinessManagementAccess(businessId) && isBusinessApproved(businessId);
    }

    @Transactional(readOnly = true)
    public void requireBookingApprovalAccess(Long businessId) {
        if (!canApproveOrRejectBookings(businessId)) {
            throw new AccessDeniedException("Booking approval requires an active OWNER or MANAGER membership on an APPROVED business.");
        }
    }

    @Transactional(readOnly = true)
    public boolean canRecordHandover(Long businessId) {
        return hasOperationalStaffPermission(businessId, PartnerStaff::isCanRecordHandover);
    }

    @Transactional(readOnly = true)
    public void requireHandoverAccess(Long businessId) {
        if (!canRecordHandover(businessId)) {
            throw new AccessDeniedException("Your partner account is not permitted to record vehicle handovers.");
        }
    }

    @Transactional(readOnly = true)
    public boolean canCollectOfflinePayments(Long businessId) {
        return hasOperationalStaffPermission(businessId, PartnerStaff::isCanCollectOfflinePayments);
    }

    @Transactional(readOnly = true)
    public void requireOfflineCollectionAccess(Long businessId) {
        if (!canCollectOfflinePayments(businessId)) {
            throw new AccessDeniedException("Your partner account is not permitted to collect offline payments.");
        }
    }

    @Transactional(readOnly = true)
    public boolean canMarkNoShow(Long businessId) {
        return hasOperationalStaffPermission(businessId, PartnerStaff::isCanMarkNoShow);
    }

    @Transactional(readOnly = true)
    public void requireNoShowAccess(Long businessId) {
        if (!canMarkNoShow(businessId)) {
            throw new AccessDeniedException("Your partner account is not permitted to mark bookings as NO_SHOW.");
        }
    }

    public boolean canEscalateRefund() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal != null && (principal.hasRole("ROLE_SUPER_ADMIN")
                || principal.hasRole("ROLE_FINANCE_ADMIN")
                || principal.hasRole("ROLE_OPERATIONS_ADMIN")
                || principal.hasRole("ROLE_SUPPORT_AGENT"));
    }

    public boolean canApproveFinancialRefunds() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal != null && (principal.hasRole("ROLE_SUPER_ADMIN")
                || principal.hasRole("ROLE_FINANCE_ADMIN"));
    }

    public void requireFinancialRefundApprovalAccess() {
        if (!canApproveFinancialRefunds()) {
            throw new AccessDeniedException("Financial refund approvals require FINANCE_ADMIN or SUPER_ADMIN authority.");
        }
    }

    private boolean hasOperationalStaffPermission(Long businessId,
                                                  java.util.function.Predicate<PartnerStaff> permissionCheck) {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null || businessId == null) {
            return false;
        }
        if (!isBusinessApproved(businessId)) {
            return false;
        }
        if (principal.hasRole("ROLE_SUPER_ADMIN") || principal.hasRole("ROLE_OPERATIONS_ADMIN")) {
            return true;
        }
        if (!hasBusinessAccess(businessId)) {
            return false;
        }
        Optional<PartnerStaff> staffOpt = partnerStaffRepository.findByBusinessIdAndUserId(businessId, principal.getId());
        if (staffOpt.isPresent()) {
            PartnerStaff staff = staffOpt.get();
            return staff.isActive() && permissionCheck.test(staff);
        }
        // Only a direct business owner without an explicit PartnerStaff row retains default owner operational authority
        return hasBusinessManagementAccess(businessId)
                && businessRepository.findById(businessId)
                        .map(b -> !b.isDeleted() && b.getOwnerUser().getId().equals(principal.getId()))
                        .orElse(false);
    }

    private UserPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}

