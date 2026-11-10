# IslandRide — Architecture Decision Records (ADRs)
**Document Version:** 1.1.0  
**Phase:** 1 (Requirements & System Design — Post-Review Revisions)  
**Status:** Approved for Implementation  

---

## ADR-001: Selection of Modular Monolith Architecture over Microservices
- **Status:** Accepted
- **Context:** IslandRide is a multi-vendor vehicle rental marketplace launching in the Andaman Islands. The system requires high consistency across inventory availability, bookings, and financial settlements. We need to decide between a distributed microservices architecture and a modular monolith.
- **Decision:** Build IslandRide as a single **Modular Monolith** using Java 25 LTS and Spring Boot 4.1.x. Organize the codebase using strict **Package-by-Feature** boundaries with domain events and ArchUnit fitness checks.
- **Consequences:** 
  - *Positive:* ACID database transactions guarantee booking and payment ledger consistency; zero network RPC latency between modules; simplified local development and CI/CD pipelines; rapid iteration for MVP.
  - *Negative:* All modules share the same deployment artifact and database connection pool. If an unoptimized query exhausts database connections, it could impact adjacent modules.
  - *Mitigation:* Strict connection pool tuning (HikariCP), query profiling, and clean interfaces that permit future microservice extraction if traffic demands it.

---

## ADR-002: Single Angular SPA with Role-Based Layouts & Strict Backend Authority
- **Status:** Accepted
- **Context:** The platform serves three primary user personas: public tourists, rental partners, and platform administrators. We must decide whether to build three separate frontend applications or a unified Angular application.
- **Decision:** Build a single **Angular 22.x Single Page Application** with distinct route-level lazy-loaded layouts (`PublicLayout`, `TouristLayout`, `PartnerLayout`, `AdminLayout`).
- **Security Boundary Clarification:** Route-level lazy loading provides performance and code organization benefits only. **Client-side code and route guards NEVER provide security.** Backend authorization on every API endpoint is strictly mandatory. An attacker modifying client-side JavaScript or inspecting downloaded bundles must never be able to access unauthorized data or execute unauthorized operations.
- **Consequences:**
  - *Positive:* 100% code reuse for design system tokens, UI component libraries, HTTP interceptors, and TypeScript DTO interfaces; single build and deployment pipeline on Nginx.
  - *Negative:* Frontend bundles may contain layout structure definitions.
  - *Mitigation:* The backend remains the sole security authority, returning `403 Forbidden` for any unauthorized access attempt.

---

## ADR-003: MySQL 8.4 LTS as Durable Truth & Redis for Ephemeral Coordination
- **Status:** Accepted
- **Context:** Booking engines require both persistent financial records and fast temporary reservation holds.
- **Decision:** Use **MySQL 8.4 LTS (InnoDB)** as the single durable source of truth for all business entities (users, vehicles, bookings, ledger entries, and active `inventory_holds`). Use **Redis** strictly for ephemeral coordination, low-latency TTL countdowns, distributed rate limiting, and cache invalidation. **Redis is never the sole record of an inventory hold.**
- **Consequences:**
  - *Positive:* If Redis experiences a restart or cache flush, zero persistent bookings, holds, or financial transactions are lost because MySQL holds the canonical state.
  - *Negative:* Hold creation writes to both MySQL (durable) and Redis (ephemeral), adding a few milliseconds during checkout initialization.

---

## ADR-004: Interval Concurrency Strategy for Double-Booking Prevention
- **Status:** Accepted
- **Context:** Standard database unique constraints (`UNIQUE(vehicle_id)`) can only enforce exact matches, but rental reservations span continuous time intervals $[T_{start}, T_{end})$. Overlapping intervals must be prevented during concurrent booking attempts.
- **Decision:** Implement a three-layer concurrency control strategy:
  1. **Hold Reservation:** Short-lived `inventory_holds` record created in MySQL and cached in Redis with a configurable duration (default: 10 minutes).
  2. **Pessimistic Locking on Final Confirmation:** During the confirmation transaction, execute `SELECT id FROM vehicles WHERE id = :id FOR UPDATE` to serialize concurrent requests for that specific vehicle.
  3. **Secondary Validation Query:** Inside the locked transaction, re-execute the interval overlap query:
     ```sql
     SELECT COUNT(b.id) FROM bookings b 
     WHERE b.vehicle_id = :vehicleId 
       AND b.status IN ('CONFIRMED', 'PICKUP_PENDING', 'ACTIVE')
       AND (b.start_time < :newEndTime AND b.end_time > :newStartTime)
     ```
     If the count $> 0$, abort and roll back.
- **Consequences:**
  - *Positive:* 100% mathematical guarantee against double-booking, even under heavy concurrent traffic.
  - *Negative:* Slight latency overhead during lock acquisition. Because locks are acquired per-vehicle for only milliseconds, lock contention is negligible across a diversified fleet.

---

## ADR-005: Decoupled Payment Gateway Adapter Pattern
- **Status:** Accepted
- **Context:** The application will launch with an Indian payment gateway (e.g. Razorpay), but business requirements may necessitate switching to Cashfree, PayU, or Stripe in the future.
- **Decision:** Abstract all payment operations behind a clean domain interface (`PaymentGatewayAdapter`) exposing vendor-agnostic methods (`createOrder`, `verifyWebhookSignature`, `initiateRefund`).
- **Consequences:**
  - *Positive:* Zero gateway SDK code leaks into booking or checkout services. Swapping payment providers requires writing a single adapter class and updating a configuration property.
  - *Negative:* Slightly higher initial abstraction.

---

## ADR-006: S3-Compatible Private Storage with Signed URLs
- **Status:** Accepted
- **Context:** IslandRide collects vehicle photos (public) and sensitive KYC documents (private Driving Licenses, Aadhaar, Trade Licenses). Storing binary files in MySQL BLOBs harms database performance and backups.
- **Decision:** Store all files in S3-compatible object storage. Public vehicle photos are served via CDN. Sensitive partner and customer identity documents are stored in private buckets and accessed exclusively via temporary pre-signed URLs (maximum 10 minutes validity) issued only after backend authorization.
- **Consequences:**
  - *Positive:* Keeps MySQL database lightweight; provides impenetrable security against unauthorized document scraping or IDOR attacks.
  - *Negative:* Requires object storage integration (AWS S3 or MinIO).

---

## ADR-007: Flyway Versioned Database Migrations
- **Status:** Accepted
- **Context:** Production databases require predictable, version-controlled schema evolution.
- **Decision:** Use **Flyway** for all database migrations. Automatic Hibernate schema generation (`ddl-auto=update`) is strictly prohibited in production.
- **Consequences:**
  - *Positive:* Complete audit history of schema changes; deterministic schema parity across local development, CI tests, staging, and production.

---

## ADR-008: Single Vehicle Per Booking Boundary for MVP
- **Status:** Accepted
- **Context:** A booking could theoretically contain multiple vehicles (e.g. a group renting 3 scooters). However, multi-vehicle bookings introduce significant complexity: partial cancellations, different vehicle handover times, separate security deposits, and multi-line billing.
- **Decision:** For the MVP, **each booking is strictly for one vehicle**. The `bookings` table directly links to `vehicle_id`. Multi-vehicle bookings are deferred to post-MVP.
- **Consequences:**
  - *Positive:* Simplifies the booking state machine, availability calculations, handover checklists, and deposit tracking.
  - *Negative:* Customers renting multiple vehicles must complete separate reservations.

---

## ADR-009: Asynchronous Refund Processing & Reconciliation
- **Status:** Accepted
- **Context:** Payment gateway refund APIs may experience network timeouts, transient gateway errors, or delayed bank processing. Treating refunds as synchronous operations that always succeed immediately causes ledger drift.
- **Decision:** Implement an **asynchronous, idempotent refund processing workflow** with automatic retries and a scheduled reconciliation worker that queries gateway status.
- **Consequences:**
  - *Positive:* Resilience against third-party payment gateway outages; zero lost refund requests.
  - *Negative:* Requires managing refund states (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`, `RECONCILED`) and background retry schedulers.
