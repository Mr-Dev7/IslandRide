# IslandRide — UI/UX Sitemap & Navigation Map
**Document Version:** 1.0.0  
**Phase:** 1 (Requirements & System Design)  
**Status:** Approved for Review  

---

## 1. Complete Application Navigation Hierarchy

```
                                  [ IslandRide Single Page Application ]
                                                     |
         +--------------------+----------------------+--------------------+--------------------+
         |                    |                      |                    |                    |
         v                    v                      v                    v                    v
  [ Public Portal ]   [ Tourist Account ]    [ Partner Console ]   [ Admin Platform ]   [ Static / Legal ]
```

---

## 2. Route Architecture & Page Inventory

### 2.1 Public & Discovery Routes (`PublicLayoutComponent`)
| Path | Component Name | Description | Access |
|---|---|---|---|
| `/` | `HomeLandingComponent` | Hero search banner, island destination cards, category quick links, partner CTA. | Public |
| `/search` | `SearchResultsComponent` | Interactive vehicle search: island filter, dates, categories, price range, transmission. | Public |
| `/vehicles/:slug` | `VehicleDetailsComponent` | Photo gallery, specifications, partner badge, deposit terms, instant/approval selector. | Public |
| `/checkout/:bookingRef` | `CheckoutComponent` | Review booking breakdown, 10-minute hold timer, full vs. 25% advance selector, payment modal. | Tourist (Auth) |
| `/booking-success/:bookingRef`| `BookingSuccessComponent`| Confirmation banner, QR code, pickup instructions, voucher download button. | Tourist (Auth) |

### 2.2 Tourist Account Routes (`TouristLayoutComponent`)
| Path | Component Name | Description | Access |
|---|---|---|---|
| `/tourist/bookings` | `TouristBookingsComponent` | Tabbed view of Active, Upcoming, and Past vehicle rentals with status badges. | `TOURIST` |
| `/tourist/bookings/:ref` | `TouristBookingDetailComponent`| Full trip voucher, pickup hub map, cancellation button, invoice PDF export. | `TOURIST` |
| `/tourist/reviews` | `TouristReviewsComponent` | List of completed trips awaiting rating and verified review submission. | `TOURIST` |
| `/tourist/profile` | `TouristProfileComponent` | Manage personal contact info, emergency contact, and driving license details. | `TOURIST` |
| `/tourist/support` | `TouristSupportComponent` | Support tickets, messaging thread with agents, damage report disputes. | `TOURIST` |

### 2.3 Partner Management Routes (`PartnerLayoutComponent`)
| Path | Component Name | Description | Access |
|---|---|---|---|
| `/partner/onboarding` | `PartnerOnboardingComponent` | Multi-step business KYC: trade license, GST, bank account, proprietor ID. | `PARTNER_OWNER` |
| `/partner/dashboard` | `PartnerDashboardComponent` | Fleet KPI cards: Active fleet, Today's pickups, Today's returns, Pending requests, Month GMV. | Partner Staff |
| `/partner/fleet` | `FleetListComponent` | Searchable table of vehicles, operational statuses, quick toggle maintenance block. | Partner Staff |
| `/partner/fleet/new` | `VehicleEditorComponent` | Multi-step vehicle form: make/model, RC upload, photos, rates, deposits. | Partner Owner/Mgr |
| `/partner/calendar` | `AvailabilityCalendarComponent`| Visual interactive calendar matrix: bookings, active holds, custom maintenance blocks. | Partner Staff |
| `/partner/requests` | `ApprovalRequestsComponent` | Countdown queue of pending booking approval requests with accept/reject buttons. | Partner Owner/Mgr |
| `/partner/handover/:ref` | `HandoverConsoleComponent` | Field console: license scan, start/end odometer, fuel slider, 4 photos, cash/UPI collection. | Partner Staff |
| `/partner/financials` | `PartnerFinancialsComponent` | Itemized earnings ledger, 4% commission statements, weekly bank payouts. | `PARTNER_OWNER` |
| `/partner/team` | `PartnerStaffComponent` | Invite and manage staff sub-accounts with role assignment (`MANAGER`, `STAFF`). | `PARTNER_OWNER` |

### 2.4 Platform Administration Routes (`AdminLayoutComponent`)
| Path | Component Name | Description | Access |
|---|---|---|---|
| `/admin/dashboard` | `AdminDashboardComponent` | Platform GMV, active partners count, active fleet count, pending approvals counter. | Admin Staff |
| `/admin/partners` | `PartnerVerificationQueueComponent`| Side-by-side KYC document viewer: approve, reject with reason, request re-submission. | `OPERATIONS_ADMIN` |
| `/admin/fleet-moderation` | `FleetModerationComponent` | Listing inspection queue: vehicle photos, RC verification, approval switch. | `OPERATIONS_ADMIN` |
| `/admin/bookings` | `GlobalBookingsComponent` | Global search across all bookings with status filter, manual override, customer assist. | All Admins |
| `/admin/finance/settlements`| `SettlementsManagerComponent` | Weekly payout batch generation, NEFT export file, UTR transaction upload. | `FINANCE_ADMIN` |
| `/admin/finance/refunds` | `RefundQueueComponent` | Pending and completed customer refund approvals. | `FINANCE_ADMIN` |
| `/admin/support` | `AdminSupportDeskComponent` | Omnichannel ticket queue, customer-partner damage arbitration console. | `SUPPORT_AGENT` |
| `/admin/geography` | `GeographySettingsComponent` | Add/edit islands, localities, pickup hubs, and GPS coordinates. | `SUPER_ADMIN` |
| `/admin/audit-logs` | `AuditLogsComponent` | Read-only security audit stream with filter by actor, IP, action, timestamp. | `SUPER_ADMIN` |

### 2.5 Authentication & Static Routes
| Path | Component Name | Description | Access |
|---|---|---|---|
| `/auth/login` | `LoginComponent` | Email/password login with role redirection and remember-me. | Public |
| `/auth/register` | `RegisterComponent` | Tabbed registration for Tourist or Partner Owner. | Public |
| `/auth/forgot-password` | `ForgotPasswordComponent` | OTP / email reset request form. | Public |
| `/auth/reset-password` | `ResetPasswordComponent` | New password creation with complexity strength meter. | Public |
| `/about-andaman` | `AndamanGuideComponent` | Tourist tips: ferry schedules, island travel rules, helmet regulations. | Public |
| `/terms` | `TermsComponent` | Marketplace terms of service, customer responsibilities, partner code of conduct. | Public |
| `/privacy` | `PrivacyPolicyComponent` | DPDP India data privacy compliance, document retention rules. | Public |

