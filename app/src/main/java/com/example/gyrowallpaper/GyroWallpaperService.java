
package com.example.gyrowallpaper;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.util.Log;

import com.google.android.filament.Camera;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GyroWallpaperService extends WallpaperService {

    private static final String TAG = "GyroWallpaper";

    static {
        com.google.android.filament.Filament.init();
    }

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    private class WallpaperEngine extends Engine
            implements SensorEventListener, Choreographer.FrameCallback {

        private final SensorManager sensors =
                (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        private final Sensor gravity =
                sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);

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

        private int cameraEntity;
        private int keyLightEntity;
        private int fillLightEntity;

        private boolean visible;
        private boolean surfaceReady;
        private boolean frameScheduled;

        private float targetX;
        private float targetY;
        private float smoothX;
        private float smoothY;

        private long lastFrameNanos;

        WallpaperEngine() {
            setTouchEventsEnabled(false);
        }

        private void initializeFilament() throws Exception {

            if (filament != null) return;

            Log.i(TAG, "Initializing Filament");

            filament =
                    com.google.android.filament.Engine.create();

            renderer = filament.createRenderer();
            scene = filament.createScene();
            view = filament.createView();

            view.setScene(scene);
            view.setBlendMode(View.BlendMode.OPAQUE);
            view.setPostProcessingEnabled(true);
            view.setAntiAliasing(View.AntiAliasing.FXAA);

            // 非黑色背景，用于确认渲染循环是否工作
            Renderer.ClearOptions clear =
                    new Renderer.ClearOptions();

            clear.clear = true;
            clear.clearColor =
                    new float[]{0.025f, 0.035f, 0.09f, 1f};

            renderer.setClearOptions(clear);

            // Camera
            cameraEntity = EntityManager.get().create();

            camera = filament.createCamera(cameraEntity);
            view.setCamera(camera);

            camera.lookAt(
                    0.0, 0.0, 5.0,
                    0.0, 0.0, 0.45,
                    0.0, 1.0, 0.0
            );

            // Main light
            keyLightEntity = EntityManager.get().create();

            new LightManager.Builder(
                    LightManager.Type.DIRECTIONAL
            )
                    .color(1f, 0.88f, 0.78f)
                    .intensity(110000f)
                    .direction(-0.45f, -0.8f, -0.6f)
                    .castShadows(true)
                    .build(filament, keyLightEntity);

            scene.addEntity(keyLightEntity);

            // Fill light
            fillLightEntity = EntityManager.get().create();

            new LightManager.Builder(
                    LightManager.Type.DIRECTIONAL
            )
                    .color(0.48f, 0.58f, 1f)
                    .intensity(45000f)
                    .direction(0.65f, -0.25f, 0.55f)
                    .build(filament, fillLightEntity);

            scene.addEntity(fillLightEntity);

            // GLB loader
            materials = new UbershaderProvider(filament);

            loader = new AssetLoader(
                    filament,
                    materials,
                    EntityManager.get()
            );

            resources = new ResourceLoader(filament);

            byte[] bytes;

            try (
                    InputStream input =
                            getAssets().open("character.glb");

                    ByteArrayOutputStream output =
                            new ByteArrayOutputStream()
            ) {

                byte[] chunk = new byte[8192];
                int count;

                while ((count = input.read(chunk)) != -1) {
                    output.write(chunk, 0, count);
                }

                bytes = output.toByteArray();
            }

            Log.i(TAG, "GLB bytes=" + bytes.length);

            if (bytes.length < 20 ||
                    bytes[0] != 'g' ||
                    bytes[1] != 'l' ||
                    bytes[2] != 'T' ||
                    bytes[3] != 'F') {

                throw new IllegalStateException(
                        "Invalid character.glb"
                );
            }

            ByteBuffer buffer =
                    ByteBuffer.allocateDirect(bytes.length)
                            .order(ByteOrder.nativeOrder());

            buffer.put(bytes);
            buffer.flip();

            model = loader.createAsset(buffer);

            if (model == null) {
                throw new IllegalStateException(
                        "Filament failed to parse GLB"
                );
            }

            resources.loadResources(model);

            scene.addEntities(model.getEntities());

            Log.i(TAG,
                    "GLB loaded. Entities=" +
                    model.getEntities().length);

            model.releaseSourceData();
        }

        private void attachSurface(SurfaceHolder holder) {

            Surface surface = holder.getSurface();

            if (filament == null ||
                    surface == null ||
                    !surface.isValid()) {

                Log.w(TAG, "Surface not ready");
                return;
            }

            if (swapChain != null) {
                filament.destroySwapChain(swapChain);
                swapChain = null;
            }

            swapChain = filament.createSwapChain(surface);

            surfaceReady = swapChain != null;

            Log.i(TAG,
                    "SwapChain ready=" + surfaceReady);

            requestFrame();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {

            super.onSurfaceCreated(holder);

            try {
                initializeFilament();
                attachSurface(holder);

            } catch (Exception ex) {

                surfaceReady = false;

                Log.e(TAG,
                        "Filament initialization failed",
                        ex);
            }
        }

        @Override
        public void onSurfaceChanged(
                SurfaceHolder holder,
                int format,
                int width,
                int height
        ) {

            super.onSurfaceChanged(
                    holder, format, width, height
            );

            if (view != null &&
                    camera != null &&
                    width > 0 &&
                    height > 0) {

                view.setViewport(
                        new Viewport(0, 0, width, height)
                );

                camera.setProjection(
                        42.0,
                        (double) width / height,
                        0.1,
                        100.0,
                        Camera.Fov.VERTICAL
                );

                Log.i(TAG,
                        "Surface size=" + width + "x" + height);
            }

            attachSurface(holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {

            surfaceReady = false;

            Choreographer.getInstance()
                    .removeFrameCallback(this);

            frameScheduled = false;

            if (filament != null && swapChain != null) {

                filament.flushAndWait();

                filament.destroySwapChain(swapChain);

                swapChain = null;
            }

            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {

            visible = isVisible;

            if (visible) {

                if (gravity != null) {
                    sensors.registerListener(
                            this,
                            gravity,
                            SensorManager.SENSOR_DELAY_GAME
                    );
                }

                requestFrame();

            } else {

                sensors.unregisterListener(this);

                Choreographer.getInstance()
                        .removeFrameCallback(this);

                frameScheduled = false;
            }

            Log.i(TAG,
                    "Wallpaper visible=" + visible);
        }

        private void requestFrame() {

            if (visible &&
                    surfaceReady &&
                    !frameScheduled) {

                frameScheduled = true;

                Choreographer.getInstance()
                        .postFrameCallback(this);
            }
        }

        @Override
        public void doFrame(long frameTimeNanos) {

            frameScheduled = false;

            if (!visible ||
                    !surfaceReady ||
                    renderer == null ||
                    swapChain == null ||
                    camera == null ||
                    view == null) {

                return;
            }

            if (frameTimeNanos - lastFrameNanos >=
                    16_000_000L) {

                lastFrameNanos = frameTimeNanos;

                smoothX +=
                        (targetX - smoothX) * 0.10f;

                smoothY +=
                        (targetY - smoothY) * 0.10f;

                camera.lookAt(
                        smoothX * 0.42f,
                        smoothY * 0.28f,
                        5.0,
                        0.0,
                        0.0,
                        0.45,
                        0.0,
                        1.0,
                        0.0
                );

                if (renderer.beginFrame(
                        swapChain,
                        frameTimeNanos
                )) {

                    renderer.render(view);
                    renderer.endFrame();
                }
            }

            requestFrame();
        }

        @Override
        public void onSensorChanged(SensorEvent event) {

            targetX = clamp(event.values[0] / 9.81f);
            targetY = clamp(event.values[1] / 9.81f);
        }

        private float clamp(float value) {

            return Math.max(
                    -1f,
                    Math.min(1f, value)
            );
        }

        @Override
        public void onAccuracyChanged(
                Sensor sensor,
                int accuracy
        ) {
        }

        private void destroyFilament() {

            if (filament == null) return;

            try {

                if (swapChain != null) {
                    filament.flushAndWait();
                    filament.destroySwapChain(swapChain);
                    swapChain = null;
                }

                if (model != null && loader != null) {

                    if (scene != null) {
                        scene.removeEntities(model.getEntities());
                    }

                    loader.destroyAsset(model);
                    model = null;
                }

                if (resources != null) {
                    resources.destroy();
                    resources = null;
                }

                if (loader != null) {
                    loader.destroy();
                    loader = null;
                }

                if (materials != null) {
                    materials.destroyMaterials();
                    materials = null;
                }

                if (view != null) {
                    filament.destroyView(view);
                    view = null;
                }

                if (scene != null) {
                    filament.destroyScene(scene);
                    scene = null;
                }

                if (renderer != null) {
                    filament.destroyRenderer(renderer);
                    renderer = null;
                }

                filament.destroy();
                filament = null;

            } catch (Exception ex) {
                Log.e(TAG, "Filament cleanup failed", ex);
            }
        }

        @Override
        public void onDestroy() {

            visible = false;
            surfaceReady = false;

            sensors.unregisterListener(this);

            Choreographer.getInstance()
                    .removeFrameCallback(this);

            frameScheduled = false;

            destroyFilament();

            super.onDestroy();
        }
    }
}
