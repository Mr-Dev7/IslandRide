# IslandRide — Open Decisions Requiring Product Owner Approval
**Document Version:** 1.1.0  
**Phase:** 1 (Requirements & System Design — Post-Review Revisions)  
**Status:** Updated with Configurable Policy Proposals  

---

The following decisions are captured as **configurable proposals** and subject to administrative override rather than hardcoded rules:

---

### Decision 1: Primary Payment Gateway Adapter for Indian Launch
- **Context:** Integration of an Indian payment gateway adapter for processing cards, NetBanking, and UPI payments.
- **Options:**
  - **Option A (Proposed Starting Adapter):** **Razorpay** — Standard in India, comprehensive webhook signatures, and mature Java SDK.
  - **Option B:** **Cashfree** — Strong payout capabilities for partner settlements.
  - **Option C:** **Local Mock Gateway** — For isolated offline tests and CI builds.
- **Decision:** Implement Option A (Razorpay adapter) with Option C (Local mock adapter) for offline builds.

---

### Decision 2: Partial Online Advance Payment Ratio
- **Context:** The advance payment percentage required to reserve a vehicle online.
- **Options:**
  - **Option A (Proposed Default):** **25% Advance** — Configurable globally or per partner listing via `partner_policies.advance_percentage`.
  - **Option B:** **Full Payment Only (100%)**.
  - **Option C:** **Tiered Advance** based on total trip value.
- **Status:** **Configurable Proposal**. The default is set to 25%, with system configuration permitting 0% to 100% adjustments.

---

### Decision 3: Temporary Inventory Hold Duration
- **Context:** Time window during checkout that an inventory hold protects a slot from being booked by another traveler.
- **Options:**
  - **Option A (Proposed Default):** **10 Minutes** — Stored durably in MySQL `inventory_holds` with Redis coordination.
  - **Option B:** **15 Minutes**.
  - **Option C:** **Configurable per vehicle category**.
- **Status:** **Configurable Proposal**. Default is set to 10 minutes in `system_settings.hold_duration_seconds`.

---

### Decision 4: Turnaround Buffer Time Between Rentals
- **Context:** Minimum gap required between the return of one reservation and the pickup of the next reservation for cleaning and refueling.
- **Options:**
  - **Option A (Proposed Default):** **60 Minutes**.
  - **Option B:** **Category-Specific Buffer** (e.g. 30 mins for scooters, 90 mins for SUVs).
- **Status:** **Configurable Proposal**. Managed via `vehicles.turnaround_buffer_minutes`.

---

### Decision 5: Marketplace Platform Commission Rate
- **Context:** Revenue share percentage retained by IslandRide from gross rental fees.
- **Options:**
  - **Option A (Proposed Default):** **4.0% Platform Commission**.
  - **Option B:** Tiered commission based on partner fleet volume (e.g., 5% for <5 vehicles, 3.5% for >15 vehicles).
- **Status:** **Configurable Proposal**. Defined in `businesses.commission_rate` with a platform default of 4.0%.

---

### Decision 6: Security Deposit Collection & Inspection Workflow
- **Context:** Management and return of customer security deposits.
- **Status:** **Approved Architecture**. Deposits are ring-fenced in a liability ledger. Return is gated by the Return Handover inspection and damage clearance.

---

### Decision 7: Tax, GST & Partner Legal Validation
- **Context:** GST rate applicability (5% vs 12%) and partner document mandates (Rent-a-Cab Scheme 1989, commercial insurance, MSME Udyam).
- **Status:** **Pending Local Validation**. Configurable fields in `businesses` and `vehicles` modules; rates and document checklists to be formally confirmed with local Andaman & Nicobar legal/tax advisors prior to commercial launch.
