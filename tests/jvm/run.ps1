param([Parameter(Mandatory=$true)][string]$JsonJar)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) { throw 'Set JAVA_HOME and ANDROID_HOME.' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$android = Join-Path $env:ANDROID_HOME 'platforms/android-35/android.jar'
$expected = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
if ((Get-FileHash -LiteralPath $JsonJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Unexpected JSON test jar.' }
$cp = "$android;$JsonJar"
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
foreach ($name in @('PinStore','CryptoStore','Credential','BwField','BwFieldCollection')) { $sources += Join-Path $root "app/src/main/java/com/alexcantini/tvvault/$name.java" }
$classes = Join-Path $PSScriptRoot 'classes'
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp $cp -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp "$classes;$JsonJar;$android" com.alexcantini.tvvault.Regression
if ($LASTEXITCODE -ne 0) { throw 'Regression failed.' }
