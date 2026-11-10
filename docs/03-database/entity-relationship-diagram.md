# IslandRide — Entity-Relationship Diagram (ERD)
**Document Version:** 1.1.0  
**Phase:** 1 (Requirements & System Design — Post-Review Revisions)  
**Status:** Approved for Implementation  

---

## 1. Updated Logical Relational Schema

### Key Model Refinements for MVP
1. **Single Vehicle per Booking (MVP Boundary):** The ambiguous `booking_items` table has been removed. Each `bookings` record directly references exactly one `vehicles` record (`vehicle_id BIGINT UNSIGNED NOT NULL`). Multi-vehicle group reservations are deferred to post-MVP.
2. **Durable Inventory Holds:** `inventory_holds` is a fully normalized, durable MySQL entity ensuring that active holds are never lost if Redis restarts.
3. **Structured Handover & Evidence Hierarchy:** Handover is separated into `handover_inspections` (capturing pickup or return inspection metadata, odometer, fuel, staff user, and condition notes) and a child entity `handover_evidence` (capturing individual photographic proofs, evidence angles, and upload coordinates).
4. **Resilient Refund & Ledger Model:** `refunds` contains retry counters, gateway reconciliation IDs, and idempotency keys to handle asynchronous payment gateway failures cleanly.

```mermaid
erDiagram
    %% Identity & Access
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : assigned
    USERS ||--o{ REFRESH_TOKENS : owns
    USERS ||--o| TOURIST_PROFILES : has
    USERS ||--o| PARTNER_STAFF : employs

    %% Partner Domain
    BUSINESSES ||--o{ PARTNER_STAFF : employs
    BUSINESSES ||--o{ BUSINESS_LOCATIONS : operates
    BUSINESSES ||--o{ BUSINESS_DOCUMENTS : submits
    BUSINESSES ||--o{ VEHICLES : owns
    BUSINESSES ||--o{ COMMISSIONS : accrues
    BUSINESSES ||--o{ SETTLEMENTS : receives

    %% Geography Domain
    COUNTRIES ||--o{ STATES : contains
    STATES ||--o{ ISLANDS : contains
    ISLANDS ||--o{ LOCATIONS : contains
    ISLANDS ||--o{ VEHICLES : stationed
    LOCATIONS ||--o{ BUSINESS_LOCATIONS : links

    %% Inventory Domain
    VEHICLE_CATEGORIES ||--o{ VEHICLES : categorizes
    VEHICLES ||--o{ VEHICLE_IMAGES : contains
    VEHICLES ||--o{ VEHICLE_DOCUMENTS : possesses
    VEHICLES ||--o{ PRICING_RULES : has
    VEHICLES ||--o{ VEHICLE_UNAVAILABILITY : blocks
    VEHICLES ||--o{ INVENTORY_HOLDS : holds
    VEHICLES ||--o{ BOOKINGS : booked

    %% Booking & Handover Domain
    USERS ||--o{ BOOKINGS : books
    BOOKINGS ||--o{ BOOKING_STATUS_HISTORY : logs
    BOOKINGS ||--o{ HANDOVER_INSPECTIONS : has_inspections
    HANDOVER_INSPECTIONS ||--o{ HANDOVER_EVIDENCE : contains_evidence
    BOOKINGS ||--o{ PAYMENTS : pays
    BOOKINGS ||--o| REVIEWS : reviews
    BOOKINGS ||--o{ SUPPORT_TICKETS : references
    BOOKINGS ||--o{ DAMAGE_REPORTS : disputes

    %% Finance & Commission
    PAYMENTS ||--o{ REFUNDS : refunds
    COMMISSIONS ||--o{ SETTLEMENT_ITEMS : aggregates
    SETTLEMENTS ||--o{ SETTLEMENT_ITEMS : contains

    USERS {
        bigint id PK
        string public_id UK
        string email UK
        string phone UK
        string password_hash
        string status
        datetime created_at
    }

    BUSINESSES {
        bigint id PK
        string public_id UK
        string legal_name
        string trade_name
        string gstin
        string pan_number
        string verification_status
        datetime created_at
    }

    ISLANDS {
        bigint id PK
        string name
        string code UK
        boolean is_active
    }

    LOCATIONS {
        bigint id PK
        bigint island_id FK
        string name
        decimal latitude
        decimal longitude
        boolean is_active
    }

    VEHICLE_CATEGORIES {
        bigint id PK
        string name UK
        string vehicle_type
        boolean is_active
    }

    VEHICLES {
        bigint id PK
        string public_id UK
        bigint business_id FK
        bigint island_id FK
        bigint category_id FK
        string make
        string model
        int model_year
        string registration_number
        decimal daily_rate
        decimal security_deposit
        string booking_mode
        string status
    }

    INVENTORY_HOLDS {
        bigint id PK
        string public_id UK
        bigint vehicle_id FK
        bigint user_id FK
        datetime start_time
        datetime end_time
        string status
        datetime expires_at
    }

    BOOKINGS {
        bigint id PK
        string public_id UK
        string booking_reference UK
        bigint user_id FK
        bigint vehicle_id FK
        bigint pickup_location_id FK
        bigint return_location_id FK
        datetime start_time
        datetime end_time
        decimal total_rental_amount
        decimal tax_amount
        decimal security_deposit_amount
        decimal advance_paid_amount
        decimal pickup_balance_due
        string status
        string payment_mode
        datetime approval_expires_at
    }

    HANDOVER_INSPECTIONS {
        bigint id PK
        string public_id UK
        bigint booking_id FK
        string handover_type
        bigint staff_user_id FK
        int odometer_reading
        int fuel_percentage
        string checklist_data
        string notes
        datetime completed_at
    }

    HANDOVER_EVIDENCE {
        bigint id PK
        bigint inspection_id FK
        string evidence_type
        string image_s3_key
        string media_mime_type
        datetime captured_at
    }

    PAYMENTS {
        bigint id PK
        string public_id UK
        bigint booking_id FK
        decimal amount
        string currency
        string payment_method
        string gateway_provider
        string gateway_order_id
        string gateway_payment_id
        string status
        string idempotency_key UK
    }

    REFUNDS {
        bigint id PK
        string public_id UK
        bigint payment_id FK
        decimal amount
        string gateway_refund_id
        string status
        int retry_count
        string idempotency_key UK
        datetime reconciled_at
    }

    COMMISSIONS {
        bigint id PK
        bigint booking_id FK
        bigint business_id FK
        decimal gross_booking_amount
        decimal commission_percentage
        decimal commission_amount
        string status
    }

    SETTLEMENTS {
        bigint id PK
        string public_id UK
        bigint business_id FK
        decimal total_gross_amount
        decimal total_commission_deducted
        decimal net_payout_amount
        string bank_utr_reference
        string status
    }
```
