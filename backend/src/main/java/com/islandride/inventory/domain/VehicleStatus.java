package com.islandride.inventory.domain;

public enum VehicleStatus {
    DRAFT,
    PENDING_REVIEW,
    ACTIVE,
    REJECTED,
    PAUSED,
    MAINTENANCE,
    SUSPENDED,
    ARCHIVED;

    public boolean isPubliclyDiscoverable() {
        return this == ACTIVE;
    }

    public boolean isEditableByPartner() {
        return this != ARCHIVED;
    }
}
