package com.example.gyrowallpaper;

import android.app.WallpaperManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.LightManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;
import com.google.android.filament.gltfio.AssetLoader;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.gltfio.ResourceLoader;
import com.google.android.filament.gltfio.UbershaderProvider;
import com.google.android.filament.utils.UiHelper;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GyroWallpaperService extends WallpaperService {
    @Override public WallpaperService.Engine onCreateEngine() { return new WallpaperEngine(); }

    private class WallpaperEngine extends WallpaperService.Engine implements SensorEventListener,
            UiHelper.RendererCallback, Choreographer.FrameCallback {
        private final Handler main = new Handler(Looper.getMainLooper());
        private final SensorManager sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        private final Sensor gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        private final UiHelper ui = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        private com.google.android.filament.Engine filament;
        private Renderer renderer;
        private View view;
        private Scene scene;
        private Camera camera;
        private SwapChain swapChain;
        private AssetLoader loader;
        private ResourceLoader resources;
        private FilamentAsset model;
        private UbershaderProvider materials;
        private int cameraEntity, lightEntity;
        private boolean visible = false, surfaceReady = false, scheduled = false;
        private float targetX = 0, targetY = 0, smoothX = 0, smoothY = 0;
        private long lastFrame = 0;

        WallpaperEngine() {
            ui.setRenderCallback(this);
            setTouchEventsEnabled(false);
        }

        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            try {
                filament = com.google.android.filament.Engine.create();
                renderer = filament.createRenderer();
                scene = filament.createScene();
                view = filament.createView();
                view.setScene(scene);
                view.setBlendMode(View.BlendMode.OPAQUE);
                view.setPostProcessingEnabled(true);
                view.setAntiAliasing(View.AntiAliasing.FXAA);
                view.setClearOptions(new View.ClearOptions());
                cameraEntity = EntityManager.get().create();
                camera = filament.createCamera(cameraEntity);
                view.setCamera(camera);
                lightEntity = EntityManager.get().create();
                new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                        .color(0.76f, 0.60f, 1.0f).intensity(85000f)
                        .direction(-0.5f, -1.0f, -0.6f).castShadows(true)
                        .build(filament, lightEntity);
                scene.addEntity(lightEntity);
                materials = new UbershaderProvider(filament);
                loader = new AssetLoader(filament, materials, EntityManager.get());
                resources = new ResourceLoader(filament);
                try (InputStream input = getAssets().open("character.glb")) {
                    byte[] bytes = new byte[input.available()];
                    int offset = 0, count;
                    while (offset < bytes.length && (count = input.read(bytes, offset, bytes.length-offset)) > 0) offset += count;
                    ByteBuffer buffer = ByteBuffer.allocateDirect(offset).order(ByteOrder.nativeOrder());
                    buffer.put(bytes, 0, offset).flip();
                    model = loader.createAsset(buffer);
                    if (model != null) {
                        resources.loadResources(model);
                        scene.addEntities(model.getEntities());
                    }
                }
                ui.attachTo(holder);
            } catch (Exception ex) { android.util.Log.e("GyroWallpaper", "Initialization failed", ex); }
        }

        @Override public void onNativeWindowChanged(Surface surface) {
            if (filament == null) return;
            if (swapChain != null) filament.destroySwapChain(swapChain);
            swapChain = filament.createSwapChain(surface);
            surfaceReady = true;
            requestFrame();
        }
        @Override public void onDetachedFromSurface() {
            surfaceReady = false;
            if (filament != null && swapChain != null) {
                filament.flushAndWait(); filament.destroySwapChain(swapChain); swapChain = null;
            }
        }
        @Override public void onResized(int width, int height) {
            if (view == null || camera == null || height == 0) return;
            view.setViewport(new Viewport(0, 0, width, height));
            camera.setProjection(45, (double)width/height, 0.05, 100, Camera.Fov.VERTICAL);
        }
        @Override public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) {
                if (gravity != null) sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
                requestFrame();
            } else sensors.unregisterListener(this);
        }
        private void requestFrame() {
            if (visible && surfaceReady && !scheduled) {
                scheduled = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
        @Override public void doFrame(long frameTimeNanos) {
            scheduled = false;
            if (!visible || !surfaceReady || renderer == null || swapChain == null) return;
            // 30 FPS cap for power savings.
            if (frameTimeNanos - lastFrame >= 33_000_000L) {
                lastFrame = frameTimeNanos;
                smoothX += (targetX - smoothX) * 0.12f;
                smoothY += (targetY - smoothY) * 0.12f;
                // Camera translation rather than rotation gives a true parallax view.
                float x = smoothX * 0.75f;
                float y = smoothY * 0.50f;
                camera.lookAt(x, y, 3.4, 0, 0.25, 0, 0, 1, 0);
                if (renderer.beginFrame(swapChain, frameTimeNanos)) {
                    renderer.render(view);
                    renderer.endFrame();
                }
            }
            requestFrame();
        }
        @Override public void onSensorChanged(SensorEvent event) {
            // Gravity values are in m/s^2; clamp to [-1, 1] to limit camera motion.
            targetX = clamp(event.values[0] / 9.81f);
            targetY = clamp(event.values[1] / 9.81f);
        }
        private float clamp(float value) { return Math.max(-1f, Math.min(1f, value)); }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            ui.detach();
            if (filament != null) {
                if (swapChain != null) { filament.flushAndWait(); filament.destroySwapChain(swapChain); swapChain = null; }
                if (model != null) { scene.removeEntities(model.getEntities()); loader.destroyAsset(model); model = null; }
                if (resources != null) { resources.destroy(); resources = null; }
                if (loader != null) { loader.destroy(); loader = null; }
                if (materials != null) { materials.destroyMaterials(); materials = null; }
                if (lightEntity != 0) { scene.removeEntity(lightEntity); filament.destroyEntity(lightEntity); }
                if (cameraEntity != 0) filament.destroyCameraComponent(cameraEntity);
                if (view != null) filament.destroyView(view);
                if (scene != null) filament.destroyScene(scene);
                if (renderer != null) filament.destroyRenderer(renderer);
                filament.destroy(); filament = null;
            }
            super.onSurfaceDestroyed(holder);
        }
        @Override public void onDestroy() {
            visible = false;
            sensors.unregisterListener(this);
            Choreographer.getInstance().removeFrameCallback(this);
            super.onDestroy();
        }
    }
}
