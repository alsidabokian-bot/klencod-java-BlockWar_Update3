package com.example.blockshooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GameRenderer implements GLSurfaceView.Renderer {

    public interface FpsListener {
        void onFps(int fps, int triangles, int chunksVisible, int chunksTotal, int seed, float x, float y, float z);
    }

    private static final float SKY_R = 0.53f, SKY_G = 0.81f, SKY_B = 0.92f;

    private static final int WORLD_X = 96, WORLD_Y = 36, WORLD_Z = 96;
    private static final int CHUNK = 16;
    private static final int MAX_RESERVE = 240;

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVP;\n" +
            "uniform vec3 uCam;\n" +
            "uniform vec2 uFog;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vColor;\n" +
            "varying float vFog;\n" +
            "void main() {\n" +
            "  vec3 light = normalize(vec3(0.45, 1.0, 0.30));\n" +
            "  float d = max(dot(aNormal, light), 0.0);\n" +
            "  vColor = aColor * (0.50 + 0.50 * d);\n" +
            "  float dist = distance(aPos, uCam);\n" +
            "  vFog = clamp((dist - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);\n" +
            "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform vec3 uFogColor;\n" +
            "varying vec3 vColor;\n" +
            "varying float vFog;\n" +
            "void main() {\n" +
            "  gl_FragColor = vec4(mix(vColor, uFogColor, vFog), 1.0);\n" +
            "}\n";

    private static class Chunk {
        int vbo;
        int vertexCount;
        float minX, minY, minZ, maxX, maxY, maxZ;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private FpsListener fpsListener;

    private World world;
    private Chunk[] chunks = new Chunk[0];

    private int program;
    private int aPos, aNormal, aColor, uMVP, uCam, uFog, uFogColor;

    private final float[] proj = new float[16];
    private final float[] gunProj = new float[16];   // fixed 70-degree projection for the gun model
    private float aspect = 1.6f;
    private float curFov = 70f;
    private final float[] view = new float[16];
    private final float[] mvp = new float[16];
    private final float[] planes = new float[24];

    private final GameInput input;
    private final Player player = new Player();
    private final Weapon weapon = new Weapon();
    private final Projectiles projectiles = new Projectiles();
    private final Enemies enemies = new Enemies();
    private final DynamicBatch batch = new DynamicBatch();
    private long lastFrameNano;

    // game flow
    private int state = GameInput.STATE_MENU;
    private int level = 1;
    private int savedLevel = 1;
    private float levelTime = 0f;
    private float clearTimer = -1f;
    private int prevKills = 0;
    private boolean soundOn = true, vibOn = true;

    private long fpsTimer;
    private int frames;

    public GameRenderer(Context context, GameInput input) {
        this.context = context;
        this.input = input;
        if (context instanceof FpsListener) fpsListener = (FpsListener) context;

        prefs = context.getSharedPreferences("blockshooter", Context.MODE_PRIVATE);
        savedLevel = Math.max(1, Math.min(Levels.TOTAL, prefs.getInt("level", 1)));
        soundOn = prefs.getBoolean("sound", true);
        vibOn = prefs.getBoolean("vibration", true);
        Sfx.enabled = soundOn;
        Haptics.enabled = vibOn;

        input.savedLevel = savedLevel;
        input.soundOn = soundOn;
        input.vibOn = vibOn;
        input.state = state;
    }

    // ------------------------------------------------------------------
    // GL lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(SKY_R, SKY_G, SKY_B, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aNormal = GLES20.glGetAttribLocation(program, "aNormal");
        aColor = GLES20.glGetAttribLocation(program, "aColor");
        uMVP = GLES20.glGetUniformLocation(program, "uMVP");
        uCam = GLES20.glGetUniformLocation(program, "uCam");
        uFog = GLES20.glGetUniformLocation(program, "uFog");
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor");

        // The GL context is new: old buffer ids are gone. The world itself is kept,
        // so returning from the background does not restart the level.
        chunks = new Chunk[0];
        if (world == null) {
            generateLevel(savedLevel);
            state = GameInput.STATE_MENU;
        }
        buildMeshes();

        lastFrameNano = System.nanoTime();
        fpsTimer = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        aspect = (float) width / Math.max(1, height);
        curFov = 70f;
        Matrix.perspectiveM(proj, 0, curFov, aspect, 0.1f, 200f);
        Matrix.perspectiveM(gunProj, 0, 70f, aspect, 0.1f, 200f);
    }

    // ------------------------------------------------------------------
    // Level loading
    // ------------------------------------------------------------------

    /** Builds the world and spawns player + soldiers for a level (CPU side only). */
    private void generateLevel(int n) {
        level = Math.max(1, Math.min(Levels.TOTAL, n));
        Levels.Config cfg = Levels.get(level);

        world = new World(WORLD_X, WORLD_Y, WORLD_Z);
        world.generate(Levels.seed(level));

        player.spawn(world);
        weapon.reset();
        projectiles.clear();
        enemies.clear();
        enemies.spawnWave(world, player, cfg.count, cfg);
        input.reset();

        levelTime = 0f;
        clearTimer = -1f;
        prevKills = 0;
        input.level = level;
        input.levelKills = 0;
        input.levelEnemies = cfg.count;
        input.levelTime = 0f;
    }

    private void loadLevel(int n) {
        generateLevel(n);
        deleteChunks();
        buildMeshes();
    }

    private void buildMeshes() {
        int cw = WORLD_X / CHUNK, cd = WORLD_Z / CHUNK;
        Chunk[] list = new Chunk[cw * cd];
        int[] ids = new int[1];
        for (int cx = 0; cx < cw; cx++) {
            for (int cz = 0; cz < cd; cz++) {
                int x0 = cx * CHUNK, z0 = cz * CHUNK;
                MeshBuilder mesh = MeshBuilder.build(world, x0, z0, x0 + CHUNK, z0 + CHUNK);
                Chunk c = new Chunk();
                c.vertexCount = mesh.vertexCount;
                c.minX = x0; c.maxX = x0 + CHUNK;
                c.minZ = z0; c.maxZ = z0 + CHUNK;
                c.minY = 0;  c.maxY = WORLD_Y;
                if (mesh.vertexCount > 0) {
                    GLES20.glGenBuffers(1, ids, 0);
                    c.vbo = ids[0];
                    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, c.vbo);
                    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.buffer.capacity() * 4,
                            mesh.buffer, GLES20.GL_STATIC_DRAW);
                }
                list[cx * cd + cz] = c;
            }
        }
        chunks = list;
    }

    private void deleteChunks() {
        int[] id = new int[1];
        for (Chunk c : chunks) {
            if (c != null && c.vbo != 0) {
                id[0] = c.vbo;
                GLES20.glDeleteBuffers(1, id, 0);
            }
        }
        chunks = new Chunk[0];
    }

    // ------------------------------------------------------------------
    // Game flow
    // ------------------------------------------------------------------

    private void startLevel(int n) {
        loadLevel(n);
        state = GameInput.STATE_PLAYING;
        input.bannerTime = System.nanoTime();
    }

    private void goMenu() {
        loadLevel(savedLevel);
        state = GameInput.STATE_MENU;
    }

    private void saveProgress() {
        prefs.edit().putInt("level", savedLevel).apply();
        input.savedLevel = savedLevel;
    }

    private void completeLevel() {
        Sfx.play(Sfx.WIN, 1f);
        Haptics.vibrate(80);
        if (level >= Levels.TOTAL) {
            savedLevel = 1;
            state = GameInput.STATE_VICTORY;
        } else {
            savedLevel = level + 1;
            state = GameInput.STATE_LEVEL_CLEAR;
        }
        saveProgress();
    }

    private void handleCommand(int cmd) {
        switch (cmd) {
            case GameInput.CMD_CONTINUE:
                if (state == GameInput.STATE_MENU) startLevel(savedLevel);
                break;
            case GameInput.CMD_NEW_GAME:
                if (state == GameInput.STATE_MENU) {
                    savedLevel = 1;
                    saveProgress();
                    startLevel(1);
                }
                break;
            case GameInput.CMD_NEXT_LEVEL:
                if (state == GameInput.STATE_LEVEL_CLEAR) startLevel(level + 1);
                break;
            case GameInput.CMD_RETRY:
                if (state == GameInput.STATE_DEAD || state == GameInput.STATE_PAUSED) startLevel(level);
                break;
            case GameInput.CMD_MENU:
                if (state != GameInput.STATE_MENU && state != GameInput.STATE_PLAYING) goMenu();
                break;
            case GameInput.CMD_PAUSE:
                if (state == GameInput.STATE_PLAYING) {
                    state = GameInput.STATE_PAUSED;
                    input.reset();
                }
                break;
            case GameInput.CMD_RESUME:
                if (state == GameInput.STATE_PAUSED) {
                    state = GameInput.STATE_PLAYING;
                    input.reset();
                }
                break;
            case GameInput.CMD_TOGGLE_SOUND:
                soundOn = !soundOn;
                Sfx.enabled = soundOn;
                input.soundOn = soundOn;
                prefs.edit().putBoolean("sound", soundOn).apply();
                break;
            case GameInput.CMD_TOGGLE_VIB:
                vibOn = !vibOn;
                Haptics.enabled = vibOn;
                input.vibOn = vibOn;
                prefs.edit().putBoolean("vibration", vibOn).apply();
                break;
        }
    }

    private void updateGame(float dt) {
        levelTime += dt;

        player.update(dt, input);
        weapon.update(dt, input, player, world, projectiles);
        if (weapon.fired) enemies.onGunshot(player.x, player.z);
        enemies.update(dt, world, player, projectiles);
        projectiles.update(dt, world, enemies, player, input);

        // rewards for kills: ammo and a little health
        int kills = input.levelKills;
        if (kills > prevKills) {
            int d = kills - prevKills;
            prevKills = kills;
            weapon.reserve = Math.min(MAX_RESERVE, weapon.reserve + 20 * d);
            player.health = Math.min(Player.MAX_HEALTH, player.health + 8f * d);
        }

        int alive = enemies.aliveCount();
        input.enemies = alive;
        input.health = (int) Math.ceil(player.health);
        input.levelTime = levelTime;

        if (!player.alive) {
            state = GameInput.STATE_DEAD;
            Sfx.play(Sfx.LOSE, 1f);
            input.reset();
            return;
        }

        if (alive == 0) {
            if (clearTimer < 0f) {
                clearTimer = 1.5f;
            } else {
                clearTimer -= dt;
                if (clearTimer <= 0f) completeLevel();
            }
        }
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    @Override
    public void onDrawFrame(GL10 unused) {
        long now0 = System.nanoTime();
        float dt = (now0 - lastFrameNano) / 1e9f;
        lastFrameNano = now0;

        int cmd;
        while ((cmd = input.takeCommand()) != 0) handleCommand(cmd);

        if (state == GameInput.STATE_PLAYING) {
            updateGame(dt);
        } else if (state == GameInput.STATE_MENU) {
            player.yaw += 8f * Math.min(dt, 0.05f);   // slow camera turn behind the menu
        }
        input.state = state;

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (state != GameInput.STATE_PLAYING && state != GameInput.STATE_PAUSED) player.scopeT = 0f;
        float fov = player.fov();
        if (Math.abs(fov - curFov) > 0.05f) {
            curFov = fov;
            Matrix.perspectiveM(proj, 0, curFov, aspect, 0.1f, 200f);
        }

        float ex = player.eyeX(), ey = player.eyeY(), ez = player.eyeZ();
        Matrix.setLookAtM(view, 0, ex, ey, ez,
                ex + player.lookX(), ey + player.lookY(), ez + player.lookZ(),
                0f, 1f, 0f);
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
        extractPlanes(mvp);

        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glUniform3f(uCam, ex, ey, ez);
        GLES20.glUniform2f(uFog, 45f, 100f);
        GLES20.glUniform3f(uFogColor, SKY_R, SKY_G, SKY_B);

        int stride = MeshBuilder.FLOATS_PER_VERTEX * 4;
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glEnableVertexAttribArray(aColor);

        int visible = 0;
        long tris = 0;
        for (Chunk c : chunks) {
            if (c == null || c.vertexCount == 0) continue;
            if (!boxVisible(c)) continue;
            visible++;
            tris += c.vertexCount / 3;
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, c.vbo);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, 12);
            GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, stride, 24);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, c.vertexCount);
        }

        // soldiers, bullets, particles
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        batch.begin();
        projectiles.emit(batch);
        enemies.emit(batch);
        batch.flush(aPos, aNormal, aColor);

        // gun model in view space, drawn over everything
        if (state == GameInput.STATE_PLAYING || state == GameInput.STATE_PAUSED) {
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUniformMatrix4fv(uMVP, 1, false, gunProj, 0);
            GLES20.glUniform3f(uCam, 0f, 0f, 0f);
            GLES20.glUniform2f(uFog, 1000f, 2000f);
            batch.begin();
            weapon.emitGun(batch);
            batch.flush(aPos, aNormal, aColor);
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        frames++;
        long now = System.currentTimeMillis();
        if (now - fpsTimer >= 1000) {
            if (fpsListener != null) fpsListener.onFps(frames, (int) tris, visible, chunks.length,
                    Levels.seed(level), player.x, player.y, player.z);
            frames = 0;
            fpsTimer = now;
        }
    }

    /** Frustum planes from the combined matrix (column-major, Android style). */
    private void extractPlanes(float[] m) {
        for (int p = 0; p < 6; p++) {
            int row = p / 2;
            float sign = (p % 2 == 0) ? 1f : -1f;
            int o = p * 4;
            planes[o]     = m[3]  + sign * m[row];
            planes[o + 1] = m[7]  + sign * m[4 + row];
            planes[o + 2] = m[11] + sign * m[8 + row];
            planes[o + 3] = m[15] + sign * m[12 + row];
        }
    }

    private boolean boxVisible(Chunk c) {
        for (int p = 0; p < 6; p++) {
            int o = p * 4;
            float a = planes[o], b = planes[o + 1], cc = planes[o + 2], d = planes[o + 3];
            float x = a >= 0 ? c.maxX : c.minX;
            float y = b >= 0 ? c.maxY : c.minY;
            float z = cc >= 0 ? c.maxZ : c.minZ;
            if (a * x + b * y + cc * z + d < 0) return false;
        }
        return true;
    }

    private static int loadShader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            GLES20.glDeleteShader(s);
            throw new RuntimeException("Shader compile error: " + log);
        }
        return s;
    }

    private static int createProgram(String vs, String fs) {
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vs);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new RuntimeException("Program link error: " + log);
        }
        return p;
    }
}
