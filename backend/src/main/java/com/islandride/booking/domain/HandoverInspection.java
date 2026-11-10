package com.islandride.booking.domain;

import com.islandride.core.domain.BaseEntity;
import com.islandride.identityaccess.domain.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "handover_inspections")
public class HandoverInspection extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = "hnd_" + UUID.randomUUID().toString().replace("-", "");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "handover_type", nullable = false, length = 20)
    private HandoverType handoverType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_user_id", nullable = false)
    private User staffUser;

    @Column(name = "odometer_reading", nullable = false)
    private int odometerReading;

    @Column(name = "fuel_percentage", nullable = false)
    private int fuelPercentage;

    @Column(name = "checklist_data", columnDefinition = "JSON")
    private String checklistData;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HandoverEvidence> evidenceList = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (completedAt == null) {
            completedAt = Instant.now();
        }
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public HandoverType getHandoverType() {
        return handoverType;
    }

    public void setHandoverType(HandoverType handoverType) {
        this.handoverType = handoverType;
    }

    public User getStaffUser() {
        return staffUser;
    }

    public void setStaffUser(User staffUser) {
        this.staffUser = staffUser;
    }

    public int getOdometerReading() {
        return odometerReading;
    }

    public void setOdometerReading(int odometerReading) {
        this.odometerReading = odometerReading;
    }

    public int getFuelPercentage() {
        return fuelPercentage;
    }

    public void setFuelPercentage(int fuelPercentage) {
        this.fuelPercentage = fuelPercentage;
    }

    public String getChecklistData() {
        return checklistData;
    }

    public void setChecklistData(String checklistData) {
        this.checklistData = checklistData;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<HandoverEvidence> getEvidenceList() {
        return evidenceList;
    }

    public void setEvidenceList(List<HandoverEvidence> evidenceList) {
        this.evidenceList = evidenceList;
    }

    public void addEvidence(EvidenceType type, String s3Key, String mimeType, Double lat, Double lng) {
        HandoverEvidence evidence = new HandoverEvidence();
        evidence.setInspection(this);
        evidence.setEvidenceType(type);
        evidence.setImageS3Key(s3Key);
        if (mimeType != null) {
            evidence.setMediaMimeType(mimeType);
        }
        if (lat != null) {
            evidence.setLatitude(java.math.BigDecimal.valueOf(lat));
        }
        if (lng != null) {
            evidence.setLongitude(java.math.BigDecimal.valueOf(lng));
        }
        this.evidenceList.add(evidence);
    }
}

