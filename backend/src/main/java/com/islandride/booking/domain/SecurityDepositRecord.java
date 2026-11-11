package com.islandride.booking.domain;

import com.islandride.core.domain.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_deposit_records")
public class SecurityDepositRecord extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = "dep_" + UUID.randomUUID().toString().replace("-", "");

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_method", nullable = false, length = 30)
    private SecurityDepositCollectionMethod collectionMethod = SecurityDepositCollectionMethod.AT_PICKUP;

    @Column(name = "expected_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedAmount = BigDecimal.ZERO;

    @Column(name = "collected_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal collectedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "forfeited_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal forfeitedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SecurityDepositStatus status = SecurityDepositStatus.PENDING;

    @Column(name = "inspection_deadline_at")
    private Instant inspectionDeadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_status", nullable = false, length = 30)
    private DepositInspectionStatus inspectionStatus = DepositInspectionStatus.PENDING;

    @Column(name = "collected_by")
    private Long collectedBy;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "payment_channel", length = 30)
    private String paymentChannel;

    @Column(name = "receipt_reference", length = 80)
    private String receiptReference;

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
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

    public SecurityDepositCollectionMethod getCollectionMethod() {
        return collectionMethod;
    }

    public void setCollectionMethod(SecurityDepositCollectionMethod collectionMethod) {
        this.collectionMethod = collectionMethod;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public BigDecimal getForfeitedAmount() {
        return forfeitedAmount;
    }

    public void setForfeitedAmount(BigDecimal forfeitedAmount) {
        this.forfeitedAmount = forfeitedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public SecurityDepositStatus getStatus() {
        return status;
    }

    public void setStatus(SecurityDepositStatus status) {
        this.status = status;
    }

    public Instant getInspectionDeadlineAt() {
        return inspectionDeadlineAt;
    }

    public void setInspectionDeadlineAt(Instant inspectionDeadlineAt) {
        this.inspectionDeadlineAt = inspectionDeadlineAt;
    }

    public DepositInspectionStatus getInspectionStatus() {
        return inspectionStatus;
    }

    public void setInspectionStatus(DepositInspectionStatus inspectionStatus) {
        this.inspectionStatus = inspectionStatus;
    }

    public Long getCollectedBy() {
        return collectedBy;
    }

    public void setCollectedBy(Long collectedBy) {
        this.collectedBy = collectedBy;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getReceiptReference() {
        return receiptReference;
    }

    public void setReceiptReference(String receiptReference) {
        this.receiptReference = receiptReference;
    }

    public Long getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(Long releasedBy) {
        this.releasedBy = releasedBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

