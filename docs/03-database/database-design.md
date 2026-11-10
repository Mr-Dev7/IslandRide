# IslandRide — Database Design Specification
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Design Principles & Standards

The database architecture for IslandRide is engineered for high data integrity, strict auditability, and fast query execution across thousands of vehicle listings and concurrent booking intervals.

### 1.1 Core Rules
1. **Engine & Charset:** All tables utilize the **InnoDB** storage engine with character set **`utf8mb4`** and collation **`utf8mb4_unicode_ci`** to ensure full multilingual character support.
2. **Surrogate Primary Keys & Public Identifiers:**
   - Every table uses an unsigned 64-bit integer surrogate primary key: `id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT`. This optimizes clustered B-Tree indexing and foreign key join performance.
   - External-facing entities (users, vehicles, bookings, payments, businesses) include an indexed, non-guessable public identifier: `public_id VARCHAR(36) NOT NULL UNIQUE` (UUIDv4 or ULID). Public APIs exclusively expose `public_id` to prevent sequential ID harvesting and IDOR vulnerabilities.
3. **Monetary Precision:** All currency fields must use **`DECIMAL(10,2)`** (e.g., base rates, taxes, delivery fees, deposits, commissions, net payables). Floating-point data types (`FLOAT`, `DOUBLE`) are strictly forbidden.
4. **Timezone Representation:** All temporal columns are defined as **`DATETIME(6)`** and stored exclusively in **UTC**. The application layer performs timezone conversion to Indian Standard Time (`Asia/Kolkata`, UTC+05:30) for localized client displays.
5. **Audit Metadata:** Every mutable table contains standardized audit columns:
   - `created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`
   - `updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)`
   - `created_by BIGINT UNSIGNED NULL`
   - `updated_by BIGINT UNSIGNED NULL`
   - `version BIGINT NOT NULL DEFAULT 0` (for JPA Optimistic Locking where applicable)
6. **Soft Deletes:** Critical business records (users, partners, vehicles, locations) are soft-deleted via `deleted_at DATETIME(6) NULL`. Hard deletes are prohibited on transactional data.

---

## 2. Table Ownership by Backend Module

To enforce the Modular Monolith boundary rules, each database table is strictly owned by exactly one module:

| Backend Module | Owned Database Tables |
|---|---|
| **`identity-access`** | `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens`, `password_reset_tokens` |
| **`user-management`** | `tourist_profiles`, `admin_profiles` |
| **`partner`** | `businesses`, `business_locations`, `business_documents`, `partner_staff`, `partner_bank_accounts` |
| **`geography`** | `countries`, `states`, `islands`, `locations` |
| **`inventory`** | `vehicle_categories`, `vehicles`, `vehicle_images`, `vehicle_documents` |
| **`pricing`** | `pricing_rules`, `seasonal_prices`, `partner_policies` |
| **`availability`** | `inventory_holds`, `vehicle_unavailability` |
| **`booking`** | `bookings`, `booking_items`, `booking_status_history`, `handover_records` |
| **`payment-finance`**| `payments`, `payment_attempts`, `refunds`, `offline_collections` |
| **`commission`** | `commissions`, `settlements`, `settlement_items` |
| **`reviews`** | `reviews`, `review_reports` |
| **`notifications`** | `notifications`, `notification_templates`, `notification_logs` |
| **`support-disputes`**| `support_tickets`, `ticket_messages`, `damage_reports` |
| **`administration`** | `system_settings`, `audit_logs` |

---

## 3. High-Performance Indexing Strategy

### 3.1 Interval Overlap Indexing on Bookings
To optimize the real-time availability evaluation query:
```sql
CREATE INDEX idx_bookings_availability ON bookings (
    vehicle_id, 
    status, 
    start_time, 
    end_time
);
```
*Purpose:* Allows the MySQL query optimizer to evaluate vehicle status and time interval boundaries with an index-only range scan without reading table rows into buffer pool memory.

### 3.2 Vehicle Search & Filtering Index
```sql
CREATE INDEX idx_vehicles_search ON vehicles (
    island_id, 
    status, 
    category_id, 
    is_instant_booking, 
    daily_rate
);
```
*Purpose:* Accelerates the tourist catalog search query by filtering on active vehicles within a specific island before applying sort orders.

---

## 4. Concurrency & Locking Strategy

```
                          [ Incoming Booking Request ]
                                       |
                   [ Step 1: Ephemeral Pre-Check via Redis ]
                                       |
                [ Step 2: Open MySQL Transaction (@Transactional) ]
                                       |
                     [ Step 3: Pessimistic Row Lock ]
               SELECT id FROM vehicles WHERE id = :id FOR UPDATE;
                                       |
                   [ Step 4: Interval Overlap Check ]
         Does any booking exist with status IN ('CONFIRMED', 'ACTIVE')
              where (start_time < :newEnd AND end_time > :newStart)?
                                       |
                   +-------------------+-------------------+
                   |                                       |
              [ Count == 0 ]                         [ Count > 0 ]
                   |                                       |
          Insert inventory_hold                    Rollback & Abort
          Commit Transaction                      Return HTTP 409 Conflict
```
- Because row locks are held for fewer than 5 milliseconds, throughput remains high even under peak seasonal booking traffic.

