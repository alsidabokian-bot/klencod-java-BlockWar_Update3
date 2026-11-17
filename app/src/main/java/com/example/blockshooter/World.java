package com.example.blockshooter;

import java.util.Arrays;
import java.util.Random;

/** Block storage + random terrain generation. */
public class World {
    public static final int AIR = 0;
    public static final int GRASS = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int WOOD = 4;
    public static final int LEAVES = 5;
    public static final int RED = 6;
    public static final int BLUE = 7;
    public static final int YELLOW = 8;
    public static final int WHITE = 9;
    public static final int SAND = 10;
    public static final int WATER = 11;
    public static final int SNOW = 12;
    public static final int BLOCK_TYPES = 13;

    public static final int SEA_LEVEL = 11;
    private static final int STONE_LINE = 19;
    private static final int SNOW_LINE = 23;
    private static final int MAX_TREE_BASE = 22;

    /** RGB per block type. */
    public static final float[][] COLORS = {
        {0f, 0f, 0f},
        {0.35f, 0.70f, 0.25f},   // grass
        {0.50f, 0.34f, 0.20f},   // dirt
        {0.52f, 0.52f, 0.55f},   // stone
        {0.45f, 0.30f, 0.15f},   // wood
        {0.18f, 0.50f, 0.18f},   // leaves
        {0.85f, 0.20f, 0.20f},   // red
        {0.20f, 0.40f, 0.90f},   // blue
        {0.95f, 0.80f, 0.15f},   // yellow
        {0.92f, 0.92f, 0.92f},   // white
        {0.86f, 0.80f, 0.55f},   // sand
        {0.20f, 0.42f, 0.80f},   // water
        {0.96f, 0.97f, 1.00f},   // snow
    };

    public final int sx, sy, sz;
    private final byte[] blocks;

    public World(int sx, int sy, int sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.blocks = new byte[sx * sy * sz];
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
    }

    public int get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return AIR;
        return blocks[(y * sz + z) * sx + x];
    }

    public void set(int x, int y, int z, int type) {
        if (!inBounds(x, y, z)) return;
        blocks[(y * sz + z) * sx + x] = (byte) type;
    }

    /** Highest non-air block in a column, or -1. */
    public int topY(int x, int z) {
        for (int y = sy - 1; y >= 0; y--) {
            if (get(x, y, z) != AIR) return y;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Terrain generation
    // ------------------------------------------------------------------

    public void generate(int seed) {
        Arrays.fill(blocks, (byte) 0);

        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                int h = heightAt(seed, x, z);
                int top;
                if (h >= SNOW_LINE) top = SNOW;
                else if (h >= STONE_LINE) top = STONE;
                else if (h <= SEA_LEVEL + 1) top = SAND;
                else top = GRASS;

                int sub = (top == GRASS) ? DIRT : (top == SAND ? SAND : STONE);
                for (int y = 0; y <= h; y++) {
                    int t;
                    if (y == h) t = top;
                    else if (y >= h - 3) t = sub;
                    else t = STONE;
                    set(x, y, z, t);
                }
                for (int y = h + 1; y <= SEA_LEVEL; y++) set(x, y, z, WATER);
            }
        }

        // trees
        Random rnd = new Random(seed);
        boolean[] hasTree = new boolean[sx * sz];
        for (int x = 4; x < sx - 4; x++) {
            for (int z = 4; z < sz - 4; z++) {
                int h = topY(x, z);
                if (h < 0 || h > MAX_TREE_BASE) continue;
                if (get(x, h, z) != GRASS) continue;
                if (rnd.nextInt(45) != 0) continue;
                if (nearTree(hasTree, x, z)) continue;
                hasTree[x * sz + z] = true;
                addTree(x, h + 1, z, rnd);
            }
        }
    }

    private boolean nearTree(boolean[] hasTree, int x, int z) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int nx = x + dx, nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= sx || nz >= sz) continue;
                if (hasTree[nx * sz + nz]) return true;
            }
        }
        return false;
    }

    private void addTree(int x, int y, int z, Random rnd) {
        int th = 4 + rnd.nextInt(3);
        for (int h = 0; h < th; h++) set(x, y + h, z, WOOD);
        for (int dy = th - 2; dy <= th + 1; dy++) {
            int r = (dy <= th - 1) ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (dy > th - 1 && Math.abs(dx) == 1 && Math.abs(dz) == 1) continue;
                    if (get(x + dx, y + dy, z + dz) == AIR) set(x + dx, y + dy, z + dz, LEAVES);
                }
            }
        }
    }

    private int heightAt(int seed, int x, int z) {
        float n = fbm(seed, x * 0.025f, z * 0.025f);
        n = (n - 0.5f) * 1.6f + 0.5f;
        if (n < 0f) n = 0f;
        if (n > 1f) n = 1f;
        int h = 3 + (int) (n * 24f);
        return Math.min(h, sy - 12);
    }

    private static float fbm(int seed, float x, float z) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int i = 0; i < 4; i++) {
            sum += valueNoise(seed + i * 1013, x, z) * amp;
            norm += amp;
            x *= 2f;
            z *= 2f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    private static float valueNoise(int seed, float x, float z) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        fx = fx * fx * (3f - 2f * fx);
        fz = fz * fz * (3f - 2f * fz);
        float a = rnd(seed, xi, zi);
        float b = rnd(seed, xi + 1, zi);
        float c = rnd(seed, xi, zi + 1);
        float d = rnd(seed, xi + 1, zi + 1);
        float top = a + (b - a) * fx;
        float bot = c + (d - c) * fx;
        return top + (bot - top) * fz;
    }

    private static float rnd(int seed, int x, int z) {
        int h = seed * 374761393 + x * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }
}
