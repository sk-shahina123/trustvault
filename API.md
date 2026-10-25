# API and authorization policy

Except `/healthz` and the login flow, endpoints require authentication. Browser sessions are the primary UI mechanism; HTTP Basic is also supported for API clients over HTTPS. Mutations require a valid CSRF token even when Basic authentication is used. Obtain the current session's token and header name from `/api/csrf`.

| Method and path | Allowed caller | Effect |
|---|---|---|
| GET `/healthz` | Anyone | Minimal database readiness result, no configuration values |
| GET `/api/health` | Signed-in user | Database connectivity check |
| GET `/api/me` | Signed-in user | Session account identity and role |
| GET `/api/csrf` | Signed-in user | Session CSRF token and header name |
| POST `/api/documents` | Enabled OWNER | Multipart upload: name, category, file |
| GET `/api/documents` | Enabled account | Latest 100 documents owned or explicitly shared with caller |
| GET or HEAD `/api/documents/{id}/download` | Enabled owner or READ/EDIT recipient | Authorized file attachment; HEAD returns headers only |
| POST `/api/documents/{id}/edit` | Enabled OWNER who owns the document or holds EDIT | Multipart name/category and optional replacement file; ownership cannot change |
| DELETE `/api/documents/{id}` | Enabled OWNER who owns this document | Delete file and grants; preserve audit history |
| GET `/api/documents/{id}/permissions` | Enabled OWNER who owns this document | Current named grants |
| POST `/api/documents/{id}/permissions` | Enabled OWNER who owns this document | URL-encoded email and permission; READ or EDIT; EDIT recipients must have OWNER role |
| DELETE `/api/documents/{id}/permissions/{recipientId}` | Enabled OWNER who owns this document | Revoke grant |
| GET `/api/admin/audit-events?before={eventId}` | Current enabled ADMIN | Up to 100 events, descending ID; optional exclusive pagination cursor |
| GET `/api/admin/check` | Session ADMIN | Legacy role-check message; audit viewing additionally checks current database role |
| GET `/login` / POST `/login` | Anyone | CSRF-protected session login |
| GET `/logout` / POST `/logout` | Signed-in user | Confirmation page / CSRF-protected logout |

Responses containing document data and audit data use `Cache-Control: no-store`. Downloads force attachment disposition and `X-Content-Type-Options: nosniff`. Missing documents and inaccessible documents have the same download response. API authentication failures return 401 JSON. Forbidden roles or missing CSRF return 403; malformed requests return controlled 400/405/415 responses; database/audit outages return 503 without file bytes.

The static pages do not authorize actions: the backend repeats the decision for every operation. All document writes and their successful audit entries share one transaction. Rejections are recorded independently so rollback cannot erase them. Framework/security failures record the authenticated identity when one has actually been established; otherwise actor identity is null. Request audit does not store passwords, tokens, request bodies or query strings.

## Registration

- `GET /register`: public HTML form with CSRF token.
- `POST /register`: public, CSRF-protected form (`email`, `password`, `confirmPassword`). The server fixes role to OWNER and ignores any client role field. Success: 303 to `/login?registered`; validation: 400; duplicate email: 409; signup limit: 429; database/audit failure: 503.
