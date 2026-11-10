# IslandRide — Project Risks & Assumptions
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Technical Risks & Mitigation Strategies

### TR-01: Double-Booking Race Conditions During Peak Demand
- **Risk Level:** High
- **Description:** Multiple tourists searching for vehicles concurrently may attempt to book the exact same vehicle for overlapping time intervals during peak tourist season (Nov - Jan).
- **Impact:** Customer arrives at island jetty with a valid voucher only to discover the vehicle has been given to another traveler. Catastrophic damage to platform reputation.
- **Mitigation:** 
  1. Three-tier concurrency architecture: Ephemeral pre-check via Redis -> Pessimistic row locking on MySQL (`SELECT FOR UPDATE`) -> Secondary SQL interval overlap validation within the atomic transaction.
  2. Mandatory multi-threaded integration tests running against Testcontainers simulating 50 concurrent booking attempts for identical slots.

### TR-02: Payment Webhook Latency or Delivery Failure
- **Risk Level:** High
- **Description:** Payment gateway delays sending webhook notification, or a temporary network glitch causes the webhook POST request to fail, occurring *after* the 10-minute hold has expired.
- **Impact:** Customer's bank account is debited, but the platform booking remains unconfirmed or expires.
- **Mitigation:**
  1. Frontend polling / server-sent fallback: If the browser payment SDK returns success, the frontend initiates a backend verification probe (`POST /api/v1/payments/verify`) that directly queries the gateway API.
  2. Late Webhook Exception Workflow: If an authoritative webhook arrives after hold expiration and the vehicle was re-booked, the system marks the transaction as `OVERBOOKED_EXCEPTION` and executes an automatic 100% refund immediately.

### TR-03: Spotty Mobile Internet Connectivity on Andaman Islands
- **Risk Level:** Medium-High
- **Description:** Counter staff at jetty hubs (especially Swaraj Dweep / Shaheed Dweep) may experience intermittent mobile 4G/5G data coverage while attempting to upload condition photos during vehicle handover.
- **Impact:** Delays in customer vehicle release; handover checklist blocked.
- **Mitigation:**
  1. The Angular Handover Console is designed with client-side image compression (downscaling 12MP photos to ~800KB before upload).
  2. Offline-tolerant state handling: The mobile web console caches form inputs in `IndexedDB` and provides an automatic background retry queue when connectivity is restored.

---

## 2. Business & Operational Risks & Mitigation

### BR-01: Weather & Sea Ferry Cancellations
- **Risk Level:** High
- **Description:** Monsoons or cyclonic weather in the Bay of Bengal can cause sudden government suspensions of inter-island passenger ferries between Port Blair, Havelock, and Neil Island.
- **Impact:** Tourists cannot reach the destination island on their scheduled pickup date.
- **Mitigation:**
  1. Platform Policy Clause: Include an explicit "Weather / Ferry Disruption Policy" permitting tourists to reschedule their booking or receive a full credit voucher upon presenting proof of cancelled ferry tickets.
  2. Operations Admin Console provides a one-click "Weather Emergency Reschedule" tool.

### BR-02: Vehicle Damage & Security Deposit Disputes
- **Risk Level:** Medium
- **Description:** Partner claims customer caused a dent or scratch; customer asserts the damage was pre-existing.
- **Impact:** Protracted disputes, chargeback threats, negative reviews.
- **Mitigation:**
  1. Mandatory 4-angle photo checklist at pickup and return.
  2. Both parties receive an immediate digital copy of the pickup inspection report before the vehicle leaves the hub.
  3. Support admins arbitrate disputes using the timestamped, immutable photographic evidence.

---

## 3. Regulatory & Legal Assumptions

1. **Intermediary Status:** IslandRide operates strictly as an electronic marketplace intermediary under Section 79 of the Information Technology Act, 2000. IslandRide does not own the vehicle fleet and is not the employer of partner counter staff.
2. **Permit Compliance:** Partner businesses must hold valid commercial rental licenses (Rent-a-Cab Scheme 1989 / Rent-a-Motorcycle Scheme 1997 where enforced) and valid commercial insurance. IslandRide collects proof of these documents during onboarding, but ultimate legal operating liability rests with the partner.
3. **Tax Compliance:** GST (Goods and Services Tax) on vehicle rental services is configured in accordance with applicable Indian tax rates (e.g., 5% without ITC or 12% with ITC as selected by the partner). Platform commission invoices (18% GST on 4% commission) are generated automatically.
4. **Data Privacy (DPDP Act 2023):** Customer driving license scans and government identity proofs are treated as sensitive digital personal data. They are encrypted at rest, stored in private object storage, accessed only on a need-to-know basis via short-lived signed URLs, and subject to documented retention and deletion schedules.

