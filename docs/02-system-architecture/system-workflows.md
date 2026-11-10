# IslandRide — System Workflows & Sequence Diagrams
**Document Version:** 1.1.0  
**Phase:** 1 (Requirements & System Design — Post-Review Revisions)  
**Status:** Approved for Implementation  

---

## 1. Instant Booking & Online Payment Workflow

This workflow illustrates an instant-confirmation booking, including durable MySQL inventory holds, Redis ephemeral coordination, gateway order creation, and signed webhook verification.

```mermaid
sequenceDiagram
    autonumber
    actor Tourist as Tourist (Browser)
    participant BookingAPI as Booking Service
    participant AvailEngine as Availability Engine
    participant DB as MySQL Database
    participant Redis as Redis Cache
    participant Gateway as Payment Gateway
    participant WebhookAPI as Webhook Handler
    participant Notif as Notification Service

    Tourist->>BookingAPI: POST /api/v1/bookings/instant (vehicleId, start, end, paymentMode)
    BookingAPI->>AvailEngine: validateAndHoldInventory(vehicleId, start, end)
    
    critical Check Overlap & Create Durable Hold
        AvailEngine->>DB: SELECT id FROM vehicles WHERE id = :id FOR UPDATE
        AvailEngine->>DB: Verify no overlapping confirmed bookings, active holds, or maintenance
        AvailEngine->>DB: INSERT INTO inventory_holds (status='HELD', expires_at=NOW()+TTL)
        AvailEngine->>Redis: SET hold:vehicleId (TTL=configurable, e.g. 600s)
    end

    BookingAPI->>Gateway: Create Order (amount, currency='INR', receiptId)
    Gateway-->>BookingAPI: Return orderId, keyId
    BookingAPI->>DB: INSERT INTO bookings (status='PENDING_PAYMENT', hold_id, order_id)
    BookingAPI-->>Tourist: 201 Created (bookingRef, orderId, holdExpiresAt)

    Tourist->>Gateway: Open Payment Modal & Authorize Payment (Card/UPI/NetBanking)
    Gateway-->>Tourist: Payment Success Screen (Browser callback)

    Note over Gateway,WebhookAPI: Authoritative Webhook Triggered Asynchronously
    Gateway->>WebhookAPI: POST /api/v1/payments/webhooks/razorpay (signature, payload)
    WebhookAPI->>WebhookAPI: Verify HMAC-SHA256 Signature
    WebhookAPI->>DB: Check idempotency_key (prevent double-processing)
    
    critical Confirm Booking & Convert Hold
        WebhookAPI->>DB: UPDATE bookings SET status='CONFIRMED'
        WebhookAPI->>DB: UPDATE inventory_holds SET status='CONVERTED'
        WebhookAPI->>Redis: DEL hold:vehicleId
        WebhookAPI->>DB: INSERT INTO payments (status='CAPTURED', amount, gateway_ref)
    end

    WebhookAPI-->>Gateway: 200 OK
    WebhookAPI->>Notif: Publish BookingConfirmedEvent
    Notif-->>Tourist: Send Booking Voucher (Email / SMS)
```

---

## 2. Vehicle Handover (Pickup & Return) Workflow

This workflow captures structured digital proof of vehicle condition, fuel levels, odometer readings, and balance settlement via `handover_inspections` and `handover_evidence` records.

```mermaid
sequenceDiagram
    autonumber
    actor Tourist as Tourist
    actor Staff as Partner Staff
    participant HandoverAPI as Handover Service
    participant S3 as Private S3 Storage
    participant DB as MySQL Ledger

    Note over Tourist,Staff: Phase A: Pickup Handover
    Tourist->>Staff: Arrives at Pickup Station with Voucher & Driving License
    Staff->>HandoverAPI: GET /api/v1/partner/handover/{bookingId}
    Staff->>Staff: Inspects original physical Driving License
    Staff->>S3: Upload 4 vehicle condition photos (scratch scan)
    Staff->>HandoverAPI: POST /api/v1/partner/handover/pickup (odometer, fuel %, photoUrls)
    
    opt Booking has Pending Pickup Balance
        Staff->>Tourist: Collect remaining balance (Cash / UPI)
        Staff->>HandoverAPI: Record offline collection (amount, method, txRef)
        HandoverAPI->>DB: INSERT INTO payments (payment_mode='OFFLINE', status='CAPTURED')
    end

    critical Record Pickup Inspection
        HandoverAPI->>DB: INSERT INTO handover_inspections (type='PICKUP', odometer, fuel)
        HandoverAPI->>DB: INSERT INTO handover_evidence (inspection_id, photo_keys)
        HandoverAPI->>DB: UPDATE bookings SET status='ACTIVE'
    end
    HandoverAPI-->>Tourist: SMS / Email Handover Receipt with photos

    Note over Tourist,Staff: Phase B: Return Handover
    Tourist->>Staff: Returns Vehicle at Station
    Staff->>S3: Upload return inspection photos
    Staff->>HandoverAPI: POST /api/v1/partner/handover/return (endingOdometer, endingFuel)
    
    alt Damage Detected
        Staff->>HandoverAPI: Create Damage Report (photos, estimatedRepairCost)
        HandoverAPI->>DB: INSERT INTO handover_inspections (type='RETURN')
        HandoverAPI->>DB: Hold security deposit deduction & notify Tourist & Admin
        HandoverAPI->>DB: UPDATE bookings SET status='DISPUTED'
    else No Damage & Return Approved
        HandoverAPI->>DB: INSERT INTO handover_inspections (type='RETURN')
        HandoverAPI->>DB: UPDATE bookings SET status='COMPLETED'
        HandoverAPI->>DB: Schedule security deposit refund
    end
```

---

## 3. Asynchronous Refund & Gateway Reconciliation Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Actor as Tourist / Admin
    participant RefundService as Refund Service
    participant DB as MySQL Database
    participant Gateway as Payment Gateway
    participant ReconcileJob as Nightly Reconciliation Cron

    Actor->>RefundService: Request Cancellation / Refund
    RefundService->>DB: INSERT INTO refunds (status='PENDING', idempotency_key=UUID)
    
    RefundService->>Gateway: POST /v1/refunds (paymentId, amount, idempotencyKey)
    alt Gateway Responds Success Immediately
        Gateway-->>RefundService: 200 OK (gateway_refund_id, status='processed')
        RefundService->>DB: UPDATE refunds SET status='SUCCESS', gateway_refund_id=...
    else Gateway Timeout / Transient Network Error
        Gateway-->>RefundService: 504 Gateway Timeout / Network Fail
        RefundService->>DB: UPDATE refunds SET status='RETRY_SCHEDULED', retry_count=retry_count+1
        Note over RefundService: Exponential backoff worker retries with same idempotency key
    end

    Note over ReconcileJob,Gateway: Scheduled Reconciliation Worker
    ReconcileJob->>Gateway: GET /v1/refunds/reconciliation (batch_date)
    Gateway-->>ReconcileJob: Return all processed refund records
    ReconcileJob->>DB: UPDATE refunds SET status='RECONCILED', reconciled_at=NOW()
```
