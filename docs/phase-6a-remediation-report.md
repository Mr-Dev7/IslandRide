# IslandRide — Phase 6 Stage 6A Audit Remediation Report

**Base Commit Preserved:** `015b6f5`  
**Branch:** `main`  
**Database Migrations:** `V1` through `V12` (`V1`–`V11` unmodified; added `V12__phase6a_audit_remediation.sql`)  
**Stage 6B Scope:** Not started (live payment execution, checkout/webhook controllers, and frontend checkout flows remain unimplemented per boundaries)

---

## 1. High-Priority Remediations

1. **`BookingPolicyService` configuration & precedence** (`backend/src/main/java/com/islandride/pricing/service/BookingPolicyService.java`, `backend/src/main/java/com/islandride/pricing/domain/PartnerPolicy.java`, `backend/src/main/resources/db/migration/V12__phase6a_audit_remediation.sql`)
   - Aligned `BookingPolicyService` setting lookups with existing `V8` keys and seeded the remaining configurable policy defaults in `V12` via `INSERT IGNORE` (preserving existing customized values such as V5 `10.00`% cancellation fee seed and custom admin `system_settings` edits).
   - Made configurable override columns on `partner_policies` nullable (`DEFAULT NULL`) in `V12` so unset partner overrides inherit live platform `system_settings` updates without hardcoded production fallbacks, while missing required configuration fails fast.
   - Established `vehicle -> partner policy -> system settings` precedence (`resolveForVehicle` / `resolveEffectivePolicy`).

2. **Booking payment deadlines** (`backend/src/main/java/com/islandride/booking/service/BookingStateMachine.java`)
   - Enforced `paymentDeadlineAt` checks directly inside `BookingStateMachine` on `APPROVED | PENDING_PAYMENT | PAYMENT_FAILED -> PENDING_PAYMENT | CONFIRMED` so expired bookings cannot confirm or retry payment regardless of caller, while allowing system expiration (`EXPIRED`) and valid retries prior to the deadline.
   - Covered in `BookingAndPaymentStateMachineTest`.

3. **Redis advisory hold locking** (`backend/src/main/java/com/islandride/availability/service/InventoryHoldService.java`)
   - Prevented false `409 Conflict` contention for concurrent non-overlapping date ranges on the same vehicle; kept Redis locks advisory while relying on MySQL `PESSIMISTIC_WRITE` row locking as the authoritative concurrency control.
   - Verified with 50-thread overlapping hold contention (`1` win, `49` conflicts) and 8-thread simultaneous disjoint/buffered hold execution (`8` wins, `0` conflicts) in `Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest`.

4. **`TenantSecurityService` & partner staff operational flags / admin business validation** (`backend/src/main/java/com/islandride/core/security/TenantSecurityService.java`)
   - Updated permission evaluation to honor explicit `false` operational flags on `PartnerStaff` (including `MANAGER` accounts).
   - Tightened admin oversight business checks to reject deleted, missing, or non-`APPROVED` businesses when performing operational approvals.

5. **Turnaround buffer precedence & boundary separation** (`backend/src/main/java/com/islandride/inventory/domain/Vehicle.java`, `backend/src/main/java/com/islandride/inventory/service/PartnerVehicleService.java`, `backend/src/main/java/com/islandride/inventory/service/VehicleMapperSupport.java`, `backend/src/main/java/com/islandride/inventory/service/PublicCatalogService.java`, `backend/src/main/java/com/islandride/availability/service/AvailabilityService.java`, `backend/src/main/java/com/islandride/availability/service/InventoryHoldService.java`, `backend/src/main/java/com/islandride/booking/repository/BookingRepository.java`, `backend/src/main/java/com/islandride/availability/repository/InventoryHoldRepository.java`)
   - Made `vehicles.turnaround_buffer_minutes` nullable in `V12` (`DEFAULT NULL`) so vehicles inherit `PartnerPolicy -> system_settings` unless explicitly overridden on the vehicle.
   - Enforced forward and reverse turnaround buffers across catalog search (`PublicCatalogService`), availability checks and calendar slots (`AvailabilityService` exposing `TURNAROUND_BUFFER` slots), and hold creation (`InventoryHoldService`).
   - Preserved `AvailabilityService.createBlock` direct-overlap check (`findFirstConflictReason`) so partners can still schedule manual/maintenance blocks starting right at a confirmed booking's return boundary (`Phase5AcceptanceAuditIntegrationTest`).

6. **`OperatingHoursService` inactive location validation** (`backend/src/main/java/com/islandride/pricing/service/OperatingHoursService.java`)
   - Rejected inactive pickup/return locations during operating-hours checks; verified in `Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest`.

---

## 2. Medium & Low-Priority Remediations

- **Multi-business `PartnerStaff` support (`V12`, `PartnerStaff.java`, `PartnerStaffRepository.java`, `CustomUserDetailsService.java`, `AuthService.java`, `PartnerService.java`):** Replaced single-user uniqueness (`uk_partner_staff_user`) with `(business_id, user_id)` uniqueness (`uk_partner_staff_business_user`) while retaining standalone `idx_partner_staff_user` for `fk_partner_staff_user`.
- **`IdempotencyService` & scheduled cleanup (`IdempotencyService.java`, `IdempotencyCleanupScheduler.java`, `application-test.yml`, `pom.xml`):** Standardized actor keys on canonical `usr:` prefix, handled `DataIntegrityViolationException` reclaim for `FAILED` records, and added expired record cleanup (`IdempotencyCleanupScheduler`) with scheduling disabled in the `test` profile.
- **`AuthoritativePricingService` (`AuthoritativePricingService.java`):** Resolved seasonal pricing plans using rental `startTime`, aligned overnight (`<24h`) seasonal applicability, and standardized 2-decimal multiplier calculations.
- **`PaymentGatewayRegistry` (`PaymentGatewayRegistry.java`):** Enforced fail-fast validation on null, blank, or unknown payment gateway provider names (`PaymentGatewayFoundationTest`).
- **`CancellationSnapshotService` & `CommissionCalculationService` (`CancellationSnapshotService.java`, `CommissionCalculationService.java`):** Enforced `appliesToDeposit` separation invariant and validated commission rate / deposit isolation guards.

---

## 3. Database Migration & Hibernate Schema Verification

- **Clean `V1..V12` migration (`islandride_clean_verify`, MySQL `8.4.13`):** Executed 12 migrations from an empty schema (`migrationsExecuted=12`, `targetSchemaVersion=12`, `success=true` in `10.402 s`) followed by `DatabaseSchemaIntegrationTest` (`6/6` passed in `12.70 s`, `hibernate.ddl-auto=validate` passed).
- **`V11 -> V12` upgrade migration (`islandride_upgrade_verify`, MySQL `8.4.13`):** Populated pre-`V12` fixture rows and customized `hold.duration.seconds = '900'`, executed `V11 -> V12` upgrade (`migrationsExecuted=1`, `targetSchemaVersion=12`, `success=true` in `0.604 s`), confirmed zero data loss (`hold.duration.seconds = '900'` preserved via `INSERT IGNORE`), then restored `hold.duration.seconds = '600'` and ran `DatabaseSchemaIntegrationTest` (`6/6` passed in `13.16 s`, `hibernate.ddl-auto=validate` passed).
- **Cleanup:** Both temporary verification schemas (`islandride_clean_verify`, `islandride_upgrade_verify`) were dropped cleanly afterward; confirmed via `SHOW DATABASES`.

---

## 4. Verification Results

### Backend (`backend\mvnw.cmd -f backend\pom.xml clean verify` — post-final-edit full run)
- **Outcome:** `BUILD SUCCESS` in `02:04 min` — **63 tests run, 0 failures, 0 errors, 0 skipped**

| Test Suite | Tests | Failures | Errors | Skipped | Time (s) |
|---|---:|---:|---:|---:|---:|
| `com.islandride.architecture.ModuleBoundaryArchitectureTest` | 3 | 0 | 0 | 0 | 3.121 |
| `com.islandride.auth.AuthenticationIntegrationTest` | 8 | 0 | 0 | 0 | 22.070 |
| `com.islandride.auth.AuthorizationAndTenantIsolationTest` | 6 | 0 | 0 | 0 | 5.024 |
| `com.islandride.auth.CsrfContractIntegrationTest` | 2 | 0 | 0 | 0 | 0.699 |
| `com.islandride.auth.ProductionHostCookieSpecificationTest` | 1 | 0 | 0 | 0 | 3.423 |
| `com.islandride.booking.service.BookingAndPaymentStateMachineTest` | 3 | 0 | 0 | 0 | 0.015 |
| `com.islandride.booking.Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest` | 12 | 0 | 0 | 0 | 47.247 |
| `com.islandride.core.api.HealthApiControllerTest` | 1 | 0 | 0 | 0 | 3.126 |
| `com.islandride.database.DatabaseSchemaIntegrationTest` | 6 | 0 | 0 | 0 | 0.617 |
| `com.islandride.inventory.AdminVehicleModerationAndSecurityIntegrationTest` | 2 | 0 | 0 | 0 | 4.708 |
| `com.islandride.inventory.Phase5AcceptanceAuditIntegrationTest` | 4 | 0 | 0 | 0 | 4.968 |
| `com.islandride.inventory.VehicleCatalogAndInventoryIntegrationTest` | 2 | 0 | 0 | 0 | 3.268 |
| `com.islandride.inventory.VehicleMediaAndAvailabilityIntegrationTest` | 3 | 0 | 0 | 0 | 2.429 |
| `com.islandride.paymentfinance.gateway.PaymentGatewayFoundationTest` | 3 | 0 | 0 | 0 | 0.010 |
| `com.islandride.pricing.Stage6APricingCancellationAndPolicyIntegrationTest` | 7 | 0 | 0 | 0 | 4.727 |

### Frontend (`frontend/` — untouched during Stage 6A remediation)
- `npm --prefix frontend run build`: `Application bundle generation complete. [4.292 seconds]` (`dist\islandride-frontend`).
- `npm --prefix frontend run test:ci`: `Chrome Headless 157.0.0.0: Executed 21 of 21 SUCCESS (0.423 secs / 0.395 secs)`.
