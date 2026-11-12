-- ==============================================================================
-- IslandRide — Migration V14: Phase 6C Payment Adapters & Partner Financials
-- Authoritative Schema Extension for Gateway HTTP Adapters & Partner Financial Flow
-- Modules: com.islandride.paymentfinance, com.islandride.commission, com.islandride.partner
-- ==============================================================================

-- 1. Seed configurable Phase 6C payment adapter & partner settlement parameters
INSERT IGNORE INTO system_settings (setting_key, setting_value, description, data_type, is_public) VALUES
    ('payment.gateway.http_timeout_seconds', '10', 'HTTP socket and connect timeout in seconds for gateway adapter calls', 'NUMBER', FALSE),
    ('partner.settlement.auto_offset_receivables', 'true', 'Whether partner receivables are automatically deducted during settlement cycles', 'BOOLEAN', FALSE),
    ('partner.settlement.cycle_days', '7', 'Default weekly settlement cycle duration in days for partner payout calculations', 'NUMBER', FALSE);

