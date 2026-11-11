package com.islandride.booking.domain;

import com.islandride.availability.domain.InventoryHold;
import com.islandride.core.domain.AuditableEntity;
import com.islandride.geography.domain.Location;
import com.islandride.identityaccess.domain.User;
import com.islandride.inventory.domain.BookingMode;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.partner.domain.Business;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking extends AuditableEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = "bkg_" + UUID.randomUUID().toString().replace("-", "");

    @Column(name = "booking_reference", nullable = false, unique = true, length = 20, updatable = false)
    private String bookingReference;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_hold_id")
    private InventoryHold inventoryHold;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_location_id", nullable = false)
    private Location pickupLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_location_id", nullable = false)
    private Location returnLocation;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "total_hours")
    private Integer totalHours;

    @Column(name = "pricing_plan_public_id", length = 36)
    private String pricingPlanPublicId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "daily_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "weekly_rate", precision = 10, scale = 2)
    private BigDecimal weeklyRate;

    @Column(name = "base_rental_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseRentalAmount;

    @Column(name = "seasonal_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal seasonalMultiplier = new BigDecimal("1.00");

    @Column(name = "seasonal_adjustment_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal seasonalAdjustmentAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "surcharge_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal surchargeAmount = BigDecimal.ZERO;

    @Column(name = "cleaning_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal cleaningFee = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "gst_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercentage = new BigDecimal("5.00");

    @Column(name = "commission_rate_snapshot", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRateSnapshot = new BigDecimal("4.00");

    @Column(name = "security_deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal securityDepositAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_collection_method", nullable = false, length = 30)
    private SecurityDepositCollectionMethod depositCollectionMethod = SecurityDepositCollectionMethod.AT_PICKUP;

    @Column(name = "total_payable_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPayableAmount;

    @Column(name = "advance_paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal advancePaidAmount = BigDecimal.ZERO;

    @Column(name = "pickup_balance_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal pickupBalanceDue = BigDecimal.ZERO;

    @Column(name = "advance_percentage_applied", nullable = false, precision = 5, scale = 2)
    private BigDecimal advancePercentageApplied = new BigDecimal("25.00");

    @Column(name = "turnaround_buffer_minutes", nullable = false)
    private int turnaroundBufferMinutes = 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_summary_status", nullable = false, length = 30)
    private PaymentSummaryStatus paymentSummaryStatus = PaymentSummaryStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "overbooking_exception_status", nullable = false, length = 30)
    private OverbookingExceptionStatus overbookingExceptionStatus = OverbookingExceptionStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 20)
    private PaymentMode paymentMode = PaymentMode.FULL;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_mode", nullable = false, length = 20)
    private BookingMode bookingMode = BookingMode.INSTANT;

    @Column(name = "customer_dl_number", length = 50)
    private String customerDlNumber;

    @Column(name = "customer_emergency_contact", length = 30)
    private String customerEmergencyContact;

    @Column(name = "dl_confirmed", nullable = false)
    private boolean dlConfirmed = false;

    @Column(name = "pricing_snapshot_json", columnDefinition = "JSON")
    private String pricingSnapshotJson;

    @Column(name = "cancellation_policy_snapshot", columnDefinition = "JSON")
    private String cancellationPolicySnapshot;

    @Column(name = "approval_expires_at")
    private Instant approvalExpiresAt;

    @Column(name = "payment_deadline_at")
    private Instant paymentDeadlineAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_by_role", length = 30)
    private String cancelledByRole;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "disruption_exception_type", length = 40)
    private DisruptionExceptionType disruptionExceptionType;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    @Column(name = "no_show_marked_by")
    private Long noShowMarkedBy;

    @Column(name = "no_show_evidence_notes", columnDefinition = "TEXT")
    private String noShowEvidenceNotes;

    @Column(name = "no_show_evidence_ref", length = 255)
    private String noShowEvidenceRef;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public Location getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(Location pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public Location getReturnLocation() {
        return returnLocation;
    }

    public void setReturnLocation(Location returnLocation) {
        this.returnLocation = returnLocation;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    public BigDecimal getBaseRentalAmount() {
        return baseRentalAmount;
    }

    public void setBaseRentalAmount(BigDecimal baseRentalAmount) {
        this.baseRentalAmount = baseRentalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getSecurityDepositAmount() {
        return securityDepositAmount;
    }

    public void setSecurityDepositAmount(BigDecimal securityDepositAmount) {
        this.securityDepositAmount = securityDepositAmount;
    }

    public BigDecimal getTotalPayableAmount() {
        return totalPayableAmount;
    }

    public void setTotalPayableAmount(BigDecimal totalPayableAmount) {
        this.totalPayableAmount = totalPayableAmount;
    }

    public BigDecimal getAdvancePaidAmount() {
        return advancePaidAmount;
    }

    public void setAdvancePaidAmount(BigDecimal advancePaidAmount) {
        this.advancePaidAmount = advancePaidAmount;
    }

    public BigDecimal getPickupBalanceDue() {
        return pickupBalanceDue;
    }

    public void setPickupBalanceDue(BigDecimal pickupBalanceDue) {
        this.pickupBalanceDue = pickupBalanceDue;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(PaymentMode paymentMode) {
        this.paymentMode = paymentMode;
    }

    public BookingMode getBookingMode() {
        return bookingMode;
    }

    public void setBookingMode(BookingMode bookingMode) {
        this.bookingMode = bookingMode;
    }

    public Instant getApprovalExpiresAt() {
        return approvalExpiresAt;
    }

    public void setApprovalExpiresAt(Instant approvalExpiresAt) {
        this.approvalExpiresAt = approvalExpiresAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<BookingStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<BookingStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public InventoryHold getInventoryHold() {
        return inventoryHold;
    }

    public void setInventoryHold(InventoryHold inventoryHold) {
        this.inventoryHold = inventoryHold;
    }

    public Integer getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(Integer totalHours) {
        this.totalHours = totalHours;
    }

    public String getPricingPlanPublicId() {
        return pricingPlanPublicId;
    }

    public void setPricingPlanPublicId(String pricingPlanPublicId) {
        this.pricingPlanPublicId = pricingPlanPublicId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public BigDecimal getWeeklyRate() {
        return weeklyRate;
    }

    public void setWeeklyRate(BigDecimal weeklyRate) {
        this.weeklyRate = weeklyRate;
    }

    public BigDecimal getSeasonalMultiplier() {
        return seasonalMultiplier;
    }

    public void setSeasonalMultiplier(BigDecimal seasonalMultiplier) {
        this.seasonalMultiplier = seasonalMultiplier;
    }

    public BigDecimal getSeasonalAdjustmentAmount() {
        return seasonalAdjustmentAmount;
    }

    public void setSeasonalAdjustmentAmount(BigDecimal seasonalAdjustmentAmount) {
        this.seasonalAdjustmentAmount = seasonalAdjustmentAmount;
    }

    public BigDecimal getSurchargeAmount() {
        return surchargeAmount;
    }

    public void setSurchargeAmount(BigDecimal surchargeAmount) {
        this.surchargeAmount = surchargeAmount;
    }

    public BigDecimal getCleaningFee() {
        return cleaningFee;
    }

    public void setCleaningFee(BigDecimal cleaningFee) {
        this.cleaningFee = cleaningFee;
    }

    public BigDecimal getGstPercentage() {
        return gstPercentage;
    }

    public void setGstPercentage(BigDecimal gstPercentage) {
        this.gstPercentage = gstPercentage;
    }

    public BigDecimal getCommissionRateSnapshot() {
        return commissionRateSnapshot;
    }

    public void setCommissionRateSnapshot(BigDecimal commissionRateSnapshot) {
        this.commissionRateSnapshot = commissionRateSnapshot;
    }

    public SecurityDepositCollectionMethod getDepositCollectionMethod() {
        return depositCollectionMethod;
    }

    public void setDepositCollectionMethod(SecurityDepositCollectionMethod depositCollectionMethod) {
        this.depositCollectionMethod = depositCollectionMethod;
    }

    public BigDecimal getAdvancePercentageApplied() {
        return advancePercentageApplied;
    }

    public void setAdvancePercentageApplied(BigDecimal advancePercentageApplied) {
        this.advancePercentageApplied = advancePercentageApplied;
    }

    public int getTurnaroundBufferMinutes() {
        return turnaroundBufferMinutes;
    }

    public void setTurnaroundBufferMinutes(int turnaroundBufferMinutes) {
        this.turnaroundBufferMinutes = turnaroundBufferMinutes;
    }

    public PaymentSummaryStatus getPaymentSummaryStatus() {
        return paymentSummaryStatus;
    }

    public void setPaymentSummaryStatus(PaymentSummaryStatus paymentSummaryStatus) {
        this.paymentSummaryStatus = paymentSummaryStatus;
    }

    public OverbookingExceptionStatus getOverbookingExceptionStatus() {
        return overbookingExceptionStatus;
    }

    public void setOverbookingExceptionStatus(OverbookingExceptionStatus overbookingExceptionStatus) {
        this.overbookingExceptionStatus = overbookingExceptionStatus;
    }

    public String getCustomerDlNumber() {
        return customerDlNumber;
    }

    public void setCustomerDlNumber(String customerDlNumber) {
        this.customerDlNumber = customerDlNumber;
    }

    public String getCustomerEmergencyContact() {
        return customerEmergencyContact;
    }

    public void setCustomerEmergencyContact(String customerEmergencyContact) {
        this.customerEmergencyContact = customerEmergencyContact;
    }

    public boolean isDlConfirmed() {
        return dlConfirmed;
    }

    public void setDlConfirmed(boolean dlConfirmed) {
        this.dlConfirmed = dlConfirmed;
    }

    public String getPricingSnapshotJson() {
        return pricingSnapshotJson;
    }

    public void setPricingSnapshotJson(String pricingSnapshotJson) {
        this.pricingSnapshotJson = pricingSnapshotJson;
    }

    public String getCancellationPolicySnapshot() {
        return cancellationPolicySnapshot;
    }

    public void setCancellationPolicySnapshot(String cancellationPolicySnapshot) {
        this.cancellationPolicySnapshot = cancellationPolicySnapshot;
    }

    public Instant getPaymentDeadlineAt() {
        return paymentDeadlineAt;
    }

    public void setPaymentDeadlineAt(Instant paymentDeadlineAt) {
        this.paymentDeadlineAt = paymentDeadlineAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(Long cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public String getCancelledByRole() {
        return cancelledByRole;
    }

    public void setCancelledByRole(String cancelledByRole) {
        this.cancelledByRole = cancelledByRole;
    }

    public DisruptionExceptionType getDisruptionExceptionType() {
        return disruptionExceptionType;
    }

    public void setDisruptionExceptionType(DisruptionExceptionType disruptionExceptionType) {
        this.disruptionExceptionType = disruptionExceptionType;
    }

    public Instant getNoShowAt() {
        return noShowAt;
    }

    public void setNoShowAt(Instant noShowAt) {
        this.noShowAt = noShowAt;
    }

    public Long getNoShowMarkedBy() {
        return noShowMarkedBy;
    }

    public void setNoShowMarkedBy(Long noShowMarkedBy) {
        this.noShowMarkedBy = noShowMarkedBy;
    }

    public String getNoShowEvidenceNotes() {
        return noShowEvidenceNotes;
    }

    public void setNoShowEvidenceNotes(String noShowEvidenceNotes) {
        this.noShowEvidenceNotes = noShowEvidenceNotes;
    }

    public String getNoShowEvidenceRef() {
        return noShowEvidenceRef;
    }

    public void setNoShowEvidenceRef(String noShowEvidenceRef) {
        this.noShowEvidenceRef = noShowEvidenceRef;
    }

    public void addStatusHistory(BookingStatus fromStatus, BookingStatus toStatus, String reason, Long changedBy) {
        BookingStatusHistory history = new BookingStatusHistory();
        history.setBooking(this);
        history.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        history.setToStatus(toStatus.name());
        history.setChangeReason(reason);
        history.setChangedBy(changedBy);
        this.statusHistory.add(history);
    }
}

