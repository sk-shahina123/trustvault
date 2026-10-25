# AI Assistance Log

## 2026-10-20 — Initial setup and login

- Request: Step-by-step guidance to build and understand TrustVault.
- AI assistance: Explained the Spring Boot project, health endpoint,
  frontend connection, and default Spring Security login.
- Issue encountered: Port 8080 was occupied. Changed server.port to
  8081 and verified successful startup.
- Manual verification: The health endpoint displayed its message,
  the UI button retrieved it, and signing in opened the UI.
- Remaining work: Named role accounts, document management,
  document permissions, audit logging, and deployment.

  ## 2026-10-21 — MySQL accounts and database-backed login

- Prompt summary: Create admin, owner, and viewer accounts in MySQL
  and connect Spring Security login to them.
- AI output: app_users schema, a seed-demo account initializer,
  BCrypt password hashing, and a JDBC UserDetailsService.
- Code review: The generated account lookup uses a SQL parameter.
  Roles come from the database, and the enabled flag controls
  whether a new login is allowed.
- Manual verification: All three demo accounts signed in successfully.
  An incorrect password was rejected. The health endpoint also
  successfully queried MySQL.
- Findings: No authentication bug was identified by these checks.
  Disabled-account behavior and document authorization still need tests.
- Code commit: Add the actual commit link after publishing.@'

## 2026-10-21 — Display the signed-in account
- Request: Continue building the interface alongside the backend.
- AI output: An /api/me endpoint and UI showing the account email,
  role, connection check, and sign-out link.
- Review and fix: Updated the frontend to distinguish HTTP errors
  from failures to reach the server.
- Manual verification: Account display, connection check, and
  sign-out worked.
- Remaining verification: HTTP error and expired-session handling
  need dedicated checks.
- Code commit: Pending.

## 2026-10-21 — Restrict an endpoint to ADMIN
- Request: Add backend authorization and check access with all three roles.
- AI output: A SecurityFilterChain rule for /api/admin/** and an
  AdminController endpoint.
- Review: The role restriction runs on the server before the controller.
  Typing the endpoint URL directly does not bypass it.
- Manual verification: ADMIN received "Admin access confirmed".
  OWNER and VIEWER received 403 Forbidden.
- Findings: No defect was found in these manual checks.
  This does not yet implement document-specific permissions or
  record denied requests in an audit log.
- Remaining work: Automated authorization tests, document permissions,
  and audit recording.
- Code commit: Pending.

## 2026-10-22 — Document upload, listing, and audit logging

- Request: Connect the dashboard to document upload and listing, with permission checks and audit records.
- AI assistance: Provided upload validation, document service/controller, error handling, audit recording, and dashboard JavaScript.
- Security review: The server derives ownership from the authenticated account. Upload requires the OWNER role. Document listing filters access in SQL. Document creation and its successful audit entry share a transaction.
- AI mistake corrected: The original DocumentService.java download was missing its final class-closing brace. This was corrected before integration.
- Verified: The application started and the OWNER upload displayed a success message. MySQL showed document 1 with an UPLOAD/SUCCESS audit entry.
- Verified: MySQL recorded the VIEWER attempt as UPLOAD/DENIED with reason OWNER_ROLE_REQUIRED. No document named "Viewer denied upload test" was created.
- Verification pending: Capture the direct request's HTTP 403/PASS output. Complete automated authorization, revocation, and transaction-rollback tests.
- Troubleshooting: Resolved database authentication failure by resetting the trustvault_app password through the MySQL administrator account and supplying it through TRUSTVAULT_DB_PASSWORD.
- Commit: Pending.

## 23 October 2026 — Cloud database connection

- Goal: Connect TrustVault to Aiven MySQL using verified TLS.
- AI guidance that needed correction: Repeated configuration changes were suggested before testing the exact password stored in the application's environment.
- Finding: Manually entered MySQL credentials worked, but the password stored in the environment was rejected.
- Fix: Verified the cloud account and updated the application environment with the working password.
- Validation: Spring Boot connected to Aiven, created the demo accounts, and admin, owner, and viewer logins worked.
- Public application deployment is still pending.
## 2026-10-24 — Downloads and cloud login troubleshooting

- Prompt: "invalid credentials".
- AI assistance: Examined authentication logs and suggested a direct MySQL login test.
- Findings: Aiven was powered off. After restarting it, MySQL rejected the application's database login.
- Fix: Verified that trustvault_app existed and was unlocked, reset its private database password, and updated both datasource environment-variable sets.
- Download implementation: Added the download endpoint and dashboard link, using the authenticated account and permission checks in DocumentService.
- Manual verification: The viewer's direct download request returned "Document not found or access unavailable". SQL checks confirmed owner SUCCESS and viewer DENIED download audit records.
- Lesson: Successful Spring Boot startup alone does not establish that database authentication works.
- Pending: Automated permission and audit tests.
## 2026-10-25 - Authentication recovery and sharing verification

- Prompt/task summary: Resolve invalid credentials after application startup, then continue sharing and revocation verification.
- AI assistance: Direct JDBC diagnostics, credential synchronization commands, permission service/controller, sharing UI, and recipient-aware audit records.
- Correction: Manual password reset and synchronization attempts still produced MySQL 1045. We generated one password, applied it to Aiven, tested the identical value through Java, and started the application only after success. The precise cause of the earlier password mismatch was not established.
- Authentication verified: /api/me returned owner@trustvault.test with role OWNER.
- Filename review: Normalized Permissions.html and Permissions.js to lowercase and matched their references for case-sensitive deployment.
- Manual sharing evidence for document 2: audit 24 GRANT SUCCESS; audit 27 viewer DOWNLOAD SUCCESS; audit 28 REVOKE SUCCESS; audit 31 viewer DOWNLOAD DENIED.
- Revocation check: The viewer remained signed in, the document disappeared after refresh, and the previously copied download URL was denied.
- Limits: These are manual checks. Automated negative tests, document editing/deletion, and deployment remain pending.
- Commit link: Add the actual GitHub commit URL after pushing.
