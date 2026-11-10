-- =============================================================================
-- IslandRide — Migration V7: Payments, Ledger, Refunds & Partner Settlements Schema
-- Modules: com.islandride.paymentfinance, com.islandride.commission
-- =============================================================================

-- Payments ledger table (Immutable transactions)
CREATE TABLE payments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    payment_type VARCHAR(30) NOT NULL DEFAULT 'RENTAL_ADVANCE', -- RENTAL_ADVANCE, RENTAL_FULL, PICKUP_BALANCE, SECURITY_DEPOSIT
    payment_method VARCHAR(30) NOT NULL DEFAULT 'GATEWAY', -- GATEWAY, OFFLINE_CASH, OFFLINE_UPI
    gateway_provider VARCHAR(30) NULL, -- RAZORPAY, CASHFREE
    gateway_order_id VARCHAR(100) NULL,
    gateway_payment_id VARCHAR(100) NULL,
    gateway_signature VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED', -- INITIATED, CAPTURED, FAILED, REFUNDED
    idempotency_key VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_public_id (public_id),
    UNIQUE KEY uk_payments_idempotency (idempotency_key),
    INDEX idx_payments_booking (booking_id),
    INDEX idx_payments_gateway_order (gateway_order_id),
    INDEX idx_payments_gateway_payment (gateway_payment_id),
    INDEX idx_payments_status (status),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payment Attempts Log (Tracks gateway webhook retries & error diagnostics)
CREATE TABLE payment_attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_id BIGINT UNSIGNED NOT NULL,
    attempt_number INT NOT NULL DEFAULT 1,
    gateway_request_payload JSON NULL,
    gateway_response_payload JSON NULL,
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100) NULL,
    error_message TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_payment_attempts_payment (payment_id),
    CONSTRAINT fk_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Refunds Ledger (Auditable refunds with asynchronous reconciliation support)
CREATE TABLE refunds (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    payment_id BIGINT UNSIGNED NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    reason VARCHAR(100) NOT NULL DEFAULT 'CANCELLATION', -- CANCELLATION, REJECTION, SECURITY_DEPOSIT_RETURN, PARTIAL_DISPUTE
    gateway_refund_id VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, SUCCESS, FAILED, RECONCILED
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    reconciled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refunds_public_id (public_id),
    UNIQUE KEY uk_refunds_idempotency (idempotency_key),
    INDEX idx_refunds_payment (payment_id),
    INDEX idx_refunds_booking (booking_id),
    INDEX idx_refunds_status (status),
    INDEX idx_refunds_gateway (gateway_refund_id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Offline Collections (In-person cash or QR UPI payments collected at physical hub)
CREATE TABLE offline_collections (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    staff_user_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    collection_type VARCHAR(30) NOT NULL DEFAULT 'PICKUP_BALANCE', -- PICKUP_BALANCE, SECURITY_DEPOSIT, DAMAGE_SETTLEMENT
    payment_method VARCHAR(20) NOT NULL DEFAULT 'CASH', -- CASH, UPI_QR
    receipt_reference VARCHAR(50) NULL,
    notes TEXT NULL,
    collected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_offline_booking (booking_id),
    INDEX idx_offline_staff (staff_user_id),
    CONSTRAINT fk_offline_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_offline_staff FOREIGN KEY (staff_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Platform Commissions Ledger
CREATE TABLE commissions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    gross_booking_amount DECIMAL(10,2) NOT NULL,
    commission_percentage DECIMAL(5,2) NOT NULL DEFAULT 4.00,
    commission_amount DECIMAL(10,2) NOT NULL,
    partner_payout_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACCRUED', -- ACCRUED, SETTLED, VOIDED
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_commissions_booking (booking_id),
    INDEX idx_commissions_business (business_id),
    INDEX idx_commissions_status (status),
    CONSTRAINT fk_commissions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commissions_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Partner Settlements (Weekly / Monthly payout batches)
CREATE TABLE settlements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    settlement_period_start DATE NOT NULL,
    settlement_period_end DATE NOT NULL,
    total_gross_amount DECIMAL(10,2) NOT NULL,
    total_commission_deducted DECIMAL(10,2) NOT NULL,
    net_payout_amount DECIMAL(10,2) NOT NULL,
    bank_utr_reference VARCHAR(50) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED', -- INITIATED, PROCESSING, COMPLETED, FAILED
    processed_by BIGINT UNSIGNED NULL,
    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlements_public_id (public_id),
    INDEX idx_settlements_business (business_id),
    INDEX idx_settlements_utr (bank_utr_reference),
    INDEX idx_settlements_status (status),
    CONSTRAINT fk_settlements_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_settlements_admin FOREIGN KEY (processed_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Settlement Items Junction table
CREATE TABLE settlement_items (
    settlement_id BIGINT UNSIGNED NOT NULL,
    commission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (settlement_id, commission_id),
    CONSTRAINT fk_settlement_items_settle FOREIGN KEY (settlement_id) REFERENCES settlements (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_items_comm FOREIGN KEY (commission_id) REFERENCES commissions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

