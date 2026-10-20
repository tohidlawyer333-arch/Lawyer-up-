$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& npm.cmd --prefix tests/web ci --ignore-scripts
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& npm.cmd --prefix tests/web test
exit $LASTEXITCODE
