# IslandRide — Multi-Vendor Tourism Vehicle Rental Marketplace

IslandRide is a production-minded multi-vendor vehicle-rental marketplace initially launching in the **Andaman and Nicobar Islands, India** (Sri Vijaya Puram / Port Blair, Swaraj Dweep / Havelock, Shaheed Dweep / Neil Island). The platform provides three integrated portals: a Public/Tourist discovery and booking portal, a Partner fleet management portal, and a Super Admin governance portal.

---

## 1. Technology Architecture & Pinned Versions

IslandRide is engineered as a **Modular Monolith** with strict **Package-by-Feature** boundaries, backed by relational ACID storage and in-memory ephemeral coordination:

| Layer | Technology Choice | Target Pinned Version | Lifecycle & Compatibility Notes |
|---|---|---|---|
| **Frontend Framework** | **Angular** | `22.x` | Enterprise architecture, Signals, standalone components, strict type checks. |
| **Node.js Runtime** | **Node.js** | `24.x LTS` (24.15+) | Official compatibility baseline for Angular 22. |
| **Backend Runtime** | **Java** | `25 LTS` (JDK 22/25) | Long-Term Support, native Virtual Threads (`java.lang.Thread.ofVirtual`). |
| **Backend Framework** | **Spring Boot** | `4.1.x` (3.3.5 baseline) | High-performance modular monolith REST APIs with Actuator metrics. |
| **Relational Database** | **MySQL** | `8.4 LTS` | InnoDB engine, strict UTC timestamps, `DECIMAL(10,2)` currency precision. |
| **In-Memory Cache** | **Redis** | `7.4-alpine` | Ephemeral inventory hold TTLs, distributed rate limiting, and cache coordination. |
| **Schema Migrations** | **Flyway** | `10.x` | Reproducible, version-controlled database schema evolution. |
| **API Documentation** | **OpenAPI / Swagger** | `3.0` | Interactive contracts at `/swagger-ui.html` and `/v3/api-docs`. |
| **Edge & Proxy** | **Nginx** | `1.27-alpine` | Reverse proxy, Brotli/Gzip static asset compression, SSL termination. |
| **Container Engine** | **Docker Compose** | `v2.x` | Multi-container local development stack with health checks. |

---

## 2. Repository Architecture

```text
islandride/
├── frontend/                     # Angular Single Page Application
│   ├── src/
│   │   ├── app/
│   │   │   ├── core/            # Interceptors, guards, API client services
│   │   │   ├── shared/          # Reusable design tokens, buttons, modal components
│   │   │   ├── layouts/         # Role-tailored layouts (Public, Tourist, Partner, Admin)
│   │   │   ├── features/        # Lazy-loaded business domain components
│   │   │   ├── app.config.ts    # Application dependency injection configuration
│   │   │   └── app.routes.ts    # Route tree with role-boundary code splitting
│   │   ├── environments/        # Environment-specific API configuration
│   │   └── styles.css           # Global design system variables & CSS reset
│   ├── angular.json             # Angular CLI workspace build config
│   ├── tsconfig.json            # Strict TypeScript compiler options
│   └── package.json             # NPM dependencies & build scripts
│
├── backend/                      # Spring Boot Modular Monolith
│   ├── src/main/java/com/islandride/
│   │   ├── core/                # Global exception handling, correlation ID filters, security
│   │   ├── identityaccess/      # Authentication, sessions, credentials, and RBAC
│   │   ├── usermanagement/      # Tourist profiles and contact preferences
│   │   ├── partner/             # Business KYC, verification, and staff permissions
│   │   ├── geography/           # Database-driven Andaman islands and pickup hubs
│   │   ├── inventory/           # Vehicle catalog, registration plates, RC docs, photos
│   │   ├── pricing/             # Daily rates, seasonal multipliers, deposits, delivery fees
│   │   ├── availability/        # Dynamic interval overlap engine & durable MySQL holds
│   │   ├── booking/             # State machine, instant/approval paths, handover records
│   │   ├── paymentfinance/      # Decoupled payment adapter, webhooks, offline pickup ledger
│   │   ├── commission/          # Platform commission accounting and partner settlements
│   │   ├── reviews/             # Verified booking customer reviews
│   │   ├── notifications/       # Transactional email, SMS, and WhatsApp alerts
│   │   ├── support/             # Dispute arbitration and customer support tickets
│   │   ├── administration/      # Administrative overrides, system settings, and audit logs
│   │   └── analytics/           # Marketplace KPIs (GMV, utilization, conversion)
│   ├── src/main/resources/
│   │   ├── application.yml      # Base application configuration
│   │   ├── application-local.yml# Local development MySQL/Redis configuration
│   │   └── db/migration/        # Flyway versioned SQL migrations (V1, V2...)
│   └── pom.xml                  # Maven dependencies & ArchUnit testing suite
│
├── infrastructure/
│   ├── docker/                  # Multi-stage Dockerfiles (frontend, backend)
│   └── nginx/                   # Reverse proxy configuration (default.conf)
│
├── docs/                        # Complete Engineering Documentation Suite (21 files)
│   ├── 01-requirements/         # SRS, functional/non-functional specs, user stories, rules
│   ├── 02-system-architecture/  # System architecture, workflows, ADRs, module design
│   ├── 03-database/             # Relational ERD, database design, data dictionary
│   ├── 04-api/                  # REST API endpoint catalog & RFC 7807 error envelopes
│   ├── 05-ui-ux/                # Sitemaps, user journey maps, wireframe requirements
│   ├── 06-security/             # Threat modeling, Argon2id, BOLA defense, pre-signed URLs
│   └── 07-project-planning/     # 12-phase roadmap, open decisions, technology versions
│
├── scripts/                     # Local developer onboarding scripts (Windows / macOS / Linux)
├── .github/workflows/           # GitHub Actions continuous integration pipeline (ci.yml)
├── .gitignore                   # Comprehensive secret, build, and IDE exclusions
├── .env.example                 # Template for local environment variables
├── docker-compose.yml           # Pinned MySQL 8.4 LTS and Redis 7.4 containers
└── README.md                    # Project documentation and developer guide
```

---

## 3. Local Environment Prerequisites

To run IslandRide locally, ensure the following tools are installed:
1. **Node.js**: `v24.x LTS` (or `v20.x+` with npm `10.x+`).
2. **Java JDK**: `Java 25 LTS` or `Java 22.0.1+` with `JAVA_HOME` configured.
3. **Docker Desktop**: With Docker Compose v2 support enabled.
4. **Git**: Version `2.40+`.

---

## 4. Local Setup & Startup Instructions

### Step 1: Clone and Configure Environment Variables
```powershell
# Copy the environment template
Copy-Item .env.example .env
```
*(Inspect `.env` and verify database credentials, ports, and JWT secret tokens.)*

### Step 2: Start Local Database & Cache Infrastructure (Docker)
```powershell
# Start MySQL 8.4 LTS and Redis 7.4 in background
docker compose up -d

# Verify container health status
docker compose ps
```
- **MySQL 8.4 LTS**: Available on `127.0.0.1:3306` (Database: `islandride_db`).
- **Redis 7.4**: Available on `127.0.0.1:6379`.

### Step 3: Run Spring Boot Backend
```powershell
cd backend

# Build and execute unit tests
.\mvnw.cmd clean verify

# Start the Spring Boot application (connects to MySQL & Redis)
.\mvnw.cmd spring-boot:run
```
- **API Base URL**: `http://localhost:8080/api/v1`
- **Health Endpoint**: `http://localhost:8080/api/v1/health`
- **Interactive Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON Spec**: `http://localhost:8080/v3/api-docs`

### Step 4: Run Angular Frontend
```powershell
cd frontend

# Install dependencies (First run only)
npm install

# Start local Angular development server
npm start
```
- **Public Portal**: `http://localhost:4200`
- **Tourist Portal**: `http://localhost:4200/tourist`
- **Partner Console**: `http://localhost:4200/partner`
- **Admin Console**: `http://localhost:4200/admin`

---

## 5. Testing & Verification Commands

### Backend Automated Test Suite
```powershell
cd backend
# Runs JUnit 5, Mockito, and ArchUnit architecture fitness checks
.\mvnw.cmd test
```

### Frontend Automated Test Suite
```powershell
cd frontend
# Runs Karma / Jasmine component tests in headless browser
npm test -- --watch=false --browsers=ChromeHeadless
```

### Validate Docker Configuration
```powershell
docker compose config
```

---

## 6. Troubleshooting Common Issues

1. **MySQL Container Connection Refused (`port 3306`)**:
   - Verify Docker Desktop is running and no other local MySQL instance is occupying port 3306:
     ```powershell
     Get-Process -Name "*sql*"
     docker compose ps
     ```
2. **Flyway Migration Checksum Failure**:
   - If local test migrations fail due to manual SQL edits:
     ```powershell
     .\mvnw.cmd flyway:repair
     ```
3. **Node Compatibility Warning with Angular**:
   - Ensure Node.js meets Angular's compatibility matrix (Node 24.x LTS recommended for Angular 22).

---

## 7. Development Guidelines

1. **Backend Authority**: Never trust client-side prices, availability, or payment status. The Spring Boot backend is the sole authority.
2. **Decimal for Currency**: Always use `DECIMAL(10,2)` (Java `BigDecimal`) for monetary values. Never use `float` or `double`.
3. **UTC Everywhere**: Database timestamps are stored strictly in UTC. Local island times (IST, UTC+05:30) are formatted for client display.
4. **Tenant Isolation**: Every partner query must be scoped by `business_id` at the repository/service layer.
