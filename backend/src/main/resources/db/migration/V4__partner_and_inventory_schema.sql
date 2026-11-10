-- =============================================================================
-- IslandRide — Migration V4: Partner Management & Vehicle Inventory Schema
-- Modules: com.islandride.partner, com.islandride.inventory
-- =============================================================================

-- Businesses table (Partner rental agencies)
CREATE TABLE businesses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255) NOT NULL,
    gstin VARCHAR(15) NULL,
    pan_number VARCHAR(10) NULL,
    verification_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    rejection_reason TEXT NULL,
    commission_rate DECIMAL(5,2) NOT NULL DEFAULT 4.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    created_by BIGINT UNSIGNED NULL,
    updated_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_businesses_public_id (public_id),
    INDEX idx_businesses_owner (owner_user_id),
    INDEX idx_businesses_trade_name (trade_name),
    INDEX idx_businesses_gstin (gstin),
    INDEX idx_businesses_pan (pan_number),
    INDEX idx_businesses_verification (verification_status),
    INDEX idx_businesses_deleted_at (deleted_at),
    CONSTRAINT fk_businesses_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Business Locations junction table
CREATE TABLE business_locations (
    business_id BIGINT UNSIGNED NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_pickup_available BOOLEAN NOT NULL DEFAULT TRUE,
    is_return_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (business_id, location_id),
    CONSTRAINT fk_bus_loc_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_bus_loc_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Business Verification Documents
CREATE TABLE business_documents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_id BIGINT UNSIGNED NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    document_number VARCHAR(80) NULL,
    document_url VARCHAR(255) NOT NULL,
    verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    verified_by BIGINT UNSIGNED NULL,
    verified_at DATETIME(6) NULL,
    rejection_reason TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_bus_docs_business (business_id),
    INDEX idx_bus_docs_status (verification_status),
    CONSTRAINT fk_bus_docs_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Partner Staff memberships
CREATE TABLE partner_staff (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role_in_business VARCHAR(30) NOT NULL DEFAULT 'STAFF',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_staff_user (user_id),
    INDEX idx_partner_staff_bus (business_id),
    CONSTRAINT fk_partner_staff_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_partner_staff_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Partner Bank Accounts for Settlements
CREATE TABLE partner_bank_accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_id BIGINT UNSIGNED NOT NULL,
    account_holder_name VARCHAR(150) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    ifsc_code VARCHAR(20) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    branch_name VARCHAR(100) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_partner_bank_bus (business_id),
    CONSTRAINT fk_partner_bank_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vehicle Categories table
CREATE TABLE vehicle_categories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    code VARCHAR(40) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL, -- TWO_WHEELER, FOUR_WHEELER
    description VARCHAR(255) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_veh_cat_name (name),
    UNIQUE KEY uk_veh_cat_code (code),
    INDEX idx_veh_cat_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vehicles table (Fleet Inventory)
CREATE TABLE vehicles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    island_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    make VARCHAR(80) NOT NULL,
    model VARCHAR(80) NOT NULL,
    model_year INT NOT NULL,
    registration_number VARCHAR(20) NOT NULL,
    color VARCHAR(40) NULL,
    transmission_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL', -- MANUAL, AUTOMATIC
    fuel_type VARCHAR(20) NOT NULL DEFAULT 'PETROL', -- PETROL, DIESEL, ELECTRIC
    seating_capacity INT NOT NULL DEFAULT 2,
    daily_rate DECIMAL(10,2) NOT NULL,
    security_deposit DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    turnaround_buffer_minutes INT NOT NULL DEFAULT 60,
    booking_mode VARCHAR(20) NOT NULL DEFAULT 'INSTANT', -- INSTANT, APPROVAL
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PENDING_REVIEW, ACTIVE, UNDER_MAINTENANCE, RETIRED
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    created_by BIGINT UNSIGNED NULL,
    updated_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicles_public_id (public_id),
    UNIQUE KEY uk_vehicles_reg_no (registration_number),
    INDEX idx_vehicles_business (business_id),
    INDEX idx_vehicles_island (island_id),
    INDEX idx_vehicles_category (category_id),
    INDEX idx_vehicles_status (status),
    INDEX idx_vehicles_booking_mode (booking_mode),
    INDEX idx_vehicles_deleted_at (deleted_at),
    -- Multi-column index for fast tourist catalog discovery & filtering
    INDEX idx_vehicles_search (island_id, status, category_id, booking_mode, daily_rate),
    CONSTRAINT fk_vehicles_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicles_island FOREIGN KEY (island_id) REFERENCES islands (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicles_category FOREIGN KEY (category_id) REFERENCES vehicle_categories (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vehicle Images table
CREATE TABLE vehicle_images (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_veh_images_vehicle (vehicle_id),
    CONSTRAINT fk_veh_images_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vehicle Compliance & Registration Documents (RC, Insurance, Pollution)
CREATE TABLE vehicle_documents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    document_type VARCHAR(40) NOT NULL, -- RC_BOOK, INSURANCE, POLLUTION_CERTIFICATE, COMMERCIAL_PERMIT
    document_number VARCHAR(80) NULL,
    document_url VARCHAR(255) NOT NULL,
    expiry_date DATE NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_veh_docs_vehicle (vehicle_id),
    CONSTRAINT fk_veh_docs_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vehicle Unavailability / Maintenance Schedule
CREATE TABLE vehicle_unavailability (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    reason VARCHAR(50) NOT NULL DEFAULT 'MAINTENANCE', -- MAINTENANCE, DAMAGE_REPAIR, PARTNER_BLOCKED
    notes TEXT NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_veh_unavail_range (vehicle_id, start_time, end_time),
    CONSTRAINT fk_veh_unavail_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed Vehicle Categories
INSERT INTO vehicle_categories (id, name, code, vehicle_type, description, display_order, is_active) VALUES
    (1, 'Scooter / Moped', 'SCOOTER', 'TWO_WHEELER', 'Gearless 110cc-125cc scooters ideal for island sightseeing (Activa, Jupiter, Access).', 1, TRUE),
    (2, 'Motorcycle', 'MOTORCYCLE', 'TWO_WHEELER', '150cc-350cc geared bikes for adventurous coastal touring (Royal Enfield, Pulsar).', 2, TRUE),
    (3, 'Hatchback', 'HATCHBACK', 'FOUR_WHEELER', 'Compact 4-seater cars easy to park and navigate island roads (Swift, i10, WagonR).', 3, TRUE),
    (4, 'Sedan', 'SEDAN', 'FOUR_WHEELER', 'Comfortable 5-seater air-conditioned sedans for family road trips (Dzire, Amaze).', 4, TRUE),
    (5, 'SUV / MUV', 'SUV', 'FOUR_WHEELER', 'Spacious 6-7 seater vehicles with high ground clearance (Ertiga, Scorpio, Innova).', 5, TRUE);

