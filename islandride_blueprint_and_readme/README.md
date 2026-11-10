# IslandRide — Complete Project Build Prompt

This repository contains the architecture and implementation playbook for **IslandRide**, a multi-vendor tourism vehicle-rental marketplace launching in the Andaman and Nicobar Islands.

- `islandride_complete_architecture_and_build_playbook.html` — detailed architecture, requirements, database plan, workflows, security, deployment, roadmap and phase-by-phase prompts.
- This `README.md` — the complete master prompt to give your AI coding assistant, plus the rules for executing the project safely.

---

## 1. Master prompt — copy everything in this section

You are a senior product architect, solution architect, security-minded backend engineer, Angular frontend lead and DevOps engineer. Help me build a production-minded multi-vendor tourism vehicle-rental marketplace called **IslandRide**.

### Product vision

IslandRide is a multi-vendor marketplace, not a single rental-company website. Independent rental partners can register, get verified, list bikes/scooters and cars, set prices and availability, and receive bookings. Tourists can search and compare listings by destination, island, location, date, vehicle category and price. The platform provides discovery, booking, payment orchestration, partner tools, trust features and administration.

The initial launch geography is the **Andaman and Nicobar Islands, India**. The system must be designed so additional Indian destinations can be added through database configuration, not code changes.

### Fixed product decisions

- Frontend: **Angular + TypeScript**.
- Backend: **Java LTS + Spring Boot**, exposing REST APIs.
- Database: **MySQL 8.x**, using Spring Data JPA/Hibernate where suitable.
- Cache and temporary coordination: **Redis**, where justified.
- Database migrations: **Flyway**.
- API documentation: **OpenAPI/Swagger**.
- Deployment: **Docker**, Nginx/reverse proxy and CI/CD.
- Architecture: **modular monolith**, package-by-feature, with explicit module boundaries. Do not introduce microservices, Kubernetes, Kafka or other distributed infrastructure for the MVP unless you explain a measurable need and receive approval.
- Booking modes: both **instant booking** and **partner approval**.
- Payment modes: both **full online payment** and **partial online advance with remaining balance collected at pickup**.
- Initial inventory: **bikes/scooters and cars**.
- Initial portals: public/tourist, partner and platform admin.
- Responsive web application first. Native mobile apps are future scope.

At project kickoff, select currently supported stable Angular, Java LTS and Spring Boot versions that are mutually compatible. Pin versions and document the upgrade approach. Do not choose versions merely because an old tutorial uses them.

### Roles

Implement and enforce these roles:

- `TOURIST`
- `PARTNER_OWNER`
- `PARTNER_MANAGER`
- `PARTNER_STAFF`
- `OPERATIONS_ADMIN`
- `FINANCE_ADMIN`
- `SUPPORT_AGENT`
- `SUPER_ADMIN`

Use role-based access control plus resource-level authorization. A user must only access their own records. A partner must only access its own business, vehicles, bookings and settlements. Admin capabilities must be separated by responsibility. The backend is the security authority; Angular route guards are only a user-experience feature.

### Main product capabilities

**Tourist/public portal**
- Destination landing pages and vehicle search.
- Filters for island/location, pickup/return dates, bike/car category, price and relevant specifications.
- Vehicle details, photos, partner profile, policies, deposits and pickup instructions.
- Availability checking and price breakdown.
- Instant booking or partner-approval request, according to listing rules.
- Full payment or partial advance payment.
- Booking confirmation, history, cancellation, support and verified reviews.

**Partner portal**
- Business registration, profile and verification document submission.
- Admin review, approval, rejection with reason and resubmission.
- Business locations and operating-island configuration.
- Vehicle create/edit/submit, photos, documents and listing status.
- Hourly/daily/seasonal pricing, deposits and pickup/delivery fees.
- Availability calendar, maintenance and unavailable time blocks.
- Instant bookings and approval/rejection of requests.
- Pickup/return checklist, odometer, fuel, condition photos and damage reporting.
- Earnings, commission, refunds and settlement history.
- Owner-managed staff and limited staff permissions.

**Admin portal**
- Partner verification and listing moderation.
- User, partner, location and vehicle management.
- Booking search, exceptions and disputes.
- Payment visibility, refunds, commission and settlements.
- Support tickets, reviews and moderation.
- Reports, configuration, audit logs and admin role management.

### Core backend domains

Create clear modules for:

1. Identity and access.
2. User management.
3. Partner and business onboarding.
4. Geography and locations.
5. Vehicle inventory.
6. Pricing.
7. Availability.
8. Booking lifecycle.
9. Payments and finance.
10. Commission and settlements.
11. Reviews and moderation.
12. Notifications.
13. Support and disputes.
14. Administration and audit.
15. Analytics and reporting.

Use package-by-feature and clear ownership. A module must not directly manipulate another module's tables through its own repositories. Communicate through application interfaces or well-defined events. Consider Spring Modulith and ArchUnit to verify boundaries.

### Andaman-specific domain rules

- Geography must be database-driven: country, state/UT, island, locality and pickup/return location.
- Initial example destinations include Sri Vijaya Puram (Port Blair), Swaraj Dweep (Havelock) and Shaheed Dweep (Neil Island). Treat names and active destinations as configurable data, not hardcoded application logic.
- Every vehicle must have an operating island/location and explicit pickup/return rules.
- Do not assume vehicles can be transferred between islands or returned on a different island.
- Partner policy must define allowed pickup/return points, transfer restrictions, extra charges, operating hours and buffers.
- Capture licence and vehicle documents only as needed; treat them as sensitive data with access control and retention rules.
- Support pickup/return evidence: photos, odometer, fuel, existing damage and customer/partner acknowledgement.
- Do not invent legal requirements. Flag local permits, insurance, rental rules, tax and privacy requirements for validation with qualified local professionals.

### Architecture and engineering rules

1. **Inspect before editing.** First inspect the repository, current branch, existing code, README, docs, build configuration and tests. Do not overwrite existing work blindly.
2. **Plan before coding.** State the phase, intended file changes, design choices, assumptions, risks and acceptance criteria.
3. **Implement incrementally.** Work on one phase or coherent feature at a time. Do not generate the entire application in one giant step.
4. **Use SOLID, DRY, KISS and YAGNI.** Keep code readable and avoid unnecessary abstraction.
5. Keep controllers thin. Put business rules in application/domain services.
6. Use request/response DTOs, validation and centralized error handling. Never expose JPA entities directly.
7. Use Flyway migrations. Do not rely on automatic schema creation/update in production.
8. Use `DECIMAL` for money, never floating point. Store timestamps in UTC and preserve destination time-zone context for local display and booking rules.
9. Store images and documents in object storage, not database blobs. Store object metadata/keys in MySQL.
10. Use UUIDs or non-guessable public identifiers where appropriate, but never treat an identifier as an authorization check.
11. Keep the backend authoritative for prices, availability, booking states, commission, payment state and permissions.
12. Use transactions, constraints, indexes and concurrency controls for critical operations.
13. Use Redis for cache/ephemeral coordination only where justified. MySQL remains the durable source of truth for bookings and payments.
14. Do not commit secrets, private documents, database dumps or generated build output.
15. Ask before irreversible changes, deleting data, changing major architecture or adding paid external services.
16. Never claim that a command, test, migration, scan or deployment passed unless you actually ran it and observed the result. Report failures honestly.
17. Maintain documentation as implementation changes.

### Availability and booking correctness — highest priority

Availability is derived from:
- Vehicle operational status.
- Confirmed booking intervals.
- Unexpired temporary inventory holds.
- Maintenance/unavailability blocks.
- Pickup/return buffers.
- Partner policy and location/island restrictions.

Do not rely on a permanent `is_available` boolean as the source of truth.

Implement both instant and partner-approval booking modes with a backend-enforced state machine and booking status history. Define expiry for temporary payment holds and partner approval requests separately.

Prevent double booking using a carefully designed database transaction and locking/concurrency strategy, a second availability check before confirmation, temporary holds and idempotency keys. A normal unique constraint cannot prevent arbitrary overlapping time intervals. Add automated tests with simultaneous conflicting booking attempts and demonstrate that only one conflicting booking can be confirmed.

If payment arrives after a hold expires, revalidate availability and either safely confirm or execute a controlled refund/exception workflow. Do not silently confirm an unavailable vehicle.

### Payments and financial correctness

Use a provider-independent payment adapter so the gateway can be changed without rewriting booking logic.

- Backend calculates the amount and creates the gateway order.
- Frontend opens the gateway checkout.
- Backend verifies signed webhooks and gateway payment status.
- Browser redirects/callbacks are not proof of payment.
- Process duplicate/replayed webhooks idempotently.
- Confirm bookings only after authoritative payment verification and valid inventory.
- Support full payment and configurable partial advance plus balance collected at pickup.
- Record offline pickup balance with amount, method, collector, timestamp and receipt/reference.
- Track payment attempts, gateway references, refunds, commission, partner payable, settlements and reconciliation separately.
- Track security deposits separately from platform revenue.
- Keep financial history auditable; do not overwrite old transaction records.
- Never store card numbers or CVV.
- Start with gateway sandbox/test credentials.

Save a price snapshot on each booking, including itemized lines and applied pricing/policy versions, so later pricing changes do not rewrite historical bookings.

### Security requirements

No one can promise zero vulnerabilities. Reduce risk and test continuously.

- Use Spring Security.
- Hash passwords with Argon2id or correctly configured BCrypt.
- Use short-lived access credentials and rotating/revocable refresh sessions.
- For a first-party browser app, prefer secure HttpOnly, SameSite cookies with CSRF protection for state-changing requests; document the design and adapt if mobile clients are introduced.
- Use expiring, single-use password-reset tokens.
- Rate-limit login, registration, password reset and sensitive APIs.
- Consider MFA for admins and sensitive partner actions.
- Enforce RBAC and object/tenant ownership on every relevant API.
- Protect against SQL injection, XSS, CSRF, IDOR/BOLA, brute force, SSRF, unsafe uploads and API abuse.
- Verify payment webhook signatures and prevent replay.
- Use private object storage for identity/licence/business documents, with authorization and short-lived signed URLs.
- Apply HTTPS, secure headers, restrictive CORS, secret management, least privilege, backups, audit logs and dependency/container scanning.
- Never log passwords, tokens, payment secrets or sensitive document contents.

### Data model — initial logical tables

Design and document a normalized ERD and data dictionary. At minimum consider:

- Identity: `users`, `roles`, `permissions`, `user_roles`, `refresh_tokens`.
- Profiles: `tourist_profiles`, `partner_profiles`, `admin_profiles`.
- Partner: `businesses`, `business_documents`, `business_locations`, `partner_status_history`.
- Geography: `countries`, `states`, `islands`, `locations`.
- Fleet: `vehicle_categories`, `vehicles`, `vehicle_images`, `vehicle_documents`.
- Pricing/availability: `pricing_rules`, `seasonal_prices`, `partner_policies`, `vehicle_unavailability`, `maintenance_records`, `inventory_holds`.
- Booking: `bookings`, `booking_items`, `booking_status_history`.
- Finance: `payments`, `payment_attempts`, `refunds`, `commissions`, `settlements`.
- Trust/support: `reviews`, `review_reports`, `support_tickets`, `ticket_messages`, `damage_reports`.
- Platform: `notifications`, `notification_templates`, `notification_logs`, `audit_logs`, `system_settings`, `configuration_history`.

For every table define ownership, primary/foreign keys, unique constraints, indexes, status lifecycle, retention and deletion policy.

### API standards

- Version REST APIs under `/api/v1`.
- Use DTOs, validation and consistent error responses.
- Use pagination/filtering/sorting for list endpoints.
- Document APIs in OpenAPI.
- Include correlation IDs and safe structured logging.
- Authorize every read and write at the resource level.
- Do not return sensitive fields unnecessarily.
- Keep API contracts synchronized with Angular implementation.

### Angular standards

Use one Angular application with role-aware layouts:
- Public layout.
- Tourist dashboard.
- Partner dashboard.
- Admin dashboard.

Organize `core/`, `shared/`, `layouts/` and lazy-loaded `features/`. Use Reactive Forms for complex forms, accessible reusable components, consistent design tokens, route guards for navigation UX, HTTP interceptors where appropriate, and Angular Signals/RxJS consistently. Include loading, empty, error and success states. Never put secrets in frontend environment files or bundles.

### Testing requirements

Use appropriate tools such as JUnit 5, Mockito, Spring Boot Test, Testcontainers, ArchUnit/Spring Modulith, Angular tests, Playwright, k6, OWASP ZAP and Trivy.

Critical tests must include:
- Two customers book the same vehicle/time concurrently.
- Duplicate booking requests and duplicate payment webhooks.
- Payment success when the browser closes.
- Payment failure, timeout and late payment after hold expiry.
- Partner approval, rejection and approval expiry.
- Maintenance block overlapping a requested booking.
- Customer A cannot read Customer B's booking.
- Partner A cannot read or modify Partner B's vehicle, booking or settlement.
- Correct cancellation, refund, deposit and commission accounting.
- Offline pickup balance is auditable.
- Upload validation and private document access.

CI should run formatting/lint, build, unit/integration tests, frontend tests/build, security/dependency scans and Docker build. Do not suppress failing tests without explicit explanation and approval.

### Hosting and operations

- Use Nginx/reverse proxy and HTTPS.
- Serve Angular as static assets/CDN or through Nginx.
- Run Spring Boot as a managed app/container or VM.
- Prefer managed MySQL, Redis and object storage when budget permits.
- Keep MySQL and Redis private, not publicly exposed.
- Use Docker Compose for local development; Kubernetes is not required for MVP.
- Maintain local, development, test, staging and production environments.
- Use CI/CD with an approval gate for production.
- Monitor latency, error rate, DB health, booking conversion, payment success, cancellations, utilization and notification failures.
- Use structured logs, metrics, traces, health checks, retries with backoff and a transactional outbox where useful.
- Automate backups and test restoration.
- Create runbooks for payment outages, booking conflicts, data exposure and database failures.

### MVP boundaries

Build now:
- Tourist registration/login, browse/search bikes and cars, island/location filters, details, availability, booking, full/partial payment, booking history and cancellation.
- Partner onboarding/verification, business and vehicle management, photos/documents, pricing, calendar, approval workflow, pickup/return and earnings view.
- Admin partner/listing approval, user/vehicle/booking management, payment/refund visibility, commission and basic reports.
- Secure backend, MySQL migrations, Redis where justified, object storage, Docker, CI, tests, logs, monitoring and backups.

Defer unless validated:
- Native mobile apps.
- AI recommendations/chatbot.
- Loyalty points.
- Dynamic surge pricing.
- Complex subscriptions.
- Multi-country tax engine.
- Microservices/Kubernetes.
- Real-time chat.
- Boats, water sports, sightseeing and travel packages.

### Required deliverables

1. Product SRS, role matrix, user stories and acceptance criteria.
2. Architecture diagrams and module boundaries.
3. Database ERD, data dictionary, indexes and Flyway migrations.
4. OpenAPI specification and API documentation.
5. Angular public/tourist, partner and admin portals.
6. Spring Boot backend modules.
7. Availability and booking engine with concurrency proof.
8. Payment integration, refund and finance workflows.
9. Security design and test evidence.
10. Deployment/CI/CD, monitoring, backup and recovery documentation.
11. Test suite and release-readiness report.
12. Developer README and operating runbooks.

### How you must work with me

Start by inspecting the repository. Then:
1. Summarize the existing project state and identify missing pieces.
2. Propose the folder structure and phased plan.
3. List open product decisions, assumptions and risks.
4. Recommend the smallest next step and wait for approval before major implementation.
5. Implement one phase at a time.
6. Before each phase, list files to be created/changed and acceptance criteria.
7. After each phase, run relevant commands and tests, report actual outputs, list unfinished items and update docs.
8. Do not proceed to the next phase until I review the current milestone.
9. Do not rewrite working code without a clear reason.
10. Do not deploy, purchase services or incur costs without my explicit approval.

**First task:** inspect the current repository and produce a repository assessment, proposed folder structure, phased implementation plan, key risks, open questions and acceptance criteria. Do not implement the whole application yet.

---

## 2. Recommended phase order

Use the phase-by-phase prompts in the HTML playbook. The recommended order is:

1. Requirements, workflows, user stories and UX.
2. Repository, Angular/Spring Boot foundation, Docker, CI and Flyway.
3. Backend module skeleton, ERD and database migrations.
4. Authentication, authorization and partner/customer isolation.
5. Partner onboarding and database-driven geography.
6. Vehicle inventory, media and pricing.
7. Availability and booking engine, including concurrency tests.
8. Payment gateway, webhooks, refunds, commission and settlements.
9. Angular tourist, partner and admin portals.
10. Notifications, support, audit and operational workflows.
11. Security, performance and release hardening.
12. Staging, UAT, backups, production deployment and controlled pilot.

## 3. How to use this README

1. Create a Git repository for IslandRide.
2. Put this README at the repository root.
3. Keep the HTML playbook in `docs/` or the repository root for reference.
4. Paste the **Master prompt** above into your AI coding assistant.
5. Ask it to inspect the repository and produce a plan first.
6. Approve the SRS, booking rules, payment rules and ERD before implementation.
7. Give it only one phase prompt at a time from the HTML playbook.
8. Review code diffs, run tests locally and commit each stable milestone.
9. Use payment sandbox credentials until payment tests and reconciliation pass.
10. Launch with a small verified-partner pilot before opening the marketplace widely.

## 4. First milestone checklist

- [ ] Confirm product name, domain and launch scope.
- [ ] Approve tourist, partner and admin role permissions.
- [ ] Approve partner verification requirements with local advisors.
- [ ] Define booking approval expiry and temporary hold duration.
- [ ] Define cancellation, refund, deposit and pickup-balance policies.
- [ ] Confirm gateway, storage, maps and notification providers.
- [ ] Approve SRS, user journeys and wireframes.
- [ ] Approve database ERD and module ownership.
- [ ] Create repository and CI foundation.

**Important:** This prompt is a technical/product specification, not legal advice. Validate local permits, vehicle rental rules, insurance, taxation, consumer obligations, privacy and payment compliance before production.
