# Deployment to Render with existing Aiven MySQL

Use one Docker web service. The application serves both the frontend and API. Keep Aiven for the existing database; do not create a replacement database or import scripts into the local MySQL instance by mistake.

## Existing database prerequisite

As the Aiven administrator, check `SELECT VERSION(), CURRENT_USER(), DATABASE();`. Expect the cloud MySQL version and `avnadmin` account, not `root@localhost`. Apply only the new grant:

```sql
GRANT UPDATE, DELETE ON trustvault.documents TO 'trustvault_app'@'%';
SHOW GRANTS FOR 'trustvault_app'@'%';
```

Existing SELECT/INSERT and document_permissions UPDATE/DELETE grants remain. Do not grant UPDATE/DELETE on all of `trustvault.*` or on `audit_events`. Script `database/005_grant_document_management.sql` contains the same new grant. No destructive schema change is needed for this release.

## Service configuration

| Setting | Value |
|---|---|
| Repository | `https://github.com/sk-shahina123/trustvault` |
| Branch | `main`, after security checks pass |
| Service | Web service |
| Runtime | Docker |
| Root directory | Repository root (leave blank in Dashboard) |
| Dockerfile | `./Dockerfile` |
| Docker context | `.` |
| Health check | `/healthz` |
| Start command override | Leave blank; Docker ENTRYPOINT starts Java |
| Profiles | `cloud,hosted` |

`render.yaml` is an optional Blueprint equivalent. If creating the service directly, Dashboard/MCP settings are the source of truth; do not also apply the Blueprint to create a duplicate service. The Blueprint defaults to the Free plan and waits for CI before automatic redeploys. Free services can sleep when idle and take time to wake. Confirm current pricing before choosing paid compute; card verification alone does not select a paid plan.

Set these environment variables on the service:

```text
SPRING_PROFILES_ACTIVE=cloud,hosted
TRUSTVAULT_DB_URL=jdbc:mysql://mysql-10ed7480-trustvault-project.e.aivencloud.com:11587/trustvault
TRUSTVAULT_DB_USERNAME=trustvault_app
TRUSTVAULT_DB_PASSWORD=<the current private database password>
```

The last value must be entered privately in Render, never in Git or chat. The CA-only truststore is copied by the Dockerfile. Do not disable `VERIFY_IDENTITY` to work around a connection problem. Avoid conflicting `SPRING_DATASOURCE_*` variables. Do not enable `seed-demo` for routine hosted starts; the three existing Aiven demo accounts have already been created.

The working Windows database credential was saved outside the repository at `%LOCALAPPDATA%\TrustVault\database-credential.xml`. On the same Windows user/device, this command copies its password to the clipboard without printing it, for pasting directly into Render's secret field:

```powershell
$trustvaultCredential = Import-Clixml -LiteralPath "$env:LOCALAPPDATA\TrustVault\database-credential.xml"
$trustvaultCredential.GetNetworkCredential().Password | Set-Clipboard
```

After pasting, clear the clipboard with `Set-Clipboard -Value ""`. Do not upload the credential file. If it is missing, use the existing known credential; a password reset is not a routine deployment step.

## Verification

1. Confirm the Render deploy reaches `live`, not just that a build started.
2. Open `/healthz` and confirm `OK`. Verify login for all three demo roles.
3. Run `python scripts/live_security_check.py https://YOUR-SERVICE.onrender.com` and save the PASS output.
4. Test the URL in a private browser window on another device/network. Test the administrator audit screen.
5. Record the real URL, deployed commit and results in README/VALIDATION. Ensure the database and service will remain available for the judges.

If startup or login fails, use the first `Caused by:` error in the deployment/runtime logs. Distinguish a database credential/TLS error from a port binding, missing environment variable, or build failure. Keep secrets redacted. Do not blindly retry or repeatedly reset credentials.
