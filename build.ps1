[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Task = @(':app:lintDebug', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:bundleRelease')
)

$ErrorActionPreference = 'Stop'
docker build --tag scancode-build $PSScriptRoot
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker run --rm `
    --mount "type=bind,source=$PSScriptRoot,target=/project" `
    --mount 'type=volume,source=scancode-gradle,target=/home/gradle/.gradle' `
    --mount 'type=volume,source=scancode-android-home,target=/root/.android' `
    scancode-build bash ./gradlew --no-daemon @Task
exit $LASTEXITCODE
