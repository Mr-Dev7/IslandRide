package com.islandride.booking.domain;

import com.islandride.core.domain.BaseEntity;
import com.islandride.geography.domain.Location;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_modifications")
public class BookingModification extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = "mod_" + UUID.randomUUID().toString().replace("-", "");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "modified_by_user_id", nullable = false)
    private Long modifiedByUserId;

    @Column(name = "previous_start_time", nullable = false)
    private Instant previousStartTime;

    @Column(name = "previous_end_time", nullable = false)
    private Instant previousEndTime;

    @Column(name = "new_start_time", nullable = false)
    private Instant newStartTime;

    @Column(name = "new_end_time", nullable = false)
    private Instant newEndTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_pickup_location_id", nullable = false)
    private Location previousPickupLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_return_location_id", nullable = false)
    private Location previousReturnLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_pickup_location_id", nullable = false)
    private Location newPickupLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_return_location_id", nullable = false)
    private Location newReturnLocation;

    @Column(name = "previous_total_payable", nullable = false, precision = 10, scale = 2)
    private BigDecimal previousTotalPayable;

    @Column(name = "new_total_payable", nullable = false, precision = 10, scale = 2)
    private BigDecimal newTotalPayable;

    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(name = "adjustment_type", nullable = false, length = 30)
    private String adjustmentType = "NONE";

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getPublicId() {
        return publicId;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Long getModifiedByUserId() {
        return modifiedByUserId;
    }

    public void setModifiedByUserId(Long modifiedByUserId) {
        this.modifiedByUserId = modifiedByUserId;
    }

    public Instant getPreviousStartTime() {
        return previousStartTime;
    }

    public void setPreviousStartTime(Instant previousStartTime) {
        this.previousStartTime = previousStartTime;
    }

    public Instant getPreviousEndTime() {
        return previousEndTime;
    }

    public void setPreviousEndTime(Instant previousEndTime) {
        this.previousEndTime = previousEndTime;
    }

    public Instant getNewStartTime() {
        return newStartTime;
    }

    public void setNewStartTime(Instant newStartTime) {
        this.newStartTime = newStartTime;
    }

    public Instant getNewEndTime() {
        return newEndTime;
    }

    public void setNewEndTime(Instant newEndTime) {
        this.newEndTime = newEndTime;
    }

    public Location getPreviousPickupLocation() {
        return previousPickupLocation;
    }

    public void setPreviousPickupLocation(Location previousPickupLocation) {
        this.previousPickupLocation = previousPickupLocation;
    }

    public Location getPreviousReturnLocation() {
        return previousReturnLocation;
    }

    public void setPreviousReturnLocation(Location previousReturnLocation) {
        this.previousReturnLocation = previousReturnLocation;
    }

    public Location getNewPickupLocation() {
        return newPickupLocation;
    }

    public void setNewPickupLocation(Location newPickupLocation) {
        this.newPickupLocation = newPickupLocation;
    }

    public Location getNewReturnLocation() {
        return newReturnLocation;
    }

    public void setNewReturnLocation(Location newReturnLocation) {
        this.newReturnLocation = newReturnLocation;
    }

    public BigDecimal getPreviousTotalPayable() {
        return previousTotalPayable;
    }

    public void setPreviousTotalPayable(BigDecimal previousTotalPayable) {
        this.previousTotalPayable = previousTotalPayable;
    }

    public BigDecimal getNewTotalPayable() {
        return newTotalPayable;
    }

    public void setNewTotalPayable(BigDecimal newTotalPayable) {
        this.newTotalPayable = newTotalPayable;
    }

    public BigDecimal getPriceDelta() {
        return priceDelta;
    }

    public void setPriceDelta(BigDecimal priceDelta) {
        this.priceDelta = priceDelta;
    }

    public String getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(String adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
