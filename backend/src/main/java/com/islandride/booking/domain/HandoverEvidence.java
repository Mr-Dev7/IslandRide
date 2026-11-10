package com.islandride.booking.domain;

import com.islandride.core.domain.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "handover_evidence")
public class HandoverEvidence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private HandoverInspection inspection;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 40)
    private EvidenceType evidenceType;

    @Column(name = "image_s3_key", nullable = false)
    private String imageS3Key;

    @Column(name = "media_mime_type", nullable = false, length = 50)
    private String mediaMimeType = "image/jpeg";

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (capturedAt == null) {
            capturedAt = Instant.now();
        }
    }

    public HandoverInspection getInspection() {
        return inspection;
    }

    public void setInspection(HandoverInspection inspection) {
        this.inspection = inspection;
    }

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(EvidenceType evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getImageS3Key() {
        return imageS3Key;
    }

    public void setImageS3Key(String imageS3Key) {
        this.imageS3Key = imageS3Key;
    }

    public String getMediaMimeType() {
        return mediaMimeType;
    }

    public void setMediaMimeType(String mediaMimeType) {
        this.mediaMimeType = mediaMimeType;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }
}

