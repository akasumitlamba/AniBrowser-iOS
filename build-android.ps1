param([switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Push-Location (Join-Path $projectRoot 'android')
try {
    & node --test app/src/test/playback.test.cjs app/src/test/navigation.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Controller tests failed.' }
    if ($TestsOnly) { return }
    $localGradle = Join-Path $projectRoot '.tools/gradle-9.7.1/bin/gradle.bat'
    $buildCommand = if (Test-Path -LiteralPath $localGradle) { $localGradle } else { '.\gradlew.bat' }
    & $buildCommand :app:assembleDebug --console=plain '-PcentralRepo=https://maven-central.storage-download.googleapis.com/maven2'
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed. No new APK is verified.' }
    Get-ChildItem app/build/outputs/apk -Recurse -Filter '*.apk' | ForEach-Object {
        Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
    }
} finally {
    Pop-Location
}
