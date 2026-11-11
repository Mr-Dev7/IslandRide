export type VehicleType = 'TWO_WHEELER' | 'FOUR_WHEELER' | 'OTHER';
export type TransmissionType = 'MANUAL' | 'AUTOMATIC';
export type FuelType = 'PETROL' | 'DIESEL' | 'ELECTRIC';
export type BookingMode = 'INSTANT' | 'APPROVAL';
export type VehicleCondition = 'EXCELLENT' | 'GOOD' | 'FAIR';
export type VehicleStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'ACTIVE'
  | 'REJECTED'
  | 'PAUSED'
  | 'MAINTENANCE'
  | 'SUSPENDED'
  | 'ARCHIVED';

export type DocumentVerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED' | 'EXPIRED';
export type BlockType = 'MANUAL_BLOCK' | 'MAINTENANCE' | 'DAMAGE_REPAIR' | 'OPERATIONAL_RESTRICTION';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface IslandDto {
  id: number;
  name: string;
  displayAlias: string | null;
  code: string;
  description: string | null;
  displayOrder: number;
  active: boolean;
}

export interface LocationDto {
  id: number;
  publicId: string;
  islandId: number;
  islandName: string;
  islandCode: string;
  name: string;
  code: string;
  address: string;
  latitude: number | null;
  longitude: number | null;
  contactPhone: string | null;
  active: boolean;
}

export interface VehicleCategoryDto {
  id: number;
  name: string;
  code: string;
  slug: string;
  vehicleType: VehicleType;
  description: string | null;
  iconRef: string | null;
  applicableAttributes: string[];
  active: boolean;
  displayOrder: number;
}

export interface VehicleDocumentTypeDto {
  id: number;
  code: string;
  name: string;
  description: string | null;
  mandatory: boolean;
  requiresExpiryDate: boolean;
  applicableVehicleTypes: string;
  active: boolean;
  displayOrder: number;
}

export interface VehicleImageDto {
  publicId: string;
  imageUrl: string;
  originalFilename: string | null;
  contentType: string | null;
  fileSizeBytes: number | null;
  primary: boolean;
  displayOrder: number;
  createdAt: string;
}

export interface PublicVehicleImageDto {
  publicId: string;
  imageUrl: string;
  primary: boolean;
  displayOrder: number;
}

export interface VehicleDocumentDto {
  publicId: string;
  vehiclePublicId: string;
  documentTypeCode: string;
  documentTypeName: string;
  documentNumber: string | null;
  downloadUrl: string;
  originalFilename: string | null;
  contentType: string | null;
  fileSizeBytes: number | null;
  issueDate: string | null;
  expiryDate: string | null;
  expired: boolean;
  expiringSoon: boolean;
  verificationStatus: DocumentVerificationStatus;
  rejectionReason: string | null;
  verifiedAt: string | null;
  createdAt: string;
}

export interface VehiclePricingPlanDto {
  publicId: string | null;
  currency: string;
  hourlyRate: number | null;
  dailyRate: number;
  weeklyRate: number | null;
  securityDeposit: number;
  minRentalHours: number;
  maxRentalDays: number;
  includedKmPerDay: number | null;
  extraKmCharge: number | null;
  extraHourCharge: number | null;
  cleaningFee: number;
  deliveryFee: number;
  additionalChargesDescription: string | null;
  lateReturnFeePerHour: number | null;
  lateReturnFlatPenalty: number | null;
  lateReturnGraceMinutes: number;
  validFrom: string | null;
  validTo: string | null;
  active: boolean;
  gstPercentage: number;
}

export interface PricingRuleDto {
  id: number;
  ruleName: string;
  ruleType: 'DURATION_DISCOUNT' | 'WEEKEND_SURGE' | 'EARLY_BIRD';
  minDays: number;
  adjustmentType: 'PERCENTAGE' | 'FIXED_AMOUNT';
  adjustmentValue: number;
  active: boolean;
}

export interface VehiclePricingOverviewDto {
  activePlan: VehiclePricingPlanDto;
  history: VehiclePricingPlanDto[];
  rules: PricingRuleDto[];
}

export interface AvailabilityBlockDto {
  publicId: string;
  vehiclePublicId: string;
  blockType: BlockType;
  startTime: string;
  endTime: string;
  reason: string;
  status: 'ACTIVE' | 'CANCELLED';
  notes: string | null;
  restrictionDetails: string | null;
  removable: boolean;
  createdAt: string;
  cancelledAt: string | null;
}

export interface PublicAvailabilitySlotDto {
  startTime: string;
  endTime: string;
  slotType: string;
  displayLabel: string;
}

export interface VehicleAvailabilityCalendarDto {
  vehiclePublicId: string;
  from: string;
  to: string;
  availableForRequestedWindow: boolean;
  conflictReason: string | null;
  unavailableSlots: PublicAvailabilitySlotDto[];
  detailedBlocks: AvailabilityBlockDto[];
}

export interface VehicleStatusHistoryDto {
  id: number;
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  changedBy: number | null;
  createdAt: string;
}

export interface AuditLogEntryDto {
  id: number;
  action: string;
  entityName: string;
  entityId: string;
  actorUserId: number | null;
  oldValues: string | null;
  newValues: string | null;
  createdAt: string;
}

export interface PublicVehicleSummaryDto {
  publicId: string;
  title: string;
  make: string;
  model: string;
  modelYear: number;
  categoryId: number;
  categoryName: string;
  categoryCode: string;
  categorySlug: string;
  vehicleType: VehicleType;
  islandId: number;
  islandName: string;
  islandDisplayAlias: string | null;
  islandCode: string;
  pickupLocation: LocationDto | null;
  returnLocation: LocationDto | null;
  allowDifferentReturnSameIsland: boolean;
  transmissionType: TransmissionType;
  fuelType: FuelType;
  seatingCapacity: number;
  vehicleCondition: VehicleCondition;
  dailyRate: number;
  securityDeposit: number;
  currency: string;
  bookingMode: BookingMode;
  partnerPublicId: string;
  partnerTradeName: string;
  verifiedPartner: boolean;
  primaryImageUrl: string | null;
  features: string[];
}

export interface PublicVehicleDetailDto {
  publicId: string;
  title: string;
  make: string;
  model: string;
  modelYear: number;
  description: string | null;
  color: string | null;
  category: VehicleCategoryDto;
  island: IslandDto;
  pickupLocation: LocationDto | null;
  returnLocation: LocationDto | null;
  allowDifferentReturnSameIsland: boolean;
  transmissionType: TransmissionType;
  fuelType: FuelType;
  seatingCapacity: number;
  mileageKmpl: number | null;
  engineCapacityCc: number | null;
  vehicleCondition: VehicleCondition;
  rentalInstructions: string | null;
  bookingMode: BookingMode;
  turnaroundBufferMinutes: number;
  partnerPublicId: string;
  partnerTradeName: string;
  verifiedPartner: boolean;
  features: string[];
  images: PublicVehicleImageDto[];
  pricingPlan: VehiclePricingPlanDto;
  pricingRules: PricingRuleDto[];
}

export interface PartnerVehicleDto {
  publicId: string;
  businessPublicId: string;
  businessTradeName: string;
  businessVerificationStatus: string;
  island: IslandDto;
  pickupLocation: LocationDto | null;
  returnLocation: LocationDto | null;
  allowDifferentReturnSameIsland: boolean;
  category: VehicleCategoryDto;
  title: string;
  make: string;
  model: string;
  modelYear: number;
  description: string | null;
  registrationNumber: string;
  color: string | null;
  transmissionType: TransmissionType;
  fuelType: FuelType;
  seatingCapacity: number;
  mileageKmpl: number | null;
  engineCapacityCc: number | null;
  vehicleCondition: VehicleCondition;
  rentalInstructions: string | null;
  dailyRate: number;
  securityDeposit: number;
  turnaroundBufferMinutes: number;
  bookingMode: BookingMode;
  status: VehicleStatus;
  rejectionReason: string | null;
  suspensionReason: string | null;
  submittedAt: string | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
  features: string[];
  images: VehicleImageDto[];
  documents: VehicleDocumentDto[];
  pricingOverview: VehiclePricingOverviewDto | null;
  statusHistory: VehicleStatusHistoryDto[];
}

export interface PartnerInventorySummaryDto {
  businessPublicId: string;
  businessTradeName: string;
  businessVerificationStatus: string;
  totalVehicles: number;
  activeVehicles: number;
  pendingReviewVehicles: number;
  draftVehicles: number;
  pausedOrMaintenanceVehicles: number;
  rejectedOrSuspendedVehicles: number;
  archivedVehicles: number;
  expiringDocumentsCount: number;
}

export interface AdminVehicleDto extends PartnerVehicleDto {
  businessLegalName: string;
  moderatedBy: number | null;
  moderatedAt: string | null;
  auditLogs: AuditLogEntryDto[];
}

export interface SaveVehicleRequest {
  businessPublicId?: string | null;
  islandId: number;
  pickupLocationPublicId?: string | null;
  returnLocationPublicId?: string | null;
  allowDifferentReturnSameIsland?: boolean;
  categoryId: number;
  title: string;
  make: string;
  model: string;
  modelYear: number;
  description?: string | null;
  registrationNumber: string;
  color?: string | null;
  transmissionType: TransmissionType;
  fuelType: FuelType;
  seatingCapacity: number;
  mileageKmpl?: number | null;
  engineCapacityCc?: number | null;
  vehicleCondition?: VehicleCondition;
  rentalInstructions?: string | null;
  dailyRate: number;
  securityDeposit?: number;
  turnaroundBufferMinutes?: number;
  bookingMode?: BookingMode;
  features?: string[];
}

export interface SaveVehiclePricingRequest {
  currency?: string;
  hourlyRate?: number | null;
  dailyRate: number;
  weeklyRate?: number | null;
  securityDeposit: number;
  minRentalHours?: number;
  maxRentalDays?: number;
  includedKmPerDay?: number | null;
  extraKmCharge?: number | null;
  extraHourCharge?: number | null;
  cleaningFee?: number;
  deliveryFee?: number;
  additionalChargesDescription?: string | null;
  lateReturnFeePerHour?: number | null;
  lateReturnFlatPenalty?: number | null;
  lateReturnGraceMinutes?: number;
  validFrom?: string | null;
  validTo?: string | null;
  active?: boolean;
}

export interface CreateAvailabilityBlockRequest {
  startTime: string;
  endTime: string;
  blockType: BlockType;
  reason: string;
  notes?: string | null;
  restrictionDetails?: string | null;
  removable?: boolean;
}

export interface VehicleSearchFilter {
  islandId?: number | null;
  islandCode?: string | null;
  pickupLocationPublicId?: string | null;
  categoryId?: number | null;
  categoryCode?: string | null;
  vehicleType?: VehicleType | null;
  brand?: string | null;
  q?: string | null;
  fuelType?: FuelType | null;
  transmissionType?: TransmissionType | null;
  minSeats?: number | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  bookingMode?: BookingMode | null;
  startTime?: string | null;
  endTime?: string | null;
  sortBy?: 'newest' | 'price_asc' | 'price_desc' | 'seats_desc' | 'year_desc';
  page?: number;
  size?: number;
}

export interface PartnerBusinessLocationDto {
  location: LocationDto;
  primary: boolean;
  pickupAvailable: boolean;
  returnAvailable: boolean;
}

export interface UpdatePartnerLocationsRequest {
  businessPublicId?: string | null;
  locations: {
    locationPublicId: string;
    primary: boolean;
    pickupAvailable: boolean;
    returnAvailable: boolean;
  }[];
}

