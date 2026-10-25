# Search and deletion release

Live URL: https://trustvault-shahina.onrender.com
Application commit: bc4a36cb80c676bc97a0dbecaacd222fe4ab8521
Render deploy: dep-dbev180n74is73cofc2g — live on 25 October 2026.

The update was initially prepared separately to protect the submitted version. The author then explicitly authorized updating the existing live website, pushed the tested code to main, and applied the scoped Aiven grant. The submitted URL remains the same.

## What changed

- Search by name, owner email, category and inclusive UTC date range. All filters combine with AND; text is treated as case-insensitive literal substrings. Only owned or explicitly shared documents can appear. The newest 100 matches are returned after filtering.
- Clear filters and controlled invalid-date/range responses.
- Protected document-metadata endpoint so older search results can still be edited without relying on the default newest-100 list.
- Scoped database grant: UPDATE/DELETE on trustvault.documents. Existing document_permissions grants remain; no UPDATE/DELETE was granted on audit_events. Authorization locks and owner checks remain intact.

## Verification

49 integration tests passed against isolated MySQL 8.4.7. Live checks passed for sharing/revocation, unauthorized access, search visibility, editing, owner deletion, and audit records. Test documents 4, 5 and 6 were cleaned up; the earlier synthetic document 3 was also verified by its test marker and removed. No existing user document was edited or deleted by the checks.

One SQL-like search probe was refused with HTTP 403 on the public route; the test harness had expected an empty list and stopped, then cleaned up its file. The normal positive search/edit/delete checks were run separately and passed. Parameterized handling of SQL-like literal input is covered by the isolated MySQL tests.

The grant helper defaults to read-only inspection. Its -Apply switch changes cloud permissions and is not needed for routine operation now that the required grant has been confirmed.

No file-version history, recovery, email verification, password-reset flow, per-user storage quotas or antivirus scanning is implemented. Search shows up to 100 matches; older matches can be found using narrower filters. Final browser/device checks and the submission video/write-up remain the author’s responsibility.
