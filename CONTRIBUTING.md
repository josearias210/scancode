# Contributing to ScanCode

Use the build commands in the [README](README.md) and keep changes focused on scanning or reviewing codes. Follow Kotlin's standard formatting and put user-facing text in Android string resources. Update the English and Spanish strings together where applicable.

Before opening a pull request, run Android Lint and the JVM tests. For camera, result, or history changes, exercise the relevant cases in the [device QA checklist](docs/release.md#device-qa). Include affected devices and Android versions, the steps you tested, and screenshots for visual changes. Use sample codes containing fictional data in reports and screenshots.

The build uses JDK 17 and the checked-in Gradle Wrapper. Keep machine-specific SDK paths and signing credentials outside version control.
