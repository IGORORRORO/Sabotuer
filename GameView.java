package com.example.saboteur;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private GameThread thread;
    private boolean isPlaying;

    private static final int STATE_MAIN_MENU = 0;
    private static final int STATE_SUB_MENU = 1;
    private static final int STATE_SLOTS = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_WARNING = 5;
    private static final int STATE_SETTINGS = 6;
    private static final int STATE_INTRO = 7;
    private static final int STATE_OUTRO = 8;
    private static final int STATE_SLOT_CONFIRM = 9;
    private int gameState = STATE_MAIN_MENU;

    private Player player;
    private List<Enemy> enemies;
    private List<Dog> dogs;
    private List<Platform> platforms;
    private List<Barrel> barrels;
    private List<Ladder> ladders;
    private List<Particle> particles;
    private List<Heart> hearts;
    private Door door;
    private Key key;

    private boolean moveLeft = false, moveRight = false, moveUp = false, moveDown = false, isJumping = false, isAttacking = false;
    private boolean jumpKeyHeld = false;
    private boolean isNearLadder = false;
    private RectF btnLeft, btnRight, btnUp, btnDown, btnJump, btnAttack, btnPause;

    private int screenW, screenH;
    private int currentRoom = 1;
    private final int TOTAL_ROOMS = 50;
    private long gameSeed = 123456789L;

    private SharedPreferences sharedPreferences;
    private boolean isSavingGame = false;
    private boolean isDeletingGame = false;
    private int selectedSlot = -1;
    private String confirmMessage = "";
    private String currentMessage = "";
    private int messageTimer = 0;
    private long lastMenuClickTime = 0;
    private int screenShake = 0;

    private MediaPlayer menuMusic;
    private MediaPlayer bgMusic;
    private SoundPool soundPool;
    private Vibrator vibrator;
    private int soundSword, soundStep;
    private boolean soundLoaded = false;
    private int stepTimer = 0;

    private int introTimer = 0;
    private int outroTimer = 0;
    private boolean outroFinished = false;
    private boolean showWaitText = false;

    // --- ОПТИМИЗАЦИЯ: Переиспользуемые объекты для рисования ---
    private Paint pBg, pBrick, pBrickLine, pWall, pWallLine, pUI, pHpBg, pHpBorder, pHpFill, pShadow, pBtnBase, pBtnStroke, pBtnText, pTitleText, pSlotText;
    private Shader sBg, sHp;
    private RectF rTemp;
    private Path pathTemp;
    private SimpleDateFormat sdf;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        thread = new GameThread();
        setFocusable(true);
        sharedPreferences = context.getSharedPreferences("SaboteurSaves", Context.MODE_PRIVATE);
        particles = new ArrayList<>();

        setupAudio(context);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        initGraphics();
    }

    private void initGraphics() {
        pBg = new Paint(); pBg.setAntiAlias(false);
        sBg = new LinearGradient(0, 0, 0, screenH > 0 ? screenH : 1080, Color.rgb(15, 15, 25), Color.rgb(40, 40, 60), Shader.TileMode.CLAMP);
        pBg.setShader(sBg);

        pBrick = new Paint(); pBrick.setAntiAlias(false); pBrick.setColor(Color.rgb(50, 50, 60));
        pBrickLine = new Paint(); pBrickLine.setColor(Color.rgb(20, 20, 30));

        pWall = new Paint(); pWall.setColor(Color.rgb(40, 40, 50));
        pWallLine = new Paint(); pWallLine.setColor(Color.rgb(20, 20, 30));

        pUI = new Paint(); pUI.setAntiAlias(false); pUI.setTypeface(Typeface.MONOSPACE); pUI.setFakeBoldText(true);

        pHpBg = new Paint(); pHpBg.setColor(Color.rgb(50, 0, 0));
        pHpBorder = new Paint(); pHpBorder.setColor(Color.BLACK); pHpBorder.setStyle(Paint.Style.STROKE); pHpBorder.setStrokeWidth(4);
        pHpFill = new Paint();
        sHp = new LinearGradient(30, 30, 30, 80, Color.rgb(0, 255, 0), Color.rgb(0, 150, 0), Shader.TileMode.CLAMP);
        pHpFill.setShader(sHp);

        pShadow = new Paint(); pShadow.setColor(Color.BLACK); pShadow.setAntiAlias(true);

        pBtnBase = new Paint(); pBtnBase.setAntiAlias(true);
        pBtnStroke = new Paint(); pBtnStroke.setStyle(Paint.Style.STROKE); pBtnStroke.setStrokeWidth(4); pBtnStroke.setColor(Color.argb(200, 0, 0, 0));
        pBtnText = new Paint(); pBtnText.setColor(Color.WHITE); pBtnText.setTextSize(45); pBtnText.setTextAlign(Paint.Align.CENTER); pBtnText.setTypeface(Typeface.MONOSPACE); pBtnText.setFakeBoldText(true);

        pTitleText = new Paint(); pTitleText.setColor(Color.WHITE); pTitleText.setTextSize(50); pTitleText.setTypeface(Typeface.MONOSPACE); pTitleText.setTextAlign(Paint.Align.CENTER);
        pSlotText = new Paint(); pSlotText.setColor(Color.WHITE); pSlotText.setTextAlign(Paint.Align.CENTER); pSlotText.setTypeface(Typeface.MONOSPACE); pSlotText.setFakeBoldText(true);

        rTemp = new RectF();
        pathTemp = new Path();
        sdf = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault());
    }

    private void setupAudio(Context context) {
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(audioAttributes).build();

            soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                if (status == 0) soundLoaded = true;
            });

            int swordId = context.getResources().getIdentifier("sword", "raw", context.getPackageName());
            int stepId = context.getResources().getIdentifier("step", "raw", context.getPackageName());
            int menuId = context.getResources().getIdentifier("menu_music", "raw", context.getPackageName());
            int bgId = context.getResources().getIdentifier("bg_music", "raw", context.getPackageName());

            if (swordId != 0) soundSword = soundPool.load(context, swordId, 1);
            if (stepId != 0) soundStep = soundPool.load(context, stepId, 1);

            if (menuId != 0) {
                menuMusic = MediaPlayer.create(context, menuId);
                if (menuMusic != null) {
                    menuMusic.setLooping(true);
                    menuMusic.setVolume(0.5f, 0.5f);
                }
            }

            if (bgId != 0) {
                bgMusic = MediaPlayer.create(context, bgId);
                if (bgMusic != null) {
                    bgMusic.setLooping(true);
                    bgMusic.setVolume(0.6f, 0.6f);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playSound(int soundId) {
        if (sharedPreferences.getBoolean("settings_sound", true) && soundLoaded && soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long duration) {
        if (!sharedPreferences.getBoolean("settings_vibro", true)) return;
        if (vibrator == null || !vibrator.hasVibrator()) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void controlMusic() {
        boolean soundOn = sharedPreferences.getBoolean("settings_sound", true);

        if (menuMusic != null) {
            if (soundOn && isPlaying && (gameState == STATE_MAIN_MENU || gameState == STATE_SUB_MENU || gameState == STATE_SETTINGS || gameState == STATE_SLOTS || gameState == STATE_PAUSED || gameState == STATE_WARNING || gameState == STATE_SLOT_CONFIRM)) {
                if (!menuMusic.isPlaying()) menuMusic.start();
            } else {
                if (menuMusic.isPlaying()) menuMusic.pause();
            }
        }

        if (bgMusic != null) {
            if (soundOn && isPlaying && (gameState == STATE_PLAYING || gameState == STATE_INTRO || gameState == STATE_OUTRO)) {
                if (!bgMusic.isPlaying()) bgMusic.start();
            } else {
                if (bgMusic.isPlaying()) bgMusic.pause();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        screenW = width;
        screenH = height;
        setupUI();
        sBg = new LinearGradient(0, 0, 0, screenH, Color.rgb(15, 15, 25), Color.rgb(40, 40, 60), Shader.TileMode.CLAMP);
        pBg.setShader(sBg);
        sHp = new LinearGradient(30, 30, 30, 80, Color.rgb(0, 255, 0), Color.rgb(0, 150, 0), Shader.TileMode.CLAMP);
        pHpFill.setShader(sHp);

        if (player == null && gameState == STATE_PLAYING) initLevel(100, screenH - 430);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenW = getWidth();
        screenH = getHeight();
        setupUI();
        isPlaying = true;
        if (thread == null || !thread.isAlive()) {
            thread = new GameThread();
            thread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isPlaying = false;
        boolean retry = true;
        while (retry) {
            try {
                if (thread != null) thread.join();
                retry = false;
            } catch (InterruptedException e) { e.printStackTrace(); }
        }
        thread = null;
        if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
        if (menuMusic != null && menuMusic.isPlaying()) menuMusic.pause();
    }

    private void setupUI() {
        int bSize = 130;
        int bGap = 15;
        float cx = 280;
        float bottomRowY = screenH - bSize - 40;
        float topRowY = bottomRowY - bSize - bGap;

        btnUp = new RectF(cx - bSize/2, topRowY, cx + bSize/2, topRowY + bSize);
        btnDown = new RectF(cx - bSize/2, bottomRowY, cx + bSize/2, bottomRowY + bSize);
        btnLeft = new RectF(cx - bSize/2 - bSize - bGap, bottomRowY, cx - bSize/2 - bGap, bottomRowY + bSize);
        btnRight = new RectF(cx + bSize/2 + bGap, bottomRowY, cx + bSize/2 + bSize + bGap, bottomRowY + bSize);

        float rcx = screenW - 280;
        btnAttack = new RectF(rcx - bSize - bGap, bottomRowY, rcx - bGap, bottomRowY + bSize);
        btnJump = new RectF(rcx + bGap, bottomRowY, rcx + bGap + bSize, bottomRowY + bSize);

        btnPause = new RectF(screenW - 120, 30, screenW - 30, 120);
    }

    private void initLevel(float startX, float startY) {
        platforms = new ArrayList<>();
        enemies = new ArrayList<>();
        dogs = new ArrayList<>();
        barrels = new ArrayList<>();
        ladders = new ArrayList<>();
        hearts = new ArrayList<>();
        particles.clear();
        door = null;
        key = null;

        Random rnd = new Random(currentRoom * 1000L + gameSeed);
        List<RectF> existingRects = new ArrayList<>();

        platforms.add(new Platform(0, screenH - 250, screenW, 250, 0));
        existingRects.add(new RectF(0, screenH - 250, screenW, screenH));

        int barrelCount = 1 + rnd.nextInt(3);
        for (int i = 0; i < barrelCount; i++) placeBarrel(rnd, existingRects);

        int heartCount = rnd.nextInt(3); // 0, 1 or 2 hearts
        for (int i = 0; i < heartCount; i++) placeHeart(rnd, existingRects);

        int exitType = rnd.nextInt(3);
        if (currentRoom == TOTAL_ROOMS) exitType = 1;

        if (exitType == 0) {
            door = new Door(screenW - 150, screenH - 450);
            boolean keyPlaced = false;
            while (!keyPlaced) {
                float kx = 300 + rnd.nextInt(screenW - 600);
                float ky = screenH - 500 - rnd.nextInt(200);
                RectF keyRect = new RectF(kx, ky, kx + 60, ky + 100);
                boolean overlap = false;
                for (RectF r : existingRects) if (RectF.intersects(r, keyRect)) { overlap = true; break; }
                if (!overlap) { key = new Key(kx, ky); keyPlaced = true; }
            }
        } else if (exitType == 1) {
            placeLadder(rnd, existingRects);
        }

        int enemyCount = Math.min(1 + currentRoom / 5, 6);
        for (int i = 0; i < enemyCount; i++) placeEnemy(rnd, existingRects, currentRoom);

        if (currentRoom >= 2) {
            int dogCount = Math.min(1 + currentRoom / 10, 4);
            for (int i = 0; i < dogCount; i++) placeDog(rnd, existingRects, currentRoom);
        }

        if (player == null) player = new Player(startX, startY);
        else {
            player.x = startX; player.y = startY; player.vx = 0; player.vy = 0; player.targetVx = 0;
            player.invincibilityTimer = 30; player.hasHit = false; player.onLadder = false;
        }
    }

    private void placeLadder(Random rnd, List<RectF> existingRects) {
        boolean placed = false; int attempts = 0;
        while (!placed && attempts < 20) {
            float lw = 60;
            float lh = screenH - 300;
            float lx = 200 + rnd.nextInt(Math.max(1, screenW - 400));
            float ly = 50;
            rTemp.set(lx, ly, lx + lw, ly + lh);
            boolean overlap = false;
            for (RectF r : existingRects) { if (RectF.intersects(r, rTemp)) { overlap = true; break; } }
            if (!overlap) { ladders.add(new Ladder(lx, ly, lw, lh)); existingRects.add(new RectF(rTemp)); placed = true; }
            attempts++;
        }
        if (!placed) { ladders.add(new Ladder(400, 50, 60, screenH - 300)); }
    }

    private void placeBarrel(Random rnd, List<RectF> existingRects) {
        boolean placed = false; int attempts = 0;
        while (!placed && attempts < 20) {
            float bw = 100 + rnd.nextInt(60); float bh = 120 + rnd.nextInt(80);
            float bx = 100 + rnd.nextInt(Math.max(1, screenW - (int)bw - 200));
            float by = screenH - 250 - bh;
            rTemp.set(bx, by, bx + bw, by + bh);
            boolean overlap = false;
            for (RectF r : existingRects) { RectF pR = new RectF(rTemp); pR.inset(-50, 0); if (RectF.intersects(r, pR)) { overlap = true; break; } }
            if (!overlap) { barrels.add(new Barrel(bx, by, bw, bh)); existingRects.add(new RectF(bx, by, bx + bw, by + bh)); placed = true; }
            attempts++;
        }
    }

    private void placeHeart(Random rnd, List<RectF> existingRects) {
        boolean placed = false; int attempts = 0;
        while (!placed && attempts < 20) {
            float hw = 50; float hh = 50;
            float hx = 100 + rnd.nextInt(Math.max(1, screenW - (int)hw - 200));
            float hy = screenH - 350 - rnd.nextInt(200);
            rTemp.set(hx, hy, hx + hw, hy + hh);
            boolean overlap = false;
            for (RectF r : existingRects) { if (RectF.intersects(r, rTemp)) { overlap = true; break; } }
            if (!overlap) { hearts.add(new Heart(hx, hy)); existingRects.add(new RectF(rTemp)); placed = true; }
            attempts++;
        }
    }

    private void placeEnemy(Random rnd, List<RectF> existingRects, int room) {
        boolean placed = false; int attempts = 0;
        while (!placed && attempts < 20) {
            float ex = 600 + rnd.nextInt(Math.max(1, screenW - 800)); // Отодвинуты от спавна
            float ey = screenH - 430;
            rTemp.set(ex, ey, ex + 120, ey + 180);
            boolean overlap = false;
            for (RectF r : existingRects) { RectF pR = new RectF(rTemp); pR.inset(-50, 0); if (RectF.intersects(r, pR)) { overlap = true; break; } }
            if (!overlap) { enemies.add(new Enemy(ex, ey, room)); existingRects.add(new RectF(ex, ey, ex + 120, ey + 180)); placed = true; }
            attempts++;
        }
    }

    private void placeDog(Random rnd, List<RectF> existingRects, int room) {
        boolean placed = false; int attempts = 0;
        while (!placed && attempts < 20) {
            float dx = 600 + rnd.nextInt(Math.max(1, screenW - 800)); // Отодвинуты от спавна
            float dy = screenH - 350;
            rTemp.set(dx, dy, dx + 140, dy + 100);
            boolean overlap = false;
            for (RectF r : existingRects) { RectF pR = new RectF(rTemp); pR.inset(-50, 0); if (RectF.intersects(r, pR)) { overlap = true; break; } }
            if (!overlap) { dogs.add(new Dog(dx, dy, room)); existingRects.add(new RectF(dx, dy, dx + 140, dy + 100)); placed = true; }
            attempts++;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(); int pointerIndex = event.getActionIndex();
        float x = event.getX(pointerIndex); float y = event.getY(pointerIndex);

        if (gameState == STATE_PLAYING) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                float ix = event.getX(i); float iy = event.getY(i);
                if (btnLeft.contains(ix, iy)) moveLeft = true; else if (!isPointerInside(event, i, btnLeft)) moveLeft = false;
                if (btnRight.contains(ix, iy)) moveRight = true; else if (!isPointerInside(event, i, btnRight)) moveRight = false;
                if (isNearLadder) {
                    if (btnUp.contains(ix, iy)) moveUp = true; else if (!isPointerInside(event, i, btnUp)) moveUp = false;
                    if (btnDown.contains(ix, iy)) moveDown = true; else if (!isPointerInside(event, i, btnDown)) moveDown = false;
                } else { moveUp = false; moveDown = false; }
                if (btnJump.contains(ix, iy)) { if (!jumpKeyHeld) { isJumping = true; jumpKeyHeld = true; } }
                if (btnAttack.contains(ix, iy)) isAttacking = true;
            }
            if (btnPause.contains(x, y) && action == MotionEvent.ACTION_DOWN) {
                gameState = STATE_PAUSED; moveLeft = false; moveRight = false; moveUp = false; moveDown = false; isJumping = false; isAttacking = false; jumpKeyHeld = false;
                controlMusic();
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                if (btnLeft.contains(x, y)) moveLeft = false;
                if (btnRight.contains(x, y)) moveRight = false;
                if (btnUp.contains(x, y)) moveUp = false;
                if (btnDown.contains(x, y)) moveDown = false;
                if (btnJump.contains(x, y)) { isJumping = false; jumpKeyHeld = false; }
                if (btnAttack.contains(x, y)) isAttacking = false;
            }
        } else if (gameState == STATE_INTRO) {
            if (action == MotionEvent.ACTION_UP) introTimer = 500;
        } else if (gameState == STATE_OUTRO) {
            if (outroFinished && outroTimer > 300 && action == MotionEvent.ACTION_UP) {
                if (isInRect(x, y, screenW/2 - 150, screenH/2 + 50, 300, 120)) {
                    gameState = STATE_MAIN_MENU;
                    showWaitText = true;
                    player = null;
                    controlMusic();
                }
            }
        } else {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) handleMenuTouch(x, y);
        }
        return true;
    }

    private boolean isPointerInside(MotionEvent event, int excludeIndex, RectF btn) {
        for (int i = 0; i < event.getPointerCount(); i++) if (i != excludeIndex && btn.contains(event.getX(i), event.getY(i))) return true;
        return false;
    }

    private void saveSlot(int i) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("slot" + i + "_room", currentRoom); editor.putInt("slot" + i + "_hp", player.hp);
        editor.putFloat("slot" + i + "_px", player.x); editor.putFloat("slot" + i + "_py", player.y);
        editor.putLong("slot" + i + "_seed", gameSeed);
        editor.putLong("slot" + i + "_time", System.currentTimeMillis());
        editor.apply();
    }

    private void loadSlot(int i) {
        int savedRoom = sharedPreferences.getInt("slot" + i + "_room", 0);
        if (savedRoom > 0) {
            currentRoom = savedRoom; gameSeed = sharedPreferences.getLong("slot" + i + "_seed", 123456789L);
            player = new Player(100, screenH - 430); player.hp = sharedPreferences.getInt("slot" + i + "_hp", 200);
            float savedX = sharedPreferences.getFloat("slot" + i + "_px", 100); float savedY = sharedPreferences.getFloat("slot" + i + "_py", screenH - 430);
            if (savedX > screenW - 100) savedX = screenW - 100; // Фикс случайного перехода
            if (savedX < 100) savedX = 100;
            initLevel(savedX, savedY); gameState = STATE_PLAYING; controlMusic();
        }
    }

    private void handleMenuTouch(float x, float y) {
        if (System.currentTimeMillis() - lastMenuClickTime < 300) return;
        lastMenuClickTime = System.currentTimeMillis();

        if (gameState == STATE_MAIN_MENU) {
            if (isInRect(x, y, screenW/2 - 200, screenH/2 - 140, 400, 120)) { gameState = STATE_SUB_MENU; showWaitText = false; }
            else if (isInRect(x, y, screenW/2 - 200, screenH/2 + 20, 400, 120)) gameState = STATE_SETTINGS;
        }
        else if (gameState == STATE_SUB_MENU) {
            if (isInRect(x, y, screenW/2 - 200, screenH/2 - 180, 400, 120)) {
                player = null; currentRoom = 1; initLevel(100, screenH - 430);
                gameState = STATE_INTRO; introTimer = 0; controlMusic();
            }
            else if (isInRect(x, y, screenW/2 - 200, screenH/2 - 20, 400, 120)) { isSavingGame = false; isDeletingGame = false; gameState = STATE_SLOTS; }
            else if (isInRect(x, y, screenW/2 - 200, screenH/2 + 140, 400, 120)) gameState = STATE_MAIN_MENU;
        }
        else if (gameState == STATE_SETTINGS) {
            boolean soundOn = sharedPreferences.getBoolean("settings_sound", true);
            boolean vibroOn = sharedPreferences.getBoolean("settings_vibro", true);
            if (isInRect(x, y, screenW/2 - 200, screenH/2 - 180, 400, 120)) {
                sharedPreferences.edit().putBoolean("settings_sound", !soundOn).apply();
                controlMusic();
            }
            else if (isInRect(x, y, screenW/2 - 200, screenH/2 - 20, 400, 120)) sharedPreferences.edit().putBoolean("settings_vibro", !vibroOn).apply();
            else if (isInRect(x, y, screenW/2 - 200, screenH/2 + 140, 400, 120)) gameState = STATE_MAIN_MENU;
        }
        else if (gameState == STATE_SLOTS) {
            if (isInRect(x, y, screenW - 300, screenH - 150, 250, 100)) { isDeletingGame = !isDeletingGame; return; }
            if (isInRect(x, y, 50, screenH - 150, 200, 100)) { isDeletingGame = false; gameState = isSavingGame ? STATE_PAUSED : STATE_SUB_MENU; return; }
            for (int i = 0; i < 10; i++) {
                int row = i / 5; int col = i % 5; float sx = screenW/2 - 600 + col * 250; float sy = screenH/2 - 150 + row * 200;
                if (isInRect(x, y, sx, sy, 200, 150)) {
                    int savedRoom = sharedPreferences.getInt("slot" + i + "_room", 0);
                    if (isDeletingGame) {
                        if (savedRoom > 0) {
                            selectedSlot = i;
                            confirmMessage = "УДАЛИТЬ СЛОТ " + (i + 1) + "?";
                            gameState = STATE_SLOT_CONFIRM;
                        }
                    } else {
                        if (isSavingGame) {
                            if (savedRoom > 0) {
                                selectedSlot = i;
                                confirmMessage = "ЗАМЕНИТЬ СЛОТ " + (i + 1) + "?";
                                gameState = STATE_SLOT_CONFIRM;
                            } else {
                                saveSlot(i);
                                gameState = STATE_MAIN_MENU; controlMusic();
                            }
                        } else {
                            if (savedRoom > 0) loadSlot(i);
                        }
                    }
                    break;
                }
            }
        }
        else if (gameState == STATE_SLOT_CONFIRM) {
            if (isInRect(x, y, screenW/2 - 300, screenH/2 + 50, 250, 120)) {
                if (isDeletingGame) {
                    sharedPreferences.edit().remove("slot" + selectedSlot + "_room").apply();
                    sharedPreferences.edit().remove("slot" + selectedSlot + "_hp").apply();
                    sharedPreferences.edit().remove("slot" + selectedSlot + "_px").apply();
                    sharedPreferences.edit().remove("slot" + selectedSlot + "_py").apply();
                    sharedPreferences.edit().remove("slot" + selectedSlot + "_seed").apply();
                    sharedPreferences.edit().remove("slot" + selectedSlot + "_time").apply();
                    isDeletingGame = false;
                    gameState = STATE_SLOTS;
                } else if (isSavingGame) {
                    saveSlot(selectedSlot);
                    gameState = STATE_MAIN_MENU; controlMusic();
                }
            } else if (isInRect(x, y, screenW/2 + 50, screenH/2 + 50, 250, 120)) {
                gameState = STATE_SLOTS;
            }
        }
        else if (gameState == STATE_PAUSED) {
            if (isInRect(x, y, screenW/2 - 200, screenH/2 - 100, 400, 120)) { isSavingGame = true; isDeletingGame = false; gameState = STATE_SLOTS; controlMusic(); }
            else if (isInRect(x, y, screenW/2 - 200, screenH/2 + 50, 400, 120)) gameState = STATE_WARNING;
            else if (isInRect(x, y, 50, screenH - 150, 200, 100)) { gameState = STATE_PLAYING; controlMusic(); }
        }
        else if (gameState == STATE_WARNING) {
            if (isInRect(x, y, screenW/2 - 300, screenH/2 + 50, 250, 120)) { gameState = STATE_MAIN_MENU; controlMusic(); }
            else if (isInRect(x, y, screenW/2 + 50, screenH/2 + 50, 250, 120)) gameState = STATE_PAUSED;
        }
    }

    private boolean isInRect(float tx, float ty, float rx, float ry, float rw, float rh) { return tx >= rx && tx <= rx + rw && ty >= ry && ty <= ry + rh; }

    public void update() {
        if (gameState == STATE_INTRO) {
            introTimer++;
            if (introTimer > 500) { gameState = STATE_PLAYING; controlMusic(); }
            return;
        }

        if (gameState == STATE_OUTRO) {
            outroTimer++;
            if (outroTimer < 200) {
                player.y -= 2;
                if (player.y < 50) player.y = 50;
                player.onLadder = true;
            } else {
                player.onLadder = false;
                outroFinished = true;
            }
            return;
        }

        if (gameState != STATE_PLAYING) return;
        if (player == null) return;

        if (screenShake > 0) screenShake--;

        isNearLadder = false;
        for (Ladder l : ladders) {
            if (RectF.intersects(player.getBodyRect(), l.getRect())) {
                isNearLadder = true;
                break;
            }
        }

        if (isNearLadder) {
            if (moveUp && !player.onLadder) player.onLadder = true;
            if (moveDown && !player.onLadder) player.onLadder = true;
        }

        if (moveLeft) player.targetVx = -20; else if (moveRight) player.targetVx = 20; else player.targetVx = 0;

        if (isJumping && player.onGround && player.jumpCooldown <= 0) {
            player.vy = -42;
            player.onGround = false; player.jumpCooldown = 12; isJumping = false;
            spawnParticles(player.x + player.w/2, player.y + player.h, 5, Color.WHITE);
        }
        if (isAttacking) player.attack();

        player.update(platforms, barrels, door, ladders);

        if (player.onLadder) {
            boolean enemiesLeft = enemies.size() > 0 || dogs.size() > 0;
            if (enemiesLeft) {
                if (player.y < 50) { player.y = 50; currentMessage = "УБЕЙ ВСЕХ ВРАГОВ!"; messageTimer = 30; }
                if (player.y > screenH - 430) { player.y = screenH - 430; currentMessage = "УБЕЙ ВСЕХ ВРАГОВ!"; messageTimer = 30; }
            } else {
                if (player.y < 0) {
                    if (currentRoom == TOTAL_ROOMS) {
                        gameState = STATE_OUTRO;
                        outroFinished = false;
                        outroTimer = 0;
                        player.x = screenW / 2 - 60;
                        player.y = screenH - 430;
                        player.onLadder = true;
                        controlMusic();
                    } else {
                        currentRoom++;
                        player.onLadder = false;
                        initLevel(player.x, screenH - 430);
                    }
                    return;
                }
                if (moveDown && player.y > screenH - 430) {
                    if (currentRoom > 1) {
                        currentRoom--;
                        player.onLadder = false;
                        initLevel(player.x, -100);
                    } else { player.y = screenH - 430; }
                    return;
                }
            }
        }

        if (player.x > screenW - 40) {
            boolean enemiesLeft = enemies.size() > 0 || dogs.size() > 0;
            boolean doorClosed = (door != null && !door.isOpen);
            boolean isWall = (ladders.size() > 0);

            if (enemiesLeft) {
                player.x = screenW - 40;
                currentMessage = "УБЕЙ ВСЕХ ВРАГОВ!";
                messageTimer = 30;
            } else if (doorClosed) {
                player.x = screenW - 40;
                if (key != null) { currentMessage = "НЕТ КЛЮЧА!"; }
                else { currentMessage = "НАЙДИ КЛЮЧ!"; }
                messageTimer = 30;
            } else if (isWall) {
                player.x = screenW - 40;
            } else {
                currentRoom++;
                if (currentRoom > TOTAL_ROOMS) {
                    gameState = STATE_OUTRO;
                    outroTimer = 0;
                    outroFinished = false;
                    player.x = screenW / 2 - 60;
                    player.y = screenH - 430;
                    player.onLadder = true;
                    controlMusic();
                    return;
                }
                initLevel(100, screenH - 430);
                return;
            }
        }
        if (messageTimer > 0) messageTimer--;
        if (key != null && RectF.intersects(player.getBodyRect(), key.getRect())) { key = null; if (door != null) door.isOpen = true; }

        for (int i = 0; i < hearts.size(); i++) {
            Heart h = hearts.get(i);
            if (RectF.intersects(player.getBodyRect(), h.getRect())) {
                player.hp = Math.min(player.hp + 5, player.maxHp);
                hearts.remove(i);
                i--;
            }
        }

        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i); e.update(player, barrels);
            float dist = Math.abs((e.x + e.w / 2) - (player.x + player.w / 2));
            if (dist < (e.w / 2 + player.w / 2) + 20 && e.attackCooldown <= 0 && !player.onLadder) {
                player.takeDamage(5); e.attackCooldown = 45; screenShake = 10;
            }
            if (player.isAttacking() && !player.hasHit && RectF.intersects(player.getAttackRect(), e.getBodyRect())) {
                e.takeDamage(40, player.facingLeft ? -1 : 1); player.hasHit = true; spawnParticles(e.x + e.w/2, e.y + e.h/2, 10, Color.RED);
                if (e.hp <= 0) {
                    enemies.remove(i); i--;
                    player.hp = Math.min(player.hp + 5, player.maxHp); // ХП за врага
                }
            }
        }
        for (int i = 0; i < dogs.size(); i++) {
            Dog d = dogs.get(i); d.update(player, barrels);
            float dist = Math.abs((d.x + d.w / 2) - (player.x + player.w / 2));
            if (dist < (d.w / 2 + player.w / 2) + 20 && d.attackCooldown <= 0 && !player.onLadder) {
                player.takeDamage(8); d.attackCooldown = 45; screenShake = 15;
            }
            if (player.isAttacking() && !player.hasHit && RectF.intersects(player.getAttackRect(), d.getBodyRect())) {
                d.takeDamage(40, player.facingLeft ? -1 : 1); player.hasHit = true; spawnParticles(d.x + d.w/2, d.y + d.h/2, 10, Color.RED);
                if (d.hp <= 0) {
                    dogs.remove(i); i--;
                    player.hp = Math.min(player.hp + 3, player.maxHp); // ХП за собаку
                }
            }
        }

        for (int i = 0; i < particles.size(); i++) { particles.get(i).update(); if (particles.get(i).life <= 0) particles.remove(i); }
    }

    private void spawnParticles(float x, float y, int count, int color) {
        for(int i=0; i<count; i++) particles.add(new Particle(x, y, (float)(Math.random()*6-3), (float)(Math.random()*-8-2), color));
    }

    public void drawGame(Canvas canvas) {
        if (canvas == null) return;
        canvas.drawColor(Color.BLACK);
        canvas.save();
        if (screenShake > 0 && gameState == STATE_PLAYING) canvas.translate((float)(Math.random()*screenShake - screenShake/2), (float)(Math.random()*screenShake - screenShake/2));

        if (gameState == STATE_PLAYING || gameState == STATE_PAUSED || gameState == STATE_WARNING || gameState == STATE_INTRO) {
            drawGameWorld(canvas);
        } else if (gameState == STATE_OUTRO) {
            drawOutro(canvas);
        }
        canvas.restore();

        if (gameState == STATE_MAIN_MENU) drawMainMenu(canvas);
        else if (gameState == STATE_SUB_MENU) drawSubMenu(canvas);
        else if (gameState == STATE_SETTINGS) drawSettingsMenu(canvas);
        else if (gameState == STATE_SLOTS) drawSlotsMenu(canvas);
        else if (gameState == STATE_SLOT_CONFIRM) drawSlotConfirmMenu(canvas);
        else if (gameState == STATE_PAUSED) drawPauseMenu(canvas);
        else if (gameState == STATE_WARNING) drawWarningMenu(canvas);
        else if (gameState == STATE_INTRO) drawIntroText(canvas);
    }

    private void drawGameWorld(Canvas canvas) {
        pBg.setShader(sBg);
        canvas.drawRect(0, 0, screenW, screenH, pBg);

        int brickH = 60, brickW = 120;
        for (int y = 0; y < screenH - 250; y += brickH) {
            int offset = (y / brickH) % 2 == 0 ? 0 : brickW / 2;
            for (int x = -brickW; x < screenW; x += brickW) {
                canvas.drawRect(x + offset + 3, y + 3, x + offset + brickW - 3, y + brickH - 3, pBrick);
                canvas.drawRect(x + offset, y, x + offset + brickW, y + 2, pBrickLine);
                canvas.drawRect(x + offset + brickW - 2, y, x + offset + brickW, y + brickH, pBrickLine);
            }
        }

        for (Platform plat : platforms) plat.draw(canvas);
        for (Ladder l : ladders) l.draw(canvas);
        if (door != null) door.draw(canvas);
        if (key != null) key.draw(canvas);
        for (Heart h : hearts) h.draw(canvas);

        if (player != null) {
            float floorY = screenH - 250;
            drawDynamicShadow(canvas, player.x + player.w/2, player.y + player.h, player.w, floorY);
            for (Enemy e : enemies) drawDynamicShadow(canvas, e.x + e.w/2, e.y + e.h, e.w, floorY);
            for (Dog d : dogs) drawDynamicShadow(canvas, d.x + d.w/2, d.y + d.h, d.w, floorY);
            for (Barrel b : barrels) drawDynamicShadow(canvas, b.x + b.w/2, b.y + b.h, b.w, floorY);

            for (Enemy enemy : enemies) enemy.draw(canvas);
            for (Dog dog : dogs) dog.draw(canvas);

            if (gameState == STATE_INTRO) drawIntro(canvas);
            else player.draw(canvas);

            if (gameState == STATE_PLAYING || gameState == STATE_PAUSED) {
                for (Barrel barrel : barrels) barrel.draw(canvas);
            }
        }

        for (Particle p : particles) p.draw(canvas);

        if (ladders.size() > 0) {
            canvas.drawRect(screenW - 40, 0, screenW, screenH - 250, pWall);
            canvas.drawRect(screenW - 45, 0, screenW - 40, screenH - 250, pWallLine);
        }

        if (gameState == STATE_PLAYING || gameState == STATE_PAUSED) {
            canvas.drawRoundRect(30, 30, 630, 80, 10, 10, pHpBg);
            canvas.drawRoundRect(30, 30, 630, 80, 10, 10, pHpBorder);
            if (player.hp > 0) {
                canvas.drawRoundRect(30, 30, 30 + (player.hp * 3), 80, 10, 10, pHpFill);
            }
            pUI.setColor(Color.WHITE); pUI.setTextSize(40); pUI.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("HP: " + player.hp, 650, 75, pUI);

            if (messageTimer > 0) {
                pUI.setColor(Color.RED); pUI.setTextSize(60); pUI.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(currentMessage, screenW / 2, screenH / 2 - 100, pUI);
                pUI.setTextAlign(Paint.Align.LEFT);
            }

            drawModernButton(canvas, btnPause, "||", Color.rgb(80, 80, 80));

            if (gameState == STATE_PLAYING) {
                drawModernButton(canvas, btnLeft, "<", Color.rgb(40, 40, 60));
                drawModernButton(canvas, btnRight, ">", Color.rgb(40, 40, 60));
                if (isNearLadder) {
                    drawModernButton(canvas, btnUp, "^", Color.rgb(100, 100, 0));
                    drawModernButton(canvas, btnDown, "v", Color.rgb(100, 100, 0));
                }
                drawModernButton(canvas, btnJump, "JMP", Color.rgb(40, 80, 40));
                drawModernButton(canvas, btnAttack, "ATK", Color.rgb(80, 40, 40));
            }
        }
    }

    private void drawOutro(Canvas canvas) {
        // Закат фон
        sBg = new LinearGradient(0, 0, 0, screenH, Color.rgb(255, 69, 0), Color.rgb(255, 215, 0), Shader.TileMode.CLAMP);
        pBg.setShader(sBg);
        canvas.drawRect(0, 0, screenW, screenH, pBg);

        // Солнце
        pUI.setColor(Color.YELLOW);
        pUI.setAntiAlias(true);
        float sunY = screenH - 250 - (outroTimer * 2);
        if (sunY < 200) sunY = 200;
        canvas.drawCircle(screenW / 2, sunY, 150, pUI);
        pUI.setAntiAlias(false);

        // Земля
        pUI.setColor(Color.rgb(50, 30, 10));
        canvas.drawRect(0, screenH - 250, screenW, screenH, pUI);

        // Лестница и игрок лезут
        if (outroTimer < 200) {
            new Ladder(player.x + 20, 50, 60, screenH - 250 - 50).draw(canvas);
        }

        // Игрок
        pUI.setColor(Color.BLACK);
        canvas.drawRect(player.x + 35, player.y, player.x + 85, player.y + 40, pUI);
        canvas.drawRect(player.x + 20, player.y + 40, player.x + 100, player.y + 120, pUI);

        if (outroTimer < 200) {
            int legOffset = (outroTimer / 5) % 2 == 0 ? 10 : -10;
            canvas.drawRect(player.x + 20, player.y + 120, player.x + 60, player.y + 180 - legOffset, pUI);
            canvas.drawRect(player.x + 60, player.y + 120, player.x + 100, player.y + 180 + legOffset, pUI);
        } else {
            canvas.drawRect(player.x + 20, player.y + 120, player.x + 60, player.y + 180, pUI);
            canvas.drawRect(player.x + 60, player.y + 120, player.x + 100, player.y + 180, pUI);
            // Ладонь ко лбу
            canvas.drawRect(player.x + 60, player.y - 10, player.x + 90, player.y + 20, pUI);
        }

        pUI.setColor(Color.RED); canvas.drawRect(player.x + 20, player.y + 70, player.x + 100, player.y + 85, pUI);
        pUI.setColor(Color.WHITE); canvas.drawRect(player.x + 65, player.y + 15, player.x + 85, player.y + 25, pUI);

        if (outroFinished && outroTimer > 300) {
            pUI.setColor(Color.BLACK); pUI.setTextSize(80); pUI.setTextAlign(Paint.Align.CENTER); pUI.setFakeBoldText(true);
            canvas.drawText("ТЫ ПРОШЕЛ ИГРУ", screenW/2, screenH/2 - 50, pUI);
            pUI.setFakeBoldText(false);
            drawButton(canvas, screenW/2 - 150, screenH/2 + 50, 300, 120, "ВЫЙТИ", Color.rgb(150, 0, 0));
        }
    }

    private void drawIntro(Canvas canvas) {
        int t = introTimer;

        if (t < 150) {
            pUI.setColor(Color.rgb(100, 150, 255));
            canvas.drawRect(0, 0, screenW, screenH, pUI);

            pUI.setColor(Color.argb(200, 255, 255, 255)); pUI.setAntiAlias(true);
            canvas.drawCircle(200 + t, 200, 60, pUI);
            canvas.drawCircle(270 + t, 230, 70, pUI);
            canvas.drawCircle(140 + t, 250, 50, pUI);
            canvas.drawCircle(800 + t, 400, 60, pUI);
            canvas.drawCircle(870 + t, 430, 70, pUI);
            pUI.setAntiAlias(false);

            float planeX = -300 + t * 5;
            float planeY = screenH / 2 - 200;
            float tilt = 0;
            if (t > 100) {
                float diveProgress = (t - 100) / 50.0f;
                planeY = (screenH / 2 - 200) + diveProgress * (screenH - 350 - (screenH / 2 - 200));
                tilt = diveProgress * 30;
            }
            canvas.save();
            canvas.rotate(tilt, planeX, planeY);
            pUI.setColor(Color.rgb(255, 165, 0)); pUI.setAntiAlias(true);
            pathTemp.reset(); pathTemp.moveTo(planeX, planeY); pathTemp.lineTo(planeX - 240, planeY - 80); pathTemp.lineTo(planeX - 160, planeY); pathTemp.close();
            canvas.drawPath(pathTemp, pUI);
            pUI.setColor(Color.BLACK); pUI.setStyle(Paint.Style.STROKE); pUI.setStrokeWidth(4);
            canvas.drawPath(pathTemp, pUI);
            pUI.setStyle(Paint.Style.FILL);
            pUI.setColor(Color.rgb(255, 100, 0));
            pathTemp.reset(); pathTemp.moveTo(planeX, planeY); pathTemp.lineTo(planeX - 240, planeY + 80); pathTemp.lineTo(planeX - 160, planeY); pathTemp.close();
            canvas.drawPath(pathTemp, pUI);
            pUI.setColor(Color.BLACK); pUI.setStyle(Paint.Style.STROKE);
            canvas.drawPath(pathTemp, pUI);
            pUI.setStyle(Paint.Style.FILL);
            pUI.setColor(Color.rgb(255, 200, 0));
            canvas.drawRect(planeX - 240, planeY - 10, planeX - 200, planeY + 10, pUI);
            float pX = planeX - 280; float pY = planeY + 20;
            pUI.setAntiAlias(false);
            pUI.setColor(Color.BLACK);
            canvas.drawRect(pX + 60, pY, pX + 80, pY + 40, pUI);
            canvas.drawRect(pX + 100, pY, pX + 120, pY + 40, pUI);
            canvas.drawRect(pX + 65, pY + 40, pX + 115, pY + 80, pUI);
            canvas.drawRect(pX + 50, pY + 80, pX + 130, pY + 140, pUI);
            int legOffset = (t / 10) % 2 == 0 ? 15 : -15;
            canvas.drawRect(pX + 50, pY + 140, pX + 90, pY + 180 - legOffset, pUI);
            canvas.drawRect(pX + 90, pY + 140, pX + 130, pY + 180 + legOffset, pUI);
            pUI.setColor(Color.RED); canvas.drawRect(pX + 50, pY + 100, pX + 130, pY + 115, pUI);
            pUI.setColor(Color.WHITE); canvas.drawRect(pX + 95, pY + 50, pX + 115, pY + 60, pUI);
            canvas.restore();
        }

        if (t >= 150 && t < 250) {
            pUI.setColor(Color.rgb(100, 150, 255));
            canvas.drawRect(0, 0, screenW, screenH - 250, pUI);
            pUI.setColor(Color.rgb(80, 60, 40));
            canvas.drawRect(0, screenH - 250, screenW, screenH, pUI);
            float planeX = 300; float planeY = screenH - 350;
            pUI.setColor(Color.rgb(255, 165, 0)); pUI.setAntiAlias(true);
            pathTemp.reset(); pathTemp.moveTo(planeX, planeY); pathTemp.lineTo(planeX - 240, planeY - 80); pathTemp.lineTo(planeX - 160, planeY); pathTemp.close();
            canvas.drawPath(pathTemp, pUI);
            pUI.setColor(Color.BLACK); pUI.setStyle(Paint.Style.STROKE); pUI.setStrokeWidth(4);
            canvas.drawPath(pathTemp, pUI);
            pUI.setStyle(Paint.Style.FILL);
            pUI.setColor(Color.rgb(255, 100, 0));
            pathTemp.reset(); pathTemp.moveTo(planeX, planeY); pathTemp.lineTo(planeX - 240, planeY + 80); pathTemp.lineTo(planeX - 160, planeY); pathTemp.close();
            canvas.drawPath(pathTemp, pUI);
            pUI.setColor(Color.BLACK); pUI.setStyle(Paint.Style.STROKE);
            canvas.drawPath(pathTemp, pUI);
            pUI.setStyle(Paint.Style.FILL);
            pUI.setColor(Color.rgb(255, 200, 0));
            canvas.drawRect(planeX - 240, planeY - 10, planeX - 200, planeY + 10, pUI);
            float walkProgress = (t - 150) / 100.0f;
            float playerX = (300 - 280) + walkProgress * ((screenW / 2 - 60) - (300 - 280));
            if (playerX > screenW / 2 - 60) playerX = screenW / 2 - 60;
            float playerY = screenH - 430;
            pUI.setAntiAlias(false);
            pUI.setColor(Color.BLACK);
            int legOffset = (t / 5) % 2 == 0 ? 10 : -10;
            canvas.drawRect(playerX + 35, playerY, playerX + 85, playerY + 40, pUI);
            canvas.drawRect(playerX + 20, playerY + 40, playerX + 100, playerY + 120, pUI);
            canvas.drawRect(playerX + 20, playerY + 120, playerX + 60, playerY + 180 - legOffset, pUI);
            canvas.drawRect(playerX + 60, playerY + 120, playerX + 100, playerY + 180 + legOffset, pUI);
            pUI.setColor(Color.RED); canvas.drawRect(playerX + 20, playerY + 70, playerX + 100, playerY + 85, pUI);
            pUI.setColor(Color.WHITE); canvas.drawRect(playerX + 65, playerY + 15, playerX + 85, playerY + 25, pUI);
        }

        if (t >= 250) {
            pUI.setColor(Color.rgb(20, 20, 30));
            canvas.drawRect(0, 0, screenW, screenH, pUI);
            float playerX = screenW / 2 - 60;
            float playerY = 50 + (t - 250) * 4;
            if (playerY > screenH - 430) playerY = screenH - 430;
            new Ladder(playerX + 20, 50, 60, screenH - 250 - 50).draw(canvas);
            pUI.setColor(Color.BLACK);
            canvas.drawRect(playerX + 35, playerY, playerX + 85, playerY + 40, pUI);
            canvas.drawRect(playerX + 20, playerY + 40, playerX + 100, playerY + 120, pUI);
            int armOffset = ((t - 250) / 5) % 2 == 0 ? 10 : -10;
            canvas.drawRect(playerX + 10, playerY + 50 + armOffset, playerX + 30, playerY + 90 + armOffset, pUI);
            canvas.drawRect(playerX + 90, playerY + 50 - armOffset, playerX + 110, playerY + 90 - armOffset, pUI);
            canvas.drawRect(playerX + 30, playerY + 120, playerX + 55, playerY + 160 - armOffset, pUI);
            canvas.drawRect(playerX + 65, playerY + 120, playerX + 90, playerY + 160 + armOffset, pUI);
            pUI.setColor(Color.RED); canvas.drawRect(playerX + 20, playerY + 70, playerX + 100, playerY + 85, pUI);
            pUI.setColor(Color.WHITE); canvas.drawRect(playerX + 65, playerY + 15, playerX + 85, playerY + 25, pUI);
        }
    }

    private void drawIntroText(Canvas canvas) {
        pUI.setColor(Color.WHITE); pUI.setTextSize(40); pUI.setTextAlign(Paint.Align.CENTER); pUI.setAlpha(150);
        canvas.drawText("TAP TO SKIP", screenW / 2, screenH - 50, pUI);
        pUI.setAlpha(255);
    }

    private void drawDynamicShadow(Canvas canvas, float centerX, float feetY, float width, float floorY) {
        float distance = floorY - feetY; if (distance < 0) distance = 0;
        float scale = 1.0f - (distance / 600.0f); if (scale < 0.2f) scale = 0.2f;
        int alpha = (int)(120 * scale); if (alpha < 30) alpha = 30;
        pShadow.setAlpha(alpha);
        float sw = width * scale; float sh = 20 * scale;
        rTemp.set(centerX - sw/2, floorY - sh/2, centerX + sw/2, floorY + sh/2);
        canvas.drawOval(rTemp, pShadow);
    }

    private void drawModernButton(Canvas canvas, RectF rect, String text, int baseColor) {
        int lightR = Math.min(255, Color.red(baseColor) + 40); int lightG = Math.min(255, Color.green(baseColor) + 40); int lightB = Math.min(255, Color.blue(baseColor) + 40);
        int lightColor = Color.rgb(lightR, lightG, lightB);
        Shader shader = new LinearGradient(rect.left, rect.top, rect.left, rect.bottom, lightColor, baseColor, Shader.TileMode.CLAMP);
        pBtnBase.setShader(shader);
        canvas.drawRoundRect(rect, 20, 20, pBtnBase);
        pBtnStroke.setStyle(Paint.Style.STROKE);
        pBtnStroke.setColor(Color.argb(200, 0, 0, 0));
        canvas.drawRoundRect(rect, 20, 20, pBtnStroke);
        canvas.drawText(text, rect.centerX(), rect.centerY() + 15, pBtnText);
    }

    private void drawButton(Canvas canvas, float x, float y, float w, float h, String text, int color) {
        rTemp.set(x, y, x + w, y + h);
        int lightR = Math.min(255, Color.red(color) + 60); int lightG = Math.min(255, Color.green(color) + 60); int lightB = Math.min(255, Color.blue(color) + 60);
        int lightColor = Color.rgb(lightR, lightG, lightB);
        Shader shader = new LinearGradient(x, y, x, y + h, lightColor, color, Shader.TileMode.CLAMP);
        pBtnBase.setShader(shader);
        canvas.drawRoundRect(rTemp, 15, 15, pBtnBase);
        pBtnStroke.setStyle(Paint.Style.STROKE);
        pBtnStroke.setColor(Color.BLACK);
        canvas.drawRoundRect(rTemp, 15, 15, pBtnStroke);
        pBtnText.setTextSize(40);
        canvas.drawText(text, x + w/2, y + h/2 + 15, pBtnText);
    }

    private void drawMainMenu(Canvas canvas) {
        pUI.setColor(Color.rgb(10, 10, 20));
        pUI.setStyle(Paint.Style.FILL);
        canvas.drawRect(0,0,screenW,screenH, pUI);
        pUI.setColor(Color.RED); pUI.setTextSize(120); pUI.setTextAlign(Paint.Align.CENTER); pUI.setFakeBoldText(true);
        canvas.drawText("SABOTEUR", screenW/2, 300, pUI);
        pUI.setFakeBoldText(false);
        drawButton(canvas, screenW/2 - 200, screenH/2 - 140, 400, 120, "PLAY", Color.rgb(0, 150, 0));
        drawButton(canvas, screenW/2 - 200, screenH/2 + 20, 400, 120, "SETTINGS", Color.rgb(100, 100, 100));
        if (showWaitText) {
            pUI.setColor(Color.YELLOW); pUI.setTextSize(40);
            canvas.drawText("Ждите обновлений", screenW/2, screenH - 50, pUI);
        }
    }

    private void drawSubMenu(Canvas canvas) {
        drawButton(canvas, screenW/2 - 200, screenH/2 - 180, 400, 120, "NEW GAME", Color.rgb(0, 150, 0));
        drawButton(canvas, screenW/2 - 200, screenH/2 - 20, 400, 120, "SAVED GAMES", Color.rgb(150, 150, 0));
        drawButton(canvas, screenW/2 - 200, screenH/2 + 140, 400, 120, "BACK", Color.rgb(150, 0, 0));
    }

    private void drawSettingsMenu(Canvas canvas) {
        pUI.setColor(Color.rgb(10, 10, 20));
        pUI.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, screenW, screenH, pUI);
        pUI.setColor(Color.YELLOW); pUI.setTextSize(80); pUI.setTextAlign(Paint.Align.CENTER); pUI.setFakeBoldText(true);
        canvas.drawText("SETTINGS", screenW/2, 200, pUI);
        pUI.setFakeBoldText(false);
        boolean soundOn = sharedPreferences.getBoolean("settings_sound", true);
        boolean vibroOn = sharedPreferences.getBoolean("settings_vibro", true);
        drawButton(canvas, screenW/2 - 200, screenH/2 - 180, 400, 120, "SOUND: " + (soundOn ? "ON" : "OFF"), soundOn ? Color.rgb(0, 100, 0) : Color.rgb(100, 0, 0));
        drawButton(canvas, screenW/2 - 200, screenH/2 - 20, 400, 120, "VIBRO: " + (vibroOn ? "ON" : "OFF"), vibroOn ? Color.rgb(0, 100, 0) : Color.rgb(100, 0, 0));
        drawButton(canvas, screenW/2 - 200, screenH/2 + 140, 400, 120, "BACK", Color.rgb(150, 0, 0));
    }

    private void drawSlotsMenu(Canvas canvas) {
        pTitleText.setColor(Color.WHITE); pTitleText.setTextSize(50);
        canvas.drawText(isDeletingGame ? "TAP SLOT TO DELETE" : (isSavingGame ? "SELECT SLOT TO SAVE" : "SELECT SLOT TO LOAD"), screenW/2, 200, pTitleText);
        for (int i = 0; i < 10; i++) {
            int row = i / 5; int col = i % 5; float sx = screenW/2 - 600 + col * 250; float sy = screenH/2 - 150 + row * 200; float bw = 200; float bh = 150;
            int savedRoom = sharedPreferences.getInt("slot" + i + "_room", 0);
            int color = savedRoom > 0 ? Color.rgb(0, 100, 150) : Color.rgb(80, 80, 80);
            if (isDeletingGame && savedRoom > 0) color = Color.rgb(150, 0, 0);
            rTemp.set(sx, sy, sx + bw, sy + bh);
            int lightR = Math.min(255, Color.red(color) + 60); int lightG = Math.min(255, Color.green(color) + 60); int lightB = Math.min(255, Color.blue(color) + 60);
            int lightColor = Color.rgb(lightR, lightG, lightB);
            Shader shader = new LinearGradient(sx, sy, sx, sy + bh, lightColor, color, Shader.TileMode.CLAMP);
            pBtnBase.setShader(shader);
            canvas.drawRoundRect(rTemp, 15, 15, pBtnBase);
            pBtnStroke.setStyle(Paint.Style.STROKE);
            pBtnStroke.setColor(Color.BLACK);
            canvas.drawRoundRect(rTemp, 15, 15, pBtnStroke);
            pSlotText.setColor(Color.WHITE);
            pSlotText.setTextSize(35);
            canvas.drawText("SLOT " + (i+1), sx + bw/2, sy + 40, pSlotText);
            if (savedRoom > 0) {
                pSlotText.setTextSize(30);
                canvas.drawText("Room: " + savedRoom, sx + bw/2, sy + 80, pSlotText);
                long savedTime = sharedPreferences.getLong("slot" + i + "_time", 0);
                if (savedTime > 0) {
                    String timeStr = sdf.format(new Date(savedTime));
                    pSlotText.setTextSize(22);
                    canvas.drawText(timeStr, sx + bw/2, sy + 115, pSlotText);
                } else {
                    pSlotText.setTextSize(22);
                    pSlotText.setColor(Color.LTGRAY);
                    canvas.drawText("Дата неизвестна", sx + bw/2, sy + 115, pSlotText);
                }
            } else {
                pSlotText.setTextSize(30);
                pSlotText.setColor(Color.LTGRAY);
                canvas.drawText("ПУСТО", sx + bw/2, sy + 100, pSlotText);
            }
        }
        drawButton(canvas, 50, screenH - 150, 200, 100, "BACK", Color.rgb(150, 0, 0));
        drawButton(canvas, screenW - 300, screenH - 150, 250, 100, "DELETE", isDeletingGame ? Color.RED : Color.rgb(100, 0, 0));
    }

    private void drawSlotConfirmMenu(Canvas canvas) {
        drawSlotsMenu(canvas);
        pUI.setColor(Color.argb(200, 0, 0, 0));
        pUI.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, screenW, screenH, pUI);
        pUI.setColor(Color.YELLOW); pUI.setTextSize(50); pUI.setTextAlign(Paint.Align.CENTER); pUI.setFakeBoldText(true);
        canvas.drawText(confirmMessage, screenW/2, screenH/2 - 50, pUI);
        pUI.setFakeBoldText(false);
        drawButton(canvas, screenW/2 - 300, screenH/2 + 50, 250, 120, "ДА", Color.rgb(150, 0, 0));
        drawButton(canvas, screenW/2 + 50, screenH/2 + 50, 250, 120, "НЕТ", Color.rgb(0, 150, 0));
    }

    private void drawPauseMenu(Canvas canvas) {
        pUI.setColor(Color.argb(200, 0, 0, 0));
        pUI.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, screenW, screenH, pUI);
        pUI.setColor(Color.WHITE); pUI.setTextSize(80); pUI.setTextAlign(Paint.Align.CENTER); pUI.setFakeBoldText(true);
        canvas.drawText("PAUSED", screenW/2, 300, pUI);
        pUI.setFakeBoldText(false);
        drawButton(canvas, screenW/2 - 200, screenH/2 - 100, 400, 120, "SAVE & EXIT", Color.rgb(150, 150, 0));
        drawButton(canvas, screenW/2 - 200, screenH/2 + 50, 400, 120, "EXIT", Color.rgb(150, 0, 0));
        drawButton(canvas, 50, screenH - 150, 200, 100, "RESUME", Color.rgb(0, 150, 0));
    }

    private void drawWarningMenu(Canvas canvas) {
        pUI.setColor(Color.argb(220, 0, 0, 0));
        pUI.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, screenW, screenH, pUI);
        pUI.setColor(Color.RED); pUI.setTextSize(35); pUI.setTextAlign(Paint.Align.CENTER); pUI.setFakeBoldText(true);
        canvas.drawText("ВЫ УВЕРЕНЫ ЧТО ХОТИТЕ ВЫЙТИ?", screenW/2, screenH/2 - 50, pUI);
        canvas.drawText("ВЕСЬ ПРОГРЕСС СГОРИТ ЕСЛИ НЕ СОХРАНИТЬ", screenW/2, screenH/2, pUI);
        pUI.setFakeBoldText(false);
        drawButton(canvas, screenW/2 - 300, screenH/2 + 50, 250, 120, "ДА", Color.rgb(150, 0, 0));
        drawButton(canvas, screenW/2 + 50, screenH/2 + 50, 250, 120, "НЕТ", Color.rgb(0, 150, 0));
    }

    public void pause() {
        isPlaying = false;
        if (gameState == STATE_PLAYING) {
            gameState = STATE_PAUSED;
            moveLeft = false; moveRight = false; moveUp = false; moveDown = false; isJumping = false; isAttacking = false; jumpKeyHeld = false;
        }
        if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
        if (menuMusic != null && menuMusic.isPlaying()) menuMusic.pause();
        try { if (thread != null) thread.join(); } catch (InterruptedException e) { e.printStackTrace(); }
    }

    public void resume() {
        isPlaying = true;
        thread = new GameThread();
        thread.start();
        controlMusic();
    }

    class GameThread extends Thread {
        @Override
        public void run() {
            while (isPlaying) {
                Canvas canvas = null;
                try { canvas = getHolder().lockCanvas(); synchronized (getHolder()) { update(); drawGame(canvas); } }
                finally { if (canvas != null) getHolder().unlockCanvasAndPost(canvas); }
                try { Thread.sleep(16); } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }
    }

    // --- КЛАССЫ ИГРОВЫХ ОБЪЕКТОВ ---

    class Particle {
        float x, y, vx, vy; int life = 30; int color; int size = 10;
        Particle(float x, float y, float vx, float vy, int color) { this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = color; }
        void update() { x += vx; y += vy; vy += 0.5f; life--; }
        void draw(Canvas canvas) {
            pUI.setColor(color); pUI.setAlpha(life * 8);
            canvas.drawRect(x, y, x + size, y + size, pUI);
            pUI.setAlpha(255);
        }
    }

    class Player {
        float x, y, vx = 0, vy = 0, targetVx = 0; float w = 120, h = 180;
        int hp = 200, maxHp = 200, regenTimer = 0, attackTimer = 0, attackCooldown = 0, animTimer = 0, invincibilityTimer = 0, jumpCooldown = 0;
        boolean onGround = false, facingLeft = false, hasHit = false, onLadder = false;

        Player(float x, float y) { this.x = x; this.y = y; }

        void update(List<Platform> plats, List<Barrel> barrels, Door door, List<Ladder> ladders) {
            if (invincibilityTimer > 0) invincibilityTimer--;
            if (jumpCooldown > 0) jumpCooldown--;

            if (onLadder) {
                if (moveUp) { vy = -10; animTimer++; }
                else if (moveDown) { vy = 10; animTimer++; }
                else vy = 0;
                if (moveLeft || moveRight) onLadder = false;
                boolean stillOnLadder = false;
                for (Ladder l : ladders) {
                    if (RectF.intersects(getBodyRect(), l.getRect())) {
                        stillOnLadder = true;
                        x = l.x + l.w/2 - w/2;
                        break;
                    }
                }
                if (!stillOnLadder) onLadder = false;
            } else {
                if (vx < targetVx) { vx += 4.0f; if (vx > targetVx) vx = targetVx; } else if (vx > targetVx) { vx -= 4.0f; if (vx < targetVx) vx = targetVx; }
                if (!moveLeft && !moveRight && onGround && hp < maxHp) {
                    regenTimer++;
                    if (regenTimer > 15) { hp++; regenTimer = 0; }
                } else { regenTimer = 0; }
                vy += 2.5f; if (vy > 30) vy = 30;
                x += vx; if (vx < 0) facingLeft = true; else if (vx > 0) facingLeft = false;
                if (Math.abs(vx) > 0.5 && onGround) {
                    stepTimer++;
                    if (stepTimer > 8) { playSound(soundStep); stepTimer = 0; }
                } else { stepTimer = 6; }
            }

            if (door != null && !door.isOpen) if (RectF.intersects(getBodyRect(), door.getRect())) { if (vx > 0) { x = door.x - w; vx = 0; } else if (vx < 0) { x = door.x + door.w; vx = 0; } }

            y += vy; onGround = false;
            for (Platform p : plats) {
                if (RectF.intersects(getBodyRect(), p.getRect())) {
                    if (p.type == 0 || p.type == 2) {
                        if (vy > 0) { y = p.y - h; vy = 0; onGround = true; onLadder = false; }
                        else if (vy < 0) { y = p.y + p.h; vy = 0; }
                    }
                }
            }

            if (x < 0) x = 0;
            if (x > screenW - 40 && (enemies.size() > 0 || dogs.size() > 0 || (door != null && !door.isOpen) || ladders.size() > 0)) x = screenW - 40;
            if (attackTimer > 0) attackTimer--;
            if (attackCooldown > 0) attackCooldown--;
            if (Math.abs(vx) > 0.5 && onGround) animTimer++;
        }

        void attack() {
            if (attackCooldown <= 0) {
                attackTimer = 15; attackCooldown = 20; hasHit = false;
                playSound(soundSword);
            }
        }

        boolean isAttacking() { return attackTimer > 4 && attackTimer < 12; }

        void takeDamage(int dmg) {
            if (invincibilityTimer <= 0) {
                hp -= dmg; invincibilityTimer = 15; screenShake = 15;
                vibrate(200);
                if (hp <= 0) { hp = maxHp; currentRoom = 1; initLevel(100, screenH - 430); }
            }
        }

        RectF getBodyRect() { return new RectF(x, y, x + w, y + h); }
        RectF getAttackRect() { if (facingLeft) return new RectF(x - 100, y + 10, x, y + h); else return new RectF(x + w, y + 10, x + w + 100, y + h); }

        void draw(Canvas canvas) {
            if (invincibilityTimer > 0 && invincibilityTimer % 4 == 0) return;
            int legOffset = (animTimer / 3) % 2 == 0 ? 0 : 15;
            if (onLadder) { legOffset = (animTimer / 2) % 2 == 0 ? 10 : -10; }
            pUI.setColor(Color.BLACK);
            canvas.drawRect(x + 35, y, x + 85, y + 40, pUI); canvas.drawRect(x + 20, y + 40, x + 100, y + 120, pUI);
            canvas.drawRect(x + 20, y + 120, x + 60, y + 180 - legOffset, pUI); canvas.drawRect(x + 60, y + 120, x + 100, y + 180 + legOffset, pUI);
            pUI.setColor(Color.RED); canvas.drawRect(x + 20, y + 70, x + 100, y + 85, pUI);
            pUI.setColor(Color.YELLOW); canvas.drawRect(x + 30, y + 45, x + 40, y + 110, pUI);
            pUI.setColor(Color.WHITE); if (facingLeft) canvas.drawRect(x + 35, y + 15, x + 55, y + 25, pUI); else canvas.drawRect(x + 65, y + 15, x + 85, y + 25, pUI);
            if (attackTimer > 0) {
                pUI.setAntiAlias(false);
                if (attackTimer > 10) { pUI.setColor(Color.LTGRAY); if (facingLeft) canvas.drawRect(x + 20, y - 30, x + 40, y + 10, pUI); else canvas.drawRect(x + 80, y - 30, x + 100, y + 10, pUI); }
                else if (attackTimer > 4) { pUI.setColor(Color.WHITE); if (facingLeft) { pUI.setAlpha(100); canvas.drawRect(x - 80, y + 30, x + 20, y + 80, pUI); pUI.setAlpha(255); canvas.drawRect(x - 90, y + 40, x + 10, y + 70, pUI); } else { pUI.setAlpha(100); canvas.drawRect(x + 100, y + 30, x + 200, y + 80, pUI); pUI.setAlpha(255); canvas.drawRect(x + 110, y + 40, x + 210, y + 70, pUI); } }
                else { pUI.setColor(Color.LTGRAY); if (facingLeft) canvas.drawRect(x + 10, y + 100, x + 30, y + 140, pUI); else canvas.drawRect(x + 90, y + 100, x + 110, y + 140, pUI); }
            }
        }
    }

    abstract class Character {
        float x, y, vx, w, h; int attackCooldown = 0, animTimer = 0, hitFlashTimer = 0; boolean facingLeft = false;
        RectF getBodyRect() { return new RectF(x, y, x + w, y + h); }
        boolean canSeePlayer(Player pl, List<Barrel> barrels) {
            float pCenter = pl.x + pl.w / 2;
            float myCenter = x + w / 2;
            for (Barrel b : barrels) {
                if (RectF.intersects(b.getRect(), pl.getBodyRect())) return false;
                float bCenter = b.x + b.w / 2;
                if ((pCenter < bCenter && myCenter > bCenter) || (pCenter > bCenter && myCenter < bCenter)) return false;
            }
            return true;
        }

        void separateFromPlayer(Player pl) {
            if (RectF.intersects(getBodyRect(), pl.getBodyRect())) {
                if (pl.x + pl.w/2 < x + w/2) {
                    pl.x = x - pl.w - 1;
                    pl.vx = 0;
                    vx = 0; // Враг тоже останавливается
                } else {
                    pl.x = x + w + 1;
                    pl.vx = 0;
                    vx = 0;
                }
            }
        }
    }

    class Enemy extends Character {
        int speed, hp = 100;
        Enemy(float x, float y, int room) { this.x = x; this.y = y; this.w = 120; this.h = 180; this.speed = 9 + room / 6; }
        void update(Player pl, List<Barrel> barrels) { if (attackCooldown > 0) attackCooldown--; if (hitFlashTimer > 0) hitFlashTimer--; if (canSeePlayer(pl, barrels)) { if (pl.x < x) { vx = -speed; facingLeft = true; } else { vx = speed; facingLeft = false; } x += vx; animTimer++; } else vx = 0; separateFromPlayer(pl); if (x < 0) x = 0; if (x > screenW - w) x = screenW - w; }
        void takeDamage(int dmg, int knockback) { hp -= dmg; hitFlashTimer = 5; x += knockback * 8; if (x < 0) x = 0; if (x > screenW - w) x = screenW - w; vibrate(50); }
        void draw(Canvas canvas) {
            int legOffset = (animTimer / 3) % 2 == 0 ? 0 : 15;
            pUI.setColor(hitFlashTimer > 0 ? Color.WHITE : Color.rgb(150, 30, 30));
            canvas.drawRect(x + 35, y, x + 85, y + 40, pUI); canvas.drawRect(x + 20, y + 40, x + 100, y + 120, pUI);
            pUI.setColor(hitFlashTimer > 0 ? Color.WHITE : Color.rgb(50, 50, 50));
            canvas.drawRect(x + 20, y + 120, x + 60, y + 180 - legOffset, pUI); canvas.drawRect(x + 60, y + 120, x + 100, y + 180 + legOffset, pUI);
            pUI.setColor(Color.YELLOW); if (facingLeft) canvas.drawRect(x + 35, y + 15, x + 55, y + 25, pUI); else canvas.drawRect(x + 65, y + 15, x + 85, y + 25, pUI);
            pUI.setColor(Color.BLACK); if (facingLeft) canvas.drawRect(x - 20, y + 60, x + 10, y + 80, pUI); else canvas.drawRect(x + 110, y + 60, x + 140, y + 80, pUI);
        }
    }

    class Dog extends Character {
        int speed, hp = 50;
        Dog(float x, float y, int room) { this.x = x; this.y = y; this.w = 140; this.h = 100; this.speed = 15 + room / 3; }
        void update(Player pl, List<Barrel> barrels) { if (attackCooldown > 0) attackCooldown--; if (hitFlashTimer > 0) hitFlashTimer--; if (canSeePlayer(pl, barrels)) { if (pl.x < x) { vx = -speed; facingLeft = true; } else { vx = speed; facingLeft = false; } x += vx; animTimer++; } else vx = 0; separateFromPlayer(pl); if (x < 0) x = 0; if (x > screenW - w) x = screenW - w; }
        void takeDamage(int dmg, int knockback) { hp -= dmg; hitFlashTimer = 5; x += knockback * 8; if (x < 0) x = 0; if (x > screenW - w) x = screenW - w; vibrate(50); }
        void draw(Canvas canvas) {
            pUI.setColor(hitFlashTimer > 0 ? Color.WHITE : Color.rgb(100, 60, 30));
            canvas.drawRect(x, y + 30, x + 120, y + 100, pUI);
            if (facingLeft) {
                canvas.drawRect(x - 30, y + 10, x + 40, y + 80, pUI);
                pUI.setColor(hitFlashTimer > 0 ? Color.WHITE : Color.rgb(80, 40, 10));
                canvas.drawRect(x - 30, y, x - 10, y + 20, pUI); canvas.drawRect(x + 120, y + 40, x + 140, y + 60, pUI);
                pUI.setColor(Color.RED); canvas.drawRect(x - 30, y + 50, x - 10, y + 70, pUI);
                int legOff = (animTimer / 2) % 2 == 0 ? 0 : 15; pUI.setColor(hitFlashTimer > 0 ? Color.WHITE : Color.rgb(100, 60, 30));
                canvas.drawRect(x + 10, y + 100, x + 40, y + 140 - legOff, pUI); canvas.drawRect(x + 70, y + 100, x + 100, y + 140 + legOff, pUI);
            } else {
                canvas.drawRect(x + 80, y + 10, x + 150, y + 80, pUI);
                pUI.setColor(hitFlashTimer > 0 ? Color.WHITE : Color.rgb(80, 40, 10));
                canvas.drawRect(x + 130, y, x + 150, y + 20, pUI); canvas.drawRect(x - 20, y + 40, x, y + 60, pUI);
                pUI.setColor(Color.RED); canvas.drawRect(x + 130, y + 50, x + 150, y + 70, pUI);
                int legOff = (animTimer / 2) % 2 == 0 ? 0 : 15; pUI.setColor(hitFlashTimer > 0 ? Color.WHITE : Color.rgb(100, 60, 30));
                canvas.drawRect(x + 10, y + 100, x + 40, y + 140 - legOff, pUI); canvas.drawRect(x + 70, y + 100, x + 100, y + 140 + legOff, pUI);
            }
        }
    }

    class Heart {
        float x, y, w = 50, h = 50;
        Heart(float x, float y) { this.x = x; this.y = y; }
        RectF getRect() { return new RectF(x, y, x + w, y + h); }
        void draw(Canvas canvas) {
            pUI.setColor(Color.RED);
            pUI.setAntiAlias(true);
            canvas.drawCircle(x + 15, y + 15, 15, pUI);
            canvas.drawCircle(x + 35, y + 15, 15, pUI);
            pathTemp.reset();
            pathTemp.moveTo(x + 5, y + 20);
            pathTemp.lineTo(x + 25, y + 45);
            pathTemp.lineTo(x + 45, y + 20);
            pathTemp.close();
            canvas.drawPath(pathTemp, pUI);
            pUI.setAntiAlias(false);
        }
    }

    class Barrel {
        float x, y, w, h;
        Barrel(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        RectF getRect() { return new RectF(x, y, x + w, y + h); }
        void draw(Canvas canvas) {
            Shader sh = new LinearGradient(x, y, x + w, y, Color.rgb(220, 160, 0), Color.rgb(150, 100, 0), Shader.TileMode.CLAMP);
            pBtnBase.setShader(sh);
            canvas.drawRect(x, y, x + w, y + h, pBtnBase);
            pUI.setColor(Color.rgb(100, 60, 0)); canvas.drawRect(x, y + h/4, x + w, y + h/4 + 12, pUI); canvas.drawRect(x, y + h/2, x + w, y + h/2 + 12, pUI);
            pBtnStroke.setStyle(Paint.Style.STROKE); pBtnStroke.setColor(Color.BLACK); pBtnStroke.setStrokeWidth(4);
            canvas.drawRect(x, y, x + w, y + h, pBtnStroke);
        }
    }

    class Ladder {
        float x, y, w, h;
        Ladder(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        RectF getRect() { return new RectF(x, y, x + w, y + h); }
        void draw(Canvas canvas) {
            pUI.setColor(Color.rgb(150, 100, 50));
            canvas.drawRect(x, y, x + 10, y + h, pUI);
            canvas.drawRect(x + w - 10, y, x + w, y + h, pUI);
            for(float i = y + 10; i < y + h; i += 30) canvas.drawRect(x, i, x + w, i + 10, pUI);
        }
    }

    class Platform {
        float x, y, w, h; int type;
        Platform(float x, float y, float w, float h, int type) { this.x = x; this.y = y; this.w = w; this.h = h; this.type = type; }
        RectF getRect() { return new RectF(x, y, x + w, y + h); }
        void draw(Canvas canvas) {
            if (type == 0) { pUI.setColor(Color.rgb(30, 30, 40)); canvas.drawRect(x, y, x + w, y + h, pUI); pUI.setColor(Color.rgb(50, 50, 60)); canvas.drawRect(x, y, x + w, y + 10, pUI); }
            else if (type == 2) { pUI.setColor(Color.rgb(100, 70, 30)); canvas.drawRect(x, y, x + w, y + h, pUI); pUI.setColor(Color.rgb(150, 110, 50)); canvas.drawRect(x, y, x + w, y + 10, pUI); }
        }
    }

    class Door {
        float x, y, w = 100, h = 200; boolean isOpen = false;
        Door(float x, float y) { this.x = x; this.y = y; }
        RectF getRect() { return new RectF(x, y, x + w, y + h); }
        void draw(Canvas canvas) {
            if (!isOpen) { pUI.setColor(Color.rgb(80, 40, 0)); canvas.drawRect(x, y, x + w, y + h, pUI); pUI.setColor(Color.rgb(120, 60, 0)); canvas.drawRect(x+5, y+5, x+w-5, y+h-5, pUI); pUI.setColor(Color.YELLOW); canvas.drawCircle(x + w - 20, y + h/2, 10, pUI); }
            else { pUI.setColor(Color.rgb(80, 40, 0)); canvas.drawRect(x, y, x + 20, y + h, pUI); canvas.drawRect(x + w - 20, y, x + w, y + h, pUI); }
        }
    }

    class Key {
        float x, y, w = 60, h = 100;
        Key(float x, float y) { this.x = x; this.y = y; }
        RectF getRect() { return new RectF(x, y, x + w, y + h); }
        void draw(Canvas canvas) {
            pUI.setColor(Color.YELLOW);
            canvas.drawCircle(x + 30, y + 20, 20, pUI); canvas.drawRect(x + 20, y + 40, x + 40, y + 80, pUI); canvas.drawRect(x + 40, y + 60, x + 50, y + 70, pUI); canvas.drawRect(x + 40, y + 70, x + 55, y + 80, pUI);
        }
    }
}