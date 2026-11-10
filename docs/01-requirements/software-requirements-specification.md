# IslandRide — Software Requirements Specification (SRS)
**Document Version:** 1.0.0  
**Status:** Approved for Review (Phase 1 Deliverable)  
**Date:** November 2026  
**System Name:** IslandRide — Tourism Vehicle Rental Marketplace  
**Initial Geography:** Andaman & Nicobar Islands, India  

---

## 1. Introduction

### 1.1 Purpose
This Software Requirements Specification (SRS) provides a comprehensive description of the **IslandRide** multi-vendor tourism vehicle-rental marketplace. It specifies the functional and non-functional requirements, external interfaces, performance expectations, and design constraints for the public/tourist portal, partner management portal, and platform administration portal.

### 1.2 Scope of the System
IslandRide is a two-sided digital marketplace connecting independent vehicle rental operators ("Partners") in tourism destinations with travelers ("Tourists"). 
- **Initial Target Market:** The Andaman and Nicobar Islands (e.g., Sri Vijaya Puram / Port Blair, Swaraj Dweep / Havelock, Shaheed Dweep / Neil Island).
- **Core Vehicle Types (MVP):** Two-wheelers (scooters, motorcycles) and four-wheelers (hatchbacks, sedans, SUVs, self-drive rental cars).
- **Extensibility:** The platform's geography, categories, pricing models, and policies are database-driven to enable zero-code expansion into additional Indian tourism territories (e.g., Goa, Leh-Ladakh, Himachal Pradesh).
- **Out of Scope for MVP:** Native mobile applications (iOS/Android), chauffeur-driven cabs, boat rentals, water sports packages, flights/ferry booking, and AI chatbot agents.

### 1.3 Definitions, Acronyms, and Abbreviations
| Term / Acronym | Definition |
|---|---|
| **IslandRide** | The software system, including frontend, backend, database, and infrastructure. |
| **Partner** | A legally registered vehicle rental vendor operating in an authorized locality. |
| **Tourist** | A registered or guest customer searching for and renting vehicles. |
| **Instant Booking** | A booking workflow where confirmation is granted immediately upon successful payment authorization, without requiring prior partner manual approval. |
| **Approval Booking** | A booking workflow where the partner must review and accept the booking request within a specified deadline before payment/confirmation. |
| **Hold** | A temporary lock placed on a vehicle's calendar slot to prevent double-booking while the tourist completes checkout. |
| **Partial Payment** | An advance online payment (token/booking fee) with the remaining rental balance collected offline at vehicle pickup. |
| **Handover Checklist** | An auditable digital record capturing odometer reading, fuel gauge, condition photos, and customer signature at pickup and return. |
| **RBAC** | Role-Based Access Control. |
| **IDOR** | Insecure Direct Object Reference. |
| **RTO / RPO** | Recovery Time Objective / Recovery Point Objective. |

---

## 2. Overall Description

### 2.1 Product Perspective
IslandRide operates as a modular monolith providing REST APIs consumed by a single responsive Angular web application. The application delivers role-tailored user interfaces for tourists, partners, and administrators. 

```
+-------------------------------------------------------------------------------+
|                             Angular SPA Client                                |
|  [ Public / Tourist Portal ]    [ Partner Portal ]    [ Super Admin Portal ]  |
+-------------------------------------------------------------------------------+
                                        | (HTTPS / REST)
+-------------------------------------------------------------------------------+
|                       Nginx Reverse Proxy & SSL Gateway                       |
+-------------------------------------------------------------------------------+
                                        |
+-------------------------------------------------------------------------------+
|                  Spring Boot Modular Monolith (Java LTS)                      |
|  +--------------------+ +--------------------+ +----------------------------+  |
|  |  Identity & Access | | Partner Management | | Availability Engine        |  |
|  +--------------------+ +--------------------+ +----------------------------+  |
|  |  Vehicle Inventory | | Booking Lifecycle  | | Payment & Finance Adapter  |  |
|  +--------------------+ +--------------------+ +----------------------------+  |
|  |  Geography Module  | | Dynamic Pricing    | | Auditing & Support Desk    |  |
|  +--------------------+ +--------------------+ +----------------------------+  |
+-------------------------------------------------------------------------------+
           |                            |                           |
+--------------------+        +--------------------+      +--------------------+
|     MySQL 8.x      |        |     Redis Cache    |      | Private S3 Storage |
| (Durable Database) |        | (Ephemeral Holds)  |      | (Photos/Documents) |
+--------------------+        +--------------------+      +--------------------+
```

### 2.2 User Classes and Personas
1. **Unauthenticated Visitor / Tourist:** Browses destinations, compares vehicle inventory, views pricing breakdowns, and creates bookings.
2. **Authenticated Tourist:** Manages active and past reservations, views pickup instructions, tracks refund statuses, and writes verified reviews.
3. **Partner Owner (`PARTNER_OWNER`):** Legal proprietor of a rental business. Registers business, submits KYC documents, configures locations, adds vehicles, configures prices, views payouts, and appoints staff.
4. **Partner Manager (`PARTNER_MANAGER`):** Operations lead for a partner. Manages fleet availability, approves/rejects booking requests, and schedules maintenance.
5. **Partner Handover Staff (`PARTNER_STAFF`):** Field staff at pickup/return counters. Conducts digital condition checklists, records odometer/fuel readings, and collects pickup balances.
6. **Operations Admin (`OPERATIONS_ADMIN`):** Verifies partner legal documents, inspects and approves vehicle listings, handles re-submissions, and resolves customer complaints.
7. **Finance Admin (`FINANCE_ADMIN`):** Inspects payment transactions, oversees gateway reconciliations, authorizes manual refunds, and reviews partner commission settlements.
8. **Support Agent (`SUPPORT_AGENT`):** Answers support tickets, assists customers with booking issues, and inspects handover disputes.
9. **Super Administrator (`SUPER_ADMIN`):** Has complete platform oversight, manages administrative roles, and updates platform-wide configuration settings.

### 2.3 Operating Environment
- **Server OS:** Ubuntu Linux 22.04 LTS / 24.04 LTS (Containerized via Docker).
- **Runtime:** Java 21 LTS, Spring Boot 3.3+.
- **Database:** MySQL 8.0+ with InnoDB storage engine (configured with `utf8mb4_unicode_ci` and UTC timezone).
- **Cache Engine:** Redis 7.x.
- **Client Devices:** Modern desktop and mobile browsers (Chrome 120+, Safari 16+, Firefox 120+, Edge 120+).
- **Storage Subsystem:** AWS S3 or S3-compatible private storage (MinIO) with pre-signed URL generation.

### 2.4 Design and Implementation Constraints
1. **Monolithic Architecture:** Must be built as a single, well-structured modular monolith. Microservices, distributed streaming brokers (Kafka), and Kubernetes are prohibited for the initial release to minimize operational overhead.
2. **Backend Authority:** The Angular frontend is strictly a presentation layer. It must never calculate prices, validate availability, or declare a booking "paid" or "confirmed".
3. **Financial Precision:** All monetary amounts must be stored in `DECIMAL(10,2)`. Floating point primitives (`float`, `double`) are strictly prohibited in financial code.
4. **Data Isolation:** Complete logical tenant separation between rental partners. No partner may access another partner's fleet, bookings, staff, or financial statements under any circumstance.
5. **Concurrency Protection:** The booking engine must prevent double-booking during concurrent booking requests through database row locking, hold timers, and second availability verification.

---

## 3. Assumptions and Dependencies
- **Payment Gateway:** A sandbox account with a licensed Indian payment gateway (e.g., Razorpay, Cashfree) will be provisioned for webhook testing and transaction settlement verification.
- **Regulatory Compliance:** Partners are solely responsible for ensuring their vehicles have valid commercial transport permits (e.g., Rent-a-Cab Scheme 1989 / Rent-a-Motorcycle Scheme 1997 where applicable in Andaman), commercial insurance, and fitness certificates. IslandRide acts as a technology intermediary platform.
- **Network Resilience:** Because Andaman island territories may experience spotty mobile internet connectivity, the digital vehicle handover feature must be designed with graceful client-side validation and retry capabilities.

