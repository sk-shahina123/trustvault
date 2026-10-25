# Search and deletion follow-up (not deployed)

Submitted baseline: `fd596161081c279b301eb68578ecc21f54bfb064`.
Improvement branch: `improvements/search-and-deletion`.

This branch does not update the submitted main branch, Render service, or Aiven database. Do not merge or redeploy it while the submitted version must remain frozen.

## Search

`GET /api/documents` accepts optional `name`, `owner` (owner email), `category`, `dateFrom`, and `dateTo` parameters. Text is matched case-insensitively as literal substrings. All filters apply together, inside the existing ownership/shared-permission scope. Dates are inclusive UTC calendar dates in YYYY-MM-DD format. Invalid dates, reversed ranges and overlong inputs return 400. Queries return the newest 100 matches; they filter before applying the limit. The UI states this limit and offers Clear filters. Newer search requests take precedence over older responses.

`GET /api/documents/{id}` returns metadata only after checking document access. The edit page uses it so an older document remains editable when it falls outside the default newest-100 listing. Unavailable and nonexistent documents both return 404. Reading this metadata is audited.

## Deletion diagnosis and scoped repair

The observed Aiven error is `SELECT with locking clause command denied ... for table documents`. MySQL requires SELECT plus an appropriate write/lock privilege for FOR UPDATE. Do not remove the row lock or bypass the owner check. The intended runtime account needs UPDATE and DELETE on documents; audit rows must retain their append-only runtime protection.

The helper script is read-only by default:

```powershell
.\scripts\check-aiven-document-grants.ps1
```

It explicitly connects to the known Aiven endpoint over verified TLS and displays the account's grants. It does not change passwords or use local MySQL. Supply the Aiven administrator password only to the MySQL prompt.

Only after approving a change to the submitted live system, apply the grant:

```powershell
.\scripts\check-aiven-document-grants.ps1 -Apply
```

This executes only `GRANT UPDATE, DELETE ON trustvault.documents TO 'trustvault_app'@'%'` and displays the resulting grants. Do not grant UPDATE/DELETE on trustvault.* or audit_events. No schema migration or password reset is required. A table grant takes effect without redeploying, but it changes the current live system's behavior, so it is deliberately not applied by this branch.

After a separately approved rollout, verify owner editing/deletion, cross-owner refusal, grant cleanup, retained audit history, and rollback if audit INSERT fails. Clean up only the synthetic test document (earlier ID 3) after confirming its contents.

## Review locally without changing main

Import the bundle into this branch, not main:

```text
git fetch "C:/Users/shaik/Downloads/TrustVault-search-deletion-review.bundle" improvements/search-and-deletion:refs/heads/improvements/search-and-deletion
git switch improvements/search-and-deletion
```

Use a separate local test database. With Docker Desktop running:

```powershell
docker compose -f compose.test.yaml up -d --wait
.\mvnw.cmd verify
docker compose -f compose.test.yaml down
```

Do not push this update to main or manually deploy it to the submitted Render service without explicit approval.
