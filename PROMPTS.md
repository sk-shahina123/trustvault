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