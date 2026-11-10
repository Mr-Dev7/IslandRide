# IslandRide — Technology Stack & Version Pinning
**Document Version:** 1.0.0  
**Phase:** 2 (Repository & Engineering Foundation)  
**Status:** Approved Architecture Baseline  

---

## 1. Selected Technology Stack & Pinned Baseline

| Component | Selected Version | Support / Lifecycle Notes |
|---|---|---|
| **Frontend Framework** | **Angular 22.x** | Currently supported stable Angular line with modern Signals, standalone components, and optimized routing. |
| **Node.js Runtime** | **Node.js 24.x LTS** | Official Angular 22 compatibility requirement (minimum Node 24.15.0+). |
| **Package Manager** | **npm 10.x / 11.x** | Bundled with Node 24.x LTS. |
| **Backend Runtime** | **Java 25 LTS** | Long-Term Support release with native Virtual Threads, pattern matching, and foreign function interfaces. |
| **Backend Framework** | **Spring Boot 4.1.x** | Modern Spring Boot release built for Java 17-27, with native observability, Spring Data 4, and virtual thread concurrency. |
| **Database** | **MySQL 8.4 LTS** | Oracle MySQL Long-Term Support release with transactional InnoDB, enhanced JSON support, and replication stability. |
| **Cache & Coordination**| **Redis 7.4 / 8.x** | Supported Redis container image for distributed ephemeral key TTLs and rate limiting. |
| **Database Migration** | **Flyway 10.x** | Version-controlled, reproducible SQL migration scripts. |
| **Build Tools** | **Apache Maven 3.9+** | Maven build system with wrapper (`mvnw`) for deterministic builds across environments. |
| **Container Engine** | **Docker Compose v2** | Multi-container local development orchestration. |
| **Reverse Proxy** | **Nginx Alpine (1.27+)** | Edge reverse proxy, SSL termination, and Brotli/Gzip static asset delivery. |

---

## 2. Machine Environment Inspection (Current Status)

During the Phase 2 kickoff inspection of the host Windows environment, the following installed binaries were detected:

| Binary / Tool | Detected Version / Path | Status Relative to Target Baseline | Action / Guidance |
|---|---|---|---|
| **Node.js** | `v20.13.1` | Outdated (Target: Node 24.x LTS for Angular 22) | Upgrade to Node 24.x LTS required prior to Angular 22 build. |
| **npm** | `10.5.2` | Compatible with Node 20; will update with Node 24 | Updates automatically with Node 24. |
| **Git** | `2.45.1.windows.1` | Compatible & Healthy | Ready. |
| **Java JDK** | `22.0.1` (`C:\Program Files\Java\jdk-22`) | Installed (Target architecture: Java 25 LTS) | JDK 22 is operational locally; project configured with Maven target compatibility for Java 22/25 LTS. |
| **javac** | `22.0.1` | Installed | Operational. |
| **Maven** | Not in system PATH | Required | We provide the standard Maven Wrapper (`mvnw` / `mvnw.cmd`) in the backend project to ensure zero-dependency local builds. |
| **Docker / Compose** | Not detected in user PATH | Required for local container stack | Docker Desktop installation required to run local MySQL 8.4 & Redis containers. |
| **Angular CLI** | Not installed globally | Expected | Use `npx @angular/cli` or local project dependencies. |

