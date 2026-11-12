-- =============================================================================
-- IslandRide — Migration V12: Stage 6A Audit Remediation Schema Refinements
-- Modules: com.islandride.partner, com.islandride.inventory, com.islandride.pricing
-- =============================================================================

-- 1. Allow a user to belong to multiple businesses on partner_staff (per-business uniqueness)
-- Keep standalone index on user_id first so fk_partner_staff_user always retains a valid index
ALTER TABLE partner_staff
    ADD INDEX idx_partner_staff_user (user_id),
    DROP INDEX uk_partner_staff_user,
    ADD UNIQUE KEY uk_partner_staff_business_user (business_id, user_id);

-- 2. Make vehicle-level turnaround buffer nullable so NULL cleanly inherits PartnerPolicy -> SystemSettings
ALTER TABLE vehicles
    MODIFY COLUMN turnaround_buffer_minutes INT NULL DEFAULT NULL;

-- 3. Make PartnerPolicy configurable override columns nullable so non-overridden fields
--    dynamically inherit platform system_settings values updated by admins
ALTER TABLE partner_policies
    MODIFY COLUMN min_rental_hours INT NULL DEFAULT NULL,
    MODIFY COLUMN max_rental_days INT NULL DEFAULT NULL,
    MODIFY COLUMN turnaround_buffer_minutes INT NULL DEFAULT NULL,
    MODIFY COLUMN advance_payment_percentage DECIMAL(5,2) NULL DEFAULT NULL,
    MODIFY COLUMN full_payment_cutoff_hours INT NULL DEFAULT NULL,
    MODIFY COLUMN cancellation_deadline_hours INT NULL DEFAULT NULL,
    MODIFY COLUMN free_cancellation_allowed BOOLEAN NULL DEFAULT NULL,
    MODIFY COLUMN cancellation_fee_percentage DECIMAL(5,2) NULL DEFAULT NULL,
    MODIFY COLUMN no_show_grace_minutes INT NULL DEFAULT NULL,
    MODIFY COLUMN no_show_penalty_percentage DECIMAL(5,2) NULL DEFAULT NULL,
    MODIFY COLUMN deposit_inspection_window_hours INT NULL DEFAULT NULL,
    MODIFY COLUMN security_deposit_collection_method VARCHAR(30) NULL DEFAULT NULL,
    MODIFY COLUMN gateway_fee_treatment VARCHAR(40) NULL DEFAULT NULL,
    MODIFY COLUMN rescheduling_allowed_for_disruption BOOLEAN NULL DEFAULT NULL;

-- 4. Seed remaining platform policy defaults into system_settings (eliminates hardcoded fallbacks)
INSERT IGNORE INTO system_settings (setting_key, setting_value, description, data_type, is_public) VALUES
    ('booking.min_rental_hours.default', '6', 'Default minimum rental duration in hours when not overridden by partner policy', 'NUMBER', TRUE),
    ('booking.max_rental_days.default', '30', 'Default maximum rental duration in days when not overridden by partner policy', 'NUMBER', TRUE),
    ('booking.cancellation.deadline_hours.default', '24', 'Default hours before pickup eligible for free cancellation', 'NUMBER', TRUE),
    ('booking.cancellation.free_allowed.default', 'true', 'Default flag indicating whether free cancellation before deadline is allowed', 'BOOLEAN', TRUE),
    ('booking.cancellation.fee_percentage.default', '10.00', 'Default cancellation fee percentage (V5 seed preserved)', 'NUMBER', TRUE),
    ('booking.deposit.inspection_window_hours.default', '24', 'Default post-return inspection window hours for security deposit release', 'NUMBER', TRUE),
    ('booking.disruption.rescheduling_allowed.default', 'true', 'Default flag allowing disruption rescheduling when not overridden by partner policy', 'BOOLEAN', TRUE);

