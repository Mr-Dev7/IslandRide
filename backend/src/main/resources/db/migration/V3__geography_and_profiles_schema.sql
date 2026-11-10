-- =============================================================================
-- IslandRide — Migration V3: Geography & Profiles Module Schema
-- Modules: com.islandride.geography, com.islandride.usermanagement
-- =============================================================================

-- Countries table
CREATE TABLE countries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    iso_code VARCHAR(3) NOT NULL,
    phone_code VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_countries_iso (iso_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- States / Union Territories table
CREATE TABLE states (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    country_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_states_code (code),
    CONSTRAINT fk_states_country FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Islands table (Data-driven geography)
CREATE TABLE islands (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    state_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(30) NOT NULL,
    description TEXT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_islands_code (code),
    INDEX idx_islands_active (is_active),
    CONSTRAINT fk_islands_state FOREIGN KEY (state_id) REFERENCES states (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Locations / Hubs table (Jetty, Airport, Station hubs)
CREATE TABLE locations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    island_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(30) NOT NULL,
    address TEXT NOT NULL,
    latitude DECIMAL(10,8) NULL,
    longitude DECIMAL(11,8) NULL,
    contact_phone VARCHAR(20) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_locations_public_id (public_id),
    UNIQUE KEY uk_locations_code (code),
    INDEX idx_locations_island (island_id),
    INDEX idx_locations_active (is_active),
    INDEX idx_locations_deleted_at (deleted_at),
    CONSTRAINT fk_locations_island FOREIGN KEY (island_id) REFERENCES islands (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tourist Profiles table
CREATE TABLE tourist_profiles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    driving_license_number VARCHAR(50) NULL,
    driving_license_expiry DATE NULL,
    driving_license_image_url VARCHAR(255) NULL,
    emergency_contact_name VARCHAR(100) NULL,
    emergency_contact_phone VARCHAR(20) NULL,
    nationality VARCHAR(50) NOT NULL DEFAULT 'Indian',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tourist_profiles_user (user_id),
    INDEX idx_tourist_profiles_dl (driving_license_number),
    CONSTRAINT fk_tourist_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Admin Profiles table
CREATE TABLE admin_profiles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    department VARCHAR(80) NOT NULL,
    designation VARCHAR(80) NOT NULL,
    employee_id VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_profiles_user (user_id),
    UNIQUE KEY uk_admin_profiles_employee (employee_id),
    CONSTRAINT fk_admin_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed Initial Geography: India -> Andaman & Nicobar -> Target Islands & Primary Hubs
INSERT INTO countries (id, name, iso_code, phone_code, is_active) VALUES
    (1, 'India', 'IND', '+91', TRUE);

INSERT INTO states (id, country_id, name, code, is_active) VALUES
    (1, 1, 'Andaman and Nicobar Islands', 'AN', TRUE);

INSERT INTO islands (id, state_id, name, code, description, display_order, is_active) VALUES
    (1, 1, 'South Andaman (Port Blair)', 'PORT_BLAIR', 'Capital island with Veer Savarkar International Airport and Phoenix Bay Jetty.', 1, TRUE),
    (2, 1, 'Swaraj Dweep (Havelock)', 'HAVELOCK', 'Premier tourism destination with Radhanagar & Elephant Beach.', 2, TRUE),
    (3, 1, 'Shaheed Dweep (Neil)', 'NEIL', 'Serene island known for Bharatpur and Laxmanpur beaches.', 3, TRUE),
    (4, 1, 'North & Middle Andaman (Diglipur)', 'DIGLIPUR', 'Eco-tourism hub with Ross & Smith Islands.', 4, TRUE);

INSERT INTO locations (id, public_id, island_id, name, code, address, latitude, longitude, contact_phone, is_active) VALUES
    (1, 'loc_pb_airport_001', 1, 'Port Blair Airport Hub', 'PB_AIRPORT', 'Veer Savarkar International Airport, Lamba Line, Port Blair', 11.64140000, 92.72970000, '+91-3192-234567', TRUE),
    (2, 'loc_pb_jetty_002', 1, 'Phoenix Bay Jetty Hub', 'PB_JETTY', 'Phoenix Bay Jetty, Marine Hill, Port Blair', 11.67080000, 92.73520000, '+91-3192-234568', TRUE),
    (3, 'loc_havelock_jetty_003', 2, 'Havelock Jetty Hub', 'HAVELOCK_JETTY', 'Govind Nagar Jetty, Swaraj Dweep', 11.98330000, 92.98330000, '+91-3192-282100', TRUE),
    (4, 'loc_neil_jetty_004', 3, 'Neil Island Jetty Hub', 'NEIL_JETTY', 'Bharatpur Jetty, Shaheed Dweep', 11.83330000, 93.05000000, '+91-3192-282200', TRUE);

