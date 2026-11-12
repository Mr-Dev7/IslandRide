-- =============================================================================
-- IslandRide — Migration V13: Phase 6B Transactional Booking, Payment & Refund
-- Workflows (Configurable Thresholds + Booking Modification Audit Trail)
-- Modules: com.islandride.booking, com.islandride.paymentfinance, com.islandride.administration
-- =============================================================================

-- 1. Seed configurable Phase 6B operational/payment parameters into system_settings
INSERT IGNORE INTO system_settings (setting_key, setting_value, description, data_type, is_public) VALUES
    ('booking.instant.payment_window_minutes', '15', 'Checkout payment deadline in minutes for INSTANT bookings before expiration', 'NUMBER', TRUE),
    ('booking.modification.cutoff_hours', '12', 'Minimum hours before pickup required to reschedule or modify a booking', 'NUMBER', TRUE),
    ('refund.retry.max_attempts', '5', 'Maximum automatic retry attempts for transient refund failures before admin escalation', 'NUMBER', FALSE),
    ('refund.retry.backoff_minutes', '15', 'Base backoff interval in minutes between automated refund retries', 'NUMBER', FALSE),
    ('payment.reconciliation.stale_minutes', '15', 'Age threshold in minutes before INITIATED gateway payments are actively reconciled', 'NUMBER', FALSE);

-- 2. Immutable audit history of in-place booking modifications / reschedules
CREATE TABLE booking_modifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    modified_by_user_id BIGINT UNSIGNED NOT NULL,
    previous_start_time DATETIME(6) NOT NULL,
    previous_end_time DATETIME(6) NOT NULL,
    new_start_time DATETIME(6) NOT NULL,
    new_end_time DATETIME(6) NOT NULL,
    previous_pickup_location_id BIGINT UNSIGNED NOT NULL,
    previous_return_location_id BIGINT UNSIGNED NOT NULL,
    new_pickup_location_id BIGINT UNSIGNED NOT NULL,
    new_return_location_id BIGINT UNSIGNED NOT NULL,
    previous_total_payable DECIMAL(10,2) NOT NULL,
    new_total_payable DECIMAL(10,2) NOT NULL,
    price_delta DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    adjustment_type VARCHAR(30) NOT NULL DEFAULT 'NONE', -- NONE, TOPUP_REQUIRED, PARTIAL_REFUND, PICKUP_BALANCE_UPDATED
    reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_mod_public_id (public_id),
    INDEX idx_booking_mod_booking (booking_id, created_at),
    CONSTRAINT fk_b_mod_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_b_mod_user FOREIGN KEY (modified_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_b_mod_prev_pick FOREIGN KEY (previous_pickup_location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_b_mod_prev_ret FOREIGN KEY (previous_return_location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_b_mod_new_pick FOREIGN KEY (new_pickup_location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_b_mod_new_ret FOREIGN KEY (new_return_location_id) REFERENCES locations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
