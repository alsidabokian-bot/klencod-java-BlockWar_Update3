package com.example.blockshooter;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements GameRenderer.FpsListener {
    private GLSurfaceView glView;
    private GameRenderer renderer;
    private GameInput input;
    private TextView info;
    private HudView hud;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        Sfx.init();
        Haptics.init(this);

        input = new GameInput();
        renderer = new GameRenderer(this, input);
        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setShadowLayer(4, 1, 1, Color.BLACK);
        info.setTextSize(12);
        info.setPadding(24, 12, 24, 12);
        info.setText("");

        FrameLayout root = new FrameLayout(this);
        root.addView(glView);
        hud = new HudView(this, input);
        root.addView(hud);
        root.addView(info, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
        setContentView(root);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onFps(final int fps, final int triangles, final int vis, final int total, final int seed,
                      final float px, final float py, final float pz) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                info.setText("FPS " + fps + "  |  " + triangles + " tris");
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (hud != null && hud.handleBack()) return;
        if (input.state == GameInput.STATE_PLAYING) {
            input.postCommand(GameInput.CMD_PAUSE);
        } else if (input.state == GameInput.STATE_PAUSED) {
            input.postCommand(GameInput.CMD_RESUME);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        input.postCommand(GameInput.CMD_PAUSE);   // auto-pause when leaving the app
        glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Sfx.release();
    }
}