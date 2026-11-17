package com.example.blockshooter;

/** Shared state between the UI thread (touch) and the GL thread (game loop). */
public class GameInput {
    // game states
    public static final int STATE_MENU = 0, STATE_PLAYING = 1, STATE_LEVEL_CLEAR = 2,
            STATE_DEAD = 3, STATE_VICTORY = 4, STATE_PAUSED = 5;

    // commands (UI -> game)
    public static final int CMD_CONTINUE = 1, CMD_NEW_GAME = 2, CMD_NEXT_LEVEL = 3,
            CMD_RETRY = 4, CMD_MENU = 5, CMD_RESUME = 6, CMD_PAUSE = 7,
            CMD_TOGGLE_SOUND = 8, CMD_TOGGLE_VIB = 9;

    // controls (UI -> game)
    public volatile float moveX = 0f;   // -1 (left) .. 1 (right)
    public volatile float moveY = 0f;   // -1 (back) .. 1 (forward)
    public volatile boolean jump = false;
    public volatile boolean fire = false;
    public volatile boolean scope = false;    // aiming down the sight (zoom)
    public volatile boolean crouch = false;   // crouching
    public volatile float lookSens = 1f;   // look sensitivity multiplier (settings)

    // status (game -> HUD)
    public volatile int state = STATE_MENU;
    public volatile int level = 1;
    public volatile int savedLevel = 1;
    public volatile int totalLevels = Levels.TOTAL;
    public volatile int levelEnemies = 0;
    public volatile int levelKills = 0;
    public volatile float levelTime = 0f;
    public volatile long bannerTime = 0L;
    public volatile boolean soundOn = true;
    public volatile boolean vibOn = true;

    public volatile int ammo = 0;
    public volatile int reserve = 0;
    public volatile boolean reloading = false;
    public volatile int health = 100;
    public volatile int enemies = 0;
    public volatile long hitTime = 0L;
    public volatile boolean hitKill = false;
    public volatile boolean hitHead = false;
    public volatile long damageTime = 0L;

    private float lookX = 0f, lookY = 0f;
    private boolean reloadRequest = false;
    private int command = 0;

    public synchronized void addLook(float dx, float dy) {
        lookX += dx;
        lookY += dy;
    }

    public synchronized float takeLookX() {
        float v = lookX;
        lookX = 0f;
        return v;
    }

    public synchronized float takeLookY() {
        float v = lookY;
        lookY = 0f;
        return v;
    }

    public synchronized void requestReload() { reloadRequest = true; }

    public synchronized boolean takeReload() {
        boolean v = reloadRequest;
        reloadRequest = false;
        return v;
    }

    public synchronized void postCommand(int cmd) { command = cmd; }

    public synchronized int takeCommand() {
        int c = command;
        command = 0;
        return c;
    }

    public void registerHit(boolean head, boolean killed) {
        hitHead = head;
        hitKill = killed;
        hitTime = System.nanoTime();
        if (killed) levelKills++;   // only called from the game thread
    }

    public void reset() {
        moveX = 0f;
        moveY = 0f;
        jump = false;
        fire = false;
        scope = false;
        crouch = false;
    }
}
