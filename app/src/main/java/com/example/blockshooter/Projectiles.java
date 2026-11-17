package com.example.blockshooter;

import java.util.ArrayList;
import java.util.Random;

/** Bullets (real moving projectiles) and impact particles. */
public class Projectiles {
    public static final float BULLET_SPEED = 140f;
    private static final float BULLET_LIFE = 2.0f;

    public static final float PLAYER_DAMAGE = 25f;
    public static final float HEADSHOT_DAMAGE = 50f;
    public static final float ENEMY_DAMAGE = 8f;

    private static class Bullet {
        float x, y, z, dx, dy, dz, life, speed, damage;
        boolean fromPlayer;
    }

    private static class Particle {
        float x, y, z, vx, vy, vz, life, size, r, g, b;
    }

    private final ArrayList<Bullet> bullets = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random rnd = new Random();

    public void clear() {
        bullets.clear();
        particles.clear();
    }

    public void spawnBullet(float x, float y, float z, float dx, float dy, float dz) {
        add(x, y, z, dx, dy, dz, BULLET_SPEED, true);
    }

    public void spawnEnemyBullet(float x, float y, float z, float dx, float dy, float dz, float speed, float damage) {
        add(x, y, z, dx, dy, dz, speed, false);
        bullets.get(bullets.size() - 1).damage = damage;
    }

    private void add(float x, float y, float z, float dx, float dy, float dz, float speed, boolean fromPlayer) {
        Bullet b = new Bullet();
        b.x = x; b.y = y; b.z = z;
        b.dx = dx; b.dy = dy; b.dz = dz;
        b.speed = speed;
        b.fromPlayer = fromPlayer;
        b.life = BULLET_LIFE + (fromPlayer ? 0f : 1f);
        bullets.add(b);
    }

    /** Solid for bullets: any block except air and water; the world's side walls are solid. */
    public static boolean solid(World w, int x, int y, int z) {
        if (y < 0) return true;
        if (x < 0 || z < 0 || x >= w.sx || z >= w.sz) return true;
        if (y >= w.sy) return false;
        int t = w.get(x, y, z);
        return t != World.AIR && t != World.WATER;
    }

    public void update(float dt, World w, Enemies enemies, Player player, GameInput input) {
        if (dt > 0.05f) dt = 0.05f;

        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            float remaining = b.speed * dt;
            boolean dead = false;
            while (remaining > 0f) {
                float step = Math.min(0.4f, remaining);
                float nx = b.x + b.dx * step, ny = b.y + b.dy * step, nz = b.z + b.dz * step;
                if (b.fromPlayer) {
                    Soldier hit = enemies.hitTest(nx, ny, nz);
                    if (hit != null) {
                        boolean head = ny > hit.y + 1.55f;
                        boolean killed = enemies.damage(hit, head ? HEADSHOT_DAMAGE : PLAYER_DAMAGE, player);
                        input.registerHit(head, killed);
                        Sfx.play(killed ? Sfx.KILL : Sfx.HIT, 0.8f);
                        if (killed) Haptics.vibrate(15);
                        burst(nx, ny, nz, 0.75f, 0.08f, 0.08f, 9);
                        dead = true;
                        break;
                    }
                } else if (player.alive && player.contains(nx, ny, nz)) {
                    player.takeDamage(b.damage > 0f ? b.damage : ENEMY_DAMAGE, input);
                    burst(nx, ny, nz, 0.75f, 0.08f, 0.08f, 6);
                    dead = true;
                    break;
                }
                int bx = (int) Math.floor(nx), by = (int) Math.floor(ny), bz = (int) Math.floor(nz);
                if (solid(w, bx, by, bz)) {
                    spawnImpact(w, b.x, b.y, b.z, bx, by, bz);
                    dead = true;
                    break;
                }
                b.x = nx; b.y = ny; b.z = nz;
                remaining -= step;
            }
            b.life -= dt;
            if (b.life <= 0f || b.y > w.sy + 30 || b.y < -5f) dead = true;
            if (dead) bullets.remove(i);
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.vy -= 14f * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.life -= dt;
            if (p.life <= 0f) particles.remove(i);
        }
    }

    private void spawnImpact(World w, float x, float y, float z, int bx, int by, int bz) {
        float[] c = {0.6f, 0.6f, 0.6f};
        if (w.inBounds(bx, by, bz)) {
            int t = w.get(bx, by, bz);
            if (t > 0) c = World.COLORS[t];
        }
        for (int i = 0; i < 7; i++) {
            if (particles.size() > 250) break;
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            p.vx = (rnd.nextFloat() - 0.5f) * 5f;
            p.vy = rnd.nextFloat() * 4f + 1f;
            p.vz = (rnd.nextFloat() - 0.5f) * 5f;
            p.life = 0.35f + rnd.nextFloat() * 0.3f;
            p.size = 0.04f + rnd.nextFloat() * 0.04f;
            float k = 0.8f + rnd.nextFloat() * 0.3f;
            p.r = c[0] * k; p.g = c[1] * k; p.b = c[2] * k;
            particles.add(p);
        }
    }

    private void burst(float x, float y, float z, float r, float g, float b, int n) {
        for (int i = 0; i < n; i++) {
            if (particles.size() > 250) break;
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            p.vx = (rnd.nextFloat() - 0.5f) * 4f;
            p.vy = rnd.nextFloat() * 3f + 0.5f;
            p.vz = (rnd.nextFloat() - 0.5f) * 4f;
            p.life = 0.3f + rnd.nextFloat() * 0.3f;
            p.size = 0.04f + rnd.nextFloat() * 0.04f;
            p.r = r; p.g = g; p.b = b;
            particles.add(p);
        }
    }

    public void emit(DynamicBatch batch) {
        for (Bullet b : bullets) {
            float ux = Math.abs(b.dy) > 0.95f ? 1f : 0f;
            float uy = Math.abs(b.dy) > 0.95f ? 0f : 1f;
            // right = normalize(d x up)
            float rx = b.dy * 0f - b.dz * uy;
            float ry = b.dz * ux - b.dx * 0f;
            float rz = b.dx * uy - b.dy * ux;
            float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            rx /= rl; ry /= rl; rz /= rl;
            // up2 = right x d
            float vx = ry * b.dz - rz * b.dy;
            float vy = rz * b.dx - rx * b.dz;
            float vz = rx * b.dy - ry * b.dx;
            float thick = 0.03f, half = 0.45f;
            batch.box(b.x - b.dx * half, b.y - b.dy * half, b.z - b.dz * half,
                    rx * thick, ry * thick, rz * thick,
                    vx * thick, vy * thick, vz * thick,
                    b.dx * half, b.dy * half, b.dz * half,
                    b.fromPlayer ? 1f : 1f, b.fromPlayer ? 0.9f : 0.35f, b.fromPlayer ? 0.35f : 0.15f);
        }
        for (Particle p : particles) {
            batch.axisBox(p.x, p.y, p.z, p.size, p.size, p.size, p.r, p.g, p.b);
        }
    }
}
