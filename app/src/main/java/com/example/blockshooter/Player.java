package com.example.blockshooter;

/** First-person player: walking, jumping, swimming, gravity and block collision. */
public class Player {
    private static final float HALF_W = 0.3f;
    private static final float HEIGHT = 1.8f;
    private static final float EYE = 1.62f;
    private static final float CROUCH_HEIGHT = 1.2f;
    private static final float CROUCH_EYE = 1.05f;
    private static final float SCOPE_FOV = 40f, NORMAL_FOV = 70f;
    private static final float WALK = 4.5f;
    private static final float GRAVITY = 26f;
    private static final float JUMP = 8.2f;
    private static final float MAX_FALL = 45f;
    private static final float LOOK_SENS = 0.15f;   // degrees per pixel

    private World world;

    public float x, y, z;   // feet position
    public float vy;
    public float yaw;       // degrees, 0 = looking toward -Z
    public float pitch;     // degrees, + = up
    public boolean onGround;
    public boolean crouching = false;
    public float scopeT = 0f;   // 0 = normal view, 1 = fully zoomed
    private float curHeight = HEIGHT;
    private float eyeCur = EYE;

    // health
    public static final float MAX_HEALTH = 100f;
    public float health = MAX_HEALTH;
    public boolean alive = true;
    private long lastHurtNano = 0L;

    public void spawn(World w) {
        world = w;
        int cx = w.sx / 2, cz = w.sz / 2;
        int sx = -1, sz = -1, sh = -1;
        search:
        for (int r = 0; r < 44; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int px = cx + dx, pz = cz + dz;
                    int h = w.topY(px, pz);
                    if (h <= World.SEA_LEVEL) continue;
                    int t = w.get(px, h, pz);
                    if (t == World.LEAVES || t == World.WOOD || t == World.WATER) continue;
                    sx = px; sz = pz; sh = h;
                    break search;
                }
            }
        }
        if (sh < 0) {
            sx = cx; sz = cz;
            sh = Math.max(w.topY(cx, cz), World.SEA_LEVEL);
        }
        x = sx + 0.5f;
        z = sz + 0.5f;
        y = sh + 1.01f;
        vy = 0f;
        yaw = 0f;
        pitch = -10f;
        onGround = false;
        health = MAX_HEALTH;
        alive = true;
        lastHurtNano = 0L;
        crouching = false;
        curHeight = HEIGHT;
        eyeCur = EYE;
        scopeT = 0f;
    }

    public void takeDamage(float amount, GameInput input) {
        if (!alive) return;
        health -= amount;
        lastHurtNano = System.nanoTime();
        input.damageTime = lastHurtNano;
        Sfx.play(Sfx.HURT, 0.9f);
        Haptics.vibrate(health <= 0f ? 300 : 40);
        if (health <= 0f) {
            health = 0f;
            alive = false;
            input.fire = false;
            input.jump = false;
        }
    }

    public boolean contains(float px, float py, float pz) {
        return Math.abs(px - x) < 0.35f && Math.abs(pz - z) < 0.35f && py >= y && py <= y + curHeight;
    }

    public float eyeX() { return x; }
    public float eyeY() { return y + eyeCur; }

    /** Current vertical field of view (degrees); narrower while aiming. */
    public float fov() {
        float t = scopeT * scopeT * (3f - 2f * scopeT);
        return NORMAL_FOV + (SCOPE_FOV - NORMAL_FOV) * t;
    }
    public float eyeZ() { return z; }

    public float lookX() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return (float) (Math.sin(yr) * Math.cos(pr));
    }
    public float lookY() { return (float) Math.sin(Math.toRadians(pitch)); }
    public float lookZ() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return (float) (-Math.cos(yr) * Math.cos(pr));
    }

    public void addRecoil(float dPitch, float dYaw) {
        pitch += dPitch;
        yaw += dYaw;
        if (pitch > 89f) pitch = 89f;
    }

    public void update(float dt, GameInput input) {
        if (dt > 0.05f) dt = 0.05f;
        if (!alive) return;

        // slow regeneration after 6 seconds without damage
        if (health < MAX_HEALTH && (System.nanoTime() - lastHurtNano) > 6_000_000_000L) {
            health = Math.min(MAX_HEALTH, health + 4f * dt);
        }

        // aim-down-sight blend
        float scopeTarget = input.scope ? 1f : 0f;
        if (scopeT < scopeTarget) scopeT = Math.min(scopeTarget, scopeT + dt * 7f);
        else scopeT = Math.max(scopeTarget, scopeT - dt * 7f);

        // crouch (standing up needs head room)
        if (input.crouch && !crouching) {
            crouching = true;
            curHeight = CROUCH_HEIGHT;
        } else if (!input.crouch && crouching && !collides(x, y, z, HEIGHT)) {
            crouching = false;
            curHeight = HEIGHT;
        }
        float eyeTarget = crouching ? CROUCH_EYE : EYE;
        eyeCur += (eyeTarget - eyeCur) * Math.min(1f, 12f * dt);

        // look (slower while zoomed so aiming stays precise)
        float zoomSens = fov() / NORMAL_FOV;
        yaw += input.takeLookX() * LOOK_SENS * input.lookSens * zoomSens;
        pitch -= input.takeLookY() * LOOK_SENS * input.lookSens * zoomSens;
        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;

        // horizontal wish direction
        float mx = input.moveX, my = input.moveY;
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 1f) { mx /= len; my /= len; }
        double yr = Math.toRadians(yaw);
        float fx = (float) Math.sin(yr), fz = (float) -Math.cos(yr);
        float rx = (float) Math.cos(yr), rz = (float) Math.sin(yr);

        boolean inWater = isWater(x, y + 0.4f, z);
        float speed = WALK * (inWater ? 0.6f : 1f);
        if (crouching) speed *= 0.45f;
        if (scopeT > 0.5f) speed *= 0.8f;
        float wx = (fx * my + rx * mx) * speed;
        float wz = (fz * my + rz * mx) * speed;

        // vertical
        if (inWater) {
            vy -= 6f * dt;
            if (vy < -3f) vy = -3f;
            if (input.jump) vy = 3.2f;
        } else {
            vy -= GRAVITY * dt;
            if (vy < -MAX_FALL) vy = -MAX_FALL;
            if (input.jump && onGround) vy = JUMP;
        }

        boolean blockedX = moveAxis(0, wx * dt);
        boolean blockedZ = moveAxis(2, wz * dt);

        // Water exit: when swimming into a ledge (up to ~1.5 blocks high), hop out onto land.
        if (inWater && (blockedX || blockedZ)) {
            float sx = blockedX ? Math.signum(wx) * 0.15f : 0f;
            float sz = blockedZ ? Math.signum(wz) * 0.15f : 0f;
            if (!collides(x + sx, y + 1.5f, z + sz)) {
                if (vy < 9.5f) vy = 9.5f;
            }
        }

        // Auto step-up: walking into a one-block ledge makes the player hop onto it.
        if (!inWater && onGround && (blockedX || blockedZ) && (mx != 0f || my != 0f)) {
            float sx = blockedX ? Math.signum(wx) * 0.15f : 0f;
            float sz = blockedZ ? Math.signum(wz) * 0.15f : 0f;
            if (!collides(x + sx, y + 1.02f, z + sz, curHeight)) {
                vy = JUMP;
            }
        }

        if (moveAxis(1, vy * dt)) vy = 0f;

        onGround = vy <= 0f && collides(x, y - 0.05f, z);
    }

    /** Moves along one axis in small steps. Returns true if blocked. */
    private boolean moveAxis(int axis, float amount) {
        if (amount == 0f) return false;
        int steps = (int) Math.ceil(Math.abs(amount) / 0.2f);
        float step = amount / steps;
        for (int i = 0; i < steps; i++) {
            float nx = x + (axis == 0 ? step : 0f);
            float ny = y + (axis == 1 ? step : 0f);
            float nz = z + (axis == 2 ? step : 0f);
            if (collides(nx, ny, nz)) return true;
            x = nx; y = ny; z = nz;
        }
        return false;
    }

    private boolean collides(float px, float py, float pz) {
        return collides(px, py, pz, curHeight);
    }

    private boolean collides(float px, float py, float pz, float height) {
        int x0 = (int) Math.floor(px - HALF_W + 0.0001f);
        int x1 = (int) Math.floor(px + HALF_W - 0.0001f);
        int y0 = (int) Math.floor(py + 0.0005f);
        int y1 = (int) Math.floor(py + height - 0.0001f);
        int z0 = (int) Math.floor(pz - HALF_W + 0.0001f);
        int z1 = (int) Math.floor(pz + HALF_W - 0.0001f);
        for (int by = y0; by <= y1; by++)
            for (int bz = z0; bz <= z1; bz++)
                for (int bx = x0; bx <= x1; bx++)
                    if (solidAt(bx, by, bz)) return true;
        return false;
    }

    private boolean solidAt(int bx, int by, int bz) {
        if (by < 0) return true;
        if (bx < 0 || bz < 0 || bx >= world.sx || bz >= world.sz) return true;
        if (by >= world.sy) return false;
        int t = world.get(bx, by, bz);
        return t != World.AIR && t != World.WATER;
    }

    private boolean isWater(float px, float py, float pz) {
        return world.get((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz)) == World.WATER;
    }
}
