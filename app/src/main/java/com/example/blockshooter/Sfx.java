package com.example.blockshooter;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/** Sound effects synthesized in code (no audio files needed). */
public final class Sfx {
    public static final int SHOT = 0, ENEMY_SHOT = 1, HIT = 2, KILL = 3, HURT = 4,
            RELOAD = 5, WIN = 6, LOSE = 7, CLICK = 8;
    private static final int COUNT = 9;
    private static final int RATE = 22050;

    public static volatile boolean enabled = true;

    private static AudioTrack[][] tracks;
    private static int[] next;
    private static boolean ready = false;

    private Sfx() {}

    public static synchronized void init() {
        if (ready) return;
        try {
            short[][] data = new short[COUNT][];
            data[SHOT] = shot();
            data[ENEMY_SHOT] = enemyShot();
            data[HIT] = hit();
            data[KILL] = kill();
            data[HURT] = hurt();
            data[RELOAD] = reload();
            data[WIN] = win();
            data[LOSE] = lose();
            data[CLICK] = click();

            tracks = new AudioTrack[COUNT][];
            next = new int[COUNT];
            for (int i = 0; i < COUNT; i++) {
                int pool = (i == SHOT || i == ENEMY_SHOT) ? 4 : 2;
                tracks[i] = new AudioTrack[pool];
                for (int j = 0; j < pool; j++) {
                    int bytes = data[i].length * 2;
                    AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            bytes, AudioTrack.MODE_STATIC);
                    t.write(data[i], 0, data[i].length);
                    tracks[i][j] = t;
                }
            }
            ready = true;
        } catch (Throwable e) {
            ready = false;
        }
    }

    public static synchronized void release() {
        if (!ready) return;
        ready = false;
        for (AudioTrack[] pool : tracks) {
            for (AudioTrack t : pool) {
                try { t.release(); } catch (Throwable ignored) { }
            }
        }
    }

    public static void play(int id) {
        play(id, 1f);
    }

    public static synchronized void play(int id, float volume) {
        if (!enabled || !ready) return;
        try {
            AudioTrack t = tracks[id][next[id]];
            next[id] = (next[id] + 1) % tracks[id].length;
            t.stop();
            t.reloadStaticData();
            t.setVolume(Math.max(0f, Math.min(1f, volume)));
            t.play();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Synthesis
    // ------------------------------------------------------------------

    private static final Random RND = new Random(42);

    private static float[] buf(float dur) {
        return new float[(int) (dur * RATE)];
    }

    private static void noise(float[] b, float start, float dur, float decay, float lp, float amp) {
        int s = (int) (start * RATE), n = (int) (dur * RATE);
        float y = 0f;
        for (int i = 0; i < n && s + i < b.length; i++) {
            float t = (float) i / RATE;
            float x = RND.nextFloat() * 2f - 1f;
            y += lp * (x - y);
            b[s + i] += y * amp * (float) Math.exp(-t * decay);
        }
    }

    private static void sine(float[] b, float start, float dur, float f0, float f1, float decay, float amp) {
        int s = (int) (start * RATE), n = (int) (dur * RATE);
        double phase = 0.0;
        for (int i = 0; i < n && s + i < b.length; i++) {
            float t = (float) i / RATE;
            float f = f0 + (f1 - f0) * (t / dur);
            phase += 2.0 * Math.PI * f / RATE;
            b[s + i] += (float) Math.sin(phase) * amp * (float) Math.exp(-t * decay);
        }
    }

    private static short[] pcm(float[] b) {
        short[] out = new short[b.length];
        for (int i = 0; i < b.length; i++) {
            float v = Math.max(-1f, Math.min(1f, b[i]));
            out[i] = (short) (v * 30000f);
        }
        return out;
    }

    private static short[] shot() {
        float[] b = buf(0.16f);
        noise(b, 0f, 0.14f, 28f, 0.8f, 0.55f);
        sine(b, 0f, 0.12f, 160f, 60f, 30f, 0.7f);
        return pcm(b);
    }

    private static short[] enemyShot() {
        float[] b = buf(0.14f);
        noise(b, 0f, 0.12f, 34f, 0.6f, 0.25f);
        sine(b, 0f, 0.10f, 110f, 50f, 35f, 0.5f);
        return pcm(b);
    }

    private static short[] hit() {
        float[] b = buf(0.07f);
        sine(b, 0f, 0.06f, 1100f, 700f, 50f, 0.6f);
        noise(b, 0f, 0.03f, 90f, 0.5f, 0.2f);
        return pcm(b);
    }

    private static short[] kill() {
        float[] b = buf(0.3f);
        sine(b, 0f, 0.28f, 520f, 160f, 9f, 0.6f);
        noise(b, 0f, 0.05f, 60f, 0.3f, 0.5f);
        return pcm(b);
    }

    private static short[] hurt() {
        float[] b = buf(0.25f);
        sine(b, 0f, 0.2f, 90f, 45f, 12f, 0.9f);
        noise(b, 0f, 0.12f, 25f, 0.4f, 0.2f);
        return pcm(b);
    }

    private static short[] reload() {
        float[] b = buf(0.45f);
        noise(b, 0f, 0.04f, 120f, 0.4f, 0.5f);
        sine(b, 0f, 0.04f, 500f, 300f, 100f, 0.4f);
        noise(b, 0.22f, 0.05f, 100f, 0.4f, 0.6f);
        sine(b, 0.22f, 0.05f, 400f, 250f, 80f, 0.4f);
        return pcm(b);
    }

    private static short[] win() {
        float[] b = buf(0.8f);
        float[] f = {523f, 659f, 784f, 1047f};
        for (int i = 0; i < f.length; i++) sine(b, i * 0.13f, 0.35f, f[i], f[i], 5f, 0.35f);
        return pcm(b);
    }

    private static short[] lose() {
        float[] b = buf(1.0f);
        sine(b, 0f, 0.9f, 330f, 60f, 2.5f, 0.6f);
        sine(b, 0f, 0.9f, 165f, 30f, 2.5f, 0.4f);
        return pcm(b);
    }

    private static short[] click() {
        float[] b = buf(0.04f);
        sine(b, 0f, 0.03f, 1400f, 1000f, 60f, 0.4f);
        return pcm(b);
    }
}
