# IslandRide — Non-Functional Requirements (NFR)
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Performance & Scalability Requirements

### 1.1 Response Latencies (SLA)
- **NFR-PERF-01 (Search & Catalog Browsing):** The vehicle search API (`GET /api/v1/vehicles`) shall return results with p95 latency under **250ms** for up to 1,000 active listings across multiple island destinations, utilizing database indexing and Redis query caching.
- **NFR-PERF-02 (Availability Calculation):** The availability query API (`GET /api/v1/vehicles/{id}/availability`) shall evaluate operational status, confirmed bookings, temporary holds, and maintenance intervals within **100ms** (p95).
- **NFR-PERF-03 (Static Assets & Frontend Load):** Initial page load for the Angular web client (First Contentful Paint) shall be under **1.8s** on standard 4G mobile networks, facilitated by route-level lazy loading and asset compression (Brotli/Gzip).

### 1.2 Concurrency & Throughput
- **NFR-PERF-04 (Concurrent Booking Requests):** The booking engine shall support a minimum of **200 concurrent booking attempts per second** without data corruption, phantom reservations, or deadlocks.
- **NFR-PERF-05 (Hold Lock Contention):** When multiple users attempt to reserve the same vehicle for overlapping time slots simultaneously, the system shall safely acquire a lock on the primary slot, granting one hold while immediately returning a clean "Unavailable for selected dates" response to the other users within **150ms**.

---

## 2. Security & Compliance Requirements

### 2.1 Authentication & Authorization
- **NFR-SEC-01 (Password Hashing):** All user credentials must be hashed using **Argon2id** (memory cost: 65,536 KiB, iterations: 3, parallelism: 4) or BCrypt with a minimum work factor of 12. Plaintext passwords must never touch logs, caches, or persistent storage.
- **NFR-SEC-02 (Session & Token Management):** The system shall issue short-lived JWT access tokens (15-minute validity) accompanied by rotating refresh tokens stored in secure, `HttpOnly`, `SameSite=Strict`, `Secure` cookies with anti-CSRF protections on all mutating endpoints.
- **NFR-SEC-03 (Resource-Level Isolation / BOLA Defense):** Every API endpoint shall enforce tenant and resource ownership checks. A partner attempting to read or modify another partner's fleet or payout data, or a tourist querying another tourist's booking details, shall receive an immediate `403 Forbidden` and trigger a security audit event.

### 2.2 Payment & Data Security
- **NFR-SEC-04 (PCI-DSS Compliance Scope):** IslandRide shall never store, process, or transmit raw credit/debit card numbers or CVV codes. All card data entry must occur exclusively within the hosted iframe/SDK of the PCI-DSS Level 1 compliant payment gateway.
- **NFR-SEC-05 (Webhook Authenticity):** Incoming payment gateway webhooks must be cryptographically verified using HMAC-SHA256 signatures before processing. Replay attacks must be prevented using database-backed idempotency keys and timestamp verification windows.
- **NFR-SEC-06 (Sensitive Document Storage):** Driving licenses, government identity cards, and business registration proofs must be stored in private, unguessable S3 bucket paths. Public web access to these files is strictly prohibited. Authorized users and admins must access documents exclusively via time-limited (max 10 minutes) pre-signed URLs.

### 2.3 Injection & Abuse Protection
- **NFR-SEC-07 (Injection Prevention):** All database operations must utilize parameterized queries via Spring Data JPA / Hibernate. Dynamic SQL string concatenation is forbidden.
- **NFR-SEC-08 (Rate Limiting):** Critical endpoints (login, registration, OTP delivery, booking creation, checkout) shall be rate-limited via Redis token-bucket filters (e.g., maximum 5 failed login attempts per minute per IP).

---

## 3. Reliability, Availability & Disaster Recovery

### 3.1 Availability & Recovery Targets
- **NFR-REL-01 (System Uptime):** The production system shall target an annual uptime SLA of **99.9%** (excluding pre-scheduled maintenance windows during off-peak night hours).
- **NFR-REL-02 (Recovery Time Objective - RTO):** In the event of catastrophic server infrastructure failure, the platform shall be restorable within an RTO of **under 60 minutes** using automated Docker compose and database recovery scripts.
- **NFR-REL-03 (Recovery Point Objective - RPO):** Automated daily full database backups combined with point-in-time binary log (binlog) backups shall guarantee an RPO of **under 15 minutes** of data loss.

### 3.2 Data Integrity & Financial Consistency
- **NFR-REL-04 (ACID Transactions):** Booking transitions, inventory hold releases, and payment ledger entries must execute within explicit `@Transactional` boundaries with appropriate isolation levels to prevent partial state commits.
- **NFR-REL-05 (Immutability of Financial Records):** Ledger entries, payment transactions, refund records, and booking status change logs must be append-only. No existing transaction or payment history record may be updated or deleted in place.

---

## 4. Usability, Accessibility & Localization

### 4.1 Accessibility & Responsiveness
- **NFR-USE-01 (Responsive Design):** The Angular web application must be fully responsive across mobile (360px width minimum), tablet, and desktop (up to 4K resolutions).
- **NFR-USE-02 (WCAG Compliance):** The user interface shall conform to **WCAG 2.1 Level AA** standards, providing appropriate color contrast, keyboard navigability, ARIA labels, and screen reader compatibility.

### 4.2 Timezone & Currency Precision
- **NFR-LOC-01 (Timezone Handling):** All database timestamps must be stored in **UTC**. The application layer shall convert and display times in Indian Standard Time (**IST / Asia/Kolkata**, UTC+05:30), accounting for local island business operating hours.
- **NFR-LOC-02 (Currency & Rounding):** All amounts must be formatted in Indian Rupees (**INR / ₹**) using standard half-up decimal rounding.

---

## 5. Maintainability, Portability & Observability

### 5.1 Architecture & Code Cleanliness
- **NFR-MAINT-01 (Modular Monolith Boundaries):** Internal modules must communicate strictly via well-defined domain services, interfaces, or internal domain events. Direct cross-module repository imports or cross-domain database table joins in JPA are prohibited and verified via ArchUnit tests.
- **NFR-MAINT-02 (Flyway Versioning):** All database schema modifications must be scripted as immutable, sequential Flyway migration files (`V1__...`, `V2__...`). Hibernate `ddl-auto` is strictly set to `validate` in production environments.

### 5.2 Observability & Telemetry
- **NFR-OBS-01 (Structured Logging):** Logs must be formatted in JSON and include a unique Correlation ID (`X-Correlation-ID`) propagated across every HTTP request, error log, and asynchronous job.
- **NFR-OBS-02 (Masking):** Passwords, API secrets, authorization tokens, payment details, and full customer phone numbers must be automatically masked before writing to log streams.
- **NFR-OBS-03 (Health Probes):** The Spring Boot backend shall expose standard Actuator health probes (`/actuator/health/liveness`, `/actuator/health/readiness`) verifying connectivity to MySQL and Redis.

