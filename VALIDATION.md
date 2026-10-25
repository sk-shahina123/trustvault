# Validation evidence

Date: 25 October 2026.

- The code supplied in `trustVaultEditReviewFile.txt` matched all eleven corresponding files at repository commit `0967c448e32452189327f931df77598f2710e36a`.
- The security integration suite ran on Java 17.0.20 with an isolated MySQL 8.4.7 server: **34 tests, 0 failures, 0 errors, 0 skipped**.
- Tests exercised actual Spring Security filters, JdbcTemplate services, schema constraints and a restricted runtime database account. Production Aiven credentials/data were not used.
- The runtime account was denied UPDATE, DELETE and TRUNCATE on `audit_events`.
- Tests injected an audit INSERT failure using a test-only trigger and verified rollback of upload/edit/delete/grant and refusal to release download bytes.
- Mutation check in a separate copy: replace the download ownership predicate with an always-true condition while retaining the query parameters. `otherOwnerCannotDownloadEvenWithSpoofedParameters` failed with **Status expected 404 but was 200**. The release copy retains the real predicate.
- New browser JavaScript passed Node syntax checks. These are syntax checks, not browser interaction tests.

Pending: GitHub Actions execution on the published change, Docker image build on the actual host, public HTTPS deployment, live script execution, browser/device smoke test, and recording the final URL. A local passing test suite does not establish any of those results.

## Earlier manual Aiven evidence

For document 2, user-supplied audit rows showed GRANT/SUCCESS (24), viewer DOWNLOAD/SUCCESS (27), REVOKE/SUCCESS (28), and viewer DOWNLOAD/DENIED (31). The viewer remained logged in and reused the old download URL after revocation. These were manual checks of the earlier implementation, separate from the new automated tests.

## Registration update

`mvn -DskipTests test-compile` completed successfully for the registration update on 25 October 2026. Six new test methods compile, but have not yet run against isolated MySQL in this session. Do not add them to the previous 34-pass count.

Live deployed commit cfb648a passed all three demo logins, upload, safe download headers, CSRF rejection, unauthorized access, sharing, READ edit/delete denial, admin audit denial for viewer, and same-session revocation. The script stopped at owner deletion: Aiven reported `SELECT with locking clause command denied` on `documents`. Apply database/005_grant_document_management.sql as the cloud administrator and rerun. Synthetic test document ID 3 needs cleanup after that grant. Admin audit contents and successful owner deletion are not yet verified by the full live script.

Logout repair: `mvn -DskipTests test-compile` passed. A session/CSRF regression test was added, but not executed against MySQL in this session. Live ADMIN login and /audit.html returned success; audit API verification is recorded separately once complete.
