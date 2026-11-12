# IslandRide — Phase 6A Test Failure Root-Cause Diagnostic

- **Commit Audited:** `dfac0e7` (`dfac0e76ebfb9b90124f343a129b163ae25bd131`, branch `main`)
- **Date:** 2026-11-12
- **Status:** Read-only diagnostic complete — **no source, test, Maven, Flyway, or configuration files were modified.**

---

### A. Execution summary

- **Command executed (full backend suite, unsandboxed):**
  ```powershell
  Push-Location backend; .\mvnw.cmd clean verify 2>&1 | Tee-Object -FilePath ..\logs\phase-6a-debug-clean-verify.log
  ```
- **Independent omitted-class verification command (unsandboxed):**
  ```powershell
  Push-Location backend; .\mvnw.cmd -Dtest=IslandRideApplicationTests test
  ```
- **Environment:** Windows 11 (`10.0`, `amd64`), execution outside restricted terminal sandbox (`BypassSandbox: true`); local MySQL `8.4` reachable on `localhost:3307` (`islandride_db`) and local Redis reachable on `localhost:6379` (`AUTH` + `PING` verified `+OK` / `+PONG`). Environment override variables (`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `SPRING_PROFILES_ACTIVE`, `REDIS_HOST`, `REDIS_PORT`) were unset, using defaults in `backend/src/main/resources/application.yml` and `application-local.yml`.
- **Java version:** `25.0.5` (Eclipse Adoptium, `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`)
- **Maven version:** Apache Maven `3.9.9` (`.\mvnw.cmd`)
- **Active Spring profiles:**
  - `local` for MySQL-backed Spring Boot integration tests (`AuthenticationIntegrationTest`, `AuthorizationAndTenantIsolationTest`, `ProductionHostCookieSpecificationTest`, `Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest`, `AdminVehicleModerationAndSecurityIntegrationTest`, `Phase5AcceptanceAuditIntegrationTest`, `DatabaseSchemaIntegrationTest`, etc.).
  - `test` for `HealthApiControllerTest` and independent `IslandRideApplicationTests` (`application-test.yml`, H2 in-memory `MODE=MySQL`).
  - Unit / architecture / contract tests without Spring Boot full context (`ModuleBoundaryArchitectureTest`, `CsrfContractIntegrationTest`, `BookingAndPaymentStateMachineTest`, `PaymentGatewayFoundationTest`).
- **Exit code:**
  - `.\mvnw.cmd clean verify`: **`0`** (`BUILD SUCCESS`, total time `02:38 min`)
  - `.\mvnw.cmd -Dtest=IslandRideApplicationTests test`: **`0`** (`BUILD SUCCESS`, total time `28.832 s`)

---

### B. Test summary

#### 1. Unsandboxed `.\mvnw.cmd clean verify`
- **Total tests executed:** `63`
- **Passed:** `63`
- **Failed:** `0`
- **Errors:** `0`
- **Skipped:** `0`
- **Discovered test classes:** `15` (out of `17` `.java` files in `backend/src/test/java`)
- **Failsafe reports (`backend/target/failsafe-reports`):** Not present (`False`; `maven-failsafe-plugin` is not configured).

| Surefire Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `com.islandride.architecture.ModuleBoundaryArchitectureTest` | 3 | 0 | 0 | 0 |
| `com.islandride.auth.AuthenticationIntegrationTest` | 8 | 0 | 0 | 0 |
| `com.islandride.auth.AuthorizationAndTenantIsolationTest` | 6 | 0 | 0 | 0 |
| `com.islandride.auth.CsrfContractIntegrationTest` | 2 | 0 | 0 | 0 |
| `com.islandride.auth.ProductionHostCookieSpecificationTest` | 1 | 0 | 0 | 0 |
| `com.islandride.booking.service.BookingAndPaymentStateMachineTest` | 3 | 0 | 0 | 0 |
| `com.islandride.booking.Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest` | 12 | 0 | 0 | 0 |
| `com.islandride.core.api.HealthApiControllerTest` | 1 | 0 | 0 | 0 |
| `com.islandride.database.DatabaseSchemaIntegrationTest` | 6 | 0 | 0 | 0 |
| `com.islandride.inventory.AdminVehicleModerationAndSecurityIntegrationTest` | 2 | 0 | 0 | 0 |
| `com.islandride.inventory.Phase5AcceptanceAuditIntegrationTest` | 4 | 0 | 0 | 0 |
| `com.islandride.inventory.VehicleCatalogAndInventoryIntegrationTest` | 2 | 0 | 0 | 0 |
| `com.islandride.inventory.VehicleMediaAndAvailabilityIntegrationTest` | 3 | 0 | 0 | 0 |
| `com.islandride.paymentfinance.gateway.PaymentGatewayFoundationTest` | 3 | 0 | 0 | 0 |
| `com.islandride.pricing.Stage6APricingCancellationAndPolicyIntegrationTest` | 7 | 0 | 0 | 0 |
| **Total (`clean verify`)** | **63** | **0** | **0** | **0** |

#### 2. Test discovery check (separate from execution)
- Files under `backend/src/test/java`: `17` `.java` files total:
  - `15` `*Test.java` classes -> all `15` discovered and executed (`63` tests).
  - `1` helper support class (`com.islandride.inventory.InventoryTestSupport`) -> contains no `@Test` methods.
  - `1` `*Tests.java` class (`com.islandride.IslandRideApplicationTests`, `1` `@Test` method `contextLoads()`) -> **excluded from `clean verify` discovery** because `backend/pom.xml` lines 184–186 specify `<includes><include>**/*Test.java</include></includes>`.
- **Independent execution of `IslandRideApplicationTests` (`.\mvnw.cmd -Dtest=IslandRideApplicationTests test`):**
  - **Total tests:** `1` (`Passed: 1`, `Failed: 0`, `Errors: 0`, `Skipped: 0`, profile: `test`).

---

### C. Actual failure details

1. **Current unsandboxed verification (`dfac0e7`):**
   - **Zero test failures and zero context-loading errors** occurred during `.\mvnw.cmd clean verify` (`63/63` passed, exit code `0`) and independent `.\mvnw.cmd -Dtest=IslandRideApplicationTests test` (`1/1` passed, exit code `0`).

2. **Root cause of the prior repeated verification failures (sandboxed runs, e.g., `task-4858` and `task-4925`):**
   - Whenever backend verification ran inside the network-restricted sandbox, every `local`-profile integration test failed on initial Spring `ApplicationContext` startup while Flyway attempted to open a JDBC socket to MySQL `localhost:3307`, and subsequent test methods in the same Surefire run cascaded with Spring TestContext caching failure (`ApplicationContext failure threshold (1) exceeded`).
   - Exact original exception chain observed from the prior sandboxed run (`task-4858.log` / `task-4925.log`, first failure in `AuthenticationIntegrationTest` / `DatabaseSchemaIntegrationTest`):
     ```text
     org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'entityManagerFactory' defined in class path resource [org/springframework/boot/hibernate/autoconfigure/HibernateJpaConfiguration.class]: Failed to initialize dependency 'flywayInitializer' of LoadTimeWeaverAware bean 'entityManagerFactory': Error creating bean with name 'flywayInitializer' defined in class path resource [org/springframework/boot/flyway/autoconfigure/FlywayAutoConfiguration$FlywayConfiguration.class]: Unable to obtain connection from database: Communications link failure

     The last packet sent successfully to the server was 0 milliseconds ago. The driver has not received any packets from the server.
     Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'flywayInitializer' defined in class path resource [org/springframework/boot/flyway/autoconfigure/FlywayAutoConfiguration$FlywayConfiguration.class]: Unable to obtain connection from database: Communications link failure
     Caused by: org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlUnableToConnectToDbException: Unable to obtain connection from database: Communications link failure
     Caused by: com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
     Caused by: com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
     Caused by: java.net.SocketException: Permission denied: getsockopt
     ```
     (Standalone Java JDBC connections inside the sandbox likewise threw `java.net.SocketException: Permission denied: connect`.)
   - **Cascading secondary error on subsequent tests in the same context configuration:**
     ```text
     java.lang.IllegalStateException: ApplicationContext failure threshold (1) exceeded: skipping repeated attempt to load context for [WebMergedContextConfiguration...]
         at org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate.loadContext(DefaultCacheAwareContextLoaderDelegate.java:157)
         at org.springframework.test.context.support.DefaultTestContext.getApplicationContext(DefaultTestContext.java:128)
     ```
   - Pure unit/MockMvc/test-profile tests in that same run (`ModuleBoundaryArchitectureTest`, `BookingAndPaymentStateMachineTest`, `PaymentGatewayFoundationTest`, `HealthApiControllerTest`) did not require MySQL `localhost:3307` and initialized/passed normally, while `task-4858` stalled during subsequent context failures before completion.

---

### D. Root-cause analysis

1. **Why prior verification attempts failed (`SocketException` + cascading context failure):**
   - Integration tests under the `local` profile connect to MySQL (`jdbc:mysql://localhost:3307/islandride_db`, `application-local.yml` line 8) during `flywayInitializer` bean initialization (`before entityManagerFactory`).
   - MySQL (`8.4` on `localhost:3307`), `islandride_db`, Flyway migrations (`V1`–`V12`), Hibernate `ddl-auto: validate`, and Redis (`localhost:6379`) are all healthy and valid on the host.
   - Executing Maven inside the restricted network sandbox denies outbound TCP socket syscalls (`SocketException: Permission denied: getsockopt` / `connect`), causing `flywayInitializer` -> `entityManagerFactory` to abort startup. Spring's `DefaultCacheAwareContextLoaderDelegate` then fast-fails remaining tests sharing that context configuration with `ApplicationContext failure threshold (1) exceeded`.
   - When the exact same `dfac0e7` commit is executed outside the sandbox, Flyway validates all `12` migrations, Hibernate `ddl-auto: validate` initializes cleanly, and all `63` `clean verify` tests pass without modification.

2. **Why `IslandRideApplicationTests` is skipped silently during `mvnw clean verify`:**
   - Maven Surefire's default include patterns are `**/*Test.java`, `**/*Tests.java`, and `**/*TestCase.java`.
   - `backend/pom.xml` lines 184–186 configure:
     ```xml
     <includes>
         <include>**/*Test.java</include>
     </includes>
     ```
   - Defining `<includes>` replaces Surefire's default pattern list instead of appending to it, excluding `com.islandride.IslandRideApplicationTests` (`*Tests.java`) from automatic discovery during `clean verify`.

---

### E. Failure classification

| Observed Issue | Classification | Details |
| --- | --- | --- |
| Prior `clean verify` / integration failures (`Communications link failure` / `Permission denied: getsockopt` & cascading `ApplicationContext failure threshold (1) exceeded`) | **Environment issue** | Terminal sandbox network isolation blocked socket connections to `localhost:3307` (MySQL) and `localhost:6379` (Redis). Outside the sandbox on `dfac0e7`, MySQL, Redis, Flyway (`12` migrations), Hibernate `ddl-auto: validate`, and all `63/63` tests pass. |
| `IslandRideApplicationTests` omitted during `.\mvnw.cmd clean verify` | **Test discovery issue** | Explicit `<include>**/*Test.java</include>` in `backend/pom.xml` (lines 184–186) excludes `**/*Tests.java`. When executed directly via `-Dtest=IslandRideApplicationTests`, it passes (`1/1`, exit code `0`). |
| Application code, test assertions, DB schema, or Spring configuration on `dfac0e7` | **None reproduced** | Zero application defects, test defects, database issues, configuration issues, or shared-state/order failures occurred in unsandboxed verification (`63/63` + `1/1` pass). |

---

### F. Recommended minimal fix

Do **not** touch application code, test logic, migrations, or DB state for `dfac0e7` test execution:

1. **Test discovery fix (`backend/pom.xml`, lines 184–186):** Add `<include>**/*Tests.java</include>` inside `<includes>` alongside `<include>**/*Test.java</include>` so `IslandRideApplicationTests` is discovered automatically during `.\mvnw.cmd clean verify` (`64` total tests).
2. **Execution policy:** Always run `.\mvnw.cmd clean verify` outside the network-restricted sandbox (`BypassSandbox: true`) when validating MySQL/Redis-backed integration tests.

*(Applied post-diagnostic under explicit authorization: added `<include>**/*Tests.java</include>` to `backend/pom.xml`, then re-ran `.\mvnw.cmd clean verify` outside the sandbox -> `BUILD SUCCESS` in `01:41 min`, `Tests run: 64, Failures: 0, Errors: 0, Skipped: 0`, including `IslandRideApplicationTests` (`1/1`, `1.679 s`); updated log saved to `logs/phase-6a-debug-clean-verify.log`.)*

---

### G. Evidence

- Full unsandboxed `.\mvnw.cmd clean verify` log (pre-fix `63/63` run superseded in-place by post-fix `64/64` run, `EXIT_CODE=0`): `logs/phase-6a-debug-clean-verify.log`
- Prior sandboxed socket failure logs:
  - `C:\Users\royde\.gemini\antigravity\brain\eb74b737-1a71-4e0a-a2e3-e24c65f23f0a\.system_generated\tasks\task-4858.log`
  - `C:\Users\royde\.gemini\antigravity\brain\eb74b737-1a71-4e0a-a2e3-e24c65f23f0a\.system_generated\tasks\task-4925.log`
- Independent `IslandRideApplicationTests` execution (`EXIT_CODE=0`, `1/1` PASS):
  - `C:\Users\royde\.gemini\antigravity\brain\eb74b737-1a71-4e0a-a2e3-e24c65f23f0a\.system_generated\tasks\task-4989.log`
- Surefire reports inspected in `backend/target/surefire-reports/`:
  - `TEST-com.islandride.architecture.ModuleBoundaryArchitectureTest.xml` (`.txt`)
  - `TEST-com.islandride.auth.AuthenticationIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.auth.AuthorizationAndTenantIsolationTest.xml` (`.txt`)
  - `TEST-com.islandride.auth.CsrfContractIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.auth.ProductionHostCookieSpecificationTest.xml` (`.txt`)
  - `TEST-com.islandride.booking.service.BookingAndPaymentStateMachineTest.xml` (`.txt`)
  - `TEST-com.islandride.booking.Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.core.api.HealthApiControllerTest.xml` (`.txt`)
  - `TEST-com.islandride.database.DatabaseSchemaIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.inventory.AdminVehicleModerationAndSecurityIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.inventory.Phase5AcceptanceAuditIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.inventory.VehicleCatalogAndInventoryIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.inventory.VehicleMediaAndAvailabilityIntegrationTest.xml` (`.txt`)
  - `TEST-com.islandride.paymentfinance.gateway.PaymentGatewayFoundationTest.xml` (`.txt`)
  - `TEST-com.islandride.pricing.Stage6APricingCancellationAndPolicyIntegrationTest.xml` (`.txt`)
  - `2026-11-12T21-34-35_566.dumpstream` (informational JVM classpath check hint only; zero test failures)
- Failsafe reports directory (`backend/target/failsafe-reports`): not present (`False`).
