# Contributing to ScanCode

Use the build commands in the [README](README.md) and keep changes focused on scanning or reviewing codes. Follow Kotlin's standard formatting and put user-facing text in Android string resources. Update the English and Spanish strings together where applicable.

## Tests and review evidence

Start with the behavior requested in the issue or pull request. Review the existing tests and use the [device QA checklist](docs/release.md#device-qa) for camera, scan result, and history changes. Add or update a test when those checks miss the changed behavior. Use `app/src/test` for JVM tests and `app/src/androidTest` for tests that launch the app or interact with its UI.

Run Android Lint and the JVM tests before opening a pull request. For a UI change, run the [Docker emulator test](README.md#test-app-launch-on-an-emulator) and exercise the affected flow. Capture the changed screen after verifying the expected result. For example, test a history change by opening **History**, checking the expected entry, and capturing that screen.

Include the steps, Android version, device, and outcome in the pull request. Attach screenshots of visual changes or link to the evidence in **Artifacts**. The automatic PR comment shows the startup screen from `HomeScreenTest`. Use fictional sample codes and keep generated evidence out of Git.

The build uses JDK 17 and the checked-in Gradle Wrapper. Keep machine-specific SDK paths and signing credentials outside version control.
