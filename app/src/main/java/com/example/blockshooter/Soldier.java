package com.example.blockshooter;

import java.util.Random;

/** One enemy soldier: perception, movement, shooting AI and blocky model. */
public class Soldier {
    static final float HALF_W = 0.3f;
    static final float HEIGHT = 1.9f;
    static final float WANDER_SPEED = 1.4f;
    static final float CHASE_SPEED = 3.4f;
    static final float GRAVITY = 26f;
    static final float JUMP = 8.2f;
    static final float ENEMY_BULLET_SPEED = 55f;

    public float x, y, z, vy, yaw;
    public float health = 100f;
    // difficulty (set per level)
    float spreadMul = 1f, speedMul = 1f, burstPauseMul = 1f, damage = 8f, sight = 45f;
    public boolean dead = false;
    public float deathTime = 0f;

    float alertTime = 0f;
    float lastX, lastZ;
    float wanderX, wanderZ, wanderPause = 0f;
    boolean hasWander = false;
    float strafeDir = 1f, strafeTimer = 0f;
    float shootCooldown = 0f, burstPause = 0f, reaction = 0f;
    int burstLeft = 3;
    float blockedTime = 0f;
    float walkPhase = 0f;
    float hitFlash = 0f;
    boolean wasSeeing = false, onGround = false;

    private final Random rnd;
    private float tx, ty, tz;   // temp for transforms

    public void configure(Levels.Config c) {
        health = c.hp;
        spreadMul = c.spreadMul;
        speedMul = c.speedMul;
        burstPauseMul = c.burstPauseMul;
        damage = c.damage;
        sight = c.sight;
    }

    public Soldier(Random rnd) {
        this.rnd = rnd;
        strafeDir = rnd.nextBoolean() ? 1f : -1f;
        strafeTimer = 1f + rnd.nextFloat() * 2f;
        walkPhase = rnd.nextFloat() * 6f;
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------

    public void update(float dt, World w, Player p, Projectiles proj) {
        if (dead) {
            deathTime += dt;
            return;
        }
        if (dt > 0.05f) dt = 0.05f;
        hitFlash = Math.max(0f, hitFlash - dt);

        float pdx = p.x - x, pdz = p.z - z;
        float dist = (float) Math.sqrt(pdx * pdx + pdz * pdz);

        boolean sees = p.alive && dist < sight && lineOfSight(w, x, y + 1.6f, z, p.x, p.eyeY() - 0.2f, p.z);
        if (sees) {
            if (!wasSeeing) reaction = 0.5f + rnd.nextFloat() * 0.4f;
            alertTime = 6f;
            lastX = p.x;
            lastZ = p.z;
        } else {
            alertTime = Math.max(0f, alertTime - dt);
        }
        wasSeeing = sees;

        float moveX = 0f, moveZ = 0f, speed = 0f;
        float faceX = 0f, faceZ = 0f;
        boolean hasFace = false;

        if (alertTime > 0f) {
            float gx = sees ? p.x : lastX, gz = sees ? p.z : lastZ;
            float tdx = gx - x, tdz = gz - z;
            float td = (float) Math.sqrt(tdx * tdx + tdz * tdz);
            if (td > 0.05f) {
                faceX = tdx;
                faceZ = tdz;
                hasFace = true;
            }
            if (sees && td > 0.05f) {
                strafeTimer -= dt;
                if (strafeTimer <= 0f) {
                    strafeDir = rnd.nextBoolean() ? 1f : -1f;
                    strafeTimer = 1.5f + rnd.nextFloat() * 2f;
                }
                float nx = tdx / td, nz = tdz / td;
                if (td > 18f) {
                    moveX = nx; moveZ = nz; speed = CHASE_SPEED * speedMul;
                } else if (td < 9f) {
                    moveX = -nx; moveZ = -nz; speed = 2.5f * speedMul;
                } else {
                    moveX = -nz * strafeDir; moveZ = nx * strafeDir; speed = 2.0f * speedMul;
                }
            } else if (!sees) {
                if (td > 1.5f) {
                    moveX = tdx / td; moveZ = tdz / td; speed = CHASE_SPEED * speedMul;
                } else {
                    alertTime = 0f;
                }
            }
        } else {
            if (wanderPause > 0f) {
                wanderPause -= dt;
            } else {
                if (!hasWander) pickWander(w);
                if (hasWander) {
                    float tdx = wanderX - x, tdz = wanderZ - z;
                    float td = (float) Math.sqrt(tdx * tdx + tdz * tdz);
                    if (td < 1.0f) {
                        hasWander = false;
                        wanderPause = 1f + rnd.nextFloat() * 3f;
                    } else {
                        moveX = tdx / td; moveZ = tdz / td; speed = WANDER_SPEED * speedMul;
                        faceX = tdx; faceZ = tdz; hasFace = true;
                    }
                }
            }
        }

        // turn toward facing direction
        if (hasFace) {
            float target = (float) Math.toDegrees(Math.atan2(faceX, -faceZ));
            float diff = angleDiff(target, yaw);
            float maxTurn = 300f * dt;
            if (Math.abs(diff) <= maxTurn) yaw = target;
            else yaw += Math.signum(diff) * maxTurn;
        }

        // movement
        boolean inWater = w.get((int) Math.floor(x), (int) Math.floor(y + 0.4f), (int) Math.floor(z)) == World.WATER;
        float sp = speed * (inWater ? 0.6f : 1f);
        boolean bx = moveAxis(w, 0, moveX * sp * dt);
        boolean bz = moveAxis(w, 2, moveZ * sp * dt);
        boolean blocked = (bx && Math.abs(moveX) > 0.2f) || (bz && Math.abs(moveZ) > 0.2f);
        if (blocked && speed > 0f) {
            blockedTime += dt;
            if (onGround && !inWater) vy = JUMP;
            if (blockedTime > 0.8f) {
                blockedTime = 0f;
                strafeDir = -strafeDir;
                hasWander = false;
                wanderPause = 0.2f;
            }
        } else {
            blockedTime = Math.max(0f, blockedTime - dt);
        }

        if (inWater) {
            vy = 2.5f;
        } else {
            vy -= GRAVITY * dt;
            if (vy < -45f) vy = -45f;
        }
        if (moveAxis(w, 1, vy * dt)) vy = 0f;
        onGround = vy <= 0f && collides(w, x, y - 0.05f, z);

        if (speed > 0f) walkPhase += speed * dt * 3f;

        // shooting
        if (sees && p.alive) {
            float target = (float) Math.toDegrees(Math.atan2(pdx, -pdz));
            float err = Math.abs(angleDiff(target, yaw));
            if (reaction > 0f) {
                reaction -= dt;
            } else if (err < 12f && dist < 38f) {
                if (burstPause > 0f) {
                    burstPause -= dt;
                } else {
                    shootCooldown -= dt;
                    if (shootCooldown <= 0f) {
                        fire(p, proj);
                        shootCooldown = 0.17f;
                        burstLeft--;
                        if (burstLeft <= 0) {
                            burstPause = (1.0f + rnd.nextFloat() * 1.3f) * burstPauseMul;
                            burstLeft = 3 + rnd.nextInt(3);
                        }
                    }
                }
            }
        }
    }

    private void fire(Player p, Projectiles proj) {
        double yr = Math.toRadians(yaw);
        float fx = (float) Math.sin(yr), fz = (float) -Math.cos(yr);
        float rx = (float) Math.cos(yr), rz = (float) Math.sin(yr);
        float mx = x + fx * 0.6f + rx * 0.25f;
        float my = y + 1.2f;
        float mz = z + fz * 0.6f + rz * 0.25f;

        float dx = p.x - mx, dy = (p.eyeY() - 0.4f) - my, dz = p.z - mz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01f) return;
        dx /= len; dy /= len; dz /= len;
        float spread = (0.04f + len * 0.0018f) * spreadMul;
        Sfx.play(Sfx.ENEMY_SHOT, 0.9f / (1f + len / 10f));
        dx += (rnd.nextFloat() - 0.5f) * 2f * spread;
        dy += (rnd.nextFloat() - 0.5f) * 2f * spread;
        dz += (rnd.nextFloat() - 0.5f) * 2f * spread;
        float l2 = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        proj.spawnEnemyBullet(mx, my, mz, dx / l2, dy / l2, dz / l2, ENEMY_BULLET_SPEED, damage);
    }

    private void pickWander(World w) {
        for (int i = 0; i < 6; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            float d = 5f + rnd.nextFloat() * 8f;
            float nx = x + (float) Math.cos(ang) * d;
            float nz = z + (float) Math.sin(ang) * d;
            int bx = (int) Math.floor(nx), bz = (int) Math.floor(nz);
            if (bx < 3 || bz < 3 || bx >= w.sx - 3 || bz >= w.sz - 3) continue;
            if (w.topY(bx, bz) <= World.SEA_LEVEL) continue;
            wanderX = nx;
            wanderZ = nz;
            hasWander = true;
            return;
        }
        wanderPause = 0.5f;
    }

    private static float angleDiff(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static boolean lineOfSight(World w, float x0, float y0, float z0, float x1, float y1, float z1) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) (len / 0.5f) + 1;
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            int bx = (int) Math.floor(x0 + dx * t);
            int by = (int) Math.floor(y0 + dy * t);
            int bz = (int) Math.floor(z0 + dz * t);
            if (Projectiles.solid(w, bx, by, bz)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Collision (same rules as the player)
    // ------------------------------------------------------------------

    private boolean moveAxis(World w, int axis, float amount) {
        if (amount == 0f) return false;
        int steps = (int) Math.ceil(Math.abs(amount) / 0.2f);
        float step = amount / steps;
        for (int i = 0; i < steps; i++) {
            float nx = x + (axis == 0 ? step : 0f);
            float ny = y + (axis == 1 ? step : 0f);
            float nz = z + (axis == 2 ? step : 0f);
            if (collides(w, nx, ny, nz)) return true;
            x = nx; y = ny; z = nz;
        }
        return false;
    }

    private boolean collides(World w, float px, float py, float pz) {
        int x0 = (int) Math.floor(px - HALF_W + 0.0001f);
        int x1 = (int) Math.floor(px + HALF_W - 0.0001f);
        int y0 = (int) Math.floor(py + 0.0005f);
        int y1 = (int) Math.floor(py + HEIGHT - 0.0001f);
        int z0 = (int) Math.floor(pz - HALF_W + 0.0001f);
        int z1 = (int) Math.floor(pz + HALF_W - 0.0001f);
        for (int by = y0; by <= y1; by++)
            for (int bz = z0; bz <= z1; bz++)
                for (int bx = x0; bx <= x1; bx++)
                    if (Projectiles.solid(w, bx, by, bz)) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    private float cy_, sy_, cx_, sx_;

    private void xf(float lx, float ly, float lz) {
        // tilt about local X (falling backwards), then yaw about Y
        float y1 = ly * cx_ - lz * sx_;
        float z1 = ly * sx_ + lz * cx_;
        tx = lx * cy_ - z1 * sy_;
        ty = y1;
        tz = lx * sy_ + z1 * cy_;
    }

    private void part(DynamicBatch b, float lift, float cx, float cy, float cz,
                      float hx, float hy, float hz, float r, float g, float bl) {
        if (hitFlash > 0f) {
            r = r * 0.4f + 0.6f;
            g = g * 0.4f;
            bl = bl * 0.4f;
        }
        xf(cx, cy, cz);
        float px = x + tx, py = y + ty + lift, pz = z + tz;
        xf(hx, 0f, 0f);
        float ux = tx, uy = ty, uz = tz;
        xf(0f, hy, 0f);
        float vx = tx, vy2 = ty, vz = tz;
        xf(0f, 0f, hz);
        b.box(px, py, pz, ux, uy, uz, vx, vy2, vz, tx, ty, tz, r, g, bl);
    }

    public void emit(DynamicBatch b) {
        double yr = Math.toRadians(yaw);
        cy_ = (float) Math.cos(yr);
        sy_ = (float) Math.sin(yr);
        float tilt = dead ? Math.min(1.5708f, deathTime * 4f) : 0f;
        cx_ = (float) Math.cos(tilt);
        sx_ = (float) Math.sin(tilt);
        float lift = dead ? 0.25f * sx_ : 0f;

        float swing = dead ? 0f : (float) Math.sin(walkPhase) * 0.2f;

        // legs
        part(b, lift, -0.14f, 0.4f, swing, 0.11f, 0.4f, 0.11f, 0.22f, 0.27f, 0.18f);
        part(b, lift, 0.14f, 0.4f, -swing, 0.11f, 0.4f, 0.11f, 0.22f, 0.27f, 0.18f);
        // torso + belt
        part(b, lift, 0f, 1.15f, 0f, 0.28f, 0.35f, 0.16f, 0.30f, 0.42f, 0.25f);
        part(b, lift, 0f, 0.82f, 0f, 0.29f, 0.05f, 0.17f, 0.30f, 0.22f, 0.12f);
        // head + helmet
        part(b, lift, 0f, 1.75f, 0f, 0.20f, 0.20f, 0.20f, 0.87f, 0.68f, 0.55f);
        part(b, lift, 0f, 1.93f, 0f, 0.23f, 0.09f, 0.23f, 0.20f, 0.30f, 0.20f);
        // arms holding a rifle (forward is -Z)
        part(b, lift, -0.34f, 1.18f, -0.22f, 0.08f, 0.08f, 0.28f, 0.30f, 0.42f, 0.25f);
        part(b, lift, 0.30f, 1.14f, -0.25f, 0.08f, 0.08f, 0.28f, 0.30f, 0.42f, 0.25f);
        // rifle
        part(b, lift, 0.22f, 1.14f, -0.55f, 0.04f, 0.06f, 0.34f, 0.12f, 0.12f, 0.13f);
    }
}
