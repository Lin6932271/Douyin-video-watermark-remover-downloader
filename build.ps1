param([switch]$Setup)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $PSScriptRoot '.tools/android-sdk' }
    if ($Setup -or -not (Test-Path -LiteralPath (Join-Path $sdk 'platforms/android-35/android.jar'))) {
        python 'scripts/setup_tools.py'
        if ($LASTEXITCODE -ne 0) { throw 'Android tool setup failed' }
    }
    python 'scripts/build.py'
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
} finally { Pop-Location }
