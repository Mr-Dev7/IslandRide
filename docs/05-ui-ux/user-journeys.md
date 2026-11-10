# IslandRide — User Journey Maps
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Tourist Rental Journey: Discover to Return

```
[ Step 1: Search ] ----> [ Step 2: Compare ] ----> [ Step 3: Reserve & Pay ]
                                                               |
[ Step 6: Review ] <---- [ Step 5: Return ] <----- [ Step 4: Pickup Handover ]
```

### Detailed Journey Progression
1. **Search & Location Selection:**
   - *Goal:* Find a self-drive scooter on Havelock Island (Swaraj Dweep) arriving via private ferry at 10:00 AM.
   - *Screen:* Landing Page / Search Bar (`/search`).
   - *Action:* Enters Island ("Swaraj Dweep"), Pickup Point ("Havelock Jetty"), Dates ("Dec 10, 10:00 AM - Dec 13, 05:00 PM").
   - *System Response:* Displays 18 available scooters, sorted by price. All vehicles shown have verified availability.
2. **Vehicle Evaluation & Price Breakdown:**
   - *Screen:* Vehicle Details (`/vehicles/honda-activa-6g-havelock-1`).
   - *Action:* Inspects vehicle photos, partner rating (4.8/5 based on 62 rentals), and security deposit terms (₹500 refundable).
   - *System Response:* Calculates itemized pricing on backend: Rental (₹1,800) + GST (₹90) + Security Deposit (₹500) = ₹2,390 Total.
3. **Checkout & Advance Payment:**
   - *Screen:* Checkout (`/checkout/IR-202611-9842`).
   - *Action:* Selects "Pay 25% Advance Online (₹472.50) + Deposit (₹500) = ₹972.50. Pay balance ₹1,417.50 at pickup".
   - *System Response:* Locks vehicle for 10 minutes via `inventory_holds`. Opens Razorpay checkout modal. Tourist pays via UPI.
4. **Voucher Generation & Arrival:**
   - *Screen:* Booking Success (`/booking-success/IR-202611-9842`).
   - *Action:* Tourist downloads PDF rental voucher with QR code and station contact details.
5. **Physical Pickup Handover:**
   - *Location:* Partner counter outside Havelock Jetty.
   - *Action:* Tourist presents voucher QR code and original driving license. Staff scans vehicle for scratches, records odometer (8,410 km) and fuel (100%). Tourist pays ₹1,417.50 balance via UPI QR.
   - *System Response:* Staff confirms handover; booking status transitions to `ACTIVE`. SMS confirmation sent.
6. **Return Handover & Deposit Clearance:**
   - *Location:* Havelock Jetty station at trip end.
   - *Action:* Staff checks vehicle; no damages; fuel returned full (100%); ending odometer (8,580 km).
   - *System Response:* Booking transitions to `COMPLETED`. The ₹500 security deposit is released automatically back to tourist's bank account.
7. **Verified Review Submission:**
   - *Screen:* Tourist Dashboard (`/tourist/reviews`).
   - *Action:* Tourist gives 5 stars and writes: "Smooth pickup right outside the jetty, clean helmet, scooter in top mechanical condition."

---

## 2. Partner Onboarding & Listing Journey

1. **Self-Registration:** Partner owner visits `/auth/register` and chooses "List Your Fleet". Enters business name ("Island Scooters Havelock"), mobile, and email.
2. **KYC & Bank Setup:** In `/partner/onboarding`, uploads Trade License, GST Certificate, PAN, and Bank Cancelled Cheque for weekly NEFT payouts. Status moves to `UNDER_REVIEW`.
3. **Admin Verification:** Operations admin inspects submitted licenses on admin queue. Verifies registration authenticity. Clicks "Approve Partner". Partner status becomes `APPROVED`.
4. **Vehicle Creation:** Partner visits `/partner/fleet/new`. Inputs scooter details: "Honda Activa 6G (2023)", registration number "AN-01-M-4821", uploads RC copy and insurance policy.
5. **Pricing Setup:** Sets base rate: ₹600/day. Adds peak season rule (Dec 15 - Jan 05: ₹900/day). Sets ₹500 refundable security deposit.
6. **Marketplace Publication:** Listing passes automated validation and is approved for instant booking.

---

## 3. Operations Admin Dispute Resolution Journey

1. **Dispute Trigger:** Customer disputes a ₹1,200 damage deduction made by a partner staff member at vehicle return.
2. **Inspection in Admin Console:** Admin opens `/admin/support` and selects Dispute #841.
3. **Evidence Comparison:** Admin reviews the Pickup Handover photos (uploaded at pickup) alongside Return Handover photos (uploaded at return).
4. **Findings:** The scratch on the left exhaust shield was already clearly visible in Pickup Photo #3.
5. **Enforcement Action:** Admin overrides the deduction, marks the dispute resolved in favor of the customer, and triggers an immediate ₹1,200 refund to the customer while issuing an official warning to the partner.

