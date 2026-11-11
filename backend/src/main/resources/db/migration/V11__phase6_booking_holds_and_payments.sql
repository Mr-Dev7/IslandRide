-- =============================================================================
-- IslandRide — Migration V11: Phase 6 Stage 6A Foundation
-- Bookings, Policy/Pricing Snapshots, Idempotency, Operating Hours, Security
-- Deposits, Overbooking Exceptions, Webhooks, Receivables & Staff Permissions
-- =============================================================================

-- 1. Extend partner_policies with configurable security deposit, full-payment
--    cutoff, no-show, disruption and gateway-fee controls (preserving V5 10% seed)
ALTER TABLE partner_policies
    ADD COLUMN security_deposit_collection_method VARCHAR(30) NOT NULL DEFAULT 'AT_PICKUP' AFTER deposit_inspection_window_hours,
    ADD COLUMN full_payment_cutoff_hours INT NOT NULL DEFAULT 12 AFTER advance_payment_percentage,
    ADD COLUMN no_show_grace_minutes INT NOT NULL DEFAULT 120 AFTER cancellation_fee_percentage,
    ADD COLUMN no_show_penalty_percentage DECIMAL(5,2) NOT NULL DEFAULT 100.00 AFTER no_show_grace_minutes,
    ADD COLUMN gateway_fee_treatment VARCHAR(40) NOT NULL DEFAULT 'PLATFORM_ABSORBED' AFTER no_show_penalty_percentage,
    ADD COLUMN rescheduling_allowed_for_disruption BOOLEAN NOT NULL DEFAULT TRUE AFTER gateway_fee_treatment;

-- 2. Configurable Multi-Tier Cancellation Schedules per Partner
CREATE TABLE partner_cancellation_tiers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_id BIGINT UNSIGNED NOT NULL,
    min_hours_before_pickup INT NOT NULL,
    max_hours_before_pickup INT NULL,
    fee_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    flat_fee_amount DECIMAL(10,2) NULL,
    applies_to_deposit BOOLEAN NOT NULL DEFAULT FALSE,
    priority INT NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_cancel_tiers_bus_active (business_id, is_active, priority),
    CONSTRAINT fk_cancel_tiers_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Partner Operating Hours (Per Day-of-Week and Optional Hub Override)
CREATE TABLE partner_operating_hours (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_id BIGINT UNSIGNED NOT NULL,
    location_id BIGINT UNSIGNED NULL,
    scope_key VARCHAR(40) NOT NULL DEFAULT 'DEFAULT',
    day_of_week INT NOT NULL, -- ISO-8601: 1 = Monday .. 7 = Sunday
    is_open BOOLEAN NOT NULL DEFAULT TRUE,
    open_time TIME NULL,
    close_time TIME NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_op_hours_scope_day (business_id, scope_key, day_of_week),
    INDEX idx_partner_op_hours_loc (location_id),
    CONSTRAINT fk_op_hours_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_op_hours_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Extend pricing_rules with Explicit Priorities & Exclusive/Stackable Controls
ALTER TABLE pricing_rules
    ADD COLUMN priority INT NOT NULL DEFAULT 100 AFTER adjustment_value,
    ADD COLUMN is_stackable BOOLEAN NOT NULL DEFAULT TRUE AFTER priority,
    ADD COLUMN exclusive_group VARCHAR(50) NULL AFTER is_stackable,
    ADD INDEX idx_pricing_rules_priority (is_active, priority);

-- 5. Explicit Operational Permissions on partner_staff
ALTER TABLE partner_staff
    ADD COLUMN can_record_handover BOOLEAN NOT NULL DEFAULT FALSE AFTER is_active,
    ADD COLUMN can_collect_offline_payments BOOLEAN NOT NULL DEFAULT FALSE AFTER can_record_handover,
    ADD COLUMN can_mark_no_show BOOLEAN NOT NULL DEFAULT FALSE AFTER can_collect_offline_payments;

-- Owners and Managers retain operational permissions by role baseline while generic STAFF stays FALSE until granted
UPDATE partner_staff
SET can_record_handover = TRUE,
    can_collect_offline_payments = TRUE,
    can_mark_no_show = TRUE
WHERE role_in_business IN ('OWNER', 'MANAGER');

-- 6. Extend inventory_holds with Hold Context, Turnaround Buffer & Idempotency
ALTER TABLE inventory_holds
    ADD COLUMN hold_purpose VARCHAR(30) NOT NULL DEFAULT 'CHECKOUT' AFTER user_id,
    ADD COLUMN pickup_location_id BIGINT UNSIGNED NULL AFTER hold_purpose,
    ADD COLUMN return_location_id BIGINT UNSIGNED NULL AFTER pickup_location_id,
    ADD COLUMN turnaround_buffer_minutes INT NOT NULL DEFAULT 60 AFTER hold_duration_seconds,
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER expires_at,
    ADD UNIQUE KEY uk_holds_user_idempotency (user_id, idempotency_key),
    ADD INDEX idx_holds_veh_status_exp (vehicle_id, status, expires_at),
    ADD CONSTRAINT fk_holds_pickup_loc FOREIGN KEY (pickup_location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_holds_return_loc FOREIGN KEY (return_location_id) REFERENCES locations (id) ON DELETE RESTRICT;

-- 7. Extend bookings with Immutable Snapshots, Payment/Overbooking Status Separation,
--    Disruption & No-Show Audit Records, and Idempotency
ALTER TABLE bookings
    ADD COLUMN inventory_hold_id BIGINT UNSIGNED NULL AFTER business_id,
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER booking_reference,
    ADD COLUMN pricing_plan_public_id VARCHAR(36) NULL AFTER total_days,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR' AFTER pricing_plan_public_id,
    ADD COLUMN hourly_rate DECIMAL(10,2) NULL AFTER currency,
    ADD COLUMN weekly_rate DECIMAL(10,2) NULL AFTER daily_rate,
    ADD COLUMN total_hours INT NULL AFTER total_days,
    ADD COLUMN seasonal_multiplier DECIMAL(5,2) NOT NULL DEFAULT 1.00 AFTER base_rental_amount,
    ADD COLUMN seasonal_adjustment_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER seasonal_multiplier,
    ADD COLUMN surcharge_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER discount_amount,
    ADD COLUMN cleaning_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER surcharge_amount,
    ADD COLUMN gst_percentage DECIMAL(5,2) NOT NULL DEFAULT 5.00 AFTER tax_amount,
    ADD COLUMN commission_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 4.00 AFTER gst_percentage,
    ADD COLUMN advance_percentage_applied DECIMAL(5,2) NOT NULL DEFAULT 25.00 AFTER pickup_balance_due,
    ADD COLUMN turnaround_buffer_minutes INT NOT NULL DEFAULT 60 AFTER advance_percentage_applied,
    ADD COLUMN deposit_collection_method VARCHAR(30) NOT NULL DEFAULT 'AT_PICKUP' AFTER security_deposit_amount,
    ADD COLUMN pricing_snapshot_json JSON NULL AFTER booking_mode,
    ADD COLUMN cancellation_policy_snapshot JSON NULL AFTER pricing_snapshot_json,
    ADD COLUMN payment_summary_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID' AFTER status,
    ADD COLUMN overbooking_exception_status VARCHAR(30) NOT NULL DEFAULT 'NONE' AFTER payment_summary_status,
    ADD COLUMN payment_deadline_at DATETIME(6) NULL AFTER approval_expires_at,
    ADD COLUMN cancelled_by BIGINT UNSIGNED NULL AFTER cancelled_at,
    ADD COLUMN cancelled_by_role VARCHAR(30) NULL AFTER cancelled_by,
    ADD COLUMN disruption_exception_type VARCHAR(40) NULL AFTER cancellation_reason,
    ADD COLUMN no_show_at DATETIME(6) NULL AFTER completed_at,
    ADD COLUMN no_show_marked_by BIGINT UNSIGNED NULL AFTER no_show_at,
    ADD COLUMN no_show_evidence_notes TEXT NULL AFTER no_show_marked_by,
    ADD COLUMN no_show_evidence_ref VARCHAR(255) NULL AFTER no_show_evidence_notes,
    ADD COLUMN customer_dl_number VARCHAR(50) NULL AFTER booking_mode,
    ADD COLUMN customer_emergency_contact VARCHAR(30) NULL AFTER customer_dl_number,
    ADD COLUMN dl_confirmed BOOLEAN NOT NULL DEFAULT FALSE AFTER customer_emergency_contact,
    ADD UNIQUE KEY uk_bookings_user_idempotency (user_id, idempotency_key),
    ADD INDEX idx_bookings_status_pay_deadline (status, payment_deadline_at),
    ADD INDEX idx_bookings_overbooking_status (overbooking_exception_status, status),
    ADD INDEX idx_bookings_payment_summary (payment_summary_status),
    ADD CONSTRAINT fk_bookings_hold FOREIGN KEY (inventory_hold_id) REFERENCES inventory_holds (id) ON DELETE SET NULL;

-- 8. Extend payments & refunds for lifecycle audit, deposit separation & retries
ALTER TABLE payments
    ADD COLUMN is_deposit BOOLEAN NOT NULL DEFAULT FALSE AFTER payment_type,
    ADD COLUMN gateway_order_expires_at DATETIME(6) NULL AFTER gateway_signature,
    ADD COLUMN captured_at DATETIME(6) NULL AFTER status,
    ADD COLUMN failed_at DATETIME(6) NULL AFTER captured_at,
    ADD COLUMN failure_reason VARCHAR(255) NULL AFTER failed_at,
    ADD INDEX idx_payments_status_created (status, created_at);

ALTER TABLE refunds
    ADD COLUMN refund_type VARCHAR(30) NOT NULL DEFAULT 'RENTAL' AFTER reason,
    ADD COLUMN gateway_fee_retained DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER amount,
    ADD COLUMN next_retry_at DATETIME(6) NULL AFTER retry_count,
    ADD COLUMN initiated_by BIGINT UNSIGNED NULL AFTER error_message,
    ADD COLUMN approved_by BIGINT UNSIGNED NULL AFTER initiated_by,
    ADD COLUMN approved_at DATETIME(6) NULL AFTER approved_by,
    ADD COLUMN escalation_ticket_ref VARCHAR(64) NULL AFTER approved_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER idempotency_key,
    ADD INDEX idx_refunds_retry_queue (status, next_retry_at);

-- 9. Extend commissions to record tax/deposit exclusions & partner receivable offsets
ALTER TABLE commissions
    ADD COLUMN tax_excluded_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER gross_booking_amount,
    ADD COLUMN deposit_excluded_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER tax_excluded_amount,
    ADD COLUMN online_collected_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER partner_payout_amount,
    ADD COLUMN offline_collected_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER online_collected_amount,
    ADD COLUMN partner_receivable_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER offline_collected_amount;

-- 10. Security Deposit Trust Liability Ledger
CREATE TABLE security_deposit_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    collection_method VARCHAR(30) NOT NULL DEFAULT 'AT_PICKUP',
    expected_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    collected_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    refunded_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    forfeited_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    inspection_deadline_at DATETIME(6) NULL,
    inspection_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    collected_by BIGINT UNSIGNED NULL,
    collected_at DATETIME(6) NULL,
    payment_channel VARCHAR(30) NULL,
    receipt_reference VARCHAR(80) NULL,
    released_by BIGINT UNSIGNED NULL,
    released_at DATETIME(6) NULL,
    notes TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sec_dep_public_id (public_id),
    UNIQUE KEY uk_sec_dep_booking (booking_id),
    INDEX idx_sec_dep_status (status),
    CONSTRAINT fk_sec_dep_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Overbooking Exceptions Ledger (Late payment captured when slot was claimed)
CREATE TABLE overbooking_exceptions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    payment_id BIGINT UNSIGNED NOT NULL,
    conflicting_booking_id BIGINT UNSIGNED NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DETECTED', -- DETECTED, REFUND_INITIATED, RESOLVED
    resolution_notes TEXT NULL,
    detected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_by BIGINT UNSIGNED NULL,
    resolved_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_overbooking_public_id (public_id),
    INDEX idx_overbooking_booking (booking_id),
    INDEX idx_overbooking_status (status),
    CONSTRAINT fk_obx_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_obx_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_obx_conflict_booking FOREIGN KEY (conflicting_booking_id) REFERENCES bookings (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Partner Receivables Ledger (Excess Offline Collection Offsets)
CREATE TABLE partner_receivables (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    commission_id BIGINT UNSIGNED NULL,
    amount_due_from_partner DECIMAL(10,2) NOT NULL,
    amount_settled DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', -- OPEN, PARTIALLY_OFFSET, SETTLED, WAIVED
    notes TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_receivable_public_id (public_id),
    INDEX idx_receivable_business_status (business_id, status),
    CONSTRAINT fk_rcv_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rcv_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rcv_commission FOREIGN KEY (commission_id) REFERENCES commissions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Payment Webhook Event Deduplication & Audit Table
CREATE TABLE payment_webhook_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    gateway_provider VARCHAR(30) NOT NULL,
    gateway_event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    signature_verified BOOLEAN NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, PROCESSED, IGNORED, FAILED
    booking_id BIGINT UNSIGNED NULL,
    payment_id BIGINT UNSIGNED NULL,
    payload_json LONGTEXT NOT NULL,
    error_message TEXT NULL,
    received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_provider_event (gateway_provider, gateway_event_id),
    INDEX idx_webhook_status_received (processing_status, received_at),
    CONSTRAINT fk_whk_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE SET NULL,
    CONSTRAINT fk_whk_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Generic API / Command Idempotency Ledger
CREATE TABLE idempotency_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scope VARCHAR(60) NOT NULL,
    actor_key VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, FAILED
    http_status INT NULL,
    resource_public_id VARCHAR(64) NULL,
    response_body LONGTEXT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idemp_scope_actor_key (scope, actor_key, idempotency_key),
    INDEX idx_idemp_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Seed Provisional Configurable Booking Policy Parameters into system_settings
INSERT IGNORE INTO system_settings (setting_key, setting_value, description, data_type, is_public) VALUES
    ('booking.full_payment_cutoff_hours.default', '12', 'Provisional dev default: cutoff hours before pickup requiring 100% FULL payment', 'NUMBER', TRUE),
    ('booking.approved.payment_window_hours', '2', 'Standard payment window in hours after partner approves an APPROVAL booking', 'NUMBER', FALSE),
    ('booking.approved.payment_window_urgent_minutes', '30', 'Urgent payment window in minutes after partner approval when pickup is < 6h away', 'NUMBER', FALSE),
    ('booking.approved.payment_window_urgent_threshold_hours', '6', 'Pickup lead-time threshold in hours that triggers urgent post-approval payment window', 'NUMBER', FALSE),
    ('booking.no_show.grace_minutes.default', '120', 'Provisional dev default: grace window in minutes before partner can claim NO_SHOW', 'NUMBER', FALSE),
    ('booking.no_show.penalty_percentage.default', '100.00', 'Provisional dev default: rental advance forfeiture percentage on verified NO_SHOW', 'NUMBER', FALSE),
    ('booking.deposit.collection_method.default', 'AT_PICKUP', 'Default security deposit collection channel (AT_PICKUP, ONLINE, PARTNER_CONFIGURABLE)', 'STRING', TRUE),
    ('booking.gateway_fee.treatment.default', 'PLATFORM_ABSORBED', 'Default gateway fee absorption policy (PLATFORM_ABSORBED, PARTNER_ABSORBED, DEDUCTED_FROM_REFUND)', 'STRING', FALSE),
    ('booking.lead_time.min_minutes', '30', 'Minimum lead time in minutes between booking/hold request and vehicle pickup start', 'NUMBER', TRUE),
    ('booking.operating_hours.timezone', 'Asia/Kolkata', 'Canonical timezone for partner operating hours validation', 'STRING', TRUE),
    ('booking.operating_hours.default_open', '07:00', 'Provisional default partner opening time in Asia/Kolkata when custom schedule is not set', 'STRING', TRUE),
    ('booking.operating_hours.default_close', '21:00', 'Provisional default partner closing time in Asia/Kolkata when custom schedule is not set', 'STRING', TRUE),
    ('booking.idempotency.ttl_hours', '24', 'Retention window in hours for API idempotency records', 'NUMBER', FALSE);

