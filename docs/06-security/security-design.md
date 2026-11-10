# IslandRide — Comprehensive Security Architecture
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Security Philosophy & Threat Modeling

IslandRide enforces **Defense-in-Depth** and **Zero Trust Architecture**. The backend application is the single security authority; client-side Angular route guards exist purely for user experience and are never trusted for authorization decisions.

### Threat Model Matrix (STRIDE & OWASP Top 10)
| Threat Category | Potential Attack Vector | Platform Countermeasure |
|---|---|---|
| **Spoofing** | Forged identity claims; stolen session tokens. | Argon2id password hashing, short-lived JWTs (15 min), rotating refresh tokens in `HttpOnly` cookies, device fingerprinting. |
| **Tampering** | Forged payment webhook notifications; client tampering with rental prices. | HMAC-SHA256 signature verification on webhooks; 100% backend price calculation; tamper-proof database price snapshots. |
| **Repudiation** | Partner disputes paying commission; customer denies vehicle condition at pickup. | Immutable financial ledgers; mandatory photo checklist at pickup/return with digital sign-off and server timestamps. |
| **Information Disclosure** | Scraping driving licenses/identity documents (IDOR/BOLA). | Private S3 bucket architecture; time-limited pre-signed download URLs (max 10 min) issued only after strict ownership checks. |
| **Denial of Service** | API abuse, bot-driven inventory locking, brute force login. | Redis token-bucket rate limiting (per-IP and per-account); automated expiration of uncompleted inventory holds (10 min). |
| **Elevation of Privilege** | Partner modifying platform commission or accessing another partner's fleet. | Multi-tier RBAC (`Spring Security`) paired with tenant-scoping (`WHERE business_id = :authenticatedBusinessId`). |

---

## 2. Authentication & Session Architecture

```mermaid
sequenceDiagram
    autonumber
    actor User as User Browser
    participant API as Spring Security API
    participant DB as MySQL Database
    participant Redis as Redis Cache

    User->>API: POST /api/v1/auth/login (email, password)
    API->>DB: Fetch user by email
    API->>API: Verify password with Argon2id
    
    critical Generate Tokens & Session
        API->>API: Generate Access JWT (15-min validity, claims: userId, role, businessId)
        API->>DB: Generate Refresh Token (UUIDv4, 7-day validity, family tracking)
        API->>Redis: Store active session key
    end

    API-->>User: 200 OK (Response Body: access_token; Set-Cookie: refresh_token, HttpOnly, Secure, SameSite=Strict)

    Note over User,API: Mutating Request with Anti-CSRF
    User->>API: POST /api/v1/bookings/instant (Authorization: Bearer <access_token>, X-CSRF-TOKEN: <csrf>)
    API->>API: Validate JWT Signature & Expiry
    API->>API: Validate CSRF Token
    API->>API: Enforce Tenant / Role Authorization
    API-->>User: 201 Created
```

### 2.1 Refresh Token Rotation & Replay Attack Defense
- Every invocation of `POST /api/v1/auth/refresh` revokes the incoming refresh token and issues a new one.
- If a previously invalidated refresh token is ever presented (indicating a stolen token), the backend immediately revokes the **entire token family**, terminates all active user sessions across all devices, and logs a critical security alert.

---

## 3. Resource Ownership & BOLA/IDOR Prevention

### 3.1 Tenant Isolation Architecture
Every database entity belonging to a rental partner has a mandatory foreign key: `business_id BIGINT UNSIGNED NOT NULL`.

In Spring Boot, tenant isolation is enforced at the domain service layer using Spring Security expressions and custom JPA Specifications:

```java
@PreAuthorize("hasRole('PARTNER_STAFF') or hasRole('PARTNER_OWNER')")
public VehicleResponse getVehicle(Long vehicleId, UserPrincipal principal) {
    return vehicleRepository.findByIdAndBusinessId(vehicleId, principal.getBusinessId())
        .map(vehicleMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found or unauthorized"));
}
```
*Security Guarantee:* Even if an attacker guesses an existing `vehicleId` belonging to another rental agency, the database query returns empty, completely preventing Insecure Direct Object Reference (IDOR) data exposure.

---

## 4. Secure Handling of Sensitive Documents (KYC & Licenses)

1. **Private S3 Bucket Configuration:**
   - The document storage bucket is completely private. Public read permissions are disabled (`BlockPublicAccess = true`).
2. **Pre-Signed Download URLs:**
   - When an authorized admin or partner needs to view an identity document, the backend generates a pre-signed URL with a **strict 10-minute expiration window**.
3. **Upload File Validation:**
   - File extensions are restricted to `.pdf`, `.jpg`, `.jpeg`, `.png`.
   - Magic byte inspection (MIME sniffing) ensures malicious executable binaries disguised with image extensions are rejected.
   - Maximum upload file size is capped at **5 MB** per document and **10 MB** per vehicle photo.

---

## 5. Webhook Security & Idempotency

### 5.1 Signature Verification
All payment gateway callbacks (`POST /api/v1/payments/webhooks/{provider}`) undergo strict signature verification before any JSON payload parsing occurs:
$$\text{Signature} = \text{HMAC-SHA256}(\text{Raw Payload Body}, \text{Gateway Webhook Secret})$$
If the generated signature does not match the `X-Razorpay-Signature` HTTP header, the request is instantly rejected with HTTP `400 Bad Request`.

### 5.2 Replay Attack & Duplicate Webhook Prevention
The webhook payload contains a unique `event_id` or `payment_id`. The handler writes an atomic entry into `payments` table with `UNIQUE(idempotency_key)`. If a replayed webhook arrives, the duplicate key violation is safely intercepted, and the system immediately returns HTTP `200 OK` without re-executing booking confirmation or ledger writes.

