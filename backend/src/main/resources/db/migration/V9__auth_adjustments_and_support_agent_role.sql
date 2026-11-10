-- =============================================================================
-- IslandRide — Migration V9: Auth Adjustments & Support Agent Role Standardization
-- Standardize canonical role name to ROLE_SUPPORT_AGENT and add token family tracking
-- =============================================================================

-- 1. Standardize Role Name: ROLE_CUSTOMER_SUPPORT -> ROLE_SUPPORT_AGENT
UPDATE roles 
SET name = 'ROLE_SUPPORT_AGENT', 
    description = 'Support Agent for tickets and customer assistance' 
WHERE name = 'ROLE_CUSTOMER_SUPPORT';

-- 2. Add family_id to refresh_tokens for isolated token-family replay revocation
ALTER TABLE refresh_tokens 
    ADD COLUMN family_id VARCHAR(36) NOT NULL AFTER user_id,
    ADD INDEX idx_refresh_tokens_family (family_id);

