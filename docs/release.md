# Android release preparation

ScanCode 1.1.0 uses the existing application ID `com.scancode.app` and increments `versionCode` to 2. The project targets API 36 and supports API 26 or later. Confirm ownership and availability of the application ID in Play Console before its first submission. [Google Play target API requirement](https://developer.android.com/google/play/requirements/target-sdk)

## Build artifacts

The [Android workflow](../.github/workflows/android.yml) runs Android Lint, JVM tests, an emulator startup test, and 16 KiB native alignment checks. Pull request runs upload the tested debug build as `scancode-preview.apk`. Successful builds on `main` build a release APK, sign it with the distribution key, verify its certificate and alignment, and install and launch that same APK on an Android emulator. The workflow then uploads `scancode.apk`. Downloads are available from the run summary or **Artifacts** for seven days to signed-in GitHub users.

Keep the encrypted distribution keystore and its password in a private backup outside the repository checkout. A Windows DPAPI password backup is tied to the original Windows user and machine. Restore the same key if the signing secrets ever need to be recreated, and check its public certificate fingerprint against `APK_SIGNING_CERT_SHA256` before restoring them.

The `android-distribution` GitHub Actions environment stores the keystore in `APK_SIGNING_KEYSTORE_BASE64` and the password in `APK_SIGNING_PASSWORD`. Its branch policy allows `main`. The signing step reads the key from a temporary file and verifies the resulting APK against the expected public fingerprint before publishing.

Gradle's debug keystore signs previews and local test builds. On Windows, `build.ps1` keeps it at `/root/.android/debug.keystore` in the `scancode-android-home` Docker volume. Android accepts in-place updates signed with a matching certificate. If the installed copy has a different signer, uninstalling it before a fresh installation removes its local app data. [Android app signing](https://developer.android.com/studio/publish/app-signing)

## 1.1.0 verification

The Docker build passed 17 JVM tests and Android Lint with zero errors. The debug APK and unsigned release bundle passed the native alignment checks. An Android 16 emulator decoded a QR code from a simulated camera feed and read an EAN-13 and a photo containing two QR codes offline. Device tests also verified saving, searching, deleting, and clearing local history. Offline tests verified HTTP and HTTPS review, opening existing history, and preserving collections after clearing history and restarting. The physical-device checks below cover real camera focus, low light, and the torch.

## Before the first submission

- [ ] Complete device QA below with an Android phone and a large-screen device or emulator. Verify dense retail barcodes, flashlight behavior and low-light focus on physical hardware.
- [ ] Configure Play App Signing and sign a fresh release bundle with the app's upload key. Android Studio supports **Build > Generate Signed Bundle / APK > Android App Bundle**. Store the upload key, its backup, and passwords privately. [App signing](https://developer.android.com/studio/publish/app-signing)
- [ ] Host the [privacy policy](../PRIVACY.md) at a public URL and enter it in Play Console. Confirm the support address and the developer details for the listing. [Google Play user data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [ ] Complete the Data safety form using the final build and SDK disclosure. The bundled ML Kit SDK may send diagnostic and usage data even though the camera and photo content are decoded locally. Review the other App content declarations for the distribution account. [ML Kit disclosure](https://developers.google.com/ml-kit/android-data-disclosure) · [Play Console App content](https://support.google.com/googleplay/android-developer/answer/9859455)
- [ ] Add the listing text and images in [store assets](store/README.md). Confirm that each screenshot reflects the build and language being listed.
- [ ] When distribution is authorized, use the applicable testing track and meet any production-access conditions for your account. [Personal account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)

## Device QA

Use codes with fictional values, and check the behavior in light and dark mode.

| Scenario | Expected result |
| --- | --- |
| First launch, deny the camera, then enable it | Photo and History stay available after denial. Live scanning starts once camera access is granted. |
| Scan an in-frame QR and an EAN-13 | Each result shows its format and decoded value. The viewfinder disregards codes centered outside its visible frame. |
| Switch frame mode and adjust zoom | Square, Wide and Large update the scan area. The slider reflects pinch-to-zoom. Torch state reflects the actual camera where flash is available. |
| Pick a photo containing several codes | ScanCode lists the detected codes for selection. A blank or unreadable photo gets specific feedback. |
| Put the device offline and scan a local image | The bundled model decodes the image with networking disabled. |
| Scan a web link, phone number and Wi-Fi code | Review each result before the external action. The Wi-Fi code offers settings and a separate password-copy control. |
| Scan an HTTPS link and a known HTTP shortener while offline | Link review identifies the destination and protocol and keeps website reputation unchecked. No page opens unless you choose Open link. |
| Collect a code, add it to a second collection, and clear history | The code appears in both collections after history is cleared. Removing one membership leaves the other available. |
| Save the same code twice and restart the app | One entry remains. Search by code text or format, open an entry, cancel deletion, confirm deletion, and clear history. |
| Rotate, resize, or enlarge text | Main controls remain reachable, camera coordinates follow the preview, and screen content clears the system bars and keyboard. |
| Install over an earlier debug build with a matching key | Existing scans remain visible and can be deleted from History. |
