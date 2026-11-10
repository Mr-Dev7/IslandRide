# IslandRide — Page Functional Requirements & Wireframe Specs
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Public Search & Catalog Page (`/search`)

### 1.1 Layout Grid & Components
- **Top Sticky Bar:** Destination Island dropdown (South Andaman, Swaraj Dweep, Shaheed Dweep), Pickup Station, Return Station (same island), Date/Time pickers, and "Update Search" button.
- **Left Sidebar Filter (Desktop) / Slide-over Drawer (Mobile):**
  - Category Checkboxes: Scooters, Commuter Bikes, Royal Enfield / Cruisers, Hatchbacks, Sedans, SUVs.
  - Transmission Radios: All, Automatic, Manual.
  - Booking Mode: All, Instant Confirmation Only.
  - Price Range Slider: ₹400/day to ₹5,000/day.
- **Main Catalog Grid:** Responsive grid (3 columns on desktop, 1 column on mobile).
  - Vehicle Card Elements: Thumbnail image carousel, Category badge, Make/Model name, Fuel/Transmission pills, Star rating with review count, Partner verification badge, Price per day (prominent), Estimated total price (muted), "View Details" primary button.

### 1.2 UX States
- **Loading State:** Shimmer skeleton placeholders matching vehicle card dimensions.
- **Empty State:** Clean illustration with message: *"No vehicles found on [Island] for the selected dates. Try adjusting your dates or expanding category filters."* Includes a "Clear Filters" button.
- **Error State:** Alert banner: *"Unable to load listings. Please check your internet connection and retry."* with a "Retry" button.

---

## 2. Vehicle Details Page (`/vehicles/:slug`)

### 2.1 Functional Zones
- **Zone 1: Gallery Carousel:** High-resolution exterior 360 viewer, interior/dashboard, and luggage compartment photos.
- **Zone 2: Specs & Highlights:** Engine displacement, power, fuel efficiency, seating capacity, helmets provided (complimentary 1 or 2 helmets for bikes), USB charger.
- **Zone 3: Partner Trust Card:** Partner business name, verification shield, operating hub address on map, operating hours (e.g. 07:00 AM – 08:00 PM).
- **Zone 4: Rental Terms & Policies:** Cancellation policy badge (Flexible / Moderate / Strict), fuel policy ("Same-to-Same: Return with equal fuel level"), security deposit requirement, excess km charges.
- **Zone 5: Booking Action Widget (Sticky on Mobile):**
  - Selected trip duration summary.
  - Itemized cost breakdown (Base rental, GST, Security deposit, Total payable).
  - Booking Mode Indicator: "⚡ Instant Confirmation" or "🕒 Partner Approval Required (Within 2 Hours)".
  - Primary CTA: "Proceed to Checkout".

---

## 3. Checkout Page (`/checkout/:bookingRef`)

### 3.1 Functional Elements & Security Guardrails
- **Inventory Hold Countdown Timer:** Prominent sticky bar at top: *"⏳ Vehicle held for you: 08:42 remaining. Complete payment to secure your booking."*
  - When timer reaches 00:00: Screen smoothly transitions to an expired hold overlay with a button: *"Check Availability Again"*.
- **Payment Mode Selector:**
  - Option A: *Pay Full Online Amount (₹2,390)* — Includes ₹1,800 rental + ₹90 GST + ₹500 refundable deposit.
  - Option B: *Pay 25% Advance Online (₹972.50)* — Pay ₹472.50 advance + ₹500 deposit now; pay ₹1,417.50 balance at vehicle pickup.
- **Customer Details Form:** Full name (as on Driving License), mobile number (with OTP verification), email for voucher delivery, emergency contact.
- **Driving License Declaration:** Checkbox: *"I confirm I possess a valid physical Driving License for this vehicle class and will present it at pickup."*

---

## 4. Partner Handover Console (`/partner/handover/:bookingRef`)

### 4.1 Mobile-First Counter Staff Console
Designed specifically for mobile browsers used by field counter staff at jetties and airport hubs:
- **Header:** Booking Reference Number, Customer Name, Vehicle Number Plate, Scheduled Trip Dates.
- **Step 1 (Identity Check):** Staff checks checkbox confirming original physical Driving License matches the booking name.
- **Step 2 (Meter Readings):**
  - Start Odometer input (integer km with photo upload of dashboard instrument cluster).
  - Fuel Level selector (0%, 25%, 50%, 75%, 100% slider).
- **Step 3 (Pre-existing Condition Inspection):**
  - 4 mandatory photo capture slots: Front bumper/wheel, Rear, Left flank, Right flank.
  - Text area for noting existing minor scratches/dents.
- **Step 4 (Offline Balance Collection):**
  - If booking has a pending pickup balance: Staff selects payment method received ("Cash" or "UPI QR"), enters amount collected, and inputs the UPI Transaction Reference / Receipt Number.
- **Step 5 (Digital Sign-off):**
  - Customer signature canvas or SMS OTP sign-off.
  - Green button: *"Complete Handover & Release Vehicle"*.

---

## 5. Admin Partner Verification Queue (`/admin/partners`)

### 5.1 Document Review Screen
- **Split-Screen Layout:**
  - Left Panel: Business Profile, Proprietor Name, GSTIN, PAN, Bank IFSC/Account, Registered Address.
  - Right Panel: High-resolution PDF/image previewer with zoom/pan controls displaying uploaded Trade License, GST Certificate, and Proprietor ID.
- **Action Controls:**
  - Green Button: *"Approve Partner & Enable Fleet Listing"*.
  - Red Button: *"Reject Application"*.
    - Opens modal with predefined rejection checkboxes (Blurry document, Expired license, Invalid GSTIN, Missing bank proof) and a custom feedback message box sent directly to the partner.

