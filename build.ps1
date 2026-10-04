param([switch]$Setup)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if ($Setup -or -not (Test-Path -LiteralPath '.tools/android-sdk/platforms/android-35/android.jar')) {
        python 'scripts/setup_tools.py'
        if ($LASTEXITCODE -ne 0) { throw 'Android tool setup failed' }
    }
    python 'scripts/build.py'
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
} finally { Pop-Location }
