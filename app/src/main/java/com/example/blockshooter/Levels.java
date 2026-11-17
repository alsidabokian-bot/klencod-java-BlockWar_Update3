package com.example.blockshooter;

/** Level definitions: each level is a fixed world seed plus enemy difficulty. */
public final class Levels {
    public static final int TOTAL = 8;

    public static class Config {
        public int count;            // soldiers to eliminate
        public float hp;             // soldier health
        public float spreadMul;      // enemy aim error multiplier (lower = more accurate)
        public float damage;         // damage per enemy bullet
        public float burstPauseMul;  // pause between bursts (lower = more aggressive)
        public float speedMul;       // enemy movement speed
        public float sight;          // enemy sight range in blocks
    }

    //                          count  hp   spread dmg  pause speed sight
    private static final float[][] TABLE = {
        {  5,  60, 1.8f,  5, 1.6f, 0.85f, 32 },
        {  7,  70, 1.5f,  6, 1.4f, 0.90f, 36 },
        {  9,  80, 1.3f,  7, 1.2f, 0.95f, 40 },
        { 11,  90, 1.1f,  8, 1.1f, 1.00f, 42 },
        { 13, 100, 1.0f,  8, 1.0f, 1.05f, 45 },
        { 15, 110, 0.85f, 9, 0.9f, 1.10f, 48 },
        { 17, 120, 0.75f, 10, 0.8f, 1.15f, 50 },
        { 20, 130, 0.65f, 10, 0.7f, 1.20f, 55 },
    };

    public static Config get(int level) {
        int i = Math.max(1, Math.min(TOTAL, level)) - 1;
        float[] t = TABLE[i];
        Config c = new Config();
        c.count = (int) t[0];
        c.hp = t[1];
        c.spreadMul = t[2];
        c.damage = t[3];
        c.burstPauseMul = t[4];
        c.speedMul = t[5];
        c.sight = t[6];
        return c;
    }

    public static int seed(int level) {
        return 1000 + level * 977;
    }

    private Levels() {}
}
