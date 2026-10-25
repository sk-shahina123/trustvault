-- Run against Aiven using the avnadmin database account.
GRANT UPDATE, DELETE
ON trustvault.document_permissions
TO 'trustvault_app'@'%';