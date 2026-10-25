# TrustVault

A Spring Boot document vault with server-enforced per-document permissions and an audit trail. Java 17, Spring Security, JDBC, MySQL 8, and same-origin HTML/CSS/JavaScript.

**Live URL:** https://trustvault-shahina.onrender.com — Render deployment confirmed; final end-to-end acceptance testing is still pending.

## Demo accounts

These intentionally public accounts are for synthetic demonstration data only. They are not MySQL credentials.

| Role | Email | Password |
|---|---|---|
| ADMIN | admin@trustvault.test | AdminDemo!2026 |
| OWNER | owner@trustvault.test | OwnerDemo!2026 |
| VIEWER | viewer@trustvault.test | ViewerDemo!2026 |

## Permission model

An enabled account may list and download a document only if it owns that document or has an explicit READ/EDIT grant. ADMIN does not bypass document authorization. An OWNER-role account may upload; being an OWNER does not grant access to another owner's files. READ permits list/download. EDIT additionally permits metadata changes and optional file replacement, and can be granted only to an OWNER-role account. Only the actual document owner may grant, revoke or delete. The application never accepts ownership from a browser field.

The administrator can review audit metadata, including permission changes and rejected attempts. That ability does not confer access to file contents. The audit endpoint also rechecks the current database role, protecting against a stale administrator session after demotion.

## Why this stack

- Spring Security provides password verification, sessions, CSRF protection and security headers.
- JDBC keeps the ownership/permission SQL visible for review and allows explicit transactions and row locks.
- MySQL stores both metadata and small file bytes, allowing document changes and success audit entries to commit together. Files are never placed in a public static directory. This is a small-file demonstration design, not a large-file storage service.
- Same-origin static HTML/JavaScript keeps the frontend and backend in one deployment and uses the existing session and CSRF token mechanism.
- Aiven supplies the database over certificate-verified TLS; Render runs the application container and terminates public HTTPS.

## Run from a clean clone

Install Java 17+ and MySQL 8. Use the Maven wrapper; no global Maven installation is required. Run commands from the folder containing `pom.xml`.

1. Create an empty `trustvault` database as a database administrator.
2. Execute `database/001_create_app_users.sql`, `002_create_documents_and_permissions.sql` and `003_create_audit_events.sql` in that database. These create tables; do not rerun them against the already-initialized Aiven database.
3. Create a runtime MySQL account with your own private password. Grant SELECT and INSERT on `trustvault.*`, and UPDATE/DELETE only on `trustvault.documents` and `trustvault.document_permissions`. It must not have UPDATE, DELETE, DROP or ALTER privileges on the audit table. The supplied 004/005 scripts target the existing Aiven account `trustvault_app`@`%`; use the correct account host for a separate local installation.
4. Set `TRUSTVAULT_DB_URL`, `TRUSTVAULT_DB_USERNAME`, and `TRUSTVAULT_DB_PASSWORD` in the terminal. For local MySQL the URL is `jdbc:mysql://localhost:3306/trustvault`. Never commit private credentials. If `SPRING_DATASOURCE_*` variables also exist, remove them or keep them consistent because they override properties.
5. Seed the three demo accounts once: `./mvnw spring-boot:run -Dspring-boot.run.profiles=seed-demo`. On Windows use `./mvnw.cmd`. The seeder creates missing accounts and does not reset existing passwords. Stop this process before starting another instance.
6. Start normally: `./mvnw spring-boot:run`, then open `http://localhost:8081`.

For the existing Aiven database use profile `cloud` and the repository's CA-only `certs/aiven-truststore.p12`. The store contains a trusted certificate, no private key. Its store password `changeit` is not the database password. If using another Aiven service, replace the trusted CA with that service's CA rather than disabling certificate verification.

## Automated security tests

The integration tests use **real MySQL**, the real Spring Security filters, real service authorization, and a restricted runtime account. They create/reset only the fixed `trustvault_security_test` database on `127.0.0.1:3307`. They never use the Aiven URL or its private credentials.

With Docker installed:

```sh
docker compose -f compose.test.yaml up -d --wait
./mvnw verify
docker compose -f compose.test.yaml down
```

On Windows use `./mvnw.cmd verify`. The compose credentials are disposable test credentials. `TRUSTVAULT_TEST_PORT` can change the local port, and `TRUSTVAULT_TEST_ROOT_PASSWORD` can match a separately provisioned test server. Do not point the tests at a database containing useful data. The tests reset their dedicated schema and test account.

GitHub Actions runs the same checks with a MySQL 8.4 service. See `VALIDATION.md` for observed results and the deliberate authorization-mutation check. A Maven invocation with `-DskipTests` is packaging only.

## Deploy the application

See `DEPLOYMENT.md`. The Dockerfile packages Java 17 and runs as a non-root user. Use a Docker **web service**, not a static-site service. The `cloud,hosted` profiles enable Aiven TLS, HTTPS session cookies, proxy forwarding and bounded server/pool settings. Use only `cloud` for local HTTP testing.

## Verify the live site

After the service is live, run with Python 3:

```sh
python scripts/live_security_check.py https://YOUR-SERVICE.onrender.com
```

This logs in with the public demo accounts, creates one synthetic document, tests unauthorized access, sharing, revocation without logging out, audit access and safe download headers, then deletes that new document. Existing documents are not edited. Audit records remain. It requires the document-management database grant in script 005.

Also open the URL in a private browser window on a different device/network. Verify the owner document screen, sharing screen, edit screen and administrator audit screen. A successful health check alone is insufficient.

## Scope and remaining work

Implemented: authentication, upload validation, filtered listing, download authorization, READ/EDIT grants, revocation, document editing/deletion, administrator audit viewing, and automated security checks.

This review branch adds search by name, owner email, category and inclusive UTC dates. It has not been deployed to the submitted site. See `IMPROVEMENTS.md` for the frozen baseline and separately approved rollout procedure. The submitted live deletion grant still needs approval and verification. Submission recording and additional product polish are outside this update. No antivirus, file-version history, recovery, per-user storage quota, MFA or comprehensive rate limiting is implemented. See `THREATS.md` for the accepted boundaries. Do not upload real confidential data to a deployment with public demo credentials.

API policy: `API.md`. Security analysis: `THREATS.md`. AI assistance history: `PROMPTS.md`.

## Self-registration

Open `/register` or use **Create an account** on the login page. New accounts always have OWNER role: they can upload and manage their own private documents and receive explicit grants. Public registration never creates an administrator. Sign in after registering; no email ownership verification is performed, so only use synthetic demo information. Passwords require at least 12 characters and at most 72 UTF-8 bytes and are hashed with BCrypt. CSRF tokens are required. Account creation and its REGISTER audit record commit together. Duplicate addresses are refused without changing the existing account.

Signup is limited to 10 attempts per client address per hour and 100 attempts per process per hour. This in-memory control resets on restart and is not distributed; shared networks may reach the limit. Email verification, account recovery, and storage quotas remain out of scope. Audit review remains ADMIN-only.

## Search (improvement branch only)

The document screen supports name, owner email, category, upload start date, and upload end date. Filters combine with AND. Text searches are case-insensitive literal substring matches, including literal `%` and `_`. Dates are inclusive UTC dates; invalid or reversed ranges are refused. Only owned or explicitly shared documents may appear, including when ADMIN searches. The newest 100 matches are shown after filtering; clear filters restores the default accessible listing. An authorized metadata endpoint allows editing older search results without relying on the default first 100 entries.
