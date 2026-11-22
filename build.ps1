[CmdletBinding()]
param(
    [switch]$InstrumentedTest,
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Task = @(':app:lintDebug', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:bundleRelease')
)

$ErrorActionPreference = 'Stop'

$image = 'scancode-build'
$buildArguments = @()
$containerArguments = @('bash', './gradlew', '--no-daemon') + $Task
if ($InstrumentedTest) {
    $image = 'scancode-build-emulator'
    $buildArguments = @('--build-arg', 'INCLUDE_ANDROID_EMULATOR=true')
    $containerArguments = @(
        'bash', 'scripts/run-android-emulator-tests.sh',
        'app/build/reports/androidTests/emulator.log', 'off', '30'
    )
}

docker build @buildArguments --tag $image $PSScriptRoot
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker run --rm `
    --mount "type=bind,source=$PSScriptRoot,target=/project" `
    --mount 'type=volume,source=scancode-gradle,target=/home/gradle/.gradle' `
    --mount 'type=volume,source=scancode-android-home,target=/root/.android' `
    $image @containerArguments
exit $LASTEXITCODE
