# IslandRide — Phased Development Roadmap
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. 12-Phase Implementation Roadmap Overview

| Phase | Milestone Name | Focus Area & Key Deliverables | Prerequisite Gate |
|---|---|---|---|
| **Phase 1** | **Requirements & System Design** | SRS, NFRs, user stories, role matrix, business rules, ERD, API specs, wireframes. | Project Kickoff |
| **Phase 2** | **Repository & Engineering Foundation** | Monorepo setup, Angular 18 + Spring Boot 3.3 skeletons, Docker Compose (MySQL/Redis), Flyway baseline, CI workflow. | Phase 1 Approval Gate |
| **Phase 3** | **Backend Architecture & Database** | 15 Package-by-feature skeletons, initial Flyway migration scripts, Testcontainers test setup. | Phase 2 Complete |
| **Phase 4** | **Authentication & Authorization** | Tourist/Partner auth, JWT/cookie session rotation, RBAC guards, Argon2id, tenant isolation tests. | Phase 3 Complete |
| **Phase 5** | **Partner Onboarding & Geography** | Database-driven Andaman locations, partner KYC upload, admin review queue, staff delegation. | Phase 4 Complete |
| **Phase 6** | **Vehicle Inventory & Pricing** | Fleet catalog CRUD, photo upload, duration/seasonal pricing engine, price snapshot generation. | Phase 5 Complete |
| **Phase 7** | **Availability & Booking Engine** | Interval overlap concurrency engine, 10-min holds, instant & approval state machine, stress tests. | Phase 6 Complete |
| **Phase 8** | **Payments & Financial Ledger** | Payment adapter (Razorpay sandbox), HMAC webhooks, partial advance ledger, refund processing. | Phase 7 Complete |
| **Phase 9** | **Angular Portals & Frontends** | Public search/checkout, Tourist dashboard, Partner console, Admin verification portal. | Phase 8 Complete |
| **Phase 10** | **Notifications, Support & Ops** | Email/SMS notifications, dispute arbitration console, handover damage reports, audit logging. | Phase 9 Complete |
| **Phase 11** | **Security & Release Hardening** | OWASP Top 10 verification, concurrency race test verification, load testing (k6), vulnerability scans. | Phase 10 Complete |
| **Phase 12** | **Production Deployment & Pilot** | Docker production image, Nginx SSL, staging verification, backup/restore drills, pilot launch. | Phase 11 Complete |

---

## 2. Phase-by-Phase Detailed Breakdown

### Phase 1: Requirements and System Design (CURRENT)
- **Deliverables:** Complete documentation suite under `docs/` covering requirements, architecture, database ERD, API designs, UX wireframes, security model, and project risks.
- **Exit Gate:** Complete review and explicit user approval before writing application code.

### Phase 2: Repository and Engineering Foundation
- **Tasks:**
  - Initialize clean monorepo: `frontend/`, `backend/`, `infrastructure/`, `docs/`, `scripts/`, `.github/workflows/`.
  - Spring Boot 3.3 skeleton with Java 21 LTS and Maven.
  - Angular 18+ application skeleton with TypeScript and Tailwind/CSS design tokens.
  - `docker-compose.yml` defining MySQL 8.x and Redis 7.x containers with health probes and persistent volumes.
  - Minimal Flyway migration `V1__init_schema.sql` and global OpenAPI configuration.
- **Verification:** Run `mvn clean verify` and `ng test / ng build`, reporting real command outputs.

### Phase 3: Backend Module Skeleton & Database Schema
- **Tasks:**
  - Establish the 15 package-by-feature module skeletons.
  - Complete Flyway migrations for all tables, indexes, constraints, and initial Andaman geography seed data.
  - Integration tests using Testcontainers running against a real MySQL container.
- **Verification:** Database schema initializes cleanly; module boundary tests pass via ArchUnit.

### Phase 4: Authentication, Authorization & Isolation
- **Tasks:**
  - Implement registration, login, logout, refresh session rotation, and password reset.
  - Enforce RBAC security filters and tenant-level resource isolation.
- **Verification:** Automated tests proving Tourist A cannot read Tourist B's profile; Partner A cannot read Partner B's data.

### Phase 5: Partner Onboarding & Geography
- **Tasks:**
  - Partner business profile submission, KYC document upload to private object storage.
  - Admin partner review queue with approve/reject actions.
  - Database-driven islands (Port Blair, Havelock, Neil) and pickup hubs.
- **Verification:** Verified partner can log into Partner Console; unverified partner is locked in `UNDER_REVIEW`.

### Phase 6: Vehicle Inventory & Pricing Engine
- **Tasks:**
  - Vehicle CRUD (Bikes, Scooters, Cars) with photo upload and RC/insurance tracking.
  - Tiered pricing engine: daily rates, seasonal peak surcharges, delivery charges, security deposits.
  - Pre-booking price snapshot generator.
- **Verification:** Price calculation unit tests across standard and peak seasonal dates.

### Phase 7: Availability & Booking Concurrency Engine
- **Tasks:**
  - Implement dynamic availability calculation from active bookings, holds, maintenance blocks, and buffers.
  - 10-minute temporary inventory hold engine in Redis and MySQL.
  - Instant and partner-approval booking state machines.
  - Pessimistic locking concurrency protection.
- **Verification:** Multi-threaded stress test simulating 50 concurrent booking attempts for the same vehicle slot, verifying that exactly one booking is confirmed and 49 receive 409 Conflict.

### Phase 8: Payment Gateway Integration & Financial Ledger
- **Tasks:**
  - Implement `PaymentGatewayAdapter` with Razorpay sandbox integration.
  - Webhook listener with HMAC-SHA256 signature verification and idempotency keys.
  - Support full online payment and 25% partial advance payment options.
  - Offline pickup balance recording in Handover Console.
  - Automated cancellation refund calculation.
- **Verification:** Automated tests for successful payment webhook, replayed/duplicate webhook, and hold-expired late webhook handling.

### Phase 9: Angular Portals & UI Implementation
- **Tasks:**
  - Build public catalog search, filters, and vehicle detail pages.
  - Build checkout flow with 10-minute hold countdown timer and Razorpay modal.
  - Build Tourist Dashboard (vouchers, cancellations).
  - Build Partner Console (fleet, calendar, approval queue, handover console, earnings).
  - Build Super Admin Console (partner verification, listing moderation, financial settlements).
- **Verification:** Component tests and Playwright end-to-end journey tests.

### Phase 10: Notifications, Support & Operations
- **Tasks:**
  - Asynchronous email and SMS dispatch for booking vouchers, approval alerts, and payment receipts.
  - Customer support ticketing system and damage dispute arbitration console.
  - Operational health probes and JSON structured audit logging.
- **Verification:** Event-driven notification test using mocked mail/SMS gateways.

### Phase 11: Security & Performance Hardening
- **Tasks:**
  - Run OWASP security scan (ZAP / dependency-check).
  - Verify CSRF, XSS, and SQL injection protections.
  - Load test search and booking endpoints with k6 (target: p95 < 250ms under load).
- **Verification:** Clean security audit report and confirmed latency benchmarks.

### Phase 12: Production Deployment & Pilot Readiness
- **Tasks:**
  - Docker multi-stage production container build.
  - Nginx reverse proxy configuration with TLS/SSL headers and gzip compression.
  - Automated database backup and tested restore runbook.
  - Pilot rollout plan with 3-5 verified Andaman partners.
- **Verification:** Successful end-to-end booking executed on staging environment with live sandbox payment.

