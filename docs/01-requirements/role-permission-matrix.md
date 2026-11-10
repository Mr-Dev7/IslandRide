# IslandRide — Role & Permission Matrix
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. System Roles Overview

The IslandRide platform defines eight distinct roles across three principal user categories:
1. **Public / Consumer Portal:** `TOURIST`
2. **Partner Management Portal:** `PARTNER_OWNER`, `PARTNER_MANAGER`, `PARTNER_STAFF`
3. **Administration Portal:** `OPERATIONS_ADMIN`, `FINANCE_ADMIN`, `SUPPORT_AGENT`, `SUPER_ADMIN`

Authorization is enforced server-side through a combination of **Role-Based Access Control (RBAC)** and **Resource Ownership Checks (Tenant Isolation)**.

---

## 2. Granular Permission Matrix

| Capability / API Operation | TOURIST | PARTNER_OWNER | PARTNER_MANAGER | PARTNER_STAFF | OPERATIONS_ADMIN | FINANCE_ADMIN | SUPPORT_AGENT | SUPER_ADMIN |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Public & Tourist Domain** | | | | | | | | |
| Browse Destinations & Vehicle Catalog | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Check Real-time Vehicle Availability | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Create Booking (Instant / Approval Request) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| View Own Bookings & Download Vouchers | ✅ (Own) | ❌ | ❌ | ❌ | ✅ (All) | ✅ (All) | ✅ (All) | ✅ (All) |
| Cancel Own Booking & Request Refund | ✅ (Own) | ❌ | ❌ | ❌ | ✅ (Override) | ❌ | ✅ (Assisted) | ✅ (All) |
| Submit Verified Review for Completed Trip | ✅ (Own) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Create Support Ticket | ✅ (Own) | ✅ (Own) | ✅ (Own) | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Partner Management Domain** | | | | | | | | |
| Register Business & Upload KYC Documents | ❌ | ✅ (Own) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Manage Partner Hubs & Operating Hours | ❌ | ✅ (Own) | ✅ (Own) | ❌ | ✅ (Audit) | ❌ | ❌ | ✅ (All) |
| Create & Edit Vehicle Listings | ❌ | ✅ (Own) | ✅ (Own) | ❌ | ✅ (Moderate) | ❌ | ❌ | ✅ (All) |
| Upload Vehicle RC, Insurance & Photos | ❌ | ✅ (Own) | ✅ (Own) | ❌ | ✅ (Audit) | ❌ | ❌ | ✅ (All) |
| Set Pricing Rules, Seasonality & Deposits | ❌ | ✅ (Own) | ✅ (Own) | ❌ | ❌ | ❌ | ❌ | ✅ (All) |
| Block Availability (Maintenance / Private) | ❌ | ✅ (Own) | ✅ (Own) | ❌ | ❌ | ❌ | ❌ | ✅ (All) |
| Approve / Reject Booking Requests | ❌ | ✅ (Own) | ✅ (Own) | ❌ | ❌ | ❌ | ❌ | ✅ (All) |
| Perform Pickup Handover Checklist | ❌ | ✅ (Own) | ✅ (Own) | ✅ (Own) | ❌ | ❌ | ❌ | ✅ (All) |
| Record Offline Pickup Balance (Cash/UPI) | ❌ | ✅ (Own) | ✅ (Own) | ✅ (Own) | ❌ | ❌ | ❌ | ✅ (All) |
| Perform Return Handover & Damage Report | ❌ | ✅ (Own) | ✅ (Own) | ✅ (Own) | ❌ | ❌ | ❌ | ✅ (All) |
| View Partner Financial Statements & Payouts | ❌ | ✅ (Own) | ❌ | ❌ | ❌ | ✅ (All) | ❌ | ✅ (All) |
| Manage Bank Account Details for Payouts | ❌ | ✅ (Own) | ❌ | ❌ | ❌ | ✅ (Verify) | ❌ | ✅ (All) |
| Invite, Edit and Remove Partner Staff | ❌ | ✅ (Own) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ (All) |
| **Administration & Governance Domain** | | | | | | | | |
| Review & Approve / Reject Partner KYC | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Moderate & Approve Vehicle Listings | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Suspend / Unsuspend Partner or Vehicle | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Search & Inspect All System Bookings | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |
| View Global Financial Transactions & Ledgers | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| Authorize / Execute Customer Refunds | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| Configure Platform Commission Rates | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| Generate Weekly Partner Settlement Batches | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| Respond to & Resolve Support Tickets | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |
| Arbitrate Damage & Security Deposit Disputes | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |
| Manage Destinations, Islands & Locations | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Provision & Revoke Admin Staff Roles | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| View System Audit Logs | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Edit System-wide Platform Configuration | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

---

## 3. Resource Ownership & Tenant Isolation Rules

1. **Self-Ownership Principle:**
   - Any query fetching tourist profile, booking history, or private tickets must enforce `WHERE user_id = :authenticatedUserId` at the repository/service layer.
2. **Partner Tenant Isolation:**
   - A user with role `PARTNER_OWNER`, `PARTNER_MANAGER`, or `PARTNER_STAFF` belongs to exactly one `business_id`.
   - Every read and write query for vehicles, bookings, pricing, calendar blocks, handover logs, and settlements must strictly enforce `WHERE business_id = :authenticatedPartnerBusinessId`.
   - Attempting to pass an alien `vehicle_id` or `booking_id` in a request payload immediately results in an HTTP `403 Forbidden` response.
3. **Role Segregation Principle for Admins:**
   - Operations admins cannot initiate financial refunds or alter commission structures.
   - Finance admins cannot approve vehicle licenses or alter fleet statuses.
   - Support agents cannot initiate refunds exceeding a defined supervisor threshold (e.g., max ₹500 without Finance Admin approval).

