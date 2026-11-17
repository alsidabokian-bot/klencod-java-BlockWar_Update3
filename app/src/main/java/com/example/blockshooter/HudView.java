package com.example.blockshooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Transparent overlay. While playing: floating joystick (left), look-drag (right),
 * fire / jump / reload / pause buttons, crosshair, health, ammo, objective.
 * In every other state it draws the menu / pause / result screens with buttons.
 */
public class HudView extends View {
    private static class Btn {
        float l, t, r, b;
        String label;
        int cmd;
        int color;
    }

    private final GameInput input;
    private final float d;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ammoText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint infoText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bigText = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---- customisable controls ----
    private static final int JOY = 0, FIRE = 1, JUMP = 2, RELOAD = 3, PAUSE = 4,
            SCOPE = 5, CROUCH = 6, N_CTRL = 7;
    private static final int SCR_NONE = 0, SCR_SETTINGS = 1, SCR_LAYOUT = 2;
    private static final int CMD_SETTINGS = 100, CMD_LAYOUT = 101, CMD_BACK = 102,
            CMD_MINUS = 103, CMD_PLUS = 104, CMD_RESET = 105, CMD_DONE = 106,
            CMD_SCOPE_MODE = 107, CMD_CROUCH_MODE = 108;
    private static final float SENS_MIN = 0.3f, SENS_MAX = 3.0f;

    private final SharedPreferences prefs;
    private final float[] lx = new float[N_CTRL];   // centre as fraction of width (NaN = default)
    private final float[] ly = new float[N_CTRL];   // centre as fraction of height (NaN = default)
    private final float[] ls = new float[N_CTRL];   // size multiplier
    private int screen = SCR_NONE;
    private int selected = FIRE;
    private boolean dragging = false;
    private float dragOffX, dragOffY;
    private boolean sliderDrag = false;
    private float sens = 1f;
    private boolean scopeToggle = false;    // false = hold, true = tap to switch on/off
    private boolean crouchToggle = false;

    private int movePointer = -1, lookPointer = -1;
    private int jumpPointer = -1, firePointer = -1, reloadPointer = -1;
    private int scopePointer = -1, crouchPointer = -1;
    private float baseX, baseY, curX, curY;
    private float lastLookX, lastLookY;
    private float lastFireX, lastFireY;

    public HudView(Context context, GameInput input) {
        super(context);
        this.input = input;
        d = context.getResources().getDisplayMetrics().density;
        prefs = context.getSharedPreferences("blockshooter", Context.MODE_PRIVATE);
        loadLayout();
        sens = Math.max(SENS_MIN, Math.min(SENS_MAX, prefs.getFloat("sens", 1f)));
        input.lookSens = sens;
        scopeToggle = prefs.getBoolean("scopeToggle", false);
        crouchToggle = prefs.getBoolean("crouchToggle", false);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f * d);
        stroke.setColor(0x88FFFFFF);

        fill.setStyle(Paint.Style.FILL);

        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(15f * d);

        ammoText.setColor(0xFFFFFFFF);
        ammoText.setTextAlign(Paint.Align.CENTER);
        ammoText.setTextSize(26f * d);
        ammoText.setShadowLayer(4f, 1f, 1f, 0xFF000000);

        cross.setColor(0xFFFFFFFF);
        cross.setStrokeWidth(2f * d);
        crossShadow.setColor(0xAA000000);
        crossShadow.setStrokeWidth(4f * d);

        bar.setStyle(Paint.Style.FILL);

        infoText.setColor(0xFFFFFFFF);
        infoText.setTextAlign(Paint.Align.CENTER);
        infoText.setTextSize(16f * d);
        infoText.setShadowLayer(4f, 1f, 1f, 0xFF000000);

        hitPaint.setStrokeWidth(3f * d);

        bigText.setColor(0xFFFFFFFF);
        bigText.setTextAlign(Paint.Align.CENTER);
        bigText.setTextSize(34f * d);
        bigText.setShadowLayer(6f, 2f, 2f, 0xFF000000);
    }

    // ---------- geometry (user-customisable) ----------
    private void loadLayout() {
        for (int i = 0; i < N_CTRL; i++) {
            lx[i] = prefs.getFloat("lx" + i, Float.NaN);
            ly[i] = prefs.getFloat("ly" + i, Float.NaN);
            ls[i] = Math.max(0.6f, Math.min(2.0f, prefs.getFloat("ls" + i, 1f)));
        }
    }

    private void saveLayout() {
        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < N_CTRL; i++) {
            ed.putFloat("lx" + i, lx[i]);
            ed.putFloat("ly" + i, ly[i]);
            ed.putFloat("ls" + i, ls[i]);
        }
        ed.apply();
    }

    private float defX(int i) {
        float w = getWidth();
        switch (i) {
            case JOY: return 110f * d;
            case FIRE: return w - 90f * d;
            case JUMP: return w - 90f * d;
            case RELOAD: return w - 195f * d;
            case SCOPE: return w - 195f * d;
            case CROUCH: return w - 195f * d;
            default: return w - 40f * d;
        }
    }

    private float defY(int i) {
        float h = getHeight();
        switch (i) {
            case JOY: return h - 100f * d;
            case FIRE: return h - 100f * d;
            case JUMP: return h - 215f * d;
            case RELOAD: return h - 70f * d;
            case SCOPE: return h - 145f * d;
            case CROUCH: return h - 220f * d;
            default: return 40f * d;
        }
    }

    private float baseR(int i) {
        switch (i) {
            case JOY: return 60f * d;
            case FIRE: return 50f * d;
            case JUMP: return 36f * d;
            case RELOAD: return 30f * d;
            case SCOPE: return 30f * d;
            case CROUCH: return 30f * d;
            default: return 22f * d;
        }
    }

    private float posX(int i) { return Float.isNaN(lx[i]) ? defX(i) : lx[i] * getWidth(); }
    private float posY(int i) { return Float.isNaN(ly[i]) ? defY(i) : ly[i] * getHeight(); }
    private float rad(int i) { return baseR(i) * ls[i]; }

    private void setPos(int i, float x, float y) {
        float r = rad(i);
        float w = getWidth(), h = getHeight();
        x = Math.max(r, Math.min(w - r, x));
        y = Math.max(r, Math.min(h - r, y));
        lx[i] = x / w;
        ly[i] = y / h;
    }

    private float joyRadius() { return rad(JOY); }
    private float fireX() { return posX(FIRE); }
    private float fireY() { return posY(FIRE); }
    private float fireR() { return rad(FIRE); }
    private float jumpX() { return posX(JUMP); }
    private float jumpY() { return posY(JUMP); }
    private float jumpR() { return rad(JUMP); }
    private float reloadX() { return posX(RELOAD); }
    private float reloadY() { return posY(RELOAD); }
    private float reloadR() { return rad(RELOAD); }
    private float pauseX() { return posX(PAUSE); }
    private float pauseY() { return posY(PAUSE); }
    private float pauseR() { return rad(PAUSE); }
    private float scopeX() { return posX(SCOPE); }
    private float scopeY() { return posY(SCOPE); }
    private float scopeR() { return rad(SCOPE); }
    private float crouchX() { return posX(CROUCH); }
    private float crouchY() { return posY(CROUCH); }
    private float crouchR() { return rad(CROUCH); }

    private static boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r * 1.5f;
    }

    // ---------- menu buttons ----------
    private Btn btn(float cx, float top, float w, float h, String label, int cmd, int color) {
        Btn b = new Btn();
        b.l = cx - w / 2f;
        b.r = cx + w / 2f;
        b.t = top;
        b.b = top + h;
        b.label = label;
        b.cmd = cmd;
        b.color = color;
        return b;
    }

    private ArrayList<Btn> buildButtons() {
        ArrayList<Btn> list = new ArrayList<>();
        float cx = getWidth() / 2f;
        float W = 240f * d, H = 38f * d, gap = 8f * d;
        float y = getHeight() * 0.38f;
        int st = input.state;
        int GREEN = 0xFF2E9E4F, BLUE = 0xFF2F6FD0, RED = 0xFFC0392B, GRAY = 0xFF555555;

        if (st == GameInput.STATE_MENU) {
            y = getHeight() * 0.40f;
            if (input.savedLevel > 1) {
                list.add(btn(cx, y, W, H, "متابعة (المرحلة " + input.savedLevel + ")", GameInput.CMD_CONTINUE, GREEN));
                y += H + gap;
                list.add(btn(cx, y, W, H, "لعبة جديدة", GameInput.CMD_NEW_GAME, BLUE));
            } else {
                list.add(btn(cx, y, W, H, "ابدأ اللعب", GameInput.CMD_NEW_GAME, GREEN));
            }
            y += H + gap;
        } else if (st == GameInput.STATE_PAUSED) {
            y = getHeight() * 0.30f;
            list.add(btn(cx, y, W, H, "متابعة", GameInput.CMD_RESUME, GREEN));
            y += H + gap;
            list.add(btn(cx, y, W, H, "إعادة المرحلة", GameInput.CMD_RETRY, BLUE));
            y += H + gap;
            list.add(btn(cx, y, W, H, "القائمة الرئيسية", GameInput.CMD_MENU, RED));
            y += H + gap;
        } else if (st == GameInput.STATE_LEVEL_CLEAR) {
            y = getHeight() * 0.50f;
            list.add(btn(cx, y, W, H, "المرحلة التالية", GameInput.CMD_NEXT_LEVEL, GREEN));
            y += H + gap;
            list.add(btn(cx, y, W, H, "القائمة الرئيسية", GameInput.CMD_MENU, GRAY));
            y += H + gap;
            return list;
        } else if (st == GameInput.STATE_DEAD) {
            y = getHeight() * 0.50f;
            list.add(btn(cx, y, W, H, "إعادة المرحلة", GameInput.CMD_RETRY, GREEN));
            y += H + gap;
            list.add(btn(cx, y, W, H, "القائمة الرئيسية", GameInput.CMD_MENU, GRAY));
            y += H + gap;
            return list;
        } else if (st == GameInput.STATE_VICTORY) {
            y = getHeight() * 0.55f;
            list.add(btn(cx, y, W, H, "القائمة الرئيسية", GameInput.CMD_MENU, GREEN));
            return list;
        } else {
            return list;
        }

        // sound / vibration toggles (menu + pause)
        float half = (W - 8f * d) / 2f;
        Btn s = btn(cx - half / 2f - 4f * d, y, half, H,
                "الصوت: " + (input.soundOn ? "تشغيل" : "إيقاف"), GameInput.CMD_TOGGLE_SOUND,
                input.soundOn ? GRAY : 0xFF333333);
        Btn v = btn(cx + half / 2f + 4f * d, y, half, H,
                "الاهتزاز: " + (input.vibOn ? "تشغيل" : "إيقاف"), GameInput.CMD_TOGGLE_VIB,
                input.vibOn ? GRAY : 0xFF333333);
        list.add(s);
        list.add(v);
        y += H + gap;
        list.add(btn(cx, y, W, H, "الإعدادات", CMD_SETTINGS, 0xFF8E5BD0));
        return list;
    }

    private void drawButtons(Canvas c) {
        drawBtnList(c, buildButtons());
    }

    private void drawBtnList(Canvas c, ArrayList<Btn> list) {
        for (Btn b : list) {
            fill.setColor(b.color);
            c.drawRoundRect(b.l, b.t, b.r, b.b, 10f * d, 10f * d, fill);
            text.setTextSize(14f * d);
            c.drawText(b.label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 5f * d, text);
        }
        text.setTextSize(15f * d);
    }

    private String formatTime(float t) {
        int s = (int) t;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    // ---------- drawing ----------
    private void button(Canvas c, float x, float y, float r, boolean pressed, int color, String label) {
        fill.setColor(pressed ? (color | 0xAA000000) : ((color & 0x00FFFFFF) | 0x44000000));
        c.drawCircle(x, y, r, fill);
        c.drawCircle(x, y, r, stroke);
        float ts = Math.max(10f * d, Math.min(20f * d, r * 0.45f));
        text.setTextSize(ts);
        c.drawText(label, x, y + ts * 0.33f, text);
        text.setTextSize(15f * d);
    }

    // ---------- settings + layout editor screens ----------
    private float sliderX0() { return getWidth() / 2f - 140f * d; }
    private float sliderX1() { return getWidth() / 2f + 140f * d; }
    private float sliderY() { return getHeight() * 0.38f; }

    private ArrayList<Btn> settingsButtons() {
        ArrayList<Btn> list = new ArrayList<>();
        float cx = getWidth() / 2f;
        float W = 300f * d, H = 36f * d, gap = 7f * d;
        float y = getHeight() * 0.46f;
        float half = (W - 8f * d) / 2f;
        list.add(btn(cx - half / 2f - 4f * d, y, half, H,
                "المنظار: " + (scopeToggle ? "ضغطة تبديل" : "ضغط مستمر"), CMD_SCOPE_MODE, 0xFF8E5BD0));
        list.add(btn(cx + half / 2f + 4f * d, y, half, H,
                "الجلوس: " + (crouchToggle ? "ضغطة تبديل" : "ضغط مستمر"), CMD_CROUCH_MODE, 0xFF8E5BD0));
        y += H + gap;
        list.add(btn(cx, y, W, H, "تعديل مكان وحجم الأزرار", CMD_LAYOUT, 0xFF2F6FD0));
        y += H + gap;
        list.add(btn(cx, y, W, H, "رجوع", CMD_BACK, 0xFF555555));
        return list;
    }

    private ArrayList<Btn> layoutButtons() {
        ArrayList<Btn> list = new ArrayList<>();
        float bw = 64f * d, bh = 36f * d, gap = 8f * d;
        float total = 4 * bw + 3 * gap;
        float x = getWidth() / 2f - total / 2f + bw / 2f;
        float top = 10f * d;
        String[] labels = {"−", "+", "افتراضي", "تم"};
        int[] cmds = {CMD_MINUS, CMD_PLUS, CMD_RESET, CMD_DONE};
        int[] cols = {0xFF555555, 0xFF555555, 0xFFC0392B, 0xFF2E9E4F};
        for (int i = 0; i < 4; i++) {
            list.add(btn(x + i * (bw + gap), top, bw, bh, labels[i], cmds[i], cols[i]));
        }
        return list;
    }

    private static String ctrlName(int i) {
        switch (i) {
            case JOY: return "عصا الحركة";
            case FIRE: return "نار";
            case JUMP: return "قفز";
            case RELOAD: return "تعبئة";
            case SCOPE: return "منظار";
            case CROUCH: return "جلوس";
            default: return "إيقاف";
        }
    }

    private void drawScreen(Canvas c, int w, int h) {
        if (screen == SCR_SETTINGS) {
            c.drawColor(0xCC000000);
            bigText.setColor(0xFFFFFFFF);
            bigText.setTextSize(34f * d);
            c.drawText("الإعدادات", w / 2f, h * 0.18f, bigText);

            infoText.setTextSize(16f * d);
            c.drawText("حساسية النظر: ×" + String.format(Locale.US, "%.2f", sens), w / 2f, h * 0.30f, infoText);
            float x0 = sliderX0(), x1 = sliderX1(), sy = sliderY();
            fill.setColor(0xFF666666);
            c.drawRoundRect(x0, sy - 4f * d, x1, sy + 4f * d, 4f * d, 4f * d, fill);
            float t = (sens - SENS_MIN) / (SENS_MAX - SENS_MIN);
            fill.setColor(0xFF2F9FFF);
            c.drawRoundRect(x0, sy - 4f * d, x0 + (x1 - x0) * t, sy + 4f * d, 4f * d, 4f * d, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(x0 + (x1 - x0) * t, sy, 12f * d, fill);
            infoText.setTextSize(12f * d);
            c.drawText("بطيء", x0 - 24f * d, sy + 4f * d, infoText);
            c.drawText("سريع", x1 + 24f * d, sy + 4f * d, infoText);

            drawBtnList(c, settingsButtons());
        } else if (screen == SCR_LAYOUT) {
            c.drawColor(0x88000000);
            // controls
            for (int i = 0; i < N_CTRL; i++) {
                float x = posX(i), y = posY(i), r = rad(i);
                if (i == JOY) {
                    fill.setColor(0x33FFFFFF);
                    c.drawCircle(x, y, r, fill);
                    c.drawCircle(x, y, r, stroke);
                    fill.setColor(0x88FFFFFF);
                    c.drawCircle(x, y, 26f * d * ls[JOY], fill);
                } else {
                    int col = i == FIRE ? 0xFFFF4444 : (i == RELOAD ? 0xFF44AAFF
                            : (i == SCOPE ? 0xFFFFDD33 : (i == CROUCH ? 0xFF66FFAA : 0xFFFFFFFF)));
                    button(c, x, y, r, false, col, i == PAUSE ? "II" : ctrlName(i));
                }
                if (i == selected) {
                    hitPaint.setColor(0xFFFFDD33);
                    hitPaint.setStyle(Paint.Style.STROKE);
                    c.drawCircle(x, y, r + 6f * d, hitPaint);
                }
            }
            drawBtnList(c, layoutButtons());
            infoText.setTextSize(13f * d);
            c.drawText("اسحب أي زر لتحريكه، ثم + و − لتغيير حجمه", w / 2f, 68f * d, infoText);
            c.drawText(ctrlName(selected) + ": " + Math.round(ls[selected] * 100f) + "%", w / 2f, 88f * d, infoText);
        }
    }

    private static boolean hit(Btn b, float x, float y) {
        return x >= b.l && x <= b.r && y >= b.t && y <= b.b;
    }

    private void setSensFromX(float x) {
        float t = (x - sliderX0()) / (sliderX1() - sliderX0());
        t = Math.max(0f, Math.min(1f, t));
        float v = SENS_MIN + t * (SENS_MAX - SENS_MIN);
        sens = Math.round(v * 20f) / 20f;
        input.lookSens = sens;
    }

    private boolean handleScreenTouch(MotionEvent e) {
        int a = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        if (screen == SCR_SETTINGS) {
            if (a == MotionEvent.ACTION_DOWN) {
                if (Math.abs(y - sliderY()) < 28f * d && x > sliderX0() - 14f * d && x < sliderX1() + 14f * d) {
                    sliderDrag = true;
                    setSensFromX(x);
                } else {
                    for (Btn b : settingsButtons()) {
                        if (hit(b, x, y)) {
                            Sfx.play(Sfx.CLICK, 0.6f);
                            if (b.cmd == CMD_LAYOUT) { screen = SCR_LAYOUT; selected = FIRE; }
                            else if (b.cmd == CMD_BACK) screen = SCR_NONE;
                            else if (b.cmd == CMD_SCOPE_MODE) {
                                scopeToggle = !scopeToggle;
                                input.scope = false;
                                prefs.edit().putBoolean("scopeToggle", scopeToggle).apply();
                            } else if (b.cmd == CMD_CROUCH_MODE) {
                                crouchToggle = !crouchToggle;
                                input.crouch = false;
                                prefs.edit().putBoolean("crouchToggle", crouchToggle).apply();
                            }
                            break;
                        }
                    }
                }
            } else if (a == MotionEvent.ACTION_MOVE && sliderDrag) {
                setSensFromX(x);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (sliderDrag) {
                    sliderDrag = false;
                    prefs.edit().putFloat("sens", sens).apply();
                }
            }
        } else if (screen == SCR_LAYOUT) {
            if (a == MotionEvent.ACTION_DOWN) {
                boolean handled = false;
                for (Btn b : layoutButtons()) {
                    if (!hit(b, x, y)) continue;
                    handled = true;
                    Sfx.play(Sfx.CLICK, 0.6f);
                    if (b.cmd == CMD_PLUS || b.cmd == CMD_MINUS) {
                        float v = ls[selected] + (b.cmd == CMD_PLUS ? 0.1f : -0.1f);
                        ls[selected] = Math.max(0.6f, Math.min(2.0f, Math.round(v * 10f) / 10f));
                        setPos(selected, posX(selected), posY(selected));
                        saveLayout();
                    } else if (b.cmd == CMD_RESET) {
                        for (int i = 0; i < N_CTRL; i++) { lx[i] = Float.NaN; ly[i] = Float.NaN; ls[i] = 1f; }
                        saveLayout();
                    } else if (b.cmd == CMD_DONE) {
                        saveLayout();
                        screen = SCR_SETTINGS;
                    }
                    break;
                }
                if (!handled) {
                    int best = -1;
                    float bestD = Float.MAX_VALUE;
                    for (int i = 0; i < N_CTRL; i++) {
                        float dx = x - posX(i), dy = y - posY(i);
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dist <= rad(i) * 1.2f && dist < bestD) { best = i; bestD = dist; }
                    }
                    if (best >= 0) {
                        selected = best;
                        dragging = true;
                        dragOffX = posX(best) - x;
                        dragOffY = posY(best) - y;
                    }
                }
            } else if (a == MotionEvent.ACTION_MOVE && dragging) {
                setPos(selected, x + dragOffX, y + dragOffY);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (dragging) { dragging = false; saveLayout(); }
            }
        }
        invalidate();
        return true;
    }

    /** Called from the activity's back button. Returns true if it was consumed. */
    public boolean handleBack() {
        if (screen == SCR_LAYOUT) {
            saveLayout();
            screen = SCR_SETTINGS;
            postInvalidate();
            return true;
        }
        if (screen == SCR_SETTINGS) {
            screen = SCR_NONE;
            postInvalidate();
            return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        int st = input.state;
        long now = System.nanoTime();

        if (screen == SCR_NONE && (st == GameInput.STATE_PLAYING || st == GameInput.STATE_PAUSED)) {
            drawGameplay(c, w, h, now);
        }

        if (screen != SCR_NONE) {
            drawScreen(c, w, h);
            postInvalidateDelayed(50);
            return;
        }

        // overlays
        if (st != GameInput.STATE_PLAYING) {
            c.drawColor(st == GameInput.STATE_MENU ? 0x66000000 : 0xAA000000);
            bigText.setTextSize(34f * d);
            infoText.setTextSize(16f * d);

            if (st == GameInput.STATE_MENU) {
                bigText.setColor(0xFFFFFFFF);
                bigText.setTextSize(44f * d);
                c.drawText("حرب المكعبات", w / 2f, h * 0.22f, bigText);
                infoText.setTextSize(16f * d);
                c.drawText("لعبة القتال بالمكعبات", w / 2f, h * 0.22f + 30f * d, infoText);
                if (input.savedLevel > 1) {
                    infoText.setTextSize(13f * d);
                    c.drawText("وصلت إلى المرحلة " + input.savedLevel + " من " + input.totalLevels,
                            w / 2f, h * 0.22f + 52f * d, infoText);
                }
            } else if (st == GameInput.STATE_PAUSED) {
                bigText.setColor(0xFFFFFFFF);
                c.drawText("متوقف مؤقتاً", w / 2f, h * 0.22f, bigText);
            } else if (st == GameInput.STATE_LEVEL_CLEAR) {
                bigText.setColor(0xFF66FF66);
                c.drawText("اكتملت المرحلة " + input.level + "!", w / 2f, h * 0.24f, bigText);
                infoText.setTextSize(18f * d);
                c.drawText("القتلى: " + input.levelKills + "   |   الوقت: " + formatTime(input.levelTime),
                        w / 2f, h * 0.36f, infoText);
            } else if (st == GameInput.STATE_DEAD) {
                bigText.setColor(0xFFFF5555);
                c.drawText("انتهت اللعبة", w / 2f, h * 0.26f, bigText);
                infoText.setTextSize(16f * d);
                c.drawText("المرحلة " + input.level + "  |  القتلى: " + input.levelKills + " / " + input.levelEnemies,
                        w / 2f, h * 0.38f, infoText);
            } else if (st == GameInput.STATE_VICTORY) {
                bigText.setColor(0xFFFFDD55);
                c.drawText("مبروك! أنهيت اللعبة", w / 2f, h * 0.26f, bigText);
                infoText.setTextSize(17f * d);
                c.drawText("أكملت جميع المراحل الـ " + input.totalLevels, w / 2f, h * 0.37f, infoText);
                c.drawText("آخر مرحلة: القتلى " + input.levelKills + "  |  الوقت " + formatTime(input.levelTime),
                        w / 2f, h * 0.37f + 28f * d, infoText);
            }
            drawButtons(c);
        }

        postInvalidateDelayed(50);
    }

    private void drawGameplay(Canvas c, int w, int h, long now) {
        // crosshair
        float cx = w / 2f, cy = h / 2f, s = 10f * d;
        c.drawLine(cx - s, cy, cx + s, cy, crossShadow);
        c.drawLine(cx, cy - s, cx, cy + s, crossShadow);
        c.drawLine(cx - s, cy, cx + s, cy, cross);
        c.drawLine(cx, cy - s, cx, cy + s, cross);

        // joystick
        if (movePointer != -1) {
            fill.setColor(0x22FFFFFF);
            c.drawCircle(baseX, baseY, joyRadius(), fill);
            c.drawCircle(baseX, baseY, joyRadius(), stroke);
            fill.setColor(0x88FFFFFF);
            c.drawCircle(curX, curY, 26f * d, fill);
        } else {
            c.drawCircle(posX(JOY), posY(JOY), joyRadius(), stroke);
        }

        // buttons
        button(c, fireX(), fireY(), fireR(), firePointer != -1, 0xFFFF4444, "نار");
        button(c, jumpX(), jumpY(), jumpR(), jumpPointer != -1, 0xFFFFFFFF, "قفز");
        button(c, reloadX(), reloadY(), reloadR(), reloadPointer != -1, 0xFF44AAFF, "تعبئة");
        button(c, scopeX(), scopeY(), scopeR(), input.scope, 0xFFFFDD33, "منظار");
        button(c, crouchX(), crouchY(), crouchR(), input.crouch, 0xFF66FFAA, "جلوس");
        button(c, pauseX(), pauseY(), pauseR(), false, 0xFFFFFFFF, "II");

        // ammo
        String label;
        if (input.reloading) {
            ammoText.setColor(0xFFFFDD55);
            label = "جاري التعبئة...";
        } else {
            ammoText.setColor(input.ammo == 0 ? 0xFFFF5555 : 0xFFFFFFFF);
            label = input.ammo + " / " + input.reserve;
        }
        c.drawText(label, w / 2f, h - 24f * d, ammoText);

        // health bar
        float bw = 190f * d, bh = 14f * d;
        float bx0 = w / 2f - bw / 2f, by0 = h - 78f * d;
        bar.setColor(0x88000000);
        c.drawRect(bx0 - 2f * d, by0 - 2f * d, bx0 + bw + 2f * d, by0 + bh + 2f * d, bar);
        float frac = Math.max(0f, Math.min(1f, input.health / 100f));
        bar.setColor(frac > 0.5f ? 0xFF44CC44 : (frac > 0.25f ? 0xFFFFAA22 : 0xFFEE3333));
        c.drawRect(bx0, by0, bx0 + bw * frac, by0 + bh, bar);
        infoText.setTextSize(13f * d);
        c.drawText("الصحة " + input.health, w / 2f, by0 - 6f * d, infoText);

        // objective (top centre)
        infoText.setTextSize(16f * d);
        c.drawText("المرحلة " + input.level + "   |   الأعداء: " + input.enemies, w / 2f, 30f * d, infoText);

        // level banner for the first seconds
        float bannerAge = (now - input.bannerTime) / 1e9f;
        if (input.bannerTime != 0L && bannerAge < 4f) {
            int alpha = (int) (255f * Math.min(1f, (4f - bannerAge)));
            bigText.setTextSize(30f * d);
            bigText.setColor((alpha << 24) | 0x00FFFFFF);
            c.drawText("المرحلة " + input.level, w / 2f, h * 0.26f, bigText);
            infoText.setTextSize(17f * d);
            infoText.setAlpha(alpha);
            c.drawText("الهدف: اقضِ على جميع الجنود (" + input.levelEnemies + ")", w / 2f, h * 0.26f + 30f * d, infoText);
            infoText.setAlpha(255);
        }

        // hit marker
        float hitAge = (now - input.hitTime) / 1e9f;
        if (input.hitTime != 0L && hitAge < 0.18f) {
            hitPaint.setColor(input.hitKill ? 0xFFFF3333 : (input.hitHead ? 0xFFFFDD33 : 0xFFFFFFFF));
            float a = 8f * d, b2 = 18f * d;
            c.drawLine(cx - b2, cy - b2, cx - a, cy - a, hitPaint);
            c.drawLine(cx + b2, cy - b2, cx + a, cy - a, hitPaint);
            c.drawLine(cx - b2, cy + b2, cx - a, cy + a, hitPaint);
            c.drawLine(cx + b2, cy + b2, cx + a, cy + a, hitPaint);
        }

        // damage flash
        float dmgAge = (now - input.damageTime) / 1e9f;
        if (input.damageTime != 0L && dmgAge < 0.5f) {
            int alpha = (int) ((1f - dmgAge / 0.5f) * 110f);
            c.drawColor((alpha << 24) | 0x00FF0000);
        }
    }

    // ---------- touch ----------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        int w = getWidth();

        // settings / layout editor screens
        if (screen != SCR_NONE) {
            movePointer = lookPointer = jumpPointer = firePointer = reloadPointer = scopePointer = crouchPointer = -1;
            input.reset();
            return handleScreenTouch(e);
        }

        // menus / overlays
        if (input.state != GameInput.STATE_PLAYING) {
            movePointer = lookPointer = jumpPointer = firePointer = reloadPointer = scopePointer = crouchPointer = -1;
            input.reset();
            if (action == MotionEvent.ACTION_DOWN) {
                float x = e.getX(), y = e.getY();
                for (Btn b : buildButtons()) {
                    if (x >= b.l && x <= b.r && y >= b.t && y <= b.b) {
                        Sfx.play(Sfx.CLICK, 0.6f);
                        if (b.cmd == CMD_SETTINGS) {
                            screen = SCR_SETTINGS;
                            invalidate();
                        } else {
                            input.postCommand(b.cmd);
                        }
                        break;
                    }
                }
            }
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = e.getPointerId(idx);
                float x = e.getX(idx), y = e.getY(idx);
                if (inCircle(x, y, pauseX(), pauseY(), pauseR())) {
                    input.postCommand(GameInput.CMD_PAUSE);
                } else if (firePointer == -1 && inCircle(x, y, fireX(), fireY(), fireR())) {
                    firePointer = id;
                    lastFireX = x;
                    lastFireY = y;
                    input.fire = true;
                } else if (jumpPointer == -1 && inCircle(x, y, jumpX(), jumpY(), jumpR())) {
                    jumpPointer = id;
                    input.jump = true;
                } else if (reloadPointer == -1 && inCircle(x, y, reloadX(), reloadY(), reloadR())) {
                    reloadPointer = id;
                    input.requestReload();
                } else if (scopePointer == -1 && inCircle(x, y, scopeX(), scopeY(), scopeR())) {
                    scopePointer = id;
                    input.scope = scopeToggle ? !input.scope : true;
                } else if (crouchPointer == -1 && inCircle(x, y, crouchX(), crouchY(), crouchR())) {
                    crouchPointer = id;
                    input.crouch = crouchToggle ? !input.crouch : true;
                } else if (movePointer == -1
                        && (x < w * 0.45f || inCircle(x, y, posX(JOY), posY(JOY), joyRadius()))) {
                    movePointer = id;
                    if (inCircle(x, y, posX(JOY), posY(JOY), joyRadius())) {
                        baseX = posX(JOY);   // touched the ring: fixed joystick
                        baseY = posY(JOY);
                    } else {
                        baseX = x;           // elsewhere on the left: floating joystick
                        baseY = y;
                    }
                    updateJoystick(x, y);
                } else if (x >= w * 0.45f && lookPointer == -1) {
                    lookPointer = id;
                    lastLookX = x;
                    lastLookY = y;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    float x = e.getX(i), y = e.getY(i);
                    if (id == movePointer) {
                        updateJoystick(x, y);
                    } else if (id == lookPointer) {
                        input.addLook(x - lastLookX, y - lastLookY);
                        lastLookX = x;
                        lastLookY = y;
                    } else if (id == firePointer) {
                        input.addLook(x - lastFireX, y - lastFireY);
                        lastFireX = x;
                        lastFireY = y;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                releasePointer(e.getPointerId(idx));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                movePointer = lookPointer = jumpPointer = firePointer = reloadPointer = scopePointer = crouchPointer = -1;
                input.reset();
                break;
        }
        invalidate();
        return true;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - baseX, dy = y - baseY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float r = joyRadius();
        if (len > r) {
            dx = dx / len * r;
            dy = dy / len * r;
        }
        curX = baseX + dx;
        curY = baseY + dy;
        input.moveX = dx / r;
        input.moveY = -dy / r;
    }

    private void releasePointer(int id) {
        if (id == movePointer) {
            movePointer = -1;
            input.moveX = 0f;
            input.moveY = 0f;
        } else if (id == lookPointer) {
            lookPointer = -1;
        } else if (id == jumpPointer) {
            jumpPointer = -1;
            input.jump = false;
        } else if (id == firePointer) {
            firePointer = -1;
            input.fire = false;
        } else if (id == reloadPointer) {
            reloadPointer = -1;
        } else if (id == scopePointer) {
            scopePointer = -1;
            if (!scopeToggle) input.scope = false;
        } else if (id == crouchPointer) {
            crouchPointer = -1;
            if (!crouchToggle) input.crouch = false;
        }
    }
}
