# IslandRide — Backend Module Architecture
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Modular Monolith Architecture

The backend application is designed following the **Package-by-Feature (Modular Monolith)** architectural pattern. Each business domain forms a self-contained module with its own internal layers:

```
com.islandride.<module-name>/
  ├── api/             # REST Controllers, Request/Response DTOs, API Validators
  ├── application/     # Use Cases, Application Services, Command/Query Handlers
  ├── domain/          # Entities, Value Objects, Domain Events, Enums, Domain Services
  └── infrastructure/  # Spring Data JPA Repositories, Database Entities, External Adapters
```

---

## 2. Inventory of Core Modules

The system is decomposed into 15 distinct functional modules:

| # | Module Name | Package Path | Core Responsibility & Owned Entities |
|---|---|---|---|
| **1** | `identity-access` | `com.islandride.identity` | User authentication, password hashing, JWT/cookie issuance, refresh session rotation, RBAC enforcement (`users`, `roles`, `permissions`, `refresh_tokens`). |
| **2** | `user-management` | `com.islandride.user` | Tourist profiles, saved contact details, driving license metadata, user preferences (`tourist_profiles`, `admin_profiles`). |
| **3** | `partner` | `com.islandride.partner` | Rental business onboarding, trade licenses, partner hubs/locations, staff permissions, verification workflow (`businesses`, `business_locations`, `business_documents`, `partner_staff`). |
| **4** | `geography` | `com.islandride.geography` | Database-driven geography: countries, states, islands, localities, pickup/return hubs, geo-coordinates (`countries`, `states`, `islands`, `locations`). |
| **5** | `inventory` | `com.islandride.inventory` | Vehicle categories, fleet catalog, registration plates, specifications, RC/insurance docs, vehicle photos (`vehicle_categories`, `vehicles`, `vehicle_images`, `vehicle_documents`). |
| **6** | `pricing` | `com.islandride.pricing` | Hourly/daily base rates, seasonal price multipliers, weekend surcharges, delivery fees, security deposit amounts (`pricing_rules`, `seasonal_prices`, `partner_policies`). |
| **7** | `availability` | `com.islandride.availability` | Real-time interval overlap calculation, temporary 10-min inventory holds, maintenance blocks (`inventory_holds`, `vehicle_unavailability`). |
| **8** | `booking` | `com.islandride.booking` | Booking state machine, instant booking, partner approval workflow, pickup/return checklists, odometer/fuel logs (`bookings`, `booking_items`, `booking_status_history`, `handover_records`). |
| **9** | `payment-finance` | `com.islandride.payment` | Payment orders, webhook verification, offline pickup balance recording, customer refunds, gateway transaction tracking (`payments`, `payment_attempts`, `refunds`). |
| **10** | `commission` | `com.islandride.commission` | 4% platform commission calculations, partner net payable statements, weekly disbursement batches (`commissions`, `settlements`, `settlement_items`). |
| **11** | `reviews` | `com.islandride.review` | Verified booking reviews, vehicle ratings, partner ratings, content moderation (`reviews`, `review_reports`). |
| **12** | `notifications` | `com.islandride.notification` | Email/SMS/WhatsApp message dispatch, notification templates, retry delivery log (`notifications`, `notification_templates`, `notification_logs`). |
| **13** | `support-disputes` | `com.islandride.support` | Customer support tickets, dispute arbitration, vehicle damage claims (`support_tickets`, `ticket_messages`, `damage_reports`). |
| **14** | `administration` | `com.islandride.administration` | System configuration parameters, administrative override actions, audit logging (`system_settings`, `audit_logs`). |
| **15** | `analytics` | `com.islandride.analytics` | Marketplace KPI aggregations: GMV, conversion rates, fleet utilization, partner performance. |

---

## 3. Inter-Module Communication Rules

To maintain long-term architectural health and prevent spaghetti dependencies, strict boundary rules are enforced:

### Rule 1: No Cross-Module Repository Access
A module's repository is strictly private to that module. 
*Example:* `BookingService` is **never** permitted to inject `VehicleRepository` directly or query the `vehicles` table via SQL. Instead, it must invoke the public application service interface: `VehicleQueryService.getVehicleForBooking(vehicleId)`.

### Rule 2: Asynchronous Communication via Domain Events
Whenever an action in one module triggers secondary side effects in other modules (e.g. notifications, analytics updates, commission generation), the initiating module publishes an application event via Spring's `ApplicationEventPublisher`.

```
[ BookingModule ] --(publishes BookingConfirmedEvent)--> [ Spring Event Bus ]
                                                                 |
               +-------------------------------------------------+-----------------------------------+
               |                                                 |                                   |
               v                                                 v                                   v
    [ NotificationModule ]                             [ CommissionModule ]                 [ AnalyticsModule ]
  (Dispatches Voucher Email)                         (Computes 4% Commission)             (Updates Daily GMV KPI)
```

### Rule 3: Architectural Fitness Enforcement (ArchUnit)
The repository includes an **ArchUnit test suite** executed during CI builds to automatically fail any pull request that violates package boundaries or introduces cyclic dependencies between modules:
```java
@ArchTest
static final ArchRule no_cross_module_repository_access =
    noClasses().that().resideInAPackage("..com.islandride.booking..")
               .should().dependOnClassesThat()
               .resideInAPackage("..com.islandride.inventory.infrastructure..");
```

