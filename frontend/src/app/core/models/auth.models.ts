export type RoleType =
  | 'ROLE_SUPER_ADMIN'
  | 'ROLE_OPERATIONS_ADMIN'
  | 'ROLE_FINANCE_ADMIN'
  | 'ROLE_SUPPORT_AGENT'
  | 'ROLE_PARTNER_OWNER'
  | 'ROLE_PARTNER_MANAGER'
  | 'ROLE_PARTNER_STAFF'
  | 'ROLE_TOURIST';

export interface UserDto {
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  status: 'ACTIVE' | 'PENDING_VERIFICATION' | 'SUSPENDED' | 'LOCKED' | 'DISABLED';
  roles: RoleType[];
  businessId?: number;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserDto;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
}

export interface PartnerRegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone: string;
  legalName: string;
  tradeName: string;
  gstin?: string;
  panNumber?: string;
}

export interface BusinessDto {
  publicId: string;
  legalName: string;
  tradeName: string;
  gstin?: string;
  panNumber?: string;
  verificationStatus: 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  rejectionReason?: string;
  commissionRate: number;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

