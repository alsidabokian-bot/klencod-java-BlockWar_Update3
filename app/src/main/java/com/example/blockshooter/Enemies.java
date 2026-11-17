package com.example.blockshooter;

import java.util.ArrayList;
import java.util.Random;

/** Owns all soldiers: spawning, updating, hit testing and drawing. */
public class Enemies {
    private final ArrayList<Soldier> list = new ArrayList<>();
    private final Random rnd = new Random();

    public void clear() {
        list.clear();
    }

    public int aliveCount() {
        int n = 0;
        for (Soldier s : list) if (!s.dead) n++;
        return n;
    }

    public void spawnWave(World w, Player p, int count, Levels.Config cfg) {
        int made = 0, attempts = 0;
        while (made < count && attempts < 900) {
            attempts++;
            float minD = attempts > 400 ? 10f : 22f;
            int x = 6 + rnd.nextInt(Math.max(1, w.sx - 12));
            int z = 6 + rnd.nextInt(Math.max(1, w.sz - 12));
            float dx = x + 0.5f - p.x, dz = z + 0.5f - p.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < minD || d > 75f) continue;
            int h = w.topY(x, z);
            if (h <= World.SEA_LEVEL) continue;
            int t = w.get(x, h, z);
            if (t == World.LEAVES || t == World.WOOD || t == World.WATER) continue;
            Soldier s = new Soldier(rnd);
            s.configure(cfg);
            s.x = x + 0.5f;
            s.y = h + 1.01f;
            s.z = z + 0.5f;
            s.yaw = rnd.nextFloat() * 360f;
            list.add(s);
            made++;
        }
    }

    public void update(float dt, World w, Player p, Projectiles proj) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Soldier s = list.get(i);
            s.update(dt, w, p, proj);
            if (s.dead && s.deathTime > 5f) list.remove(i);
        }
    }

    /** Alerts soldiers near a gunshot. */
    public void onGunshot(float px, float pz) {
        for (Soldier s : list) {
            if (s.dead || s.alertTime > 0f) continue;
            float dx = s.x - px, dz = s.z - pz;
            if (dx * dx + dz * dz < 35f * 35f) {
                s.alertTime = 4f;
                s.lastX = px;
                s.lastZ = pz;
            }
        }
    }

    public Soldier hitTest(float x, float y, float z) {
        for (Soldier s : list) {
            if (s.dead) continue;
            if (Math.abs(x - s.x) < 0.38f && Math.abs(z - s.z) < 0.38f
                    && y >= s.y && y <= s.y + Soldier.HEIGHT) {
                return s;
            }
        }
        return null;
    }

    /** Returns true if the soldier died from this hit. */
    public boolean damage(Soldier s, float dmg, Player p) {
        if (s.dead) return false;
        s.health -= dmg;
        s.hitFlash = 0.12f;
        s.alertTime = 6f;
        s.lastX = p.x;
        s.lastZ = p.z;
        if (s.health <= 0f) {
            s.dead = true;
            s.deathTime = 0f;
            return true;
        }
        return false;
    }

    public void emit(DynamicBatch b) {
        for (Soldier s : list) s.emit(b);
    }
}
