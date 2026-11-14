import { BookingMode, PageResponse } from './vehicle.models';
export type { BookingMode };

export type BookingStatus =
  | 'AWAITING_PARTNER_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'PENDING_PAYMENT'
  | 'PAYMENT_FAILED'
  | 'CONFIRMED'
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'NO_SHOW'
  | 'DISPUTED';

export type PaymentMode = 'FULL' | 'PARTIAL_ADVANCE';
export type PaymentSummaryStatus =
  | 'UNPAID'
  | 'ORDER_CREATED'
  | 'PARTIALLY_PAID'
  | 'PAID_IN_FULL'
  | 'FAILED'
  | 'REFUND_PENDING'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export type OverbookingExceptionStatus = 'NONE' | 'DETECTED' | 'REFUND_INITIATED' | 'RESOLVED';
export type SecurityDepositCollectionMethod = 'AT_PICKUP' | 'ONLINE';
export type SecurityDepositStatus = 'PENDING' | 'COLLECTED' | 'INSPECTION_HOLD' | 'RELEASED' | 'FORFEITED' | 'WAIVED';
export type DepositInspectionStatus = 'PENDING' | 'PASSED' | 'DAMAGE_CLAIMED' | 'SETTLED';
export type DisruptionExceptionType =
  | 'WEATHER_DISRUPTION'
  | 'FERRY_CANCELLATION'
  | 'ADMIN_FORCE_MAJEURE'
  | 'RESCHEDULED_APPROVED';

export type PaymentType = 'RENTAL_FULL' | 'RENTAL_ADVANCE' | 'PICKUP_BALANCE' | 'SECURITY_DEPOSIT';
export type PaymentMethod = 'GATEWAY' | 'OFFLINE_CASH' | 'OFFLINE_UPI';
export type PaymentStatus = 'INITIATED' | 'CAPTURED' | 'FAILED' | 'REFUNDED' | 'PARTIALLY_REFUNDED' | 'RECONCILED';
export type RefundType = 'CANCELLATION' | 'OVERBOOKING' | 'SECURITY_DEPOSIT' | 'DISRUPTION' | 'PARTIAL_ADJUSTMENT' | 'MANUAL';
export type RefundStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'ESCALATED' | 'RECONCILED';
export type HoldPurpose = 'CHECKOUT' | 'PARTNER_MANUAL';
export type HoldStatus = 'HELD' | 'CONVERTED' | 'RELEASED' | 'EXPIRED';

export interface PricePreviewResponse {
  vehiclePublicId: string;
  vehicleTitle: string;
  bookingMode: BookingMode;
  available: boolean;
  conflictReason: string | null;
  startTime: string;
  endTime: string;
  pickupLocationPublicId: string | null;
  pickupLocationName: string | null;
  returnLocationPublicId: string | null;
  returnLocationName: string | null;
  currency: string;
  billingUnit: string;
  billableDays: number;
  billableHours: number | null;
  dailyRate: number;
  hourlyRate: number | null;
  baseRentalAmount: number;
  seasonalMultiplier: number;
  seasonalAdjustmentAmount: number;
  discountAmount: number;
  surchargeAmount: number;
  cleaningFee: number;
  deliveryFee: number;
  gstPercentage: number;
  taxAmount: number;
  totalPayableAmount: number;
  securityDepositAmount: number;
  depositCollectionMethod: SecurityDepositCollectionMethod;
  requestedPaymentMode: PaymentMode;
  enforcedPaymentMode: PaymentMode;
  advancePercentageApplied: number;
  advanceDueNow: number;
  pickupBalanceDue: number;
  turnaroundBufferMinutes: number;
  fullPaymentCutoffHours: number;
  holdDurationSeconds: number;
  cancellationDeadlineHours: number;
  freeCancellationAllowed: boolean;
  cancellationFeePercentage: number;
}

export interface AcquireHoldRequest {
  vehiclePublicId: string;
  startTime: string;
  endTime: string;
  pickupLocationPublicId?: string | null;
  returnLocationPublicId?: string | null;
  purpose?: HoldPurpose;
  idempotencyKey?: string;
}

export interface HoldResultDto {
  id: number;
  publicId: string;
  vehiclePublicId: string;
  userId: number;
  purpose: HoldPurpose;
  pickupLocationPublicId: string | null;
  returnLocationPublicId: string | null;
  startTime: string;
  endTime: string;
  turnaroundBufferMinutes: number;
  holdDurationSeconds: number;
  status: HoldStatus;
  expiresAt: string;
}

export interface InventoryHoldResponse {
  publicId: string;
  vehiclePublicId: string;
  vehicleTitle: string;
  partnerTradeName: string;
  islandName: string;
  bookingMode: BookingMode;
  purpose: HoldPurpose;
  status: HoldStatus;
  pickupLocationPublicId: string | null;
  pickupLocationName: string | null;
  returnLocationPublicId: string | null;
  returnLocationName: string | null;
  startTime: string;
  endTime: string;
  turnaroundBufferMinutes: number;
  holdDurationSeconds: number;
  expiresAt: string;
  remainingSeconds: number;
  fullQuote: PricePreviewResponse;
  partialQuote: PricePreviewResponse;
}

export interface CreateBookingRequest {
  holdPublicId: string;
  paymentMode: PaymentMode;
  deliveryRequested: boolean;
  customerDlNumber: string;
  customerEmergencyContact: string;
  dlConfirmed: boolean;
  idempotencyKey?: string;
}

export interface GatewayCheckoutOrderResponse {
  paymentPublicId: string;
  bookingPublicId: string;
  bookingReference: string;
  gatewayProvider: string;
  gatewayOrderId: string;
  keyId?: string | null;
  amount: number;
  currency: string;
  paymentType: PaymentType;
  liveEnabled: boolean;
  orderExpiresAt: string;
  bookingPaymentDeadlineAt: string | null;
}

export interface BookingSummaryDto {
  publicId: string;
  bookingReference: string;
  vehiclePublicId: string;
  vehicleTitle: string;
  businessPublicId: string;
  partnerTradeName: string;
  customerName: string;
  customerEmail: string | null;
  pickupLocationName: string | null;
  returnLocationName: string | null;
  startTime: string;
  endTime: string;
  totalDays: number;
  status: BookingStatus;
  paymentSummaryStatus: PaymentSummaryStatus;
  overbookingExceptionStatus: OverbookingExceptionStatus;
  bookingMode: BookingMode;
  paymentMode: PaymentMode;
  currency: string;
  totalPayableAmount: number;
  advancePaidAmount: number;
  pickupBalanceDue: number;
  securityDepositAmount: number;
  depositCollectionMethod: SecurityDepositCollectionMethod;
  approvalExpiresAt: string | null;
  paymentDeadlineAt: string | null;
  createdAt: string;
}

export interface PaymentSummaryDto {
  publicId: string;
  bookingPublicId: string;
  bookingReference: string;
  amount: number;
  currency: string;
  paymentType: PaymentType;
  deposit: boolean;
  paymentMethod: PaymentMethod;
  gatewayProvider: string | null;
  gatewayOrderId: string | null;
  gatewayPaymentId: string | null;
  status: PaymentStatus;
  gatewayOrderExpiresAt: string | null;
  capturedAt: string | null;
  failedAt: string | null;
  failureReason: string | null;
  createdAt: string;
}

export interface RefundSummaryDto {
  publicId: string;
  paymentPublicId: string;
  bookingPublicId: string;
  bookingReference: string;
  amount: number;
  gatewayFeeRetained: number;
  currency: string;
  reason: string;
  refundType: RefundType;
  gatewayRefundId: string | null;
  status: RefundStatus;
  retryCount: number;
  nextRetryAt: string | null;
  errorMessage: string | null;
  escalationTicketRef: string | null;
  reconciledAt: string | null;
  createdAt: string;
}

export interface OfflineCollectionDto {
  id: number;
  amount: number;
  collectionType: string;
  paymentMethod: string;
  receiptReference: string | null;
  notes: string | null;
  collectedAt: string;
}

export interface SecurityDepositDto {
  publicId: string;
  collectionMethod: SecurityDepositCollectionMethod;
  expectedAmount: number;
  collectedAmount: number;
  refundedAmount: number;
  forfeitedAmount: number;
  currency: string;
  status: SecurityDepositStatus;
  inspectionStatus: DepositInspectionStatus;
  inspectionDeadlineAt: string | null;
  paymentChannel: string | null;
  receiptReference: string | null;
  collectedAt: string | null;
  releasedAt: string | null;
  notes: string | null;
}

export interface HandoverInspectionDto {
  publicId: string;
  handoverType: 'PICKUP' | 'RETURN';
  odometerReading: number;
  fuelPercentage: number;
  checklistDataJson: string | null;
  notes: string | null;
  completedAt: string;
  evidenceKeys: string[];
}

export interface BookingStatusHistoryDto {
  fromStatus: BookingStatus | null;
  toStatus: BookingStatus;
  reason: string | null;
  changedBy: number | null;
  createdAt: string;
}

export interface BookingModificationDto {
  publicId: string;
  previousStartTime: string;
  previousEndTime: string;
  newStartTime: string;
  newEndTime: string;
  previousPickupLocationName: string | null;
  newPickupLocationName: string | null;
  previousReturnLocationName: string | null;
  newReturnLocationName: string | null;
  previousTotalPayable: number;
  newTotalPayable: number;
  priceDelta: number;
  adjustmentType: string;
  reason: string | null;
  createdAt: string;
}

export interface CommissionSummaryDto {
  currency: string;
  grossBookingAmount: number;
  taxExcludedAmount: number;
  depositExcludedAmount: number;
  commissionRatePercent: number;
  commissionAmount: number;
  onlineCollectedAmount: number;
  offlineCollectedAmount: number;
  partnerPayoutAmount: number;
  partnerReceivableAmount: number;
  status: string;
}

export interface BookingDetailDto {
  summary: BookingSummaryDto;
  customerDlNumber: string;
  customerEmergencyContact: string;
  dlConfirmed: boolean;
  dailyRate: number;
  hourlyRate: number | null;
  baseRentalAmount: number;
  seasonalMultiplier: number;
  seasonalAdjustmentAmount: number;
  discountAmount: number;
  surchargeAmount: number;
  cleaningFee: number;
  deliveryFee: number;
  taxAmount: number;
  gstPercentage: number;
  commissionRateSnapshot: number;
  advancePercentageApplied: number;
  onlineAmountDueNow: number;
  turnaroundBufferMinutes: number;
  pricingSnapshotJson: string | null;
  cancellationPolicySnapshotJson: string | null;
  approvedAt: string | null;
  cancelledAt: string | null;
  cancelledByRole: string | null;
  cancellationReason: string | null;
  disruptionExceptionType: DisruptionExceptionType | null;
  completedAt: string | null;
  noShowAt: string | null;
  noShowEvidenceNotes: string | null;
  noShowEvidenceRef: string | null;
  securityDeposit: SecurityDepositDto | null;
  commission: CommissionSummaryDto | null;
  payments: PaymentSummaryDto[];
  refunds: RefundSummaryDto[];
  offlineCollections: OfflineCollectionDto[];
  inspections: HandoverInspectionDto[];
  statusHistory: BookingStatusHistoryDto[];
  modifications: BookingModificationDto[];
}

export interface BookingCreationResult {
  booking: BookingDetailDto;
  checkoutOrder: GatewayCheckoutOrderResponse | null;
}

export interface PaymentVerificationResponse {
  bookingPublicId: string;
  bookingReference: string;
  bookingStatus: string;
  paymentSummaryStatus: string;
  payment: PaymentSummaryDto;
  overbookingDetected: boolean;
}

export interface MockPaymentSimulateResponse {
  gatewayOrderId: string;
  gatewayPaymentId: string;
  checkoutSignature: string;
  webhookEventId: string;
  webhookPayload: string;
  webhookSignature: string;
  outcome: string;
}

export interface CancellationPreviewResponse {
  bookingPublicId: string;
  currentStatus: BookingStatus;
  totalPayableAmount: number;
  rentalOnlinePaid: number;
  depositOnlinePaid: number;
  rentalPenaltyAmount: number;
  gatewayFeeDeducted: number;
  netRentalRefundAmount: number;
  securityDepositRefundAmount: number;
  totalRefundAmount: number;
  depositProtected: boolean;
  reschedulingEligible: boolean;
  policyAppliedDescription: string;
  cancellationEvaluatedAt: string;
}

export interface OverbookingExceptionDto {
  publicId: string;
  bookingPublicId: string;
  bookingReference: string;
  conflictingBookingPublicId: string | null;
  conflictingBookingReference: string | null;
  paymentId: number;
  status: string;
  resolutionNotes: string | null;
  detectedAt: string;
  resolvedAt: string | null;
}

export interface SecurityDepositTrustSummaryDto {
  totalExpectedDeposits: number;
  totalCollectedHeldLiability: number;
  totalInspectionHoldAmount: number;
  totalReleasedDeposits: number;
  totalForfeitedDeposits: number;
  activeHeldCount: number;
  inspectionHoldCount: number;
}

export interface WebhookEventSummaryDto {
  publicId: string;
  gatewayProvider: string;
  gatewayEventId: string;
  eventType: string;
  signatureVerified: boolean;
  processingStatus: string;
  bookingPublicId: string | null;
  paymentPublicId: string | null;
  errorMessage: string | null;
  receivedAt: string;
  processedAt: string | null;
}

export interface AdminFinanceOverviewDto {
  totalOnlineRentalCaptured: number;
  totalOfflineRentalRecorded: number;
  totalPlatformCommission: number;
  totalPendingRefundAmount: number;
  totalCompletedRefundAmount: number;
  failedRefundsCount: number;
  openOverbookingExceptionsCount: number;
  depositTrustSummary: SecurityDepositTrustSummaryDto;
  recentPayments: PaymentSummaryDto[];
  recentRefunds: RefundSummaryDto[];
  recentWebhooks: WebhookEventSummaryDto[];
  overbookingExceptions: OverbookingExceptionDto[];
}

export interface CommissionDetailDto {
  commissionId: number;
  bookingPublicId: string | null;
  bookingReference: string | null;
  vehicleTitle: string | null;
  grossRentalAmount: number;
  commissionPercentage: number;
  commissionAmount: number;
  partnerEarnings: number;
  status: string;
  createdAt: string;
}

export interface SettlementDetailDto {
  publicId: string;
  periodStart: string;
  periodEnd: string;
  grossAmount: number;
  commissionDeducted: number;
  netPayoutAmount: number;
  bankUtrReference: string | null;
  status: string;
  processedAt: string | null;
  createdAt: string;
}

export interface PartnerFinancialOverviewDto {
  businessId: number;
  tradeName: string;
  totalGrossBookings: number;
  totalCommissionDeducted: number;
  totalNetEarnings: number;
  onlineCollectedByPlatform: number;
  offlineCollectedByPartner: number;
  totalSettledPaidOut: number;
  pendingSettlementBalance: number;
  openReceivablesBalance: number;
  totalCompletedBookings: number;
  settlementCycleDays?: number;
  autoOffsetReceivables?: boolean;
  recentCommissions: CommissionDetailDto[];
  recentSettlements: SettlementDetailDto[];
}

export type BookingPage = PageResponse<BookingSummaryDto>;
