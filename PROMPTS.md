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
- Code commit: https://github.com/sk-shahina123/trustvault/commit/75017e66e30124760884ae23c2f809d6f94ccaf1

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
- Code commit: https://github.com/sk-shahina123/trustvault/commit/835efe0d894553cbd92cfc670110f8fe984b941e

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
- Code commit: https://github.com/sk-shahina123/trustvault/commit/858c313489aa952a80f60e22ceada5c2e368dce7

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
- Code commit: https://github.com/sk-shahina123/trustvault/commit/858c313489aa952a80f60e22ceada5c2e368dce7

## 2026-10-22 — Document upload, listing, and audit logging

- Request: Connect the dashboard to document upload and listing, with permission checks and audit records.
- AI assistance: Provided upload validation, document service/controller, error handling, audit recording, and dashboard JavaScript.
- Security review: The server derives ownership from the authenticated account. Upload requires the OWNER role. Document listing filters access in SQL. Document creation and its successful audit entry share a transaction.
- AI mistake corrected: The original DocumentService.java download was missing its final class-closing brace. This was corrected before integration.
- Verified: The application started and the OWNER upload displayed a success message. MySQL showed document 1 with an UPLOAD/SUCCESS audit entry.
- Verified: MySQL recorded the VIEWER attempt as UPLOAD/DENIED with reason OWNER_ROLE_REQUIRED. No document named "Viewer denied upload test" was created.
- Verification pending: Capture the direct request's HTTP 403/PASS output. Complete automated authorization, revocation, and transaction-rollback tests.
- Troubleshooting: Resolved database authentication failure by resetting the trustvault_app password through the MySQL administrator account and supplying it through TRUSTVAULT_DB_PASSWORD.
- Commit: https://github.com/sk-shahina123/trustvault/commit/09f6d3e80c163cfb70366d63e3c7a3edfde3d9af

## 23 October 2026 — Cloud database connection

- Goal: Connect TrustVault to Aiven MySQL using verified TLS.
- AI guidance that needed correction: Repeated configuration changes were suggested before testing the exact password stored in the application's environment.
- Finding: Manually entered MySQL credentials worked, but the password stored in the environment was rejected.
- Fix: Verified the cloud account and updated the application environment with the working password.
- Validation: Spring Boot connected to Aiven, created the demo accounts, and admin, owner, and viewer logins worked.
- Public application deployment is still pending.
- Code commit: https://github.com/sk-shahina123/trustvault/commit/c69c1e9b614cf4de9e626fb7bc35f2001badfbd4

## 2026-10-24 — Downloads and cloud login troubleshooting

- Prompt: "invalid credentials".
- AI assistance: Examined authentication logs and suggested a direct MySQL login test.
- Findings: Aiven was powered off. After restarting it, MySQL rejected the application's database login.
- Fix: Verified that trustvault_app existed and was unlocked, reset its private database password, and updated both datasource environment-variable sets.
- Download implementation: Added the download endpoint and dashboard link, using the authenticated account and permission checks in DocumentService.
- Manual verification: The viewer's direct download request returned "Document not found or access unavailable". SQL checks confirmed owner SUCCESS and viewer DENIED download audit records.
- Lesson: Successful Spring Boot startup alone does not establish that database authentication works.
- Pending: Automated permission and audit tests.
- Code commit: https://github.com/sk-shahina123/trustvault/commit/b62b6843268a2481cfe25433518902dd04892688

## 2026-10-25 - Authentication recovery and sharing verification

- Prompt/task summary: Resolve invalid credentials after application startup, then continue sharing and revocation verification.
- AI assistance: Direct JDBC diagnostics, credential synchronization commands, permission service/controller, sharing UI, and recipient-aware audit records.
- Correction: Manual password reset and synchronization attempts still produced MySQL 1045. We generated one password, applied it to Aiven, tested the identical value through Java, and started the application only after success. The precise cause of the earlier password mismatch was not established.
- Authentication verified: /api/me returned owner@trustvault.test with role OWNER.
- Filename review: Normalized Permissions.html and Permissions.js to lowercase and matched their references for case-sensitive deployment.
- Manual sharing evidence for document 2: audit 24 GRANT SUCCESS; audit 27 viewer DOWNLOAD SUCCESS; audit 28 REVOKE SUCCESS; audit 31 viewer DOWNLOAD DENIED.
- Revocation check: The viewer remained signed in, the document disappeared after refresh, and the previously copied download URL was denied.
- Limits: These are manual checks. Automated negative tests, document editing/deletion, and deployment remain pending.
- Commit link: https://github.com/sk-shahina123/trustvault/commit/0967c448e32452189327f931df77598f2710e36a


## 2026-10-25 — Security review, audit screen and deployment preparation

- Prompt/task summary: Prioritize the required audit trail, important security behavior, live testing and a project the author can explain. Review the repository and the supplied edit-review file, then finish the essential work.
- Source check: All eleven supplied source files matched repository commit 0967c44. The review and changes were made against that actual repository baseline.
- Finding and fix: Earlier service-level audit calls did not cover requests rejected by Spring Security before a controller was reached. Added request auditing for authentication, role, CSRF and firewall rejections, plus controlled API errors. The requests stay blocked if logging fails.
- Finding and fix: EDIT permission existed but had no editing operation. Added server-authorized metadata/file edits, owner-only deletion, matching UI, transactional success auditing and the narrowly scoped database grant. Ownership cannot be changed by submitted fields.
- Finding and fix: Audit rows existed but an administrator could not review them through the application. Added an administrator audit API and screen, with a fresh database check of the enabled ADMIN role. ADMIN has no implicit document-content access.
- Review: Success audits commit with document/permission changes. Denied/failure events use independent transactions. Runtime database privileges prevent audit UPDATE/DELETE/TRUNCATE. This is not tamper-proof against a database administrator or a compromised server that can insert false events; THREATS.md states those boundaries.
- Verification: 34 integration tests passed against isolated MySQL 8.4.7 using real Spring Security filters, services and a restricted database account. No production credentials or data were used. The suite covers other-owner access, role restrictions, current-session revocation, forged parameters, CSRF, audit authorization and rollback when audit insertion fails.
- Test quality check: In a separate copy, deliberately replacing the download ownership condition with an always-true condition made the cross-owner test fail: expected 404, received 200. The release code retains the real authorization condition.
- Deployment preparation: Added HTTPS cookie/proxy settings in a separate hosted profile, health checking, CI, deployment instructions and a script to exercise a live HTTPS site using one synthetic document. Local HTTP use keeps the cloud profile without hosted.
- Delivery blockers: The GitHub integration returned 403 Resource not accessible by integration when publishing was attempted. No remote commit was created by that request. Render is connected but requires the user to confirm its workspace before operations. A Git bundle provides the tested commits for import through the existing authenticated local Git setup.
- Evidence limits: Live HTTPS testing and a Docker build on the host are still pending. Local tests do not prove a public deployment. Search filters and the submission video remain unfinished and are explicitly listed in README.md.
- Security implementation commit: https://github.com/sk-shahina123/trustvault/commit/124973b500d9dd2e41366370119f92d2229dae64 (available on GitHub after the bundle is pushed).

## 2026-10-25 - Public registration and live login regression

- Request: Add registration so new testers can upload private files; retain admin-only audit review.
- Produced: CSRF-protected public signup, fixed OWNER role, BCrypt validation, account/audit transaction, signup throttling, and six regression/security test methods.
- AI error: The explicit login page used Java formatting with an unescaped CSS percent sign; the earlier integration suite tested login submission but did not render the login page. The live check exposed the defect.
- Correction: Escaped the CSS percent sign in commit cfb648a and added GET /login regression coverage in this update. HTML login requests in the live script explicitly request text/html.
- Registration checks cover role spoofing, cross-user access, upload, admin-audit denial, CSRF, duplicates, weak/mismatched passwords, and rollback if audit fails. These new tests must not be described as passed until executed.
- Fix commit: see the registration release commit in Git history.

## 2026-10-25 - Sign-out route regression

- Request: Repair sign-out returning a resource-not-found response and verify live audit viewing.
- Cause: The UI linked to GET /logout, but the custom security setup did not provide a logout confirmation page.
- Correction: Add an explicit GET confirmation page containing the CSRF token; POST /logout invalidates the session, clears authentication/cookie and redirects to /login?logout. GET does not terminate sessions.
- Verification: Production sources and test sources compile. Added a test covering confirmation rendering, CSRF refusal, preservation of session after refused logout, and successful logout/session invalidation. Execution of new MySQL tests remains pending.
- Commit: see the sign-out repair commit in repository history.
