# IslandRide — Data Dictionary
**Document Version:** 1.1.0  
**Phase:** 1 (Requirements & System Design — Post-Review Revisions)  
**Status:** Approved for Implementation  

---

## 1. Identity & Access Module (`com.islandride.identity`)

### Table: `users`
Represents all system actors (Tourists, Partner staff, Admins).
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Internal surrogate key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public UUIDv4 for API exposure. |
| `email` | `VARCHAR(191)` | NO | None | UNIQUE, INDEX | User login email. |
| `phone` | `VARCHAR(20)` | YES | NULL | INDEX | Contact mobile number. |
| `password_hash`| `VARCHAR(255)` | NO | None | None | Argon2id / BCrypt password hash. |
| `status` | `VARCHAR(30)` | NO | 'ACTIVE' | None | `ACTIVE`, `SUSPENDED`, `LOCKED`. |
| `created_at` | `DATETIME(6)` | NO | CURRENT_TIMESTAMP(6) | None | UTC timestamp of creation. |
| `updated_at` | `DATETIME(6)` | NO | CURRENT_TIMESTAMP(6) | None | UTC timestamp of last update. |
| `deleted_at` | `DATETIME(6)` | YES | NULL | INDEX | Soft deletion timestamp. |

---

## 2. Partner Management Module (`com.islandride.partner`)

### Table: `businesses`
Stores registered vehicle rental partner agencies.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Internal surrogate key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public UUIDv4 identifier. |
| `owner_user_id`| `BIGINT UNSIGNED` | NO | None | FK -> `users.id` | The owning partner user. |
| `legal_name` | `VARCHAR(255)` | NO | None | None | Registered legal entity name. |
| `trade_name` | `VARCHAR(255)` | NO | None | INDEX | Public trade / storefront name. |
| `gstin` | `VARCHAR(15)` | YES | NULL | INDEX | Indian GSTIN (subject to turnover validation). |
| `pan_number` | `VARCHAR(10)` | YES | NULL | INDEX | Tax PAN (subject to entity validation). |
| `verification_status`| `VARCHAR(30)`| NO | 'DRAFT' | INDEX | `DRAFT`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `SUSPENDED`. |
| `rejection_reason` | `TEXT` | YES | NULL | None | Feedback when KYC rejected. |
| `commission_rate` | `DECIMAL(5,2)` | NO | 4.00 | None | Commission % override (configurable). |
| `created_at` | `DATETIME(6)` | NO | CURRENT_TIMESTAMP(6) | None | UTC timestamp of registration. |

---

## 3. Geography Module (`com.islandride.geography`)

### Table: `islands`
Configurable island territories.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate primary key. |
| `name` | `VARCHAR(100)` | NO | None | INDEX | e.g., 'Swaraj Dweep (Havelock)'. |
| `code` | `VARCHAR(30)` | NO | None | UNIQUE, INDEX | e.g., 'HAVELOCK'. |
| `state_id` | `BIGINT UNSIGNED` | NO | None | FK -> `states.id` | Andaman & Nicobar UT ID. |
| `is_active` | `BOOLEAN` | NO | TRUE | INDEX | Status flag for search. |

### Table: `locations`
Pickup and return hubs.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate primary key. |
| `island_id` | `BIGINT UNSIGNED` | NO | None | FK -> `islands.id` | Enforces location grouping. |
| `name` | `VARCHAR(150)` | NO | None | INDEX | e.g., 'Havelock Jetty Hub'. |
| `address` | `TEXT` | NO | None | None | Physical station address. |
| `latitude` | `DECIMAL(10,8)` | YES | NULL | None | GPS latitude coordinate. |
| `longitude`| `DECIMAL(11,8)` | YES | NULL | None | GPS longitude coordinate. |
| `is_active` | `BOOLEAN` | NO | TRUE | INDEX | Operational status. |

---

## 4. Vehicle Inventory Module (`com.islandride.inventory`)

### Table: `vehicles`
Individual vehicles listed by rental partners.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate primary key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public UUIDv4. |
| `business_id` | `BIGINT UNSIGNED` | NO | None | FK -> `businesses.id`, INDEX | Partner owning the vehicle. |
| `island_id` | `BIGINT UNSIGNED` | NO | None | FK -> `islands.id`, INDEX | Operating island. |
| `category_id` | `BIGINT UNSIGNED` | NO | None | FK -> `vehicle_categories.id` | e.g. Scooter, Car. |
| `make` | `VARCHAR(80)` | NO | None | None | e.g. 'Honda', 'Maruti Suzuki'. |
| `model` | `VARCHAR(80)` | NO | None | None | e.g. 'Activa 6G', 'Swift'. |
| `model_year`| `INT` | NO | None | None | Manufacturing year. |
| `registration_number`| `VARCHAR(20)`| NO | None | UNIQUE, INDEX | Masked in public APIs. |
| `daily_rate`| `DECIMAL(10,2)`| NO | None | INDEX | Base daily rental rate. |
| `security_deposit`| `DECIMAL(10,2)`| NO | 0.00 | None | Refundable security deposit. |
| `turnaround_buffer_minutes`| `INT` | NO | 60 | None | Buffer before next rental (configurable). |
| `booking_mode`| `VARCHAR(20)`| NO | 'INSTANT' | INDEX | `INSTANT` or `APPROVAL`. |
| `status` | `VARCHAR(30)` | NO | 'DRAFT' | INDEX | `DRAFT`, `PENDING_REVIEW`, `ACTIVE`, `UNDER_MAINTENANCE`, `RETIRED`. |

---

## 5. Availability Module (`com.islandride.availability`)

### Table: `inventory_holds` (Durable MySQL Source of Truth)
Short-lived temporary locks preventing double-booking during checkout.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public hold token. |
| `vehicle_id` | `BIGINT UNSIGNED` | NO | None | FK -> `vehicles.id`, INDEX | Reserved vehicle. |
| `user_id` | `BIGINT UNSIGNED` | NO | None | FK -> `users.id` | Reserving tourist. |
| `start_time` | `DATETIME(6)` | NO | None | INDEX | Rental start interval. |
| `end_time` | `DATETIME(6)` | NO | None | INDEX | Rental end interval. |
| `status` | `VARCHAR(20)` | NO | 'HELD' | INDEX | `HELD`, `CONVERTED`, `EXPIRED`, `RELEASED`. |
| `expires_at` | `DATETIME(6)` | NO | None | INDEX | Expiration timestamp (configurable). |

---

## 6. Booking Lifecycle Module (`com.islandride.booking`)

### Table: `bookings`
Canonical rental booking records (strictly 1 vehicle per booking for MVP).
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public UUIDv4. |
| `booking_reference`| `VARCHAR(16)`| NO | None | UNIQUE, INDEX | Human-readable ref: e.g. `IR-202611-9842`. |
| `user_id` | `BIGINT UNSIGNED` | NO | None | FK -> `users.id`, INDEX | Booking customer. |
| `vehicle_id` | `BIGINT UNSIGNED` | NO | None | FK -> `vehicles.id`, INDEX | Directly links single vehicle. |
| `business_id` | `BIGINT UNSIGNED` | NO | None | FK -> `businesses.id`, INDEX | Owning partner. |
| `pickup_location_id`| `BIGINT UNSIGNED`| NO| None | FK -> `locations.id` | Pickup station. |
| `return_location_id`| `BIGINT UNSIGNED`| NO| None | FK -> `locations.id` | Return station. |
| `start_time` | `DATETIME(6)` | NO | None | INDEX | Trip start datetime (UTC). |
| `end_time` | `DATETIME(6)` | NO | None | INDEX | Trip end datetime (UTC). |
| `total_rental_amount`| `DECIMAL(10,2)`| NO| None| None | Total rental fee. |
| `tax_amount` | `DECIMAL(10,2)`| NO | 0.00 | None | Applicable taxes. |
| `security_deposit_amount`|`DECIMAL(10,2)`| NO| 0.00 | None | Refundable deposit. |
| `advance_paid_amount`| `DECIMAL(10,2)`| NO| 0.00 | None | Amount paid online. |
| `pickup_balance_due`| `DECIMAL(10,2)`| NO| 0.00 | None | Balance due at pickup. |
| `status` | `VARCHAR(30)` | NO | 'PENDING_PAYMENT' | INDEX | Booking status. |
| `payment_mode`| `VARCHAR(20)` | NO | 'FULL' | None | `FULL` or `PARTIAL_ADVANCE`. |
| `approval_expires_at`| `DATETIME(6)`| YES | NULL | INDEX | Deadline for partner approval (configurable). |

### Table: `handover_inspections`
Handover events recorded at pickup and return.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public UUIDv4. |
| `booking_id` | `BIGINT UNSIGNED` | NO | None | FK -> `bookings.id`, INDEX | Associated booking. |
| `handover_type`| `VARCHAR(20)`| NO | None | INDEX | `PICKUP` or `RETURN`. |
| `staff_user_id`| `BIGINT UNSIGNED`| NO| None | FK -> `users.id` | Staff performing inspection. |
| `odometer_reading`| `INT` | NO | None | None | Odometer in kilometers. |
| `fuel_percentage`| `INT` | NO | None | None | Fuel level percentage (0 to 100). |
| `checklist_data`| `JSON` | YES | NULL | None | Checklist responses (helmet, tools). |
| `notes` | `TEXT` | YES | NULL | None | Handover inspection remarks. |
| `completed_at` | `DATETIME(6)` | NO | CURRENT_TIMESTAMP(6) | None | Completion timestamp. |

### Table: `handover_evidence`
Photographic and digital proof attached to a handover inspection.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate key. |
| `inspection_id`| `BIGINT UNSIGNED`| NO | None | FK -> `handover_inspections.id`, INDEX | Parent inspection. |
| `evidence_type`| `VARCHAR(30)` | NO | None | None | `ODOMETER_PHOTO`, `FUEL_PHOTO`, `EXTERIOR_FRONT`, `EXTERIOR_REAR`, `EXTERIOR_LEFT`, `EXTERIOR_RIGHT`, `DAMAGE_PHOTO`, `SIGNATURE`. |
| `image_s3_key` | `VARCHAR(255)` | NO | None | None | Private object storage key. |
| `media_mime_type`| `VARCHAR(50)` | NO | 'image/jpeg' | None | MIME type. |
| `captured_at` | `DATETIME(6)` | NO | CURRENT_TIMESTAMP(6) | None | Capture timestamp. |

---

## 7. Payments & Financial Ledger Module (`com.islandride.payment`)

### Table: `payments`
Immutable financial transactions.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public UUIDv4. |
| `booking_id` | `BIGINT UNSIGNED` | NO | None | FK -> `bookings.id`, INDEX | Associated reservation. |
| `amount` | `DECIMAL(10,2)`| NO | None | None | Transaction amount. |
| `currency` | `VARCHAR(3)` | NO | 'INR' | None | ISO currency code. |
| `payment_type`| `VARCHAR(30)` | NO | 'RENTAL_ADVANCE' | None | `RENTAL_ADVANCE`, `RENTAL_FULL`, `PICKUP_BALANCE`, `SECURITY_DEPOSIT`. |
| `payment_method`| `VARCHAR(30)`| NO | 'GATEWAY' | None | `GATEWAY`, `OFFLINE_CASH`, `OFFLINE_UPI`. |
| `gateway_provider`|`VARCHAR(30)`| YES | NULL | None | e.g. 'RAZORPAY'. |
| `gateway_order_id`|`VARCHAR(100)`| YES| NULL | INDEX | Gateway Order ID. |
| `gateway_payment_id`|`VARCHAR(100)`|YES| NULL | INDEX | Gateway Payment ID. |
| `status` | `VARCHAR(30)` | NO | 'INITIATED' | INDEX | `INITIATED`, `CAPTURED`, `FAILED`, `REFUNDED`. |
| `idempotency_key`|`VARCHAR(128)`| NO | None | UNIQUE, INDEX | Prevents duplicate processing. |

### Table: `refunds`
Auditable refund records supporting asynchronous reconciliation and retries.
| Column Name | Data Type | Nullable | Default | Constraints / Index | Description |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | NO | AUTO_INCREMENT | PK | Surrogate key. |
| `public_id` | `VARCHAR(36)` | NO | None | UNIQUE, INDEX | Public UUIDv4. |
| `payment_id` | `BIGINT UNSIGNED` | NO | None | FK -> `payments.id`, INDEX | Associated payment. |
| `amount` | `DECIMAL(10,2)`| NO | None | None | Refunded amount. |
| `gateway_refund_id`| `VARCHAR(100)`| YES| NULL | INDEX | Gateway Refund ID. |
| `status` | `VARCHAR(30)` | NO | 'PENDING' | INDEX | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`, `RECONCILED`. |
| `retry_count` | `INT` | NO | 0 | None | Failure retry attempts. |
| `error_message`| `TEXT` | YES | NULL | None | Gateway error details if failed. |
| `idempotency_key`| `VARCHAR(128)`| NO| None | UNIQUE, INDEX | Ensures idempotent gateway calls. |
| `reconciled_at`| `DATETIME(6)` | YES | NULL | None | Timestamp verified via reconciliation. |
