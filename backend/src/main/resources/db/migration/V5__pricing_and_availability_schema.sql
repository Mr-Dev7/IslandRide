-- =============================================================================
-- IslandRide — Migration V5: Pricing Rules & Ephemeral Availability Holds Schema
-- Modules: com.islandride.pricing, com.islandride.availability
-- =============================================================================

-- Pricing Rules table (Duration-based discounts, surge adjustments)
CREATE TABLE pricing_rules (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vehicle_id BIGINT UNSIGNED NULL, -- NULL implies global/category default rule
    category_id BIGINT UNSIGNED NULL,
    business_id BIGINT UNSIGNED NULL,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(40) NOT NULL, -- DURATION_DISCOUNT, WEEKEND_SURGE, EARLY_BIRD
    min_days INT NOT NULL DEFAULT 1,
    adjustment_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE', -- PERCENTAGE, FIXED_AMOUNT
    adjustment_value DECIMAL(10,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_pricing_rules_veh (vehicle_id),
    INDEX idx_pricing_rules_bus (business_id),
    CONSTRAINT fk_pricing_veh FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_pricing_cat FOREIGN KEY (category_id) REFERENCES vehicle_categories (id) ON DELETE CASCADE,
    CONSTRAINT fk_pricing_bus FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seasonal Peak Pricing Calendars (e.g., Dec-Jan Peak Island Season)
CREATE TABLE seasonal_prices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    island_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NULL,
    season_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(4,2) NOT NULL DEFAULT 1.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_seasonal_island_date (island_id, start_date, end_date),
    CONSTRAINT fk_seasonal_island FOREIGN KEY (island_id) REFERENCES islands (id) ON DELETE CASCADE,
    CONSTRAINT fk_seasonal_category FOREIGN KEY (category_id) REFERENCES vehicle_categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Partner Operational Policies (Configurable overrides for buffers, cancellation, deposits)
CREATE TABLE partner_policies (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_id BIGINT UNSIGNED NOT NULL,
    min_rental_hours INT NOT NULL DEFAULT 6,
    max_rental_days INT NOT NULL DEFAULT 30,
    turnaround_buffer_minutes INT NOT NULL DEFAULT 60,
    advance_payment_percentage DECIMAL(5,2) NOT NULL DEFAULT 25.00,
    cancellation_deadline_hours INT NOT NULL DEFAULT 24,
    free_cancellation_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    cancellation_fee_percentage DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    deposit_inspection_window_hours INT NOT NULL DEFAULT 24,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_policies_bus (business_id),
    CONSTRAINT fk_policies_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Durable MySQL Inventory Holds (Source of Truth protecting checkout locks)
CREATE TABLE inventory_holds (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    hold_duration_seconds INT NOT NULL DEFAULT 600, -- Default 10 minutes (configurable)
    status VARCHAR(20) NOT NULL DEFAULT 'HELD', -- HELD, CONVERTED, EXPIRED, RELEASED
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_holds_public_id (public_id),
    INDEX idx_holds_user (user_id),
    -- High performance range index to evaluate active concurrent holds
    INDEX idx_holds_availability (vehicle_id, status, start_time, end_time),
    -- Background cleaner index to purge or expire stale holds
    INDEX idx_holds_expiration (status, expires_at),
    CONSTRAINT fk_holds_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_holds_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

