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
