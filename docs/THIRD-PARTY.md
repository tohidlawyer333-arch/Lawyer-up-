# Third-party components

The input LawyerUp_Fixed source is preserved and extended. No blanket license or ownership transfer is asserted for the user's source.

- Gradle wrapper 8.13: Apache-2.0; official scripts/JAR from gradle/gradle v8.13.0. Wrapper and distribution hashes are pinned.
- Vazirmatn 33.003: SIL Open Font License 1.1. Original license is included in licenses/Vazirmatn-OFL.txt.
- Tesseract tessdata_fast 4.1.0 (fas + eng): Apache-2.0. Original license is included in licenses/Tessdata-LICENSE.txt. Trained models are bundled for offline use; hashes are recorded.
- Tesseract4Android 4.9.0: Apache-2.0 project with upstream native Tesseract/Leptonica components; distributed by its pinned Maven/JitPack dependency. Review upstream notices when distributing compiled binaries.
- PDFBox-Android 2.0.27.0: Apache-2.0. Fonts and transitive components retain their upstream notices.
- AndroidX, Compose, Room, WorkManager: Android Open Source Project libraries with their upstream licensing.
- jsdom 24.1.3: MIT, development/test-only; npm lockfile preserves dependency hashes.

Dependency licenses and notices must be reviewed for a commercial distribution. No private judicial records are included in tests; identifiers are deliberately synthetic.
