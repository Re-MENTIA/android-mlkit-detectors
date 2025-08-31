# android-mlkit-detectors

A minimal, focused demo app to evaluate ML Kit detectors (Face to start; Pose optional) with a simple, color‑coded UI and live metrics.

## Goals
- Visualize detection status clearly while previewing camera frames.
- Track basic metrics: FPS, last inference latency, faces count.
- Keep architecture simple so detector swaps (Pose, Barcode) are easy.

## Project Layout
- `app/` single‑module Android app (Compose + CameraX).
- Core screen: `MainActivity` → `DetectorScreen()` displays `PreviewView` and a status bar.

## Build & Run (local)
1) Open in Android Studio (Giraffe+). When prompted, let it sync Gradle.
2) Connect a device, enable camera permission.
3) Run the `app` configuration. You’ll see:
   - Top metrics (Faces, FPS, Latency)
   - Camera preview
   - Status pill: green=detected, red=none, amber=transient

CLI:
```
cd android-mlkit-detectors
./gradlew assembleDebug
./gradlew installDebug
```

## Tech
- CameraX: `camera-core`, `camera-camera2`, `camera-view`, `camera-lifecycle`
- ML Kit Face (play‑services): `com.google.android.gms:play-services-mlkit-face-detection:17.1.0`
- Jetpack Compose for UI

## Extending (Pose / Accuracy)
- Add Pose: `com.google.mlkit:pose-detection-accurate:<ver>` and create a `PoseAnalyzer` like `ImageAnalysis` pipeline.
- Thresholding: compute face size ratio + position centrality for “valid” detections; expose as UI toggles.
- Logging: write per‑frame JSON to `Documents/` with timestamp, result, latency. Optional CSV export.
- Bench runs: fixed‑length capture (e.g., 60s) with summary (precision/recall if you label ground truth).

## Repository Setup (GitHub)
If you want this hosted at `Re-MENTIA/android-mlkit-detectors`:
```
cd android-mlkit-detectors
git init && git add . && git commit -m "chore: initial scaffold"
# Org owners only:
gh repo create Re-MENTIA/android-mlkit-detectors --private --source=. --push
```

## Why a separate demo app?
- Keeps the measurement loop isolated from production code.
- Faster iteration for detector tuning (params, versions, device perf).
- Easy to hand off: small codebase, clear metrics, safe to experiment.

