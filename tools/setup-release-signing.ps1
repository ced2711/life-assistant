# Creates a new Life Assistant release signing key and configures Gradle to use it.
# Run it yourself in PowerShell:  powershell -ExecutionPolicy Bypass -File tools\setup-release-signing.ps1
# The password is typed here only; it is written to your personal Gradle properties file
# (%USERPROFILE%\.gradle\gradle.properties), never to the repository.

$ErrorActionPreference = 'Stop'

$signingDir = Join-Path $env:USERPROFILE '.life-assistant-signing'
$keystore = Join-Path $signingDir 'life-assistant-release.p12'
$alias = 'life-assistant'
$gradleProperties = Join-Path $env:USERPROFILE '.gradle\gradle.properties'

$keytool = $null
foreach ($candidate in @($env:JAVA_HOME, (Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName))) {
    if ($candidate -and (Test-Path (Join-Path $candidate 'bin\keytool.exe'))) { $keytool = Join-Path $candidate 'bin\keytool.exe'; break }
}
if (-not $keytool) { throw 'keytool.exe was not found. Install JDK 17 first.' }

if (Test-Path $keystore) {
    Write-Host "A signing key already exists at $keystore. Refusing to overwrite it." -ForegroundColor Yellow
    exit 1
}

function Read-Secret([string]$prompt) {
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

Write-Host 'Choose a password for the new release signing key (at least 12 characters).'
Write-Host 'Store it in a password manager: losing it means future updates cannot be installed over this version.'
$first = Read-Secret 'Password'
if ($first.Length -lt 12) { throw 'The password must have at least 12 characters.' }
$second = Read-Secret 'Repeat password'
if ($first -ne $second) { throw 'The passwords do not match.' }
if ($first -match '[\\\r\n]') { throw 'Please avoid backslashes and line breaks in the password.' }

New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
$env:LA_SIGNING_PASSWORD = $first
try {
    & $keytool -genkeypair -v -storetype PKCS12 -keystore $keystore -alias $alias `
        -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=ced2711, O=ced2711' `
        -storepass:env LA_SIGNING_PASSWORD -keypass:env LA_SIGNING_PASSWORD | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the key.' }

    $fingerprints = & $keytool -list -v -keystore $keystore -alias $alias -storepass:env LA_SIGNING_PASSWORD |
        Select-String -Pattern 'SHA1:|SHA256:' | ForEach-Object { $_.Line.Trim() }
    Set-Content -Path (Join-Path $signingDir 'certificate-fingerprints.txt') -Value $fingerprints -Encoding utf8
} finally {
    Remove-Item Env:\LA_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}

# Replace any previous Life Assistant signing lines, keep everything else in the file.
$keys = 'taskledger.keystore.path', 'taskledger.keystore.password', 'taskledger.key.alias', 'taskledger.key.password'
$existing = if (Test-Path $gradleProperties) { Get-Content $gradleProperties | Where-Object { $line = $_; -not ($keys | Where-Object { $line -like "$_=*" }) } } else { @() }
$escapedPath = $keystore -replace '\\', '/'
$lines = @($existing) + @(
    "taskledger.keystore.path=$escapedPath",
    "taskledger.keystore.password=$first",
    "taskledger.key.alias=$alias",
    "taskledger.key.password=$first"
)
New-Item -ItemType Directory -Force -Path (Split-Path $gradleProperties) | Out-Null
Set-Content -Path $gradleProperties -Value $lines -Encoding utf8
$first = $null; $second = $null

Write-Host ''
Write-Host "Release key created: $keystore" -ForegroundColor Green
Write-Host 'Certificate fingerprints (not secret; needed for Google Drive setup):'
Get-Content (Join-Path $signingDir 'certificate-fingerprints.txt') | ForEach-Object { Write-Host "  $_" }
Write-Host ''
Write-Host 'Back up the whole folder above (for example to a USB drive or an encrypted cloud folder).' -ForegroundColor Yellow
