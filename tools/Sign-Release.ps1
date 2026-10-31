param(
    [string]$KeystorePath,
    [string]$Alias = 'tvvault',
    [string]$ExpectedCertificateSha256
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$signer = Get-ChildItem -LiteralPath (Join-Path $sdkPath 'build-tools') -Filter apksigner.bat -Recurse |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $signer) { throw 'apksigner non trovato nel SDK Android.' }
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
$unsigned = Join-Path $PSScriptRoot 'TVVault_v0.17.0_release-unsigned.apk'
$outputApk = Join-Path $PSScriptRoot 'TVVault_v0.17.0_release.apk'
$pendingApk = Join-Path $PSScriptRoot '.TVVault-signing-pending.apk'
if (-not (Test-Path -LiteralPath $unsigned)) { throw 'Lo script deve essere accanto all APK release-unsigned.' }
if (-not $KeystorePath) { $KeystorePath = Read-Host 'Percorso completo in cui conservare la chiave definitiva .jks (fuori dal repository)' }
$KeystorePath = [IO.Path]::GetFullPath($KeystorePath.Trim('"'))
$createNew = -not (Test-Path -LiteralPath $KeystorePath)
if ($createNew) {
    Write-Host 'Verrà creata una nuova chiave. Non può aggiornare un APK firmato con una chiave diversa.' -ForegroundColor Yellow
    Write-Host 'Non disinstallare TV Vault e non cancellarne i dati per risolvere un errore di firma: perderesti il vault.' -ForegroundColor Yellow
}
$storeSecret = Read-Host 'Password della chiave (scegline una robusta e conservala; non viene mostrata)' -AsSecureString
if ($storeSecret.Length -lt 8) { $storeSecret.Dispose(); throw 'Usa una password di almeno 8 caratteri.' }
$keySecret = $null
$confirmSecret = $null
$storePtr = [IntPtr]::Zero
$keyPtr = [IntPtr]::Zero
$confirmPtr = [IntPtr]::Zero
try {
    $storePtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storeSecret)
    $env:TVVAULT_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($storePtr)
    if ($createNew) {
        $confirmSecret = Read-Host 'Ripeti la password' -AsSecureString
        $confirmPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirmSecret)
        if ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($confirmPtr) -cne $env:TVVAULT_STORE_PASSWORD) { throw 'Le password non coincidono.' }
        $parentPath = Split-Path -Parent $KeystorePath
        if (-not (Test-Path -LiteralPath $parentPath)) { New-Item -ItemType Directory -Path $parentPath -Force | Out-Null }
        & $keytool -genkeypair -keystore $KeystorePath -storetype JKS -alias $Alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Alex Cantini, OU=TV Vault' -storepass:env TVVAULT_STORE_PASSWORD -keypass:env TVVAULT_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Creazione della chiave non riuscita.' }
        $env:TVVAULT_KEY_PASSWORD = $env:TVVAULT_STORE_PASSWORD
        Write-Host "Chiave creata: $KeystorePath. Conservala e fanne un backup privato separato." -ForegroundColor Yellow
    } else {
        $keySecret = Read-Host "Password alias $Alias (Invio se uguale alla password della chiave)" -AsSecureString
        if ($keySecret.Length -eq 0) { $env:TVVAULT_KEY_PASSWORD = $env:TVVAULT_STORE_PASSWORD }
        else {
            $keyPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySecret)
            $env:TVVAULT_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPtr)
        }
    }
    & $signer.FullName sign --alignment-preserved true --ks $KeystorePath --ks-key-alias $Alias --ks-pass env:TVVAULT_STORE_PASSWORD --key-pass env:TVVAULT_KEY_PASSWORD --out $pendingApk $unsigned
    if ($LASTEXITCODE -ne 0) { throw 'Firma non riuscita. Controlla la chiave e le password.' }
    $verification = & $signer.FullName verify --print-certs $pendingApk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Verifica della firma fallita.' }
    $match = [regex]::Match(($verification -join "`n"), 'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
    if (-not $match.Success) { throw 'Impossibile ricavare l impronta pubblica della firma.' }
    $fingerprint = $match.Groups[1].Value.ToLowerInvariant()
    if ($ExpectedCertificateSha256 -and $fingerprint -ne $ExpectedCertificateSha256.Replace(':','').ToLowerInvariant()) { throw 'La firma non corrisponde a quella attesa. Non installare questo APK.' }
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'TVVault-signing-certificate-sha256.txt')) {
        $previousFingerprint = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'TVVault-signing-certificate-sha256.txt') -Raw).Trim()
        if ($previousFingerprint -ne $fingerprint) { throw 'La firma differisce da quella precedentemente registrata in questa cartella. Non pubblicare questo APK.' }
    }
    Move-Item -LiteralPath $pendingApk -Destination $outputApk -Force
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'TVVault-signing-certificate-sha256.txt'), $fingerprint + "`n", [Text.UTF8Encoding]::new($false))
    $verification | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'TVVault-signature-verification.txt') -Encoding utf8
    Write-Host "APK firmato e verificato: $outputApk" -ForegroundColor Green
    Write-Host "Impronta pubblica SHA-256: $fingerprint"
    Write-Host 'Prima dell installazione confronta la firma con quella della versione già presente sulla TV.' -ForegroundColor Yellow
    if ($createNew) {
        $backupPath = Read-Host 'Cartella privata di backup su un altro supporto (Invio per farlo manualmente dopo)'
        if ($backupPath) {
            if (-not (Test-Path -LiteralPath $backupPath -PathType Container)) { throw 'Cartella di backup non trovata. Esegui il backup manualmente.' }
            $backupFile = Join-Path $backupPath ([IO.Path]::GetFileName($KeystorePath))
            if (Test-Path -LiteralPath $backupFile) { throw 'Esiste già una chiave con quel nome nel backup. Non viene sovrascritta.' }
            Copy-Item -LiteralPath $KeystorePath -Destination $backupFile
            Write-Host "Backup della chiave creato: $backupFile. Conserva separatamente la password."
        }
    }
} finally {
    Remove-Item -LiteralPath $pendingApk -ErrorAction SilentlyContinue
    Remove-Item Env:TVVAULT_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:TVVAULT_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($storePtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($storePtr) }
    if ($keyPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPtr) }
    if ($confirmPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($confirmPtr) }
    $storeSecret.Dispose()
    if ($keySecret) { $keySecret.Dispose() }
    if ($confirmSecret) { $confirmSecret.Dispose() }
}
