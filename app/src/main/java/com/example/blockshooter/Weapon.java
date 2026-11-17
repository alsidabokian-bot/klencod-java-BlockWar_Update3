package com.example.blockshooter;

import java.util.Random;

/** The assault rifle: ammo, reloading, fire rate, recoil, and the first-person model. */
public class Weapon {
    public static final int MAG = 30;
    public static final int START_RESERVE = 120;
    private static final float FIRE_INTERVAL = 0.09f;   // ~11 shots per second
    private static final float RELOAD_TIME = 1.6f;
    private static final float SPREAD_DEG = 0.6f;

    public int ammo = MAG;
    public int reserve = START_RESERVE;
    public boolean reloading = false;
    public boolean fired = false;
    public float scopeT = 0f;      // copied from the player: 0..1 aim-down-sight blend   // true during the frame a shot was fired

    private float reloadTimer = 0f;
    private float cooldown = 0f;
    private float kick = 0f;
    private float flash = 0f;
    private float bobPhase = 0f;
    private float bobMag = 0f;
    private final Random rnd = new Random();

    public void reset() {
        ammo = MAG;
        reserve = START_RESERVE;
        reloading = false;
        reloadTimer = 0f;
        cooldown = 0f;
        kick = 0f;
        flash = 0f;
    }

    public void update(float dt, GameInput in, Player p, World w, Projectiles proj) {
        if (dt > 0.05f) dt = 0.05f;
        fired = false;
        scopeT = p.scopeT;

        cooldown = Math.max(0f, cooldown - dt);
        kick = Math.max(0f, kick - dt * 9f);
        flash = Math.max(0f, flash - dt);

        float mag = Math.min(1f, (float) Math.hypot(in.moveX, in.moveY));
        bobMag = p.onGround ? mag : 0f;
        if (bobMag > 0.05f) bobPhase += dt * 9f * bobMag;

        if (in.takeReload() && !reloading && ammo < MAG && reserve > 0) startReload();

        if (reloading) {
            reloadTimer -= dt;
            if (reloadTimer <= 0f) finishReload();
        } else if (ammo == 0 && reserve > 0) {
            startReload();
        }

        if (in.fire && !reloading && ammo > 0 && cooldown <= 0f) shoot(p, w, proj);

        in.ammo = ammo;
        in.reserve = reserve;
        in.reloading = reloading;
    }

    private void startReload() {
        reloading = true;
        reloadTimer = RELOAD_TIME;
        Sfx.play(Sfx.RELOAD, 0.8f);
    }

    private void finishReload() {
        int take = Math.min(MAG - ammo, reserve);
        ammo += take;
        reserve -= take;
        reloading = false;
    }

    private void shoot(Player p, World w, Projectiles proj) {
        ammo--;
        fired = true;
        Sfx.play(Sfx.SHOT, 0.7f);
        cooldown = FIRE_INTERVAL;
        kick = 1f;
        flash = 0.05f;

        float yaw = p.yaw, pitch = p.pitch;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        // centre-of-screen direction (no spread) -> used to place the muzzle
        float fx = (float) (Math.sin(yr) * Math.cos(pr));
        float fy = (float) Math.sin(pr);
        float fz = (float) (-Math.cos(yr) * Math.cos(pr));
        float rx = (float) Math.cos(yr), rz = (float) Math.sin(yr);
        // up = right x forward
        float ux = 0f * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - 0f * fx;

        float ex = p.eyeX(), ey = p.eyeY(), ez = p.eyeZ();
        float mx = ex + fx * 0.6f + rx * 0.17f - ux * 0.14f;
        float my = ey + fy * 0.6f - uy * 0.14f;
        float mz = ez + fz * 0.6f + rz * 0.17f - uz * 0.14f;

        // spread: random small change of the aim angles
        float spreadDeg = SPREAD_DEG * (1f - 0.5f * p.scopeT) * (p.crouching ? 0.7f : 1f);
        float sy = (rnd.nextFloat() - 0.5f) * 2f * spreadDeg;
        float sp = (rnd.nextFloat() - 0.5f) * 2f * spreadDeg;
        double yr2 = Math.toRadians(yaw + sy), pr2 = Math.toRadians(pitch + sp);
        float dx = (float) (Math.sin(yr2) * Math.cos(pr2));
        float dy = (float) Math.sin(pr2);
        float dz = (float) (-Math.cos(yr2) * Math.cos(pr2));

        // aim point: where the crosshair ray hits something (so bullets land at the crosshair)
        float dist = rayDistance(w, ex, ey, ez, dx, dy, dz, 100f);
        float tx = ex + dx * dist, ty = ey + dy * dist, tz = ez + dz * dist;
        float bx = tx - mx, by = ty - my, bz = tz - mz;
        float bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        if (bl < 1f) { bx = dx; by = dy; bz = dz; } else { bx /= bl; by /= bl; bz /= bl; }

        proj.spawnBullet(mx, my, mz, bx, by, bz);

        // recoil
        float recoilMul = (1f - 0.35f * p.scopeT) * (p.crouching ? 0.8f : 1f);
        p.addRecoil((0.45f + rnd.nextFloat() * 0.25f) * recoilMul, (rnd.nextFloat() - 0.5f) * 0.4f * recoilMul);
    }

    private static float rayDistance(World w, float ox, float oy, float oz,
                                     float dx, float dy, float dz, float max) {
        for (float t = 0f; t < max; t += 0.1f) {
            int x = (int) Math.floor(ox + dx * t);
            int y = (int) Math.floor(oy + dy * t);
            int z = (int) Math.floor(oz + dz * t);
            if (Projectiles.solid(w, x, y, z)) return t;
        }
        return max;
    }

    /** Draws the gun in view space (camera at origin looking toward -Z). */
    public void emitGun(DynamicBatch b) {
        float calm = 1f - 0.8f * scopeT;
        float ox = (float) Math.cos(bobPhase * 0.5f) * 0.006f * bobMag * calm;
        float oy = (float) Math.sin(bobPhase) * 0.008f * bobMag * calm + kick * 0.012f * calm;
        float oz = kick * 0.07f * calm;
        if (reloading) {
            float p = 1f - Math.max(0f, reloadTimer) / RELOAD_TIME;
            oy -= 0.35f * (float) Math.sin(Math.PI * p);
            ox -= 0.05f * (float) Math.sin(Math.PI * p);
        }
        float gx = 0.26f * (1f - scopeT) + ox, gy = -0.24f + 0.11f * scopeT + oy, gz = -0.62f + oz;

        // body, barrel, stock, grip, magazine, sight
        b.axisBox(gx, gy, gz, 0.040f, 0.050f, 0.220f, 0.22f, 0.23f, 0.25f);
        b.axisBox(gx, gy + 0.03f, gz - 0.33f, 0.018f, 0.018f, 0.20f, 0.10f, 0.10f, 0.11f);
        b.axisBox(gx, gy - 0.02f, gz + 0.24f, 0.035f, 0.060f, 0.10f, 0.28f, 0.20f, 0.12f);
        b.axisBox(gx, gy - 0.09f, gz + 0.12f, 0.025f, 0.050f, 0.030f, 0.15f, 0.15f, 0.16f);
        b.axisBox(gx, gy - 0.10f, gz - 0.02f, 0.025f, 0.065f, 0.040f, 0.20f, 0.25f, 0.14f);
        b.axisBox(gx, gy + 0.07f, gz - 0.08f, 0.010f, 0.020f, 0.020f, 0.08f, 0.08f, 0.09f);
        // forearm and sleeve
        b.axisBox(gx + 0.02f, gy - 0.20f, gz + 0.22f, 0.050f, 0.060f, 0.13f, 0.30f, 0.45f, 0.30f);
        b.axisBox(gx - 0.01f, gy - 0.08f, gz - 0.12f, 0.030f, 0.030f, 0.055f, 0.87f, 0.68f, 0.55f);

        if (flash > 0f) {
            float s = 0.05f + rnd.nextFloat() * 0.03f;
            b.axisBox(gx, gy + 0.03f, gz - 0.58f, s, s, 0.04f, 1f, 0.85f, 0.25f);
        }
    }
}
