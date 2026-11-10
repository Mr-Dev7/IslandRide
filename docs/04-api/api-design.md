# IslandRide — RESTful API Design Specification
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. API Standards & Conventions

### 1.1 Base URL & Versioning
All REST APIs are exposed under versioned prefixes:
```text
https://api.islandride.in/api/v1
```

### 1.2 Common Request Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `Content-Type` | String | YES | `application/json` for mutation endpoints; `multipart/form-data` for file uploads. |
| `Accept` | String | YES | `application/json` |
| `X-Correlation-ID` | String | OPTIONAL | Client-supplied tracing identifier (auto-generated if omitted). |
| `Idempotency-Key` | String | REQUIRED | Unique UUID required on booking creation and payment capture requests to prevent duplicate transactions. |

### 1.3 Standard Response Formats

#### Success Envelope (Single Entity)
```json
{
  "success": true,
  "data": {
    "publicId": "veh_9f81a7b2c3d4",
    "make": "Honda",
    "model": "Activa 6G",
    "dailyRate": 600.00
  },
  "timestamp": "2026-11-10T18:30:00Z"
}
```

#### Success Envelope (Paginated List)
```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 84,
    "totalPages": 5,
    "isLast": false
  },
  "timestamp": "2026-11-10T18:30:00Z"
}
```

#### Standard Error Envelope (RFC 7807 Problem Details)
```json
{
  "type": "https://errors.islandride.in/booking/interval-conflict",
  "title": "Vehicle Unavailable",
  "status": 409,
  "detail": "The vehicle is already booked or held for the requested interval.",
  "instance": "/api/v1/bookings/instant",
  "correlationId": "corr_c841b8a9",
  "invalidParams": [
    {
      "name": "startTime",
      "reason": "Overlaps with confirmed reservation IR-202611-1092"
    }
  ],
  "timestamp": "2026-11-10T18:30:00Z"
}
```

---

## 2. API Endpoint Catalog

### 2.1 Identity & Access (`/api/v1/auth`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Tourist / Partner self-registration. |
| `POST` | `/api/v1/auth/login` | Public | Authenticates credentials, returns JWT & sets HttpOnly refresh cookie. |
| `POST` | `/api/v1/auth/refresh` | Public (Cookie) | Rotates refresh token and issues new short-lived access JWT. |
| `POST` | `/api/v1/auth/logout` | Authenticated | Revokes current refresh token session and clears auth cookies. |
| `POST` | `/api/v1/auth/forgot-password` | Public | Sends expiring single-use password reset link. |
| `POST` | `/api/v1/auth/reset-password` | Public | Verifies token and updates password. |

### 2.2 Geography & Locations (`/api/v1/locations`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/v1/locations/islands` | Public | Lists active destination islands in Andaman (Port Blair, Havelock, Neil). |
| `GET` | `/api/v1/locations/hubs` | Public | Lists pickup/return stations for an island (`?islandCode=HAVELOCK`). |

### 2.3 Vehicles & Inventory (`/api/v1/vehicles`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/v1/vehicles` | Public | Paginated vehicle search with island, date, category, and price filters. |
| `GET` | `/api/v1/vehicles/{publicId}` | Public | Full vehicle specifications, photos, partner profile, and deposit terms. |
| `GET` | `/api/v1/vehicles/{publicId}/availability` | Public | Real-time interval check for requested dates (`?start=...&end=...`). |
| `POST` | `/api/v1/vehicles/{publicId}/price-preview` | Public | Authoritative server price breakdown (rental, taxes, delivery, deposit). |

### 2.4 Bookings & Lifecycle (`/api/v1/bookings`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/bookings/instant` | TOURIST | Creates instant booking with 10-minute hold and gateway order. |
| `POST` | `/api/v1/bookings/request-approval` | TOURIST | Submits approval-mode booking request to the partner. |
| `GET` | `/api/v1/bookings/me` | TOURIST | Lists authenticated tourist's booking history (paginated). |
| `GET` | `/api/v1/bookings/{publicId}` | TOURIST / ADMIN | Returns detailed booking voucher, payment breakdown, and QR code. |
| `POST` | `/api/v1/bookings/{publicId}/cancel` | TOURIST | Cancels reservation, releases hold, and schedules policy refund. |

### 2.5 Partner Console (`/api/v1/partner`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/partner/business` | PARTNER_OWNER | Registers business legal details and tax info. |
| `POST` | `/api/v1/partner/documents` | PARTNER_OWNER | Uploads trade license / GST certificate to private storage. |
| `GET` | `/api/v1/partner/fleet` | PARTNER_OWNER/MGR | Lists partner's fleet with status and maintenance flags. |
| `POST` | `/api/v1/partner/fleet` | PARTNER_OWNER/MGR | Adds a new vehicle listing with RC and insurance documents. |
| `POST` | `/api/v1/partner/fleet/{id}/photos` | PARTNER_OWNER/MGR | Uploads vehicle gallery images (max 10). |
| `POST` | `/api/v1/partner/bookings/{id}/approve` | PARTNER_OWNER/MGR | Approves pending booking request, notifying tourist. |
| `POST` | `/api/v1/partner/bookings/{id}/reject` | PARTNER_OWNER/MGR | Declines pending booking request with required reason. |
| `POST` | `/api/v1/partner/handover/pickup` | PARTNER_STAFF | Records start odometer, fuel level, photos, and offline balance. |
| `POST` | `/api/v1/partner/handover/return` | PARTNER_STAFF | Records return odometer, fuel, damages, and clears deposit. |
| `GET` | `/api/v1/partner/settlements` | PARTNER_OWNER | Views itemized earnings ledger, commission deductions, and payouts. |

### 2.6 Payments & Webhooks (`/api/v1/payments`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/payments/orders` | TOURIST | Generates payment gateway order for a confirmed/approved booking. |
| `POST` | `/api/v1/payments/webhooks/{provider}` | Public (HMAC Verified) | Receives and verifies asynchronous payment gateway webhook. |
| `POST` | `/api/v1/payments/refunds` | FINANCE_ADMIN | Triggers gateway refund API call for cancelled booking. |

### 2.7 Super Administration (`/api/v1/admin`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/partners/pending` | OPERATIONS_ADMIN | Lists partner KYC verification queue. |
| `PATCH` | `/api/v1/admin/partners/{id}/status` | OPERATIONS_ADMIN | Approves or rejects partner registration with feedback. |
| `PATCH` | `/api/v1/admin/vehicles/{id}/moderate` | OPERATIONS_ADMIN | Moderates vehicle listing (Approves / Suspends). |
| `GET` | `/api/v1/admin/finance/reconciliation` | FINANCE_ADMIN | Inspects gateway charges vs. bank deposits. |
| `POST` | `/api/v1/admin/finance/settlements/batch` | FINANCE_ADMIN | Generates weekly partner payout disbursement batch. |
| `GET` | `/api/v1/admin/audit-logs` | SUPER_ADMIN | Queries immutable platform security audit log. |

