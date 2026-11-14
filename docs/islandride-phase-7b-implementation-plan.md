# IslandRide — Phase 7B: Razorpay Checkout Integration Implementation Plan

**Date:** 2026-11-14  
**Status:** PROPOSED (Awaiting User Review & Explicit Authorization)  
**Author:** Antigravity Autonomous Architecture & Security Review  
**Git Baseline Commit:** `6361d64` (`test(inventory): isolate public catalog integration fixtures`)  
**Target Milestone:** Phase 7B — Production Razorpay Checkout Integration  
**Deliverable Document:** `docs/islandride-phase-7b-implementation-plan.md`  

---

## 1. Executive Summary & Objective

In **Phase 7A**, the platform infrastructure and runtime configuration were hardened for production readiness:
- Production Redis configuration bound and validated with SSL support.
- Razorpay gateway adapter configuration validated with live-mode safety guards.
- Frontend Docker build-context corrected with a clean root `.dockerignore`.
- Angular production build configured with production environment replacements.
- All 134 backend integration and domain tests passing with 100% success.

The primary objective of **Phase 7B** is to integrate **real Razorpay Checkout (`checkout.js`)** into the Angular frontend and connect it end-to-end with the existing Spring Boot backend. 

Currently, vehicle reservations complete payment through a **frontend mock simulator modal** that generates local signatures and fires simulated callbacks. Phase 7B replaces this modal with the official Razorpay Checkout popup in production and staging environments, while **seamlessly preserving the mock simulation flow** for local development and offline CI testing.

### Key Goals
1. **Dynamic Razorpay SDK Loading:** Load Razorpay's standard `checkout.js` on demand via an Angular singleton service without bloated npm dependencies or page-load penalties.
2. **Dual-Mode Gateway Support:** Automatically launch the native Razorpay modal when `gatewayProvider === 'razorpay'`, while retaining the developer mock simulator when `gatewayProvider === 'mock'`.
3. **End-to-End Cryptographic Integrity:** Pass public `keyId` securely from backend order generation to frontend checkout, capture payment in Razorpay's PCI-DSS compliant iframe, verify HMAC-SHA256 signatures on the backend, and update booking state atomically.
4. **Resilient Failure & Webhook Reconciliation:** Gracefully handle user dismissals, network drops, bank declines, lapsed inventory holds, concurrent duplicate verifications, and asynchronous webhook delivery.

---

## 2. Current Implementation Findings & Architectural Audit

Before drafting the implementation plan, an exhaustive inspection of all payment, booking, configuration, and infrastructure files was conducted.

| Area | Target Files Inspected | Current Status & Capabilities | Gaps / Deficiencies Identified |
| :--- | :--- | :--- | :--- |
| **Frontend Checkout Component** | `frontend/src/app/features/tourist/checkout/tourist-checkout.component.ts` | Displays inventory hold countdown, validates driving license, allows payment mode selection (FULL vs PARTIAL_ADVANCE), initiates order via `createBooking(...)`. | Renders an inline modal (`@if (activeCheckoutOrder(); as order)`) simulating success, failure, and webhook capture. No Razorpay SDK integration or options builder. |
| **Frontend Booking Detail** | `frontend/src/app/features/tourist/bookings/tourist-booking-detail.component.ts` | Allows tourist to manage booking, view quotes, and pay for approved or pending bookings via `openPaymentModal(...)`. | Renders an identical mock payment simulation modal (`completeMockPayment`). No Razorpay SDK integration. |
| **Frontend Booking Service & Models** | `frontend/src/app/core/services/booking.service.ts`<br>`frontend/src/app/core/models/booking.models.ts` | Clean REST API bindings for `createBooking`, `initiateCheckoutOrder`, `verifyPayment`, `simulateMockPayment`, `sendMockWebhook`. | `GatewayCheckoutOrderResponse` lacks `keyId` (Razorpay Key ID) and customer prefill details. No TypeScript types for Razorpay `window.Razorpay` or options. |
| **Backend Booking Controller** | `backend/src/main/java/com/islandride/booking/api/TouristBookingController.java` | Maps `POST /api/v1/tourist/bookings/{id}/payment-order` and `POST /api/v1/tourist/payments/verify` with role guards and idempotency support. | Controller delegates directly to `PaymentOrchestrationService`; ready for production. |
| **Backend Payment Orchestrator** | `backend/src/main/java/com/islandride/paymentfinance/service/PaymentOrchestrationService.java` | Canonical write lock ordering (`Vehicle -> Booking -> Payment`), atomic capture, overbooking detection, stale order reuse, reconciliation scheduler. | `toCheckoutOrderResponse(...)` does not include `keyId` in the response payload. |
| **Backend Razorpay Adapter** | `backend/src/main/java/com/islandride/paymentfinance/gateway/RazorpayGatewayAdapter.java` | Fully implemented using Spring 6 `RestClient`. Calls `/orders`, `/payments/{id}`, `/refunds`. Gated by `liveEnabled`. | Fully operational. Verified in Phase 7A Step 2 with unit tests. |
| **Backend Signature Verifier** | `backend/src/main/java/com/islandride/paymentfinance/gateway/RazorpaySignatureVerifier.java` | RFC 2104 HMAC-SHA256 signature verification with constant-time `MessageDigest.isEqual`. | Fully operational. |
| **Backend Webhook Receiver** | `backend/src/main/java/com/islandride/paymentfinance/api/PaymentWebhookController.java`<br>`WebhookProcessingService.java` | Reads verbatim raw bytes before Jackson parse for HMAC integrity. Idempotent event deduplication in DB. | Fully operational for `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`. |
| **Nginx & Docker Proxy** | `infrastructure/nginx/default.conf` | Reverse proxies `/api/` to `backend:8080/api/`. Security headers include `X-Frame-Options: SAMEORIGIN`. | No blocking CSP headers present. Inbound webhooks correctly routed. |

---

## 3. Existing Capabilities That Can Be Reused

The platform already possesses a robust, enterprise-grade payment and finance foundation:
1. **Server-Side Razorpay REST API Integration:** `RazorpayGatewayAdapter.createOrder(...)` creates authentic orders on Razorpay's API with exact amount in paise, currency, receipt reference, and booking metadata.
2. **Pessimistic Locking & Deadlock Prevention:** `PaymentOrchestrationService` guarantees canonical locking order `Vehicle -> Booking -> Payment`, eliminating race conditions between client verification and asynchronous webhooks.
3. **RFC 2104 Cryptographic Signature Verification:** `RazorpaySignatureVerifier` verifies order signatures (`order_id + "|" + payment_id`) and webhook signatures using constant-time digest comparison to prevent timing attacks.
4. **Idempotency Safeguards:** `Idempotency-Key` headers on order initiation and payment verification prevent duplicate charges. If verification is submitted twice, the second call returns the existing payment record without side effects.
5. **Overbooking Detection & Auto-Refund:** If an inventory hold expires during payment and another tourist reserves the vehicle, `OverbookingExceptionRecord` is created, and an automatic refund is triggered.
6. **Inbound Webhook Pipeline:** `WebhookProcessingService` processes webhooks idempotently using the `PaymentWebhookEvent` table with unique constraint on `(gateway_provider, gateway_event_id)`.
7. **Mock Simulation Pipeline:** `MockGatewayAdapter` and simulation endpoints allow developers and automated tests to run offline without internet or Razorpay API keys.

---

## 4. Missing Functionality to Implement

To achieve seamless, secure production payment checkout, the following components must be added:

1. **Backend `keyId` Inclusion in Checkout DTO:**
   - Expose the public `keyId` in `PaymentDtos.GatewayCheckoutOrderResponse`.
   - Update `PaymentOrchestrationService.toCheckoutOrderResponse` to populate `keyId` (empty for `mock`, and `properties.getRazorpay().getKeyId()` for `razorpay`).
   - *Security Note:* Only the public Key ID (e.g. `rzp_test_...` or `rzp_live_...`) is exposed. The Key Secret and Webhook Secret remain strictly server-side.
2. **Frontend Razorpay TypeScript Type Definitions:**
   - Define type-safe interfaces (`RazorpayOptions`, `RazorpayPaymentSuccessResponse`, `RazorpayErrorResponse`, `RazorpayInstance`) to avoid `any` casting and prevent runtime integration bugs.
3. **Frontend Dynamic SDK Loader Service (`RazorpayLoaderService`):**
   - An Angular singleton service that injects `https://checkout.razorpay.com/v1/checkout.js` asynchronously when required.
   - Reuses existing script tag if already loaded, returns a cached Promise, and handles network or script load failures with clear user messaging.
4. **Dual-Mode UI Checkout Orchestration:**
   - In `TouristCheckoutComponent`:
     - If `order.gatewayProvider === 'razorpay'`: initialize and open the Razorpay popup.
     - If `order.gatewayProvider === 'mock'`: display the developer simulation modal.
   - Handle the Razorpay callback:
     - On success: send `VerifyPaymentRequest` with `{ bookingPublicId, gatewayOrderId, gatewayPaymentId, gatewaySignature }` and `Idempotency-Key: ver_{orderId}`.
     - On dismiss / user cancellation: reset loading state, preserve booking hold, and show non-intrusive notification: *"Payment cancelled. You can retry before your reservation hold expires."*
     - On payment failure: display failure reason and allow immediate retry.
5. **Pending Booking Payment in `TouristBookingDetailComponent`:**
   - Update `openPaymentModal(...)` in `tourist-booking-detail.component.ts` to use `RazorpayLoaderService` when `gatewayProvider === 'razorpay'`, matching the checkout flow.

---

## 5. End-to-End Payment Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Tourist as Tourist Browser
    participant CheckoutComp as TouristCheckoutComponent
    participant RzpLoader as RazorpayLoaderService
    participant RzpModal as Razorpay Checkout SDK (checkout.js)
    participant BackendAPI as TouristBookingController
    participant Orchestrator as PaymentOrchestrationService
    participant RzpAPI as Razorpay REST API
    participant WebhookCtrl as PaymentWebhookController

    Tourist->>CheckoutComp: Clicks "Proceed to Payment"
    CheckoutComp->>BackendAPI: POST /api/v1/tourist/bookings (Create Booking & Order)
    BackendAPI->>Orchestrator: createBookingFromHold() & initiateOrRetryCheckoutOrder()
    Orchestrator->>RzpAPI: POST /orders (amount in paise, receipt, notes)
    RzpAPI-->>Orchestrator: 200 OK (order_id: "order_xyz", status: "created")
    Orchestrator-->>BackendAPI: GatewayCheckoutOrderResponse (gatewayOrderId, keyId, amount, currency)
    BackendAPI-->>CheckoutComp: 201 Created (Booking + CheckoutOrder)

    alt gatewayProvider == "mock"
        CheckoutComp->>Tourist: Render Developer Mock Simulation Modal
    else gatewayProvider == "razorpay"
        CheckoutComp->>RzpLoader: loadRazorpaySdk()
        RzpLoader-->>CheckoutComp: SDK Loaded (window.Razorpay ready)
        CheckoutComp->>RzpModal: new Razorpay(options).open()
        RzpModal->>Tourist: Displays Secure Hosted Checkout Modal (UPI / Card / NetBanking)
        
        alt Tourist Closes Modal (Dismissal)
            Tourist->>RzpModal: Clicks "X" / Closes window
            RzpModal->>CheckoutComp: modal.ondismiss() callback
            CheckoutComp->>Tourist: Show "Payment cancelled, retry available"
        else Payment Fails (Bank Decline / Timeout)
            RzpModal->>CheckoutComp: payment.failed event callback
            CheckoutComp->>Tourist: Display failure message & allow retry
        else Payment Succeeded
            Tourist->>RzpModal: Authenticates via OTP / UPI PIN
            RzpModal->>RzpAPI: Process & Capture Payment
            RzpAPI-->>RzpModal: Payment Captured
            
            par Direct Client Verification
                RzpModal->>CheckoutComp: handler(response: order_id, payment_id, signature)
                CheckoutComp->>BackendAPI: POST /api/v1/tourist/payments/verify (with Idempotency-Key: ver_order_xyz)
                BackendAPI->>Orchestrator: verifyClientPayment()
                Orchestrator->>Orchestrator: RazorpaySignatureVerifier.verifyCheckoutSignature()
                Orchestrator->>Orchestrator: Lock Vehicle -> Booking -> Payment; Capture & Confirm
                Orchestrator-->>BackendAPI: PaymentVerificationResponse(status: CONFIRMED)
                BackendAPI-->>CheckoutComp: 200 OK (Booking Confirmed)
                CheckoutComp->>Tourist: Navigate to /tourist/bookings/{publicId} (Confirmed!)
            and Asynchronous Webhook
                RzpAPI->>WebhookCtrl: POST /api/v1/payments/webhooks/razorpay (payment.captured)
                WebhookCtrl->>Orchestrator: processRawWebhook() -> capturePaymentLocked()
                Orchestrator->>Orchestrator: Verify Webhook HMAC & Idempotent Update
            end
        end
    end
```

---

## 6. Step-by-Step Implementation Sequence

The implementation is broken down into structured, isolated, verifiable steps:

```
┌────────────────────────────────────────────────────────┐
│ Phase 7B Step 1: Backend DTO & Service Update           │
│ - Add keyId to GatewayCheckoutOrderResponse record     │
│ - Populate keyId in PaymentOrchestrationService        │
│ - Update frontend GatewayCheckoutOrderResponse type    │
│ - Run backend test suite to guarantee 0 regressions    │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Phase 7B Step 2: Frontend Razorpay Types & Loader      │
│ - Create core/models/razorpay.models.ts                │
│ - Create core/services/razorpay-loader.service.ts      │
│ - Unit test RazorpayLoaderService (mock window.Razorpay)│
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Phase 7B Step 3: Tourist Checkout Razorpay Integration  │
│ - Integrate Razorpay in TouristCheckoutComponent       │
│ - Wire options, prefill, handler, dismiss, fail        │
│ - Preserve mock simulation modal for provider='mock'   │
│ - Update component unit tests                          │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Phase 7B Step 4: Tourist Booking Detail Integration    │
│ - Integrate Razorpay in TouristBookingDetailComponent  │
│ - Wire pay online modal to use RazorpayLoaderService   │
│ - Preserve mock simulation for provider='mock'         │
│ - Update component unit tests                          │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Phase 7B Step 5: Full Build, Verification & Testing    │
│ - Run full backend test suite (134+ tests)             │
│ - Run frontend test suite (test:ci)                    │
│ - Run Angular production build (npm run build)         │
│ - Verify zero bundle budget violations                 │
│ - Verify dual-mode behavior (mock vs razorpay)         │
└────────────────────────────────────────────────────────┘
```

---

## 7. Exact Files Expected to Change

### Backend Files
1. `backend/src/main/java/com/islandride/paymentfinance/dto/PaymentDtos.java`
   - Add `String keyId` to `GatewayCheckoutOrderResponse` record.
2. `backend/src/main/java/com/islandride/paymentfinance/service/PaymentOrchestrationService.java`
   - In `toCheckoutOrderResponse(Payment p, Booking b, boolean liveEnabled)`: resolve public `keyId` from `PaymentGatewayProperties` when `provider == "razorpay"`, or `null` when `provider == "mock"`.
3. `backend/src/test/java/com/islandride/paymentfinance/gateway/PaymentGatewayConfigurationBindingTest.java` (and related test fixtures)
   - Verify constructor compatibility and assert `keyId` is properly mapped.

### Frontend Files
1. `frontend/src/app/core/models/booking.models.ts`
   - Add `keyId?: string | null;` and `amountInPaise?: number;` to `GatewayCheckoutOrderResponse`.
2. `frontend/src/app/core/models/razorpay.models.ts` *(NEW FILE)*
   - Declare interfaces for `RazorpayOptions`, `RazorpayPrefill`, `RazorpayTheme`, `RazorpayPaymentSuccessResponse`, `RazorpayErrorResponse`, `RazorpayInstance`.
3. `frontend/src/app/core/services/razorpay-loader.service.ts` *(NEW FILE)*
   - Dynamic script injection service for `https://checkout.razorpay.com/v1/checkout.js`.
4. `frontend/src/app/core/services/razorpay-loader.service.spec.ts` *(NEW FILE)*
   - Unit tests validating SDK loading, caching, and error handling.
5. `frontend/src/app/features/tourist/checkout/tourist-checkout.component.ts`
   - Inject `RazorpayLoaderService`, `AuthService`.
   - Update `proceedToPayment()` to conditionally invoke `launchRazorpay(order)` if `order.gatewayProvider === 'razorpay'`.
   - Retain mock simulation modal if `order.gatewayProvider === 'mock'`.
   - Implement `handler`, `modal.ondismiss`, `payment.failed`.
6. `frontend/src/app/features/tourist/checkout/tourist-checkout.component.spec.ts`
   - Unit tests covering both Razorpay launch and mock simulation paths.
7. `frontend/src/app/features/tourist/bookings/tourist-booking-detail.component.ts`
   - Update `openPaymentModal()` to invoke `launchRazorpay(order)` when `gatewayProvider === 'razorpay'`.
8. `frontend/src/app/features/tourist/bookings/tourist-booking-detail.component.spec.ts`
   - Unit tests for booking detail payment initiation.

---

## 8. Required Dependencies

- **Backend:** **Zero new dependencies.** Spring 6 `RestClient`, Jackson, standard Java `javax.crypto.Mac` are already present and fully configured.
- **Frontend:** **Zero new npm packages.** Razorpay's official architectural standard requires loading `checkout.js` directly from Razorpay's CDN to maintain PCI-DSS Level 1 compliance and access the latest payment instruments without requiring Angular application rebuilds or redeployments.

---

## 9. Security, Isolation & Failure Handling

### A. PCI-DSS Compliance & Card Data Isolation
- **Zero Card Handling:** Neither the Angular frontend code nor the Spring Boot backend ever encounters, parses, or logs primary account numbers (PAN), expiration dates, or CVVs. All sensitive payment data is entered inside Razorpay's hosted iframe.
- **Client Tokenization:** Razorpay captures payment and returns only an opaque `razorpay_payment_id` and a cryptographic HMAC-SHA256 signature.

### B. Secret Isolation & Zero Leakage
- **Secret Confinement:** `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` are never bundled in frontend code, never rendered in HTML, and never passed in API responses.
- **Key ID Exposure:** Only the public identifier `keyId` (`rzp_test_...` or `rzp_live_...`) is sent to the browser, exactly as required by the Razorpay Checkout SDK.

### C. Cryptographic Signature Verification
- **Checkout Signature Formula:**
  $$\text{HMAC-SHA256}(\text{order\_id} \parallel "|" \parallel \text{payment\_id}, \text{key\_secret})$$
- **Webhook Signature Formula:**
  $$\text{HMAC-SHA256}(\text{raw\_request\_body\_bytes}, \text{webhook\_secret})$$
- **Constant-Time Verification:** Handled via `MessageDigest.isEqual(...)` in `RazorpaySignatureVerifier.java`, preventing timing attack vulnerabilities.

### D. User Dismissal & Modal Cancellation
- If the tourist cancels the modal (e.g. clicks the "X" button), the `modal.ondismiss` callback triggers.
- The UI resets `submitting.set(false)`, leaves the reservation hold active, and displays an informational message: *"Payment was not completed. You can retry before your reservation hold expires in MM:SS."*
- The vehicle hold lock remains active until the expiration countdown completes, allowing the tourist to retry immediately.

### E. Network Drop / Tab Closed After Capture
- If the tourist's browser drops connection or closes after entering their UPI PIN on the bank page:
  1. Razorpay asynchronously emits a `payment.captured` webhook to `https://<domain>/api/v1/payments/webhooks/razorpay`.
  2. `WebhookProcessingService` verifies the HMAC signature and captures the booking.
  3. `PaymentReconciliationScheduler` runs every 5 minutes as an additional safety net, querying Razorpay's REST API for unconfirmed `INITIATED` orders.
  4. When the tourist reopens IslandRide and visits "My Reservations", the booking status is already `CONFIRMED`.

---

## 10. Idempotency & Duplicate Payment Handling

1. **Order Initiation Idempotency:**
   - Order creation requests carry an `Idempotency-Key` header (`pay_{bookingPublicId}_{uuid}`).
   - If an order has already been initiated for the same booking and online due amount, and has not expired, `PaymentOrchestrationService` reuses the active `Payment(INITIATED)` record to avoid orphaned duplicate Razorpay orders.
2. **Payment Verification Idempotency:**
   - Client verification sends `Idempotency-Key: ver_{gatewayOrderId}`.
   - `capturePaymentLocked` verifies if `payment.getStatus() == PaymentStatus.CAPTURED`. If already captured, it immediately returns the confirmed response without duplicating payments, ledger entries, or notifications.
3. **Webhook Deduplication:**
   - `PaymentWebhookEvent` table enforces a database-level unique constraint on `(gateway_provider, gateway_event_id)`.
   - Repeated webhook deliveries from Razorpay are acknowledged with HTTP 200 and logged as duplicate events without re-executing booking transitions.

---

## 11. Webhook & Reconciliation Strategy

### Razorpay Webhook Configuration
The following webhook events must be enabled in the Razorpay Dashboard:
- `payment.captured`: Triggers instant booking confirmation if client verification was interrupted.
- `order.paid`: Additional confirmation event for order completion.
- `payment.failed`: Records payment attempt failure and notifies tourist if within deadline.
- `refund.processed`: Confirms automated customer refund completion.
- `refund.failed`: Alerts operations admin to escalated refund failures.

### Reconciliation Pipeline
- `PaymentReconciliationScheduler` runs on a scheduled cadence (every 5 minutes).
- Checks all payments in `INITIATED` status created more than `staleMinutes` (default: 5 minutes) ago.
- If Razorpay reports `captured`, the scheduler executes `capturePaymentLocked` to confirm the booking.
- If Razorpay reports `failed` or order has expired, the payment is marked `FAILED`.

---

## 12. Local Testing Strategy Using Razorpay Test Mode

To test real Razorpay Checkout locally:
1. Obtain Razorpay Test Keys (`rzp_test_...` and Test Secret) from Razorpay Dashboard (free, instant test sandbox).
2. In `.env`:
   ```bash
   ISLANDRIDE_PAYMENT_PROVIDER=razorpay
   ISLANDRIDE_PAYMENT_LIVE_ENABLED=true
   RAZORPAY_KEY_ID=rzp_test_51...
   RAZORPAY_KEY_SECRET=your_test_secret
   RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
   ```
3. Start local backend and frontend (`npm start`).
4. Select a vehicle, acquire a hold, and click "Proceed to Payment".
5. The real Razorpay modal opens.
6. Use Razorpay test credentials:
   - **Card:** `4111 1111 1111 1111`, any future expiry, any 3-digit CVV, OTP `123456`.
   - **UPI:** `success@razorpay`.
   - **NetBanking:** Select any test bank (e.g. SBI / HDFC) and click "Success".
7. Confirm that `TouristBookingController.verifyPayment` executes, signature verifies, and booking status transitions to `CONFIRMED`.
8. To test mock mode: set `ISLANDRIDE_PAYMENT_PROVIDER=mock`, restart backend, and verify the mock simulation modal opens instead.

---

## 13. Production Environment Variables & Deployment Prerequisites

### Environment Variables
| Variable | Production Value / Description | Required? |
| :--- | :--- | :--- |
| `ISLANDRIDE_PAYMENT_PROVIDER` | `razorpay` | Yes |
| `ISLANDRIDE_PAYMENT_LIVE_ENABLED` | `true` | Yes |
| `ISLANDRIDE_PAYMENT_ORDER_EXPIRY_MINUTES` | `15` | Yes |
| `ISLANDRIDE_PAYMENT_RAZORPAY_KEY_ID` | `rzp_live_...` (Production Key ID) | Yes |
| `ISLANDRIDE_PAYMENT_RAZORPAY_KEY_SECRET` | Production Secret (from Vault/K8s Secret) | Yes |
| `ISLANDRIDE_PAYMENT_RAZORPAY_WEBHOOK_SECRET` | Secret configured in Razorpay Webhook settings | Yes |

### Content Security Policy (CSP) Requirements
If CSP headers are enabled on Nginx or reverse proxy:
```nginx
script-src 'self' 'unsafe-inline' https://checkout.razorpay.com;
frame-src 'self' https://api.razorpay.com;
connect-src 'self' https://lumberjack.razorpay.com https://api.razorpay.com;
img-src 'self' data: https://*.razorpay.com;
```

---

## 14. Rollback Strategy

1. **Immediate Zero-Downtime Feature Rollback:**
   - If Razorpay experiences an outage or credentials need urgent revocation, update environment variable:
     `ISLANDRIDE_PAYMENT_PROVIDER=mock`
   - Restart or reload backend without modifying code. The frontend dynamically reads `gatewayProvider` from the order response and falls back to mock simulation mode.
2. **Git Rollback:**
   - Each step will be committed in isolated, single-responsibility Git commits.
   - Reverting to Git commit `6361d64` restores the pre-Phase 7B state cleanly.

---

## 15. Comprehensive Acceptance Criteria

### Functional Acceptance Criteria
- [ ] **AC-01 (Backend DTO):** `GatewayCheckoutOrderResponse` contains the public `keyId` when `provider == "razorpay"`, and `null` or empty when `provider == "mock"`.
- [ ] **AC-02 (Secret Isolation):** `keySecret` and `webhookSecret` are strictly excluded from all client-facing responses and logs.
- [ ] **AC-03 (Dynamic SDK Loading):** `RazorpayLoaderService` injects `checkout.js` on demand, resolves only when `window.Razorpay` is available, and caches the script to avoid duplicate network requests.
- [ ] **AC-04 (Dual-Mode Execution):**
  - When `gatewayProvider === 'razorpay'`, the native Razorpay Checkout popup opens.
  - When `gatewayProvider === 'mock'`, the existing developer simulation modal opens.
- [ ] **AC-05 (Successful Verification):** Valid checkout responses from Razorpay submit `VerifyPaymentRequest` to `/api/v1/tourist/payments/verify`, signature verification succeeds, booking status transitions to `CONFIRMED`, and tourist is redirected to the booking confirmation screen.
- [ ] **AC-06 (User Dismissal Handling):** Closing the Razorpay popup without payment triggers `modal.ondismiss`, resets loading state, displays a retry notice, and preserves the active hold lock.
- [ ] **AC-07 (Payment Failure Handling):** Failed payment attempts in the modal or webhook update attempt history and permit retry before the hold deadline.
- [ ] **AC-08 (Booking Detail Payment):** "Pay Online Now" button in `TouristBookingDetailComponent` launches Razorpay Checkout for approved/pending bookings.

### Technical & Quality Acceptance Criteria
- [ ] **AC-09 (Full Backend Test Suite):** 100% of backend tests pass (`mvn test`), including all 134 existing integration tests and new signature/DTO assertions.
- [ ] **AC-10 (Frontend Test Suite):** All frontend unit tests pass (`npm run test:ci`).
- [ ] **AC-11 (Production Build & Budgets):** Angular production build succeeds (`npm run build`) with zero budget violations and zero localhost references.
- [ ] **AC-12 (No Destructive Git Changes):** All work conducted on clean branches with zero destructive git commands (`git reset`, `git clean`).

---

**End of Phase 7B Implementation Plan.**  
*Awaiting user review and authorization before executing any changes.*

