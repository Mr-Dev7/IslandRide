-- =============================================================================
-- IslandRide — Migration V8: Reviews, Support Disputes, Notifications & System Audit Schema
-- Modules: com.islandride.reviews, com.islandride.support, com.islandride.notifications, com.islandride.administration
-- =============================================================================

-- Reviews table (Verified customer feedback linked strictly to completed bookings)
CREATE TABLE reviews (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    rating INT NOT NULL, -- 1 to 5 stars
    review_text TEXT NULL,
    partner_reply TEXT NULL,
    partner_replied_at DATETIME(6) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED', -- PUBLISHED, FLAGGED, HIDDEN
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_public_id (public_id),
    UNIQUE KEY uk_reviews_booking (booking_id),
    INDEX idx_reviews_vehicle (vehicle_id),
    INDEX idx_reviews_business (business_id),
    INDEX idx_reviews_rating (rating),
    INDEX idx_reviews_status (status),
    CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Review Moderation Reports
CREATE TABLE review_reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id BIGINT UNSIGNED NOT NULL,
    reported_by BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW', -- PENDING_REVIEW, RESOLVED, DISMISSED
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_review_reports_review (review_id),
    CONSTRAINT fk_review_reports_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reports_user FOREIGN KEY (reported_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Customer Support Tickets
CREATE TABLE support_tickets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    ticket_number VARCHAR(20) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    booking_id BIGINT UNSIGNED NULL,
    category VARCHAR(50) NOT NULL, -- BOOKING, PAYMENT, VEHICLE_BREAKDOWN, CANCELLATION, GENERAL
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, URGENT
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', -- OPEN, IN_PROGRESS, RESOLVED, CLOSED
    assigned_to BIGINT UNSIGNED NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_public_id (public_id),
    UNIQUE KEY uk_support_ticket_number (ticket_number),
    INDEX idx_support_user (user_id),
    INDEX idx_support_booking (booking_id),
    INDEX idx_support_status (status),
    INDEX idx_support_assigned (assigned_to),
    CONSTRAINT fk_support_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_support_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE SET NULL,
    CONSTRAINT fk_support_assignee FOREIGN KEY (assigned_to) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ticket Discussion Messages
CREATE TABLE ticket_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    sender_user_id BIGINT UNSIGNED NOT NULL,
    message_text TEXT NOT NULL,
    attachment_url VARCHAR(255) NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE, -- Internal staff note
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_ticket_messages_ticket (ticket_id),
    CONSTRAINT fk_messages_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Damage Reports (Incident & Insurance claim tracking)
CREATE TABLE damage_reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    reported_by BIGINT UNSIGNED NOT NULL,
    handover_inspection_id BIGINT UNSIGNED NULL,
    description TEXT NOT NULL,
    estimated_repair_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    settlement_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'REPORTED', -- REPORTED, UNDER_ASSESSMENT, SETTLED, DISPUTED
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_damage_reports_public_id (public_id),
    INDEX idx_damage_booking (booking_id),
    INDEX idx_damage_vehicle (vehicle_id),
    CONSTRAINT fk_damage_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_damage_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_damage_reporter FOREIGN KEY (reported_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_damage_inspection FOREIGN KEY (handover_inspection_id) REFERENCES handover_inspections (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- In-App Notifications
CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    body TEXT NOT NULL,
    channel VARCHAR(30) NOT NULL DEFAULT 'IN_APP', -- IN_APP, EMAIL, SMS, WHATSAPP
    target_url VARCHAR(255) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_public_id (public_id),
    INDEX idx_notifications_user_read (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Notification Templates
CREATE TABLE notification_templates (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_code VARCHAR(80) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    body_template TEXT NOT NULL,
    channel VARCHAR(30) NOT NULL, -- EMAIL, SMS, WHATSAPP, IN_APP
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notif_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Platform System Settings (Dynamically configurable parameters)
CREATE TABLE system_settings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NOT NULL,
    description VARCHAR(255) NULL,
    data_type VARCHAR(20) NOT NULL DEFAULT 'STRING', -- STRING, NUMBER, BOOLEAN, JSON
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_settings_key (setting_key),
    CONSTRAINT fk_system_settings_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- System Audit Logs (Immutable security and operations log)
CREATE TABLE audit_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    action VARCHAR(80) NOT NULL, -- USER_LOGIN, BOOKING_CREATED, REFUND_INITIATED, PARTNER_KYC_APPROVED
    entity_name VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80) NULL,
    correlation_id VARCHAR(64) NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    old_values JSON NULL,
    new_values JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_audit_actor (actor_user_id),
    INDEX idx_audit_entity (entity_name, entity_id),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed Default Platform Parameters
INSERT INTO system_settings (setting_key, setting_value, description, data_type, is_public) VALUES
    ('hold.duration.seconds', '600', 'Duration in seconds for checkout inventory hold locks (10 mins)', 'NUMBER', TRUE),
    ('turnaround.buffer.minutes.default', '60', 'Default inspection and turnaround buffer between bookings', 'NUMBER', FALSE),
    ('advance.payment.percentage.default', '25.0', 'Default advance booking payment percentage', 'NUMBER', TRUE),
    ('platform.commission.rate.default', '4.00', 'Default platform commission percentage on gross rentals', 'NUMBER', FALSE),
    ('approval.deadline.hours.long', '6', 'Approval window deadline for bookings > 24 hours away', 'NUMBER', FALSE),
    ('approval.deadline.hours.medium', '2', 'Approval window deadline for bookings 6-24 hours away', 'NUMBER', FALSE),
    ('approval.deadline.minutes.urgent', '45', 'Approval window deadline for urgent bookings < 6 hours away', 'NUMBER', FALSE);

