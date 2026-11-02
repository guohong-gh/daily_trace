#requires -Version 5.1
# Daily Trace quality gate: unit tests -> lint -> release build -> metrics.
# Output is intentionally English to avoid PowerShell 5.1 console encoding issues.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$gradlew = Join-Path $root "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradlew)) {
    Write-Host "FAILED: gradlew.bat not found at $gradlew" -ForegroundColor Red
    exit 1
}

function Invoke-GradleStep {
    param(
        [string]$Title,
        [string[]]$Arguments
    )
    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor Cyan
    # PowerShell 5.1: gradle writes JVM/Robolectric warnings to stderr; when the caller
    # captures output with 2>&1 they become ErrorRecords, and $ErrorActionPreference=Stop
    # would abort this script before we can report the step result. Keep reporting in our hands.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $gradlew @Arguments --console=plain
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $Title (gradle exit code $LASTEXITCODE)" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "OK: $Title" -ForegroundColor Green
}

Invoke-GradleStep -Title "1/3 Unit tests (testDebugUnitTest)" -Arguments @("testDebugUnitTest")
Invoke-GradleStep -Title "2/3 Android lint (lintDebug)" -Arguments @("lintDebug")
Invoke-GradleStep -Title "3/3 Release build (assembleRelease)" -Arguments @("assembleRelease")

# --- Test metrics ---------------------------------------------------------
$resultDir = Join-Path $root "app\build\test-results\testDebugUnitTest"
$xmlFiles = @(Get-ChildItem -LiteralPath $resultDir -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue)
if ($xmlFiles.Count -eq 0) {
    Write-Host "FAILED: no JUnit XML results found in $resultDir" -ForegroundColor Red
    exit 1
}

$total = 0
$failures = 0
$errors = 0
$skipped = 0
foreach ($file in $xmlFiles) {
    [xml]$doc = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $suites = @()
    if ($null -ne $doc.testsuite) { $suites = @($doc.testsuite) }
    elseif ($null -ne $doc.testsuites) { $suites = @($doc.testsuites.testsuite) }
    foreach ($suite in $suites) {
        if ($null -eq $suite) { continue }
        $total += [int]$suite.tests
        $failures += [int]$suite.failures
        $errors += [int]$suite.errors
        $skipped += [int]$suite.skipped
    }
}
$passed = $total - $failures - $errors - $skipped

# --- Lint metrics ---------------------------------------------------------
$lintReport = Join-Path $root "app\build\reports\lint-results-debug.txt"
if (-not (Test-Path -LiteralPath $lintReport)) {
    Write-Host "FAILED: lint report not found at $lintReport" -ForegroundColor Red
    exit 1
}
$lintText = [System.IO.File]::ReadAllText($lintReport, [System.Text.Encoding]::UTF8)
$lintMatch = [regex]::Match($lintText, "(?m)^(\d+) errors?, (\d+) warnings?\s*$")
if (-not $lintMatch.Success) {
    Write-Host "FAILED: could not parse lint summary" -ForegroundColor Red
    exit 1
}
$lintErrors = [int]$lintMatch.Groups[1].Value
$lintWarnings = [int]$lintMatch.Groups[2].Value

# --- APK metrics ----------------------------------------------------------
$apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    Write-Host "FAILED: release APK not found at $apk" -ForegroundColor Red
    exit 1
}
$apkBytes = (Get-Item -LiteralPath $apk).Length
$apkMb = [math]::Round($apkBytes / 1MB, 2)

Write-Host ""
Write-Host "================ VERIFY SUMMARY ================" -ForegroundColor Cyan
Write-Host ("Tests   : {0} total, {1} passed, {2} failed, {3} errors, {4} skipped" -f $total, $passed, $failures, $errors, $skipped)
Write-Host ("Lint    : {0} errors, {1} warnings" -f $lintErrors, $lintWarnings)
Write-Host ("Release : {0} bytes ({1} MB)" -f $apkBytes, $apkMb)
Write-Host ("APK     : {0}" -f $apk)
Write-Host "================================================" -ForegroundColor Cyan

if ($failures -gt 0 -or $errors -gt 0 -or $lintErrors -gt 0) {
    Write-Host "VERIFY FAILED" -ForegroundColor Red
    exit 1
}

Write-Host "VERIFY PASSED" -ForegroundColor Green
exit 0
