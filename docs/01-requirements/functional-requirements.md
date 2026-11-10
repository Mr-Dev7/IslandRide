# IslandRide — Functional Requirements Specification
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Public & Tourist Portal Requirements

### 1.1 Destination & Location Discovery
- **FR-TOU-101 (Geography Selection):** The system shall allow tourists to choose a destination island (e.g., South Andaman / Port Blair, Havelock Island / Swaraj Dweep, Neil Island / Shaheed Dweep) and specific pickup/return locations (e.g., Veer Savarkar International Airport, Haddo Jetty, Phoenix Bay Jetty, Havelock Jetty).
- **FR-TOU-102 (Trip Window Selection):** The system shall allow tourists to input rental start date/time and return date/time, enforcing a minimum rental duration (default: 4 hours or 1 day) and verifying that return date/time is strictly greater than pickup date/time.
- **FR-TOU-103 (Same/Different Location Return):** The system shall support returns to a different location within the *same island*, subject to partner policy and applicable inter-location transfer fees. Cross-island vehicle transfers shall be rejected by the system.

### 1.2 Vehicle Search & Filtering
- **FR-TOU-104 (Category Filter):** The system shall allow filtering by vehicle category (e.g., Scooter, Commuter Motorcycle, Cruiser, Compact Hatchback, Sedan, SUV).
- **FR-TOU-105 (Attribute Filters):** The system shall allow filtering by transmission type (Automatic vs. Manual), fuel type (Petrol, Electric, Diesel), seating capacity (2-seater, 4-seater, 5-seater, 7-seater), and minimum user rating (1 to 5 stars).
- **FR-TOU-106 (Dynamic Pricing Display):** Search results shall display the estimated total rental cost calculated server-side, inclusive of estimated taxes, delivery fees, and security deposit requirements.
- **FR-TOU-107 (Sorting):** The system shall allow sorting search results by Price: Low to High, Price: High to Low, Top Rated, and Most Popular.

### 1.3 Vehicle Details & Partner Transparency
- **FR-TOU-108 (Gallery & Specifications):** The system shall display verified high-resolution photographs, vehicle specifications (make, model, manufacturing year, engine CC/range), and key amenities (helmets provided, USB charger, phone mount, boot space).
- **FR-TOU-109 (Partner Profile):** The vehicle page shall display the verified partner name, aggregate partner rating, total trips completed, and operating hub address.
- **FR-TOU-110 (Policy Transparency):** The system shall clearly present cancellation terms, fuel policy (same-to-same), excess kilometer charges, security deposit amount, and document requirements (valid Indian/International Driving Permit).

### 1.4 Booking & Payment Execution
- **FR-TOU-111 (Availability Check):** Prior to booking creation, the system shall execute an authoritative backend check to confirm the vehicle has no overlapping bookings, active holds, or maintenance blocks for the requested window.
- **FR-TOU-112 (Booking Mode Flow):**
  - **Instant Booking:** If the listing has instant booking enabled, the system creates an inventory hold and directly initiates the payment order.
  - **Approval Booking:** If the listing requires partner approval, the system creates a pending approval record with a partner notification and an approval deadline (e.g., 4 hours).
- **FR-TOU-113 (Payment Flexibility):**
  - **Full Online Payment:** Tourist pays 100% of rental, taxes, delivery fee, and optional deposit online.
  - **Partial Advance Payment:** Tourist pays a configured advance percentage (e.g., 25%) online via payment gateway. The remaining balance is earmarked for offline collection at pickup.
- **FR-TOU-114 (Hold Timer):** During online checkout, an inventory hold shall protect the vehicle for exactly 10 minutes. If payment verification is not received within 10 minutes, the hold expires and the slot is released.

### 1.5 Tourist Account & Booking Management
- **FR-TOU-115 (Booking Voucher):** Upon payment verification, the system shall generate a unique Booking Reference Number, an itemized tax invoice, and a downloadable PDF rental voucher with QR verification.
- **FR-TOU-116 (Booking Cancellation):** Tourists can initiate self-service cancellation through their dashboard. The system shall calculate the refund amount automatically based on the partner's tiered cancellation policy and schedule the gateway refund.
- **FR-TOU-117 (Verified Reviews):** Only tourists with a booking status of `COMPLETED` shall be permitted to submit a rating (1-5 stars) and a written review for the vehicle and partner.

---

## 2. Partner Management Portal Requirements

### 2.1 Partner Onboarding & Business Profile
- **FR-PRT-201 (Self-Registration):** Rental business owners can register an account specifying business legal name, trade name, GSTIN (if registered), trade license/panchayat permit, and contact details.
- **FR-PRT-202 (KYC Document Submission):** Partners must upload business proof (e.g., GST certificate, MSME Udyam, shop establishment license), proprietor ID proof (Aadhaar / PAN), and bank account cancel cheque for payouts.
- **FR-PRT-203 (Operating Hubs):** Partners can configure one or more physical pickup/return stations per island, specifying operating hours (e.g., 07:00 AM – 08:00 PM), contact numbers, and geo-coordinates.

### 2.2 Fleet Inventory Management
- **FR-PRT-204 (Vehicle Creation):** Partners can add vehicles specifying category, make, model, variant, year, transmission, fuel, seating capacity, and registration number (number plate).
- **FR-PRT-205 (Document Compliance):** For every vehicle, partners must upload registration certificate (RC), commercial insurance policy, and pollution under control (PUC) certificate with expiry dates.
- **FR-PRT-206 (Photo Management):** Partners can upload up to 10 photos per vehicle (exterior 360, interior/console, odometer). Files are validated for image MIME types and optimized in private S3 storage.
- **FR-PRT-207 (Listing Status):** Vehicles progress through states: `DRAFT` -> `PENDING_REVIEW` -> `ACTIVE` -> `UNDER_MAINTENANCE` -> `SUSPENDED` -> `RETIRED`.

### 2.3 Pricing & Calendar Control
- **FR-PRT-208 (Tiered Rate Configuration):** Partners can define base daily rates, hourly rates (for excess duration), weekend surcharges, and festive/peak seasonal pricing rules (e.g., Andaman peak tourist season: Nov 15 – Jan 15).
- **FR-PRT-209 (Fees & Deposits):** Partners can configure security deposit amounts (collected online or in cash at pickup), out-of-station delivery fees, and late return penalty rates.
- **FR-PRT-210 (Availability Calendar):** Partners can view a calendar of bookings and manually block date/time ranges for preventive maintenance, private use, or repairs (`vehicle_unavailability`).

### 2.4 Booking Execution & Handover
- **FR-PRT-211 (Approval Queue):** For listings requiring partner approval, partners receive instant notifications and can accept or reject requests with a mandatory reason before the approval timer expires.
- **FR-PRT-212 (Pickup Handover Console):**
  - Partner staff inspects tourist's original driving license and matches name.
  - Staff records start odometer reading and fuel level percentage.
  - Staff uploads 4 mandatory condition photos (front, rear, left, right) capturing pre-existing scratches/dents.
  - If a balance or deposit is due, staff enters the collected offline amount (Cash / UPI) and transaction reference.
  - Both parties digitally confirm handover, transitioning booking to `ACTIVE`.
- **FR-PRT-213 (Return Handover Console):**
  - Staff records ending odometer and fuel level.
  - Staff checks for new damages and can create a `damage_report` with photographic evidence.
  - System calculates excess kilometers, fuel shortage charges, or late fees if applicable.
  - Security deposit refund or deduction is recorded.
  - Booking transitions to `COMPLETED`.

### 2.5 Partner Financials & Staff Administration
- **FR-PRT-214 (Earnings Ledger):** Partners can view itemized earnings per booking, showing gross rental, platform commission deduction (e.g., 4%), gateway charges, and net payable.
- **FR-PRT-215 (Settlement Tracking):** Partners can track weekly payout batches, UTR/bank reference numbers, and download monthly GST commission invoices.
- **FR-PRT-216 (Staff Access Delegation):** Partner owners can invite and manage sub-accounts (`PARTNER_MANAGER`, `PARTNER_STAFF`), restricting staff permissions to handover operations and calendar viewing while hiding financial bank details.

---

## 3. Platform Administration Portal Requirements

### 3.1 Partner & Fleet Moderation
- **FR-ADM-301 (Partner Verification Queue):** Admins can inspect submitted business documents, verify trade license authenticity, and either approve the partner account or reject it with actionable feedback.
- **FR-ADM-302 (Listing Moderation):** Admins can inspect vehicle listings, registration plates, RC details, and photo quality before granting the listing `ACTIVE` status on the public marketplace.
- **FR-ADM-303 (Suspension & Blacklisting):** Admins can suspend or blacklist partners or individual vehicles in case of safety violations, customer fraud, or expired commercial permits.

### 3.2 Booking Operations & Dispute Resolution
- **FR-ADM-304 (Global Booking Lookup):** Admins can search and filter all platform bookings by status, reference code, tourist name, partner, island, or date range.
- **FR-ADM-305 (Manual Exception Handling):** Admins can execute manual interventions: extending hold deadlines, initiating partner reassignment in vehicle breakdown scenarios, or overriding booking states with mandatory audit notes.
- **FR-ADM-306 (Dispute Arbitration):** When a tourist disputes damage charges or unreturned security deposits, support admins can inspect handover photos/odometer logs and enforce resolutions.

### 3.3 Financial Governance & Platform Settings
- **FR-ADM-307 (Refund Governance):** Finance admins can review and authorize pending refund queues, trigger automated gateway refund API calls, and audit failed webhook retries.
- **FR-ADM-308 (Commission Engine):** Admins can adjust the global platform commission rate or set partner-specific promotional commission overrides.
- **FR-ADM-309 (Payout Settlement Execution):** Finance admins can generate weekly settlement batches, export payout files formatted for bank NEFT/RTGS processing, and record bank disbursement UTRs.
- **FR-ADM-310 (Geography Management):** Admins can dynamically configure countries, states, islands, localities, and pickup hubs without modifying code or redeploying the application.
- **FR-ADM-311 (Audit Trail):** Every administrative write action (status overrides, refund authorizations, rate changes, user suspensions) is immutably logged with actor ID, IP address, timestamp, previous state, and new state.

