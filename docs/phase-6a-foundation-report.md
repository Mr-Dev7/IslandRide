# IslandRide — Phase 6 Stage 6A Foundation Report

**Date:** 2026-11-12  
**Branch:** `main` (building on Phase 5 acceptance commit `5ee7b01`)  
**Scope:** Stage 6A Backend Foundations & Database Migrations (Booking Lifecycle, Inventory Holds, Pricing/Policy/Cancellation Snapshots, Commission Separation, Operational Permissions, Payment Gateway SPI Scaffold)

---

## 1. Stage 6A Boundaries & Compliance

- **No Tourist Checkout API or UI:** Confirmed zero `@RestController` endpoints or Angular checkout components were added in Stage 6A (`booking`, `paymentfinance`, `pricing`, `availability`, `commission`, `core/idempotency`).
- **Live Gateway Calls Disabled:** `PaymentGatewayProperties.liveEnabled` defaults to `false` with `provider = "mock"`, empty Razorpay secrets, and `RazorpayGatewayAdapter` explicitly throws `BusinessRuleException` on `createOrder`, `fetchPaymentStatus`, and `initiateRefund` while `liveEnabled` is `false`.
- **No Phase 7+ Features:** Scope is restricted strictly to Stage 6A foundational schema, domain models, transactional services, security helpers, gateway SPI, and verification tests.

---

## 2. Database Migrations (`V11__phase6_booking_holds_and_payments.sql`)

- **Flyway Migration:** Added `backend/src/main/resources/db/migration/V11__phase6_booking_holds_and_payments.sql`.
  - **Extended Existing Tables:** `partner_policies`, `pricing_rules`, `partner_staff`, `inventory_holds`, `bookings`, `payments`, `refunds`, `commissions`.
  - **Added New Tables:** `partner_cancellation_tiers`, `partner_operating_hours`, `security_deposit_records`, `overbooking_exceptions`, `partner_receivables`, `payment_webhook_events`, `idempotency_records`.
  - **Provisional Config Seeds:** Seeded provisional Phase 6 defaults into `system_settings`.
- **Migration & Schema Verification:**
  - Verified **V10 → V11 incremental upgrade** on local MySQL 8.4 (`islandride_db`, `59` total tables including `flyway_schema_history`, migrations `V1`–`V11` all `success = 1`).
  - Verified **clean V1 → V11 fresh install** on temporary empty MySQL 8.4 schema `islandride_clean_verify` (`00:22.268s` Flyway apply + Hibernate ORM `7.4.13.Final` `ddl-auto: validate` + `DatabaseSchemaIntegrationTest`: `5` tests, `0` failures; temporary schema dropped after check).

---

## 3. Core Components Implemented

### 3.1 Lifecycle State Machines & Audit Trail
- `BookingStateMachine`, `BookingActorRole`, and `BookingLifecycleFoundationService` (`com.islandride.booking.service`): enforces actor permissions, pay-after-approval flow (`AWAITING_PARTNER_APPROVAL → APPROVED → CONFIRMED → ACTIVE → COMPLETED`), approval and payment expiry deadlines, terminal immutability, `BookingStatusHistory` persistence, and `AuditLog` entries.
- `PaymentStateMachine` and `RefundStateMachine` (`com.islandride.paymentfinance.service`): enforces valid payment and refund transitions (including support escalation vs financial approval).
- Extended domain enums and entities: `BookingStatus`, `Booking`, `SecurityDepositRecord`, `OverbookingExceptionRecord`, `Payment`, `Refund`, `RefundType`, `WebhookProcessingStatus`, `PaymentWebhookEvent`, `PartnerReceivable`, `ReceivableStatus`, `Commission`.

### 3.2 Policy, Operating Hours, Authoritative Pricing, Cancellation & Commission
- `BookingPolicyService` and `EffectiveBookingPolicy` (`com.islandride.pricing.service`): merges `PartnerPolicy` and `system_settings`, computing V8 approval deadlines, post-approval payment windows, and full payment cutoffs.
- `OperatingHoursService` and `OperatingHoursValidationResult`: enforces island isolation (rejects cross-island dropoffs), day-of-week schedule, hub-specific overrides (`LOC_<locationId>`), and `Asia/Kolkata` local open/close hours.
- `AuthoritativePricingService` and `PricingSnapshot`: computes seasonal prices, `PricingRule` stacking/exclusivity/priority, GST, advance vs full payment split (`PARTIAL_ADVANCE` / `FULL`), excluding security deposits from rental total, GST, and commission.
- `CancellationSnapshotService`, `CancellationPolicySnapshot`, and `CancellationSettlementBreakdown` (`com.islandride.booking.service`): snapshots tiered partner cancellation rules at booking time, preserves the V5 `10.00%` fallback when no custom tiers exist, protects `100%` of security deposits from cancellation/no-show fees, supports weather/ferry disruption rescheduling/full-refund handling, and gates no-show penalties behind grace window and evidence requirements.
- `CommissionCalculationService` (`com.islandride.commission.service`): calculates commission on taxable rental subtotal only (excluding GST and security deposit) and creates an open `PartnerReceivable` record when online collection is below platform commission.

### 3.3 Concurrency Holds, Turnaround Buffers & Idempotency
- `InventoryHoldService` and `HoldExpirationScheduler` (`com.islandride.availability.service`): combines Redis `SET NX` advisory locking (Lua unlock with safe fallback), MySQL `VehicleRepository.findByPublicIdForUpdate` pessimistic row lock, booking/hold/maintenance overlap checks with `turnaround_buffer_minutes`, `OperatingHoursService` validation, atomic hold conversion/release, scheduled stale hold expiration, and idempotency replay/conflict detection via `IdempotencyService`.
- Updated `AvailabilityService` and `PublicCatalogService` to apply consistent `turnaround_buffer_minutes` and `BookingStatus.NON_BLOCKING_STATUSES` filtering across public catalog search and vehicle availability checks.

### 3.4 Operational Permissions & Gateway SPI Scaffold
- Updated `PartnerStaff` (`canRecordHandover`, `canCollectOfflinePayments`, `canMarkNoShow` defaulted `false`) and `TenantSecurityService` (`canApproveOrRejectBookings`, `canRecordHandover`, `canCollectOfflinePayments`, `canMarkNoShow`, `canEscalateRefund`, `canApproveFinancialRefunds` plus `require*` guards).
- Added gateway SPI contracts and implementations under `com.islandride.paymentfinance.gateway`: `PaymentGatewayProperties`, `PaymentGatewayAdapter`, `PaymentGatewayRegistry`, `RazorpaySignatureVerifier`, `RazorpayGatewayAdapter` (live calls blocked in Stage 6A), and `MockGatewayAdapter` (`@Profile({"local", "test"})`).

---

## 4. Configurable Provisional Defaults

The following provisional defaults are active via `V11`, `application.yml`, and `BookingPolicyService` until overridden in Admin `system_settings` or Partner policy configuration:

| Setting / Policy | Provisional Default |
|---|---|
| Cancellation fallback percentage (when no custom tiers configured) | `10.00%` (V5 seed preserved) |
| Advance payment percentage (`PARTIAL_ADVANCE`) | `25.00%` |
| Platform commission percentage | `4.00%` (excl. GST & deposit) |
| Platform GST percentage | `5.00%` |
| Default turnaround buffer | `60` minutes |
| Full payment cutoff before pickup | `12` hours |
| Inventory hold duration | `600` seconds (`10` minutes) |
| Minimum booking lead time | `30` minutes |
| Approval deadline (`> 24h` / `6h–24h` / `< 6h` before pickup) | `6h` / `2h` / `45m` |
| Post-approval payment window (`>= 6h` / `< 6h` before pickup) | `2h` / `30m` |
| No-show grace window & default penalty | `120` minutes / `100.00%` rental (deposit `100%` protected) |
| Default deposit collection method | `AT_PICKUP` |
| Gateway fee treatment | `PLATFORM_ABSORBED` |
| Operating hours default (`Asia/Kolkata`) | `07:00` – `21:00` |

---

## 5. Verification Audit

### 5.1 Backend Full Suite (`backend\mvnw.cmd -f backend\pom.xml clean verify`)
**Result:** `BUILD SUCCESS` in `01:11 min` — **`Tests run: 52, Failures: 0, Errors: 0, Skipped: 0`**

| Test Suite | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `ModuleBoundaryArchitectureTest` | 3 | 0 | 0 | 0 |
| `AuthenticationIntegrationTest` | 8 | 0 | 0 | 0 |
| `AuthorizationAndTenantIsolationTest` | 6 | 0 | 0 | 0 |
| `CsrfContractIntegrationTest` | 2 | 0 | 0 | 0 |
| `ProductionHostCookieSpecificationTest` | 1 | 0 | 0 | 0 |
| `BookingAndPaymentStateMachineTest` | 3 | 0 | 0 | 0 |
| `Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest` | 6 | 0 | 0 | 0 |
| `HealthApiControllerTest` | 1 | 0 | 0 | 0 |
| `DatabaseSchemaIntegrationTest` | 5 | 0 | 0 | 0 |
| `AdminVehicleModerationAndSecurityIntegrationTest` | 2 | 0 | 0 | 0 |
| `Phase5AcceptanceAuditIntegrationTest` | 4 | 0 | 0 | 0 |
| `VehicleCatalogAndInventoryIntegrationTest` | 2 | 0 | 0 | 0 |
| `VehicleMediaAndAvailabilityIntegrationTest` | 3 | 0 | 0 | 0 |
| `PaymentGatewayFoundationTest` | 2 | 0 | 0 | 0 |
| `Stage6APricingCancellationAndPolicyIntegrationTest` | 4 | 0 | 0 | 0 |

### 5.2 Clean Database Fresh Migration Check
- Temporary empty MySQL 8.4 schema `islandride_clean_verify`: Flyway applied all `11` migrations (`V1`–`V11`), Hibernate `ddl-auto: validate` succeeded, `DatabaseSchemaIntegrationTest` passed (`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` in `40.961 s`), and the temporary database was dropped immediately afterward.

### 5.3 Frontend Regression Verification
- `npm --prefix frontend run test:ci`: `TOTAL: 21 SUCCESS` (`Chrome Headless 157.0.0.0`).
- `npm --prefix frontend run build`: `Application bundle generation complete` (`4.951 s`, exit code `0`).

### 5.4 Phase 5 Test Alignment Note
- Updated `Phase5AcceptanceAuditIntegrationTest.availability_confirmedBooking_boundaryWindows_andDraftHiding` (lines 151–167): previously expected an adjacent `[day4, day6]` catalog query after a booking ending at `day4` to be free (`1`); aligned expectations with the Phase 6 approved `60`-minute turnaround buffer rule (`[day4, day6]` blocked → `0`; `[day4 + 60m, day6]` free → `1`).

---

## 6. Next Stage

Stage 6A foundations are complete and verified. **Stage 6B** (tourist checkout API/UI, partner booking approval & handover workflows, webhook/payment order orchestration, and admin/finance settlement endpoints) awaits user review and approval before implementation.
