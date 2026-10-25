param(
    [switch]$Apply,
    [string]$MySqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    [string]$CaFile = "$env:USERPROFILE\Downloads\ca.pem"
)

$ErrorActionPreference = "Stop"
if (!(Test-Path -LiteralPath $MySqlExe)) { throw "MySQL client not found. Supply -MySqlExe with its full path." }
if (!(Test-Path -LiteralPath $CaFile)) { throw "Aiven CA certificate not found. Supply -CaFile with its full path." }

# Read-only unless -Apply is explicitly supplied. Never connects to local MySQL.
$trustvaultSql = "SELECT VERSION() AS mysql_version, CURRENT_USER() AS connected_as, DATABASE() AS database_name;"
if ($Apply) {
    $trustvaultSql += " GRANT UPDATE, DELETE ON trustvault.documents TO 'trustvault_app'@'%';"
}
$trustvaultSql += " SHOW GRANTS FOR 'trustvault_app'@'%';"
$trustvaultSql += " SELECT GRANTEE, TABLE_NAME, PRIVILEGE_TYPE FROM information_schema.TABLE_PRIVILEGES WHERE TABLE_SCHEMA='trustvault' AND TABLE_NAME IN ('documents','audit_events') AND GRANTEE=CONCAT(CHAR(39),'trustvault_app',CHAR(39),'@',CHAR(39),'%',CHAR(39)) ORDER BY TABLE_NAME, PRIVILEGE_TYPE;"

$trustvaultArgs = @(
    "--no-defaults", "--protocol=TCP",
    "--host=mysql-10ed7480-trustvault-project.e.aivencloud.com", "--port=11587",
    "--user=avnadmin", "--password", "--ssl-mode=VERIFY_IDENTITY",
    "--ssl-ca=$((Resolve-Path -LiteralPath $CaFile).Path)", "--database=trustvault",
    "--execute=$trustvaultSql"
)
Write-Host "Enter the Aiven avnadmin password at the MySQL prompt. Do not paste it into chat."
& $MySqlExe @trustvaultArgs
if ($LASTEXITCODE -ne 0) { throw "Aiven privilege check failed. Keep the error output; no credentials were printed by this script." }
if ($Apply) {
    Write-Host "Document grant applied on Aiven. No audit UPDATE/DELETE privileges were granted."
} else {
    Write-Host "Read-only check complete. No privileges changed. Use -Apply only after approving changes to the submitted live system."
}
