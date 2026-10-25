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

## Isolated improvement-branch verification — 25 October 2026

- Branch: improvements/search-and-deletion; submitted baseline fd596161081c279b301eb68578ecc21f54bfb064 left unchanged remotely.
- Full real-MySQL integration suite: **49 tests, 0 failures, 0 errors, 0 skipped**, MySQL 8.4.7, Java 17.0.20.
- Search checks: combined case-insensitive filters, ownership and READ scope, ADMIN isolation, revocation, literal wildcard/SQL-like inputs, inclusive dates, invalid inputs, filtering before the 100-row limit, and protected individual metadata access.
- Deletion repair reproduced the exact missing-privilege condition by revoking document UPDATE/DELETE in the isolated test database. Owner deletion returned 503 and retained the document; granting only documents UPDATE/DELETE restored deletion. Cross-owner deletion remained denied, permission rows were removed, successful deletion was audited, and audit DELETE remained forbidden.
- Registration, login-page rendering, CSRF, HTML/API logout and session invalidation tests now all executed and passed, in addition to the earlier suite.
- Initial full run found one test expectation issue: no-Accept logout returns 204 rather than a browser redirect. Corrected the HTML test to request text/html and added a separate JSON 204/session-invalidation test. No live logout behavior was changed.
- Dashboard/edit JavaScript syntax checks passed. No new interactive browser acceptance check was performed for the search UI.
- No Aiven grant was applied; no remote main branch update or Render deployment was performed. Production deletion remains pending separately approved application of the scoped grant and a live recheck.

## Approved live rollout and acceptance — 25 October 2026

The author explicitly approved upgrading the existing submitted URL, pushed bc4a36c to main, and supplied Aiven output showing document UPDATE/DELETE grants, while audit_events retained only the existing schema SELECT/INSERT access. Render deploy dep-dbev180n74is73cofc2g reached live at 12:07:50 UTC.

- Original live security check: PASS on synthetic document 4, including upload, private download, role restrictions, CSRF refusal, READ sharing, same-session revocation, owner deletion, and retained success/denial audit records. Document 4 was deleted by the check.
- Search visibility: combined name/owner/category/date filters returned only the owner’s synthetic document 5; ADMIN and ungranted VIEWER saw no match. READ sharing made it visible to VIEWER; revocation removed it. Invalid date returned 400.
- A SQL-like query probe received 403 rather than the empty list expected by the ad-hoc harness; that harness stopped and cleaned document 5. This was not recorded as a full pass. No attempt was made to bypass the refusal. Literal SQL/wildcard handling is covered by the real-MySQL integration suite.
- Positive search/edit/delete acceptance: PASS on synthetic document 6. All filters matched; ADMIN could not see it; owner edit returned 200; old-name search stopped matching; new-name search and protected metadata reflected the edit. Owner deletion returned 200; download returned 404; search no longer returned the deleted file. Admin audit API returned successful UPLOAD, EDIT and DELETE events.
- Earlier synthetic document 3 was downloaded to confirm its test marker and then deleted successfully. No real user document was modified.
- Current feature implementation no longer has the previously reported search/deletion gap. Earlier pending notes describe historical checkpoints.
