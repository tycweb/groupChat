# FlowCam

A native Android camera app built with Kotlin, Jetpack Compose, CameraX, and Camera2 interop,
focused on a smooth, low-latency preview that automatically adapts to whatever frame rate and
resolution the device's camera hardware actually supports.

## What's implemented

- **Fullscreen Compose preview** using `PreviewView` in `PERFORMANCE` implementation mode,
  wrapped via `AndroidView`.
- **Real capability detection** (`CameraCapabilityDetector`) — every FPS range, resolution,
  flash/AF/stabilization/HDR capability, zoom range, and exposure range shown anywhere in the app
  is read live from `CameraCharacteristics` via Camera2. Nothing is hardcoded or assumed; the UI
  only ever offers options that are actually supported by the connected camera.
- **Camera2 interop through CameraX** (`CameraController`) — the FPS range, video stabilization
  mode, and HDR scene mode are set via `Camera2Interop.Extender` on the `Preview`, `ImageCapture`,
  and `VideoCapture` builders, so the true capture pipeline runs at the negotiated rate rather than
  a UI-only label.
- **Graceful degradation** (`CameraConfigurationSelector`) — resolves the user's requested
  resolution/FPS against what the hardware reports and always falls back to the closest valid
  combination, surfacing a plain-language explanation (e.g. "60 FPS unavailable on this device -
  falling back to 30 FPS.") instead of silently failing or crashing.
- **Photo capture** via `ImageCapture`, saved straight to MediaStore (`Pictures/FlowCam`), with
  tap-to-focus, focus lock, exposure compensation, and flash mode.
- **Video recording** via CameraX `Recorder`/`VideoCapture`, saved to MediaStore
  (`Movies/FlowCam`), with a live recording timer, optional audio (requested only when the user
  actually enables it), and automatic quality selection.
- **Real, measured performance overlay** — the FPS number in the on-screen HUD comes from an
  `ImageAnalysis` analyzer that counts actual frames delivered per second
  (`PerformanceMonitor`), not a fabricated constant. If thermal throttling or low light drops the
  real throughput below the configured target, the overlay reflects that honestly.
- **MAX SMOOTHNESS mode** — when enabled, configuration resolution prefers the highest stable FPS
  the hardware reports and a slightly lower resolution tier for headroom, without ever requesting
  a combination the camera doesn't support.
- **Capability Inspector** screen — a developer/debug view listing every detected camera's
  resolutions, FPS ranges, and a supported/unsupported checklist (autofocus, flash, HDR,
  stabilization, zoom, 4K@60fps, etc.), useful for testing across different phones.
- **Settings screen** backed by Jetpack DataStore (`SettingsRepository`): FPS, resolution, flash,
  stabilization, HDR, grid, audio, performance overlay, MAX SMOOTHNESS, keep-screen-awake.
- **Runtime permissions done minimally**: `CAMERA` is requested on launch; `RECORD_AUDIO` is only
  requested right before a recording actually needs it.
- **Never crashes on unsupported hardware** — every camera lookup, bind, and characteristic read
  is wrapped so a missing feature (no flash, no front camera, no 60fps, no 4K, permission denial,
  camera disconnect) degrades to a warning message rather than a crash.

## Project structure

```
app/src/main/java/com/flowcam/
├── MainActivity.kt              Compose NavHost, keep-screen-awake control
├── camera/
│   ├── CameraController.kt      CameraX binding + Camera2Interop (FPS/stabilization/HDR), zoom/exposure/focus/torch
│   ├── CameraCapabilities.kt    Capability data model + feature checklist
│   ├── CameraCapabilityDetector.kt  Camera2 characteristics inspection
│   ├── CameraConfiguration.kt   Resolves desired settings -> valid (size, fps) with fallback
│   ├── CameraState.kt           Enums + immutable CameraUiState
│   └── CameraViewModel.kt       Orchestrates everything into UI state
├── recording/VideoRecorder.kt   Recording start/stop + event -> state translation
├── photo/PhotoCapture.kt        Photo capture + MediaStore finalize
├── performance/PerformanceMonitor.kt  Measured (not faked) frame-rate tracking
├── storage/MediaStoreManager.kt MediaStore output targets for photo/video
├── settings/SettingsRepository.kt  DataStore-backed persisted settings
└── ui/
    ├── CameraScreen.kt          Fullscreen preview + controls
    ├── SettingsScreen.kt
    ├── CapabilityScreen.kt
    ├── theme/                   Compose Material3 theme
    └── components/              Reusable pieces (grid overlay, HUD, permission prompt, buttons)
```

## Building

1. Open the project root in Android Studio (Koala/2024.1 or newer recommended).
2. Let Gradle sync — it will download the Android Gradle Plugin 8.5.0, Kotlin 1.9.24, and all
   CameraX/Compose/DataStore/Coil dependencies from Google's and Maven Central's repositories, so
   an internet connection is required for the first sync.
3. Build → Run on a physical device. **A physical device with a real camera is strongly
   recommended** — the emulator's virtual camera does not report realistic FPS ranges or
   stabilization/HDR capabilities, which defeats the point of this app's capability detection.

### Note on the Gradle wrapper jar

This project ships `gradlew` / `gradlew.bat` and `gradle/wrapper/gradle-wrapper.properties`
(pointing at Gradle 8.7), but the binary `gradle-wrapper.jar` itself isn't included, since it's a
compiled binary artifact and this project was generated in a sandboxed environment without network
access to fetch it. Two easy options:

- **Recommended:** open the project folder directly in Android Studio. Android Studio will detect
  the project as an Android Gradle project and offer to regenerate the wrapper jar / sync with its
  bundled Gradle automatically.
- Or, if you have Gradle installed locally, run `gradle wrapper --gradle-version 8.7` once from the
  project root to generate `gradle/wrapper/gradle-wrapper.jar`.

### Minimum requirements

- Android Studio Koala (2024.1) or newer
- JDK 17 (bundled with recent Android Studio)
- minSdk 26 (Android 8.0), compileSdk/targetSdk 34

## Continuous integration

`.github/workflows/build.yml` builds a debug APK on every push/PR via GitHub Actions. It installs
JDK 17, uses `gradle/actions/setup-gradle` to get a `gradle` binary on `PATH`, runs
`gradle wrapper --gradle-version 8.7` once to generate the wrapper jar this repo doesn't commit,
then lints and assembles the debug APK with `./gradlew`, uploading both as build artifacts.

## Known limitations / honest caveats

- CameraX's `Quality` enum for video recording only has discrete tiers (`SD`, `HD`, `FHD`, `UHD`);
  there's no dedicated 1440p tier, so a 1440p request records at the closest available tier (FHD)
  with a fallback strategy — this is a CameraX platform limitation, not an oversight.
- HDR is exposed via the Camera2 `CONTROL_SCENE_MODE_HDR` capability check, which is what a plain
  Camera2/CameraX app can query without vendor extensions. Some devices expose a richer HDR
  pipeline only through the CameraX Extensions API (`camera-extensions`), which requires an
  async capability query against a specific vendor library and was intentionally kept out of the
  default binding path here to avoid crashing on devices/emulators without an extensions vendor
  library installed.
- This project was assembled and reviewed in a sandboxed environment without access to an Android
  SDK/emulator or network-based Gradle sync, so it has not been compiled end-to-end here. The code
  was written and cross-checked carefully against the CameraX 1.3.4 / Compose BOM 2024.06.00 /
  AGP 8.5.0 APIs, and every cross-file call site was checked to have a matching function signature,
  but please treat the first Android Studio sync + build as the actual compile check, and file
  issues against anything that doesn't line up.
