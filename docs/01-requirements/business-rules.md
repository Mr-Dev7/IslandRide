# IslandRide — Business Rules & Policies
**Document Version:** 1.1.0  
**Phase:** 1 (Requirements & System Design — Post-Review Revisions)  
**Status:** Approved for Implementation  

---

## 1. Booking State Machine & Transition Rules

The lifecycle of every booking is governed by a deterministic, server-enforced state machine. Direct state manipulation is forbidden; every transition must occur through an authorized domain event. For the MVP, **each booking is strictly for one vehicle**. Multi-vehicle group bookings are deferred to post-MVP.

```
+---------------------------------------------------------------------------------------------------+
|                                      BOOKING STATE MACHINE                                        |
+---------------------------------------------------------------------------------------------------+

       [ Tourist Submits Request ]
                    |
       +------------+------------+
       |                         |
 (Approval Mode)          (Instant Mode)
       |                         |
       v                         |
[ PENDING_PARTNER_APPROVAL ]     |
       |                         |
       +---> [ REJECTED ]        |
       |     (Partner declines)  |
       |                         |
       +---> [ EXPIRED ]         |
       |     (Configurable timer)|
       |                         |
       v                         v
[ APPROVED ] ------------> [ PENDING_PAYMENT ]
                                 |
                                 +---> [ PAYMENT_FAILED ] / [ EXPIRED ]
                                 |     (Hold timer / Gateway fail)
                                 v
                           [ CONFIRMED ]  <-- Authoritative webhook verified
                                 |
                                 +---> [ CANCELLED ] (Prior to pickup)
                                 v
                         [ PICKUP_PENDING ]
                                 |
                                 v
                             [ ACTIVE ]    <-- Pickup handover completed & balance collected
                                 |
                                 v
                         [ RETURN_PENDING ]
                                 |
                                 +---> [ DISPUTED ] (Damage / deposit deduction claim)
                                 v
                            [ COMPLETED ]  <-- Return inspection passed & deposit settled
                                 |
                                 v
                            [ REFUNDED ]   <-- Gateway refund reconciled / deposit returned
```

### Valid Transition Matrix
| Current State | Target State | Triggering Event | Authorized Actor |
|---|---|---|---|
| `DRAFT` | `PENDING_PARTNER_APPROVAL` | Request Approval Booking | Tourist |
| `DRAFT` | `PENDING_PAYMENT` | Request Instant Booking | Tourist |
| `PENDING_PARTNER_APPROVAL` | `APPROVED` | Partner Accepts Request | Partner Owner / Manager |
| `PENDING_PARTNER_APPROVAL` | `REJECTED` | Partner Declines Request (with reason) | Partner Owner / Manager |
| `PENDING_PARTNER_APPROVAL` | `EXPIRED` | Approval Timer Expires (configurable) | System Scheduler |
| `APPROVED` | `PENDING_PAYMENT` | Checkout Started | Tourist |
| `PENDING_PAYMENT` | `CONFIRMED` | Authoritative Webhook Verified | Payment Adapter |
| `PENDING_PAYMENT` | `EXPIRED` | Hold Timer Expires (configurable) | System Scheduler |
| `PENDING_PAYMENT` | `PAYMENT_FAILED` | Gateway Payment Failed | Payment Adapter |
| `CONFIRMED` | `CANCELLED` | Tourist Cancels Booking | Tourist / Operations Admin |
| `CONFIRMED` | `PICKUP_PENDING` | Pickup Date Arrival (T-N hours, configurable) | System Scheduler |
| `PICKUP_PENDING` | `ACTIVE` | Pickup Handover Checklist & Balance Recorded | Partner Staff |
| `ACTIVE` | `RETURN_PENDING` | Scheduled Return Time | System Scheduler |
| `RETURN_PENDING` | `COMPLETED` | Return Inspection & Deposit Cleared | Partner Staff |
| `RETURN_PENDING` | `DISPUTED` | Unresolved Damage or Deposit Claim | Partner Staff / Tourist |
| `DISPUTED` | `COMPLETED` | Admin Dispute Arbitration Resolution | Operations / Support Admin |
| `CANCELLED` | `REFUNDED` | Gateway Refund Reconciled via Webhook/Batch | Finance Admin / System |

---

## 2. Inventory Holds & Concurrency Rules

### BR-HLD-01 (Configurable Hold Duration & Durable MySQL Persistence)
- When a tourist proceeds to checkout, the system creates a durable inventory hold record in the MySQL `inventory_holds` table.
- **Hold Duration:** Configurable via system configuration (proposed default: **10 minutes**).
- **Dual Coordination:** Redis is used for ephemeral coordination, low-latency TTL countdowns, and cache invalidation. **MySQL remains the durable source of truth**; if Redis restarts, the hold remains safely recorded in MySQL.
- If verified payment is received before hold expiry, the hold record transitions to `CONVERTED`.
- If the hold timer expires without payment, a scheduled worker marks the MySQL record `EXPIRED` and invalidates the Redis key, returning the calendar slot to public availability.

### BR-HLD-02 (Interval Overlap Concurrency Prevention)
- Availability is mathematically evaluated against the condition that for any requested interval $[T_{start}, T_{end})$:
  $$\forall \text{ existing interval } [E_{start}, E_{end}): \quad (T_{start} < E_{end}) \land (T_{end} > E_{start}) = \text{FALSE}$$
- During the booking confirmation transaction, the system applies **pessimistic row locking** (`SELECT id FROM vehicles WHERE id = :id FOR UPDATE`) and executes a secondary validation query inside the transaction to eliminate race conditions between concurrent requests.

### BR-HLD-03 (Late Webhook Arrival & Asynchronous Refund Reconciliation)
- If a payment gateway webhook confirms success *after* a hold has expired:
  1. The system checks if the vehicle remains free for the requested interval.
  2. If free, the system creates a new hold and safely confirms the reservation.
  3. If another user has booked the vehicle in the interim, the system flags the booking as `OVERBOOKED_EXCEPTION`, alerts Operations Admin, and initiates an **asynchronous, idempotent refund workflow with automated retries and gateway status reconciliation**. The system does not assume gateway refunds always succeed immediately.

---

## 3. Partner Approval Expiry Rules

### BR-APP-01 (Configurable Approval Time Windows)
- Bookings requiring partner approval are governed by configurable deadlines:
  - Default proposed tiers:
    - If pickup is $> 48$ hours away: Approval window is **6 hours** (configurable).
    - If pickup is between $12$ and $48$ hours away: Approval window is **2 hours** (configurable).
    - If pickup is $< 12$ hours away: Approval window is **45 minutes** (configurable).
- All deadlines are stored in `system_settings` and can be adjusted globally or per partner agreement.
- If a partner fails to respond before the deadline, the booking transitions to `EXPIRED`, the tourist is notified, and alternative vehicles are recommended.

---

## 4. Payment, Advance & Financial Ledger Rules

### BR-PAY-01 (Full vs. Partial Advance Payment)
- **Full Online Payment:** The tourist pays 100% of the calculated total (Rental + Taxes + Delivery Fee + Security Deposit).
- **Partial Advance Payment:**
  - The advance payment percentage is **configurable per partner or platform policy** (proposed default: **25%**).
  - The remaining balance is flagged as `pickup_balance_due` with status `PAYABLE_AT_PICKUP`.
  - At handover, partner staff collects the balance via Cash or UPI and enters the collection method, amount, and transaction reference in the Handover Console before vehicle release.

### BR-PAY-02 (Security Deposit Management & Dispute Inspection)
- Security deposits are strictly classified as **trust liabilities**, never as platform revenue or partner earnings.
- **Deposit Release Gate:** Security deposits are not released automatically without inspection. Upon vehicle return, the partner staff must complete the return handover inspection. 
- If no damages are documented within the inspection window (configurable, default: 24 hours), the deposit refund is initiated.
- If damage is reported, the deposit is held in `DISPUTED` status pending customer acknowledgment or administrative dispute arbitration.

### BR-PAY-03 (Platform Commission & Settlement Scheduling)
- Platform commission is a **configurable parameter** (proposed starting rate: **4.0%**, subject to commercial agreements and partner tiers).
- Settlement frequency is **configurable** (e.g., weekly batch disbursement or T+3 rolling payout).
- Payout calculation:
  $$\text{Partner Payout} = \text{Gross Rental} - \text{Platform Commission} - \text{Gateway Fees} - \text{Collected Advance Deductions}$$

---

## 5. Cancellation & Refund Rules

### BR-CAN-01 (Configurable & Versioned Cancellation Policies)
Partners may select from version-controlled cancellation policy templates configured on the platform (e.g., Flexible, Moderate, Strict). All policy terms, thresholds, and penalty percentages are stored as configuration records:
- **Proposed Moderate Template (Default Proposal):**
  - $> 24$ hours prior to pickup: 100% rental refund.
  - $< 24$ hours prior to pickup: Advance forfeited / cancellation fee applied.
  - Security deposits are always 100% refunded on cancellation prior to vehicle pickup.
- Every booking captures a **snapshot of the active cancellation policy version** at the time of reservation creation, ensuring that subsequent policy changes do not alter historical rights.

### BR-CAN-02 (Refund Failure Handling & Reconciliation)
- When a refund is initiated, the system creates a `refunds` ledger record in `PENDING` state.
- The refund worker issues the gateway refund API call with an idempotency key.
- If the gateway returns an error or times out, the system marks the record `RETRY_SCHEDULED` and initiates exponential backoff retries.
- A nightly reconciliation job queries the gateway API to match gateway refund statuses with internal ledger records.

---

## 6. Location, Island & Logistics Policies

### BR-GEO-01 (Operating Island & Inter-Island Transfer Policies)
- Inter-island vehicle movement is governed by **configurable partner and location operating policies**, subject to local ferry transport rules and commercial permits.
- Proposed launch policy: Vehicles must be returned to an authorized return station on their registered operating island. Cross-island returns are disabled by default unless a partner explicitly enables an inter-island transfer package with validated ferry documentation.

### BR-GEO-02 (Configurable Turnaround Buffer Times)
- To allow for cleaning, mechanical inspection, and refueling between consecutive rentals, the platform enforces a **configurable turnaround buffer** (proposed default: **60 minutes**).
- Buffers can be adjusted by vehicle category (e.g., 30 minutes for scooters; 90 minutes for cars) or per partner policy.

---

## 7. Tax, GST & Partner Document Compliance

### BR-TAX-01 (Configurable Tax & Compliance Verification)
- Tax applicability, GST rates (e.g., 5% without ITC or 12% with ITC), and trade license requirements are treated as **configurable compliance parameters subject to formal legal and accounting validation** in the Andaman & Nicobar Union Territory.
- Document requirements (e.g., Trade License, MSME Udyam, Rent-a-Cab Scheme permit, Commercial Insurance, PAN) are configured per partner business entity type (proprietorship, partnership, private limited) and verified during onboarding.
