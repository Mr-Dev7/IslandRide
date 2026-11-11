-- =============================================================================
-- IslandRide — Migration V10: Phase 5 Vehicle Catalog & Inventory Enhancements
-- Modules: geography, inventory, pricing, availability, administration
-- =============================================================================

-- 1. Geography enhancements (display alias & audit metadata)
ALTER TABLE islands
    ADD COLUMN display_alias VARCHAR(120) NULL AFTER name,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

UPDATE islands
SET display_alias = 'Sri Vijaya Puram (Port Blair)'
WHERE code = 'PORT_BLAIR';

INSERT INTO locations (public_id, island_id, name, code, address, latitude, longitude, contact_phone, is_active)
SELECT 'loc_pb_aberdeen_005', i.id, 'Sri Vijaya Puram Aberdeen Bazaar Hub', 'SVP_ABERDEEN',
       'Aberdeen Bazaar Clock Tower Road, Sri Vijaya Puram (Port Blair)', 11.66730000, 92.74210000, '+91-3192-234570', TRUE
FROM islands i
WHERE i.code = 'PORT_BLAIR'
  AND NOT EXISTS (SELECT 1 FROM locations l WHERE l.code = 'SVP_ABERDEEN');

INSERT INTO locations (public_id, island_id, name, code, address, latitude, longitude, contact_phone, is_active)
SELECT 'loc_hl_radhanagar_006', i.id, 'Swaraj Dweep Radhanagar Road Hub', 'HL_RADHANAGAR',
       'Vijay Nagar & Radhanagar Junction, Swaraj Dweep (Havelock)', 11.98890000, 92.96920000, '+91-3192-282150', TRUE
FROM islands i
WHERE i.code = 'HAVELOCK'
  AND NOT EXISTS (SELECT 1 FROM locations l WHERE l.code = 'HL_RADHANAGAR');

-- 2. Vehicle Categories enhancements (slug, icon, applicable attributes JSON, audit)
ALTER TABLE vehicle_categories
    ADD COLUMN slug VARCHAR(60) NULL AFTER code,
    MODIFY COLUMN description VARCHAR(500) NULL,
    ADD COLUMN icon_ref VARCHAR(255) NULL AFTER description,
    ADD COLUMN applicable_attributes JSON NULL AFTER icon_ref,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

UPDATE vehicle_categories SET
    slug = 'scooters',
    icon_ref = 'icon-scooter',
    applicable_attributes = '["transmissionType","fuelType","engineCapacityCc","mileageKmpl","seatingCapacity"]'
WHERE code = 'SCOOTER';

UPDATE vehicle_categories SET
    slug = 'motorcycles',
    icon_ref = 'icon-motorcycle',
    applicable_attributes = '["transmissionType","fuelType","engineCapacityCc","mileageKmpl","seatingCapacity"]'
WHERE code = 'MOTORCYCLE';

UPDATE vehicle_categories SET
    slug = 'hatchbacks',
    icon_ref = 'icon-hatchback',
    applicable_attributes = '["transmissionType","fuelType","engineCapacityCc","mileageKmpl","seatingCapacity"]'
WHERE code = 'HATCHBACK';

UPDATE vehicle_categories SET
    slug = 'sedans',
    icon_ref = 'icon-sedan',
    applicable_attributes = '["transmissionType","fuelType","engineCapacityCc","mileageKmpl","seatingCapacity"]'
WHERE code = 'SEDAN';

UPDATE vehicle_categories SET
    slug = 'suvs',
    icon_ref = 'icon-suv',
    applicable_attributes = '["transmissionType","fuelType","engineCapacityCc","mileageKmpl","seatingCapacity"]'
WHERE code = 'SUV';

ALTER TABLE vehicle_categories
    MODIFY COLUMN slug VARCHAR(60) NOT NULL,
    ADD UNIQUE KEY uk_veh_cat_slug (slug);

INSERT INTO vehicle_categories (name, code, slug, vehicle_type, description, icon_ref, applicable_attributes, display_order, is_active)
VALUES
    ('Jeeps / 4x4', 'JEEP', 'jeeps', 'FOUR_WHEELER',
     'Open-top and rugged 4x4 jeeps built for island coastal trails and beaches (Thar, Gypsy, Jimny).',
     'icon-jeep', '["transmissionType","fuelType","engineCapacityCc","mileageKmpl","seatingCapacity"]', 6, TRUE),
    ('Other Vehicles', 'OTHER', 'other', 'OTHER',
     'Configurable specialty island transport types such as bicycles, EV pods, or utility rentals.',
     'icon-other', '["fuelType","seatingCapacity","mileageKmpl"]', 7, TRUE);

-- 3. Configurable Vehicle Document Types
CREATE TABLE vehicle_document_types (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    requires_expiry_date BOOLEAN NOT NULL DEFAULT TRUE,
    applicable_vehicle_types VARCHAR(100) NOT NULL DEFAULT 'ALL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_veh_doc_type_code (code),
    INDEX idx_veh_doc_type_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO vehicle_document_types (code, name, description, is_mandatory, requires_expiry_date, applicable_vehicle_types, is_active, display_order)
VALUES
    ('RC_BOOK', 'Registration Certificate (RC)', 'Official transport authority registration certificate for the vehicle.', TRUE, TRUE, 'ALL', TRUE, 1),
    ('INSURANCE', 'Motor Insurance Policy', 'Valid commercial / rental vehicle third-party or comprehensive insurance policy.', TRUE, TRUE, 'ALL', TRUE, 2),
    ('PERMIT', 'Commercial / Tourist Permit', 'Island commercial tourist rental permit (Rent-a-Cab / Rent-a-Bike where applicable).', FALSE, TRUE, 'FOUR_WHEELER', TRUE, 3),
    ('FITNESS_CERTIFICATE', 'Fitness Certificate', 'Periodic mechanical and roadworthiness fitness certificate.', FALSE, TRUE, 'ALL', TRUE, 4),
    ('POLLUTION_CERTIFICATE', 'Pollution Under Control (PUC)', 'Valid emission compliance certificate for fuel vehicles.', FALSE, TRUE, 'ALL', TRUE, 5),
    ('OTHER', 'Other Supporting Document', 'Additional compliance or safety certificate required by operations.', FALSE, FALSE, 'ALL', TRUE, 6);

-- 4. Vehicles table enhancements
ALTER TABLE vehicles
    ADD COLUMN title VARCHAR(150) NOT NULL DEFAULT '' AFTER category_id,
    ADD COLUMN pickup_location_id BIGINT UNSIGNED NULL AFTER island_id,
    ADD COLUMN return_location_id BIGINT UNSIGNED NULL AFTER pickup_location_id,
    ADD COLUMN allow_different_return_same_island BOOLEAN NOT NULL DEFAULT FALSE AFTER return_location_id,
    ADD COLUMN description TEXT NULL AFTER model_year,
    ADD COLUMN mileage_kmpl DECIMAL(6,2) NULL AFTER seating_capacity,
    ADD COLUMN engine_capacity_cc INT NULL AFTER mileage_kmpl,
    ADD COLUMN vehicle_condition VARCHAR(30) NOT NULL DEFAULT 'GOOD' AFTER engine_capacity_cc,
    ADD COLUMN rental_instructions TEXT NULL AFTER vehicle_condition,
    ADD COLUMN rejection_reason TEXT NULL AFTER status,
    ADD COLUMN suspension_reason TEXT NULL AFTER rejection_reason,
    ADD COLUMN submitted_at DATETIME(6) NULL AFTER suspension_reason,
    ADD COLUMN approved_at DATETIME(6) NULL AFTER submitted_at,
    ADD COLUMN moderated_by BIGINT UNSIGNED NULL AFTER approved_at,
    ADD COLUMN moderated_at DATETIME(6) NULL AFTER moderated_by,
    ADD INDEX idx_vehicles_pickup_loc (pickup_location_id),
    ADD INDEX idx_vehicles_created_at (created_at),
    ADD CONSTRAINT fk_vehicles_pickup_loc FOREIGN KEY (pickup_location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_vehicles_return_loc FOREIGN KEY (return_location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_vehicles_moderated_by FOREIGN KEY (moderated_by) REFERENCES users (id) ON DELETE SET NULL;

UPDATE vehicles
SET title = CONCAT(make, ' ', model, ' (', model_year, ')')
WHERE title = '';

-- 5. Structured Vehicle Features table
CREATE TABLE vehicle_features (
    vehicle_id BIGINT UNSIGNED NOT NULL,
    feature_name VARCHAR(80) NOT NULL,
    PRIMARY KEY (vehicle_id, feature_name),
    CONSTRAINT fk_veh_features_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Vehicle Lifecycle Status History (Audit Trail)
CREATE TABLE vehicle_status_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    reason TEXT NULL,
    changed_by BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_veh_status_history_vehicle (vehicle_id, created_at),
    CONSTRAINT fk_veh_status_history_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_veh_status_history_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Vehicle Images enhancements
ALTER TABLE vehicle_images
    ADD COLUMN public_id VARCHAR(36) NULL AFTER id,
    ADD COLUMN storage_key VARCHAR(255) NULL AFTER image_url,
    ADD COLUMN original_filename VARCHAR(255) NULL AFTER storage_key,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER original_filename,
    ADD COLUMN file_size_bytes BIGINT UNSIGNED NULL AFTER content_type,
    ADD COLUMN uploaded_by BIGINT UNSIGNED NULL AFTER display_order,
    ADD CONSTRAINT fk_veh_images_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL;

UPDATE vehicle_images
SET public_id = CONCAT('img_', REPLACE(UUID(), '-', ''))
WHERE public_id IS NULL;

ALTER TABLE vehicle_images
    MODIFY COLUMN public_id VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_veh_images_public_id (public_id);

-- 8. Vehicle Documents enhancements
ALTER TABLE vehicle_documents
    ADD COLUMN public_id VARCHAR(36) NULL AFTER id,
    ADD COLUMN document_type_id BIGINT UNSIGNED NULL AFTER vehicle_id,
    ADD COLUMN storage_key VARCHAR(255) NULL AFTER document_url,
    ADD COLUMN original_filename VARCHAR(255) NULL AFTER storage_key,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER original_filename,
    ADD COLUMN file_size_bytes BIGINT UNSIGNED NULL AFTER content_type,
    ADD COLUMN issue_date DATE NULL AFTER file_size_bytes,
    ADD COLUMN verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' AFTER is_verified,
    ADD COLUMN rejection_reason TEXT NULL AFTER verification_status,
    ADD COLUMN uploaded_by BIGINT UNSIGNED NULL AFTER rejection_reason,
    ADD COLUMN verified_by BIGINT UNSIGNED NULL AFTER uploaded_by,
    ADD COLUMN verified_at DATETIME(6) NULL AFTER verified_by,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
    ADD INDEX idx_veh_docs_expiry (expiry_date, verification_status),
    ADD CONSTRAINT fk_veh_docs_type FOREIGN KEY (document_type_id) REFERENCES vehicle_document_types (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_veh_docs_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_veh_docs_verified_by FOREIGN KEY (verified_by) REFERENCES users (id) ON DELETE SET NULL;

UPDATE vehicle_documents
SET public_id = CONCAT('vdc_', REPLACE(UUID(), '-', '')),
    verification_status = CASE WHEN is_verified = TRUE THEN 'VERIFIED' ELSE 'PENDING' END
WHERE public_id IS NULL;

ALTER TABLE vehicle_documents
    MODIFY COLUMN public_id VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_veh_docs_public_id (public_id);

-- 9. Vehicle Pricing Configuration table
CREATE TABLE vehicle_pricing_configs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    vehicle_id BIGINT UNSIGNED NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    hourly_rate DECIMAL(10,2) NULL,
    daily_rate DECIMAL(10,2) NOT NULL,
    weekly_rate DECIMAL(10,2) NULL,
    security_deposit DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    min_rental_hours INT NOT NULL DEFAULT 6,
    max_rental_days INT NOT NULL DEFAULT 30,
    included_km_per_day INT NULL,
    extra_km_charge DECIMAL(10,2) NULL,
    extra_hour_charge DECIMAL(10,2) NULL,
    cleaning_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    additional_charges_description VARCHAR(500) NULL,
    late_return_fee_per_hour DECIMAL(10,2) NULL,
    late_return_flat_penalty DECIMAL(10,2) NULL,
    late_return_grace_minutes INT NOT NULL DEFAULT 30,
    valid_from DATETIME(6) NULL,
    valid_to DATETIME(6) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    updated_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_veh_pricing_public_id (public_id),
    INDEX idx_veh_pricing_vehicle_active (vehicle_id, is_active),
    CONSTRAINT fk_veh_pricing_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Vehicle Unavailability enhancements
ALTER TABLE vehicle_unavailability
    ADD COLUMN public_id VARCHAR(36) NULL AFTER id,
    ADD COLUMN block_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL_BLOCK' AFTER vehicle_id,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER reason,
    ADD COLUMN restriction_details VARCHAR(255) NULL AFTER notes,
    ADD COLUMN is_removable BOOLEAN NOT NULL DEFAULT TRUE AFTER restriction_details,
    ADD COLUMN cancelled_by BIGINT UNSIGNED NULL AFTER created_by,
    ADD COLUMN cancelled_at DATETIME(6) NULL AFTER cancelled_by,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
    ADD INDEX idx_veh_unavail_active_range (vehicle_id, status, start_time, end_time),
    ADD CONSTRAINT fk_veh_unavail_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id) ON DELETE SET NULL;

UPDATE vehicle_unavailability
SET public_id = CONCAT('blk_', REPLACE(UUID(), '-', '')),
    block_type = CASE WHEN reason = 'MAINTENANCE' THEN 'MAINTENANCE' WHEN reason = 'DAMAGE_REPAIR' THEN 'DAMAGE_REPAIR' ELSE 'MANUAL_BLOCK' END
WHERE public_id IS NULL;

ALTER TABLE vehicle_unavailability
    MODIFY COLUMN public_id VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_veh_unavail_public_id (public_id);

-- 11. Configurable Currency & Tax defaults in system_settings
INSERT INTO system_settings (setting_key, setting_value, description, data_type, is_public)
SELECT 'platform.currency', 'INR', 'Default marketplace currency ISO 4217 code', 'STRING', TRUE
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'platform.currency');

INSERT INTO system_settings (setting_key, setting_value, description, data_type, is_public)
SELECT 'platform.gst.percentage', '5.00', 'Default configurable GST tax rate percentage for rentals', 'NUMBER', TRUE
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'platform.gst.percentage');

