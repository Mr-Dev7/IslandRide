-- =============================================================================
-- IslandRide — Migration V6: Booking Lifecycle & Handover Inspections Schema
-- Module: com.islandride.booking
-- =============================================================================

-- Core Bookings table (Canonical single vehicle per booking contract)
CREATE TABLE bookings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_reference VARCHAR(20) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    pickup_location_id BIGINT UNSIGNED NOT NULL,
    return_location_id BIGINT UNSIGNED NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    total_days INT NOT NULL,
    daily_rate DECIMAL(10,2) NOT NULL,
    base_rental_amount DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    security_deposit_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_payable_amount DECIMAL(10,2) NOT NULL,
    advance_paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    pickup_balance_due DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_mode VARCHAR(20) NOT NULL DEFAULT 'FULL', -- FULL, PARTIAL_ADVANCE
    booking_mode VARCHAR(20) NOT NULL DEFAULT 'INSTANT', -- INSTANT, APPROVAL
    approval_expires_at DATETIME(6) NULL,
    approved_at DATETIME(6) NULL,
    approved_by BIGINT UNSIGNED NULL,
    cancelled_at DATETIME(6) NULL,
    cancellation_reason TEXT NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    updated_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookings_public_id (public_id),
    UNIQUE KEY uk_bookings_reference (booking_reference),
    INDEX idx_bookings_user (user_id),
    INDEX idx_bookings_vehicle (vehicle_id),
    INDEX idx_bookings_business (business_id),
    INDEX idx_bookings_status (status),
    INDEX idx_bookings_approval_deadline (status, approval_expires_at),
    -- High Performance Interval Overlap Indexing for real-time calendar checks
    INDEX idx_bookings_availability (vehicle_id, status, start_time, end_time),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_pickup_loc FOREIGN KEY (pickup_location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_return_loc FOREIGN KEY (return_location_id) REFERENCES locations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Booking Status Audit History Log
CREATE TABLE booking_status_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    change_reason VARCHAR(255) NULL,
    changed_by BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_booking_history_booking (booking_id),
    CONSTRAINT fk_booking_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Handover Inspections (Recorded at Pickup and Return)
CREATE TABLE handover_inspections (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    handover_type VARCHAR(20) NOT NULL, -- PICKUP, RETURN
    staff_user_id BIGINT UNSIGNED NOT NULL,
    odometer_reading INT NOT NULL,
    fuel_percentage INT NOT NULL,
    checklist_data JSON NULL, -- Helmets provided, toolkit, clean condition
    notes TEXT NULL,
    completed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_handover_public_id (public_id),
    INDEX idx_handover_booking (booking_id),
    INDEX idx_handover_staff (staff_user_id),
    CONSTRAINT fk_handover_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_handover_staff FOREIGN KEY (staff_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Handover Photographic Evidence & Signatures
CREATE TABLE handover_evidence (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT UNSIGNED NOT NULL,
    evidence_type VARCHAR(40) NOT NULL, -- ODOMETER_PHOTO, FUEL_PHOTO, EXTERIOR_FRONT, EXTERIOR_REAR, EXTERIOR_LEFT, EXTERIOR_RIGHT, DAMAGE_PHOTO, SIGNATURE
    image_s3_key VARCHAR(255) NOT NULL,
    media_mime_type VARCHAR(50) NOT NULL DEFAULT 'image/jpeg',
    latitude DECIMAL(10,8) NULL,
    longitude DECIMAL(11,8) NULL,
    captured_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_handover_evidence_inspection (inspection_id),
    CONSTRAINT fk_evidence_inspection FOREIGN KEY (inspection_id) REFERENCES handover_inspections (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

