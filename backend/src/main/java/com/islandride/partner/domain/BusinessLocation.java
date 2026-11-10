package com.islandride.partner.domain;

import com.islandride.geography.domain.Location;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "business_locations")
public class BusinessLocation {

    @EmbeddedId
    private BusinessLocationId id = new BusinessLocationId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("businessId")
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("locationId")
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "is_pickup_available", nullable = false)
    private boolean pickupAvailable = true;

    @Column(name = "is_return_available", nullable = false)
    private boolean returnAvailable = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public BusinessLocationId getId() {
        return id;
    }

    public void setId(BusinessLocationId id) {
        this.id = id;
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
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public boolean isPickupAvailable() {
        return pickupAvailable;
    }

    public void setPickupAvailable(boolean pickupAvailable) {
        this.pickupAvailable = pickupAvailable;
    }

    public boolean isReturnAvailable() {
        return returnAvailable;
    }

    public void setReturnAvailable(boolean returnAvailable) {
        this.returnAvailable = returnAvailable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

