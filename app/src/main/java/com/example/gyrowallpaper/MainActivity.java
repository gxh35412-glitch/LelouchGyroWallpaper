package com.example.gyrowallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 60, 36, 36);
        TextView info = new TextView(this);
        info.setText("Gyro 3D Wallpaper\n\nTilt your phone to move the camera around the GLB model.\n\nReplace app/src/main/assets/character.glb with your own GLB before building.");
        info.setTextSize(18);
        Button button = new Button(this);
        button.setText("Set live wallpaper");
        button.setOnClickListener(v -> {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new android.content.ComponentName(this, GyroWallpaperService.class));
            startActivity(intent);
        });
        layout.addView(info); layout.addView(button); setContentView(layout);
    }
}
