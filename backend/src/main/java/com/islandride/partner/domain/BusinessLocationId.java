package com.islandride.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class BusinessLocationId implements Serializable {

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "location_id")
    private Long locationId;

    public BusinessLocationId() {}

    public BusinessLocationId(Long businessId, Long locationId) {
        this.businessId = businessId;
        this.locationId = locationId;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessLocationId that = (BusinessLocationId) o;
        return Objects.equals(businessId, that.businessId) && Objects.equals(locationId, that.locationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(businessId, locationId);
    }
}

