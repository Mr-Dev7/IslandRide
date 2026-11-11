package com.islandride.pricing.domain;

import com.islandride.core.domain.BaseEntity;
import com.islandride.geography.domain.Location;
import com.islandride.partner.domain.Business;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "partner_operating_hours")
public class PartnerOperatingHours extends BaseEntity {

    public static final String DEFAULT_SCOPE = "DEFAULT";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "scope_key", nullable = false, length = 40)
    private String scopeKey = DEFAULT_SCOPE;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (scopeKey == null || scopeKey.isBlank()) {
            scopeKey = location != null && location.getId() != null
                    ? "LOC_" + location.getId()
                    : DEFAULT_SCOPE;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
        this.scopeKey = location != null && location.getId() != null
                ? "LOC_" + location.getId()
                : DEFAULT_SCOPE;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

