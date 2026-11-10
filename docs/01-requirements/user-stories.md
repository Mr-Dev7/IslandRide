# IslandRide — User Stories and Acceptance Criteria
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Tourist User Stories

### US-TOU-01: Destination & Vehicle Discovery
- **As a** visiting tourist to the Andaman Islands,  
- **I want to** select my destination island (e.g., Havelock Island / Swaraj Dweep) and trip dates/times,  
- **So that** I can view all available rental two-wheelers and cars that I can pick up at my arrival point (e.g., Havelock Jetty).

#### Acceptance Criteria (Gherkin):
```gherkin
Scenario: Tourist searches for available scooters on Swaraj Dweep
  Given the tourist selects destination "Swaraj Dweep (Havelock)"
  And selects pickup date "2026-12-01 09:00" and return date "2026-12-04 18:00"
  And filters by category "Scooter"
  When the tourist submits the search query
  Then the system displays only vehicles located on Swaraj Dweep
  And excludes any vehicle with conflicting bookings, active holds, or maintenance blocks
  And displays the estimated total rental price including base daily rate and mandatory taxes
```

---

### US-TOU-02: Instant Booking with 10-Minute Hold
- **As a** tourist ready to reserve a specific vehicle,  
- **I want to** initiate checkout on an instant-booking listing,  
- **So that** the vehicle is temporarily reserved for 10 minutes while I complete my online payment.

#### Acceptance Criteria:
```gherkin
Scenario: Successful instant booking hold creation
  Given a vehicle has instant booking enabled and is available for the selected dates
  When the tourist clicks "Proceed to Book"
  Then the system creates an inventory hold with status "HELD" expiring in 10 minutes
  And creates a payment order with the exact calculated amount
  And opens the secure payment gateway modal

Scenario: Hold expiration due to abandoned payment
  Given an inventory hold was created at 10:00:00 AM
  When the clock reaches 10:10:01 AM and no verified payment webhook has been received
  Then the system automatically transitions the hold to "EXPIRED"
  And releases the calendar slot back into public availability
```

---

### US-TOU-03: Partial Advance Payment Option
- **As a** tourist planning my travel budget,  
- **I want to** choose between paying the full rental amount or paying a 25% advance online,  
- **So that** I can secure my vehicle while retaining cash liquidity until I inspect the vehicle at pickup.

#### Acceptance Criteria:
```gherkin
Scenario: Booking with partial advance payment
  Given a booking with a total rental cost of ₹3,000 and a 25% advance policy
  When the tourist selects "Pay 25% Advance Online (₹750)"
  And successfully authorizes the payment via the payment gateway
  Then the booking status becomes "CONFIRMED"
  And the system records an online payment ledger entry of ₹750
  And logs an outstanding balance of ₹2,250 marked "PAYABLE_AT_PICKUP"
  And sends a voucher clearly stating that ₹2,250 is due at handover
```

---

### US-TOU-04: Transparent Self-Service Cancellation
- **As a** tourist whose ferry or travel plans have changed,  
- **I want to** cancel my booking from my dashboard,  
- **So that** I can receive an automated refund according to the partner's cancellation policy.

#### Acceptance Criteria:
```gherkin
Scenario: Cancellation 48 hours prior to pickup (Full Refund)
  Given a confirmed booking with pickup at "2026-12-10 10:00"
  And the partner policy offers 100% refund up to 24 hours prior to pickup
  When the tourist cancels the booking at "2026-12-08 09:00"
  Then the booking status updates to "CANCELLED"
  And a gateway refund is queued for the full amount paid
  And the vehicle calendar slot is immediately released for other travelers
```

---

## 2. Partner User Stories

### US-PRT-01: Business Onboarding and Verification Submission
- **As a** local vehicle rental agency owner in Port Blair,  
- **I want to** submit my business details, trade licenses, and bank account information,  
- **So that** the platform admins can verify my business and grant me access to list my fleet.

#### Acceptance Criteria:
```gherkin
Scenario: Partner submits KYC documents for review
  Given a registered partner on the partner portal
  When the partner fills in business details and uploads trade license and PAN proof
  And submits the application
  Then the partner verification status updates to "UNDER_REVIEW"
  And the partner cannot publish live vehicle listings until approved by an admin
```

---

### US-PRT-02: Fleet Inventory & Document Management
- **As a** verified rental partner,  
- **I want to** add a new vehicle with registration details, photos, and compliance documents,  
- **So that** it can be published on the marketplace once approved.

#### Acceptance Criteria:
```gherkin
Scenario: Partner adds a new scooter listing
  Given an approved partner logged into the partner console
  When the partner creates a vehicle with Category "Scooter", Make "Honda", Model "Activa 6G"
  And uploads registration certificate (RC), insurance, and 4 exterior photos
  And sets daily rate to ₹600 and security deposit to ₹500
  Then the vehicle listing is created with status "PENDING_REVIEW"
  And notifies the admin moderation queue
```

---

### US-PRT-03: Approval-Based Booking Queue Management
- **As a** partner who prefers manual scheduling control,  
- **I want to** receive booking approval requests with a countdown timer,  
- **So that** I can verify physical vehicle readiness before accepting the traveler's booking.

#### Acceptance Criteria:
```gherkin
Scenario: Partner accepts a booking request within deadline
  Given a tourist requests a vehicle requiring approval
  And the booking has status "PENDING_PARTNER_APPROVAL" with an expiry of 4 hours
  When the partner reviews the request and clicks "Approve"
  Then the booking status transitions to "APPROVED"
  And the tourist receives an automated notification with a payment link valid for 2 hours
```

---

### US-PRT-04: Digital Pickup Handover & Offline Balance Collection
- **As a** partner counter staff member,  
- **I want to** record start odometer, fuel level, vehicle condition photos, and collect the pickup balance,  
- **So that** both parties have an indisputable digital record of the vehicle's state at handover.

#### Acceptance Criteria:
```gherkin
Scenario: Completing pickup handover with pending balance
  Given a confirmed booking with an outstanding balance of ₹1,500 payable at pickup
  When staff records odometer "12,450 km", fuel "100%", and uploads 4 exterior photos
  And enters offline payment collection: Amount ₹1,500, Method "UPI", Reference "UPI/234891"
  And both staff and customer submit handover confirmation
  Then the booking status transitions to "ACTIVE"
  And the offline payment record is stored in the financial ledger
```

---

## 3. Administrator User Stories

### US-ADM-01: Partner Verification & Compliance Audit
- **As an** Operations Admin,  
- **I want to** review partner legal documents and trade licenses in an approval queue,  
- **So that** only genuine, legally permitted rental businesses operate on IslandRide.

#### Acceptance Criteria:
```gherkin
Scenario: Admin approves a partner application
  Given a partner application in "UNDER_REVIEW" state
  When the admin inspects the uploaded documents and clicks "Approve Partner"
  Then the partner status updates to "APPROVED"
  And an automated onboarding email is dispatched to the partner
  And an entry is recorded in the platform audit log
```

---

### US-ADM-02: Database-Driven Island & Hub Configuration
- **As a** Super Administrator,  
- **I want to** add a new pickup location or island destination via the admin console,  
- **So that** the marketplace can expand to new areas without code modifications or application redeployment.

#### Acceptance Criteria:
```gherkin
Scenario: Admin adds a new pickup location on Swaraj Dweep
  Given an admin with SUPER_ADMIN role
  When the admin creates a new location "Govind Nagar Beach Hub" under island "Swaraj Dweep"
  And defines GPS coordinates and operational hours
  Then the new location immediately becomes selectable in the tourist search filters
```

---

### US-ADM-03: Commission & Partner Payout Settlement
- **As a** Finance Admin,  
- **I want to** calculate weekly partner earnings after deducting the 4% platform commission,  
- **So that** batch bank payouts can be generated and reconciled accurately.

#### Acceptance Criteria:
```gherkin
Scenario: Generating weekly settlement batch
  Given 20 completed bookings for Partner A totaling ₹50,000 gross revenue
  When the admin triggers settlement generation for the preceding week
  Then the system calculates platform commission of ₹2,000 (4%)
  And deducts any online payment gateway processing fees
  And creates a settlement record for Partner A with net payable ₹48,000 in "PENDING_DISBURSEMENT" state
```

