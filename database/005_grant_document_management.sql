-- Run once as avnadmin on the Aiven database. No audit UPDATE/DELETE permission is granted.
GRANT UPDATE, DELETE ON trustvault.documents TO 'trustvault_app'@'%';
