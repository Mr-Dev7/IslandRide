package com.islandride.partner.api;

import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.security.UserPrincipal;
import com.islandride.partner.dto.PartnerFinancialDtos;
import com.islandride.partner.service.PartnerFinancialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/partners/financials")
@PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
@Tag(name = "Partner Financials", description = "Financial governance, commission statements, and settlement ledger for partners")
public class PartnerFinancialController {

    private final PartnerFinancialService partnerFinancialService;

    public PartnerFinancialController(PartnerFinancialService partnerFinancialService) {
        this.partnerFinancialService = partnerFinancialService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get financial overview, earnings, commission accruals, and settlements for a partner business")
    public ResponseEntity<PartnerFinancialDtos.PartnerFinancialOverviewDto> getFinancialOverview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long businessId) {
        Long targetBusinessId = resolveTargetBusinessId(principal, businessId);
        return ResponseEntity.ok(partnerFinancialService.getFinancialOverview(targetBusinessId));
    }

    @GetMapping("/commissions")
    @Operation(summary = "Get itemized commission records and deduction details for a partner business")
    public ResponseEntity<List<PartnerFinancialDtos.CommissionDetailDto>> getCommissions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long businessId) {
        Long targetBusinessId = resolveTargetBusinessId(principal, businessId);
        return ResponseEntity.ok(partnerFinancialService.getCommissions(targetBusinessId));
    }

    private Long resolveTargetBusinessId(UserPrincipal principal, Long requestedBusinessId) {
        if (requestedBusinessId != null) {
            return requestedBusinessId;
        }
        if (principal != null && principal.getBusinessId() != null) {
            return principal.getBusinessId();
        }
        throw new BusinessRuleException("Business ID must be specified or present in user session context.");
    }
}

