# Gyro 3D Android Live Wallpaper

Android Java / Filament / glTF 2.0 GLB / gravity-sensor parallax wallpaper.

## Build

1. Open this folder in Android Studio (JDK 17, Android SDK 35).
2. Let Gradle sync. This environment has no Android SDK or Gradle, so the project **has not been compiled or device-tested**. You may need to adjust Filament API calls to match the pinned version.
3. Build > Build APK(s). Output typically: `app/build/outputs/apk/debug/app-debug.apk`.
4. Install the APK, open the launcher icon, tap **Set live wallpaper**.

## Replace the placeholder model

Replace `app/src/main/assets/character.glb` with a real textured GLB character. The bundled GLB is an intentionally simple geometric **test mannequin**, NOT a reconstruction of the reference illustration. It contains a protruding hand and separate fingers for testing parallax. Model convention: Y up, face +Z, origin around torso, roughly 2–3 meters tall. Export a self-contained GLB with embedded textures. If your character is too large/small, resize it in Blender.

## Sensor and camera

Uses `TYPE_GRAVITY` (gravity vector, often sensor-fused) for stable tilt instead of integrating raw gyroscope angular velocity (which drifts). Adjust `camera.lookAt(...)` and `smoothX/smoothY` gains in `GyroWallpaperService.java` for more/less parallax. Render cap is ~30fps, pauses when invisible. Device rotation and launchers may need orientation tuning.

## Limitations

- No APK is included: compilation requires Android Studio/SDK and dependency downloads.
- No finished Lelouch 3D mesh: the included GLB is a placeholder mannequin.
- Some Filament library versions can change API signatures; validate in Android Studio.
- Rendering quality, resource usage and compatibility must be tested on your phone.
