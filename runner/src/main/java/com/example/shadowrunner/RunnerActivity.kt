package com.example.shadowrunner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.min

class RunnerActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF101827.toInt()
        window.navigationBarColor = 0xFF101827.toInt()
        setContentView(RunnerView(this))
    }
}

private class RunnerView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var last = 0L
    private var world = 0f
    private var speed = 360f
    private var score = 0
    private var jumping = false
    private var sliding = false
    private var jumpTime = 0f
    private var slideTime = 0f
    private var dead = false
    private var started = false
    private val obstacles = mutableListOf<Obstacle>()
    private val coins = mutableListOf<Float>()
    private data class Obstacle(var x: Float, val type: Int, var passed: Boolean = false)

    private val loop = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceIn(0f, 0.04f)
            last = now
            if (started && !dead) update(dt)
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    init {
        isFocusable = true
        reset()
        handler.post(loop)
    }

    private fun reset() {
        world = 0f
        speed = 360f
        score = 0
        jumping = false
        sliding = false
        jumpTime = 0f
        slideTime = 0f
        dead = false
        started = false
        obstacles.clear()
        coins.clear()
        for (i in 0..5) obstacles.add(Obstacle(850f + i * 620f, if (i % 2 == 0) 0 else 1))
        for (i in 0..8) coins.add(560f + i * 390f)
    }

    private fun update(dt: Float) {
        world += speed * dt
        speed = min(650f, speed + 5f * dt)
        score = (world / 12f).toInt()
        if (jumping) {
            jumpTime += dt
            if (jumpTime >= 0.72f) jumping = false
        }
        if (sliding) {
            slideTime += dt
            if (slideTime >= 0.62f) sliding = false
        }
        obstacles.forEach { o ->
            val x = o.x - world
            if (!o.passed && x < width * 0.25f) {
                o.passed = true
                val playerY = groundY()
                val safe = if (o.type == 0) jumping else sliding
                if (!safe && x > width * 0.25f - 100f) dead = true
            }
        }
        if (obstacles.lastOrNull()?.x?.minus(world) ?: 0f < width + 300f) {
            val lastX = obstacles.lastOrNull()?.x ?: world
            obstacles.add(Obstacle(lastX + 560f + (score % 3) * 90f, if (obstacles.size % 2 == 0) 0 else 1))
        }
        obstacles.removeAll { it.x - world < -150f }
        coins.removeAll { it - world < -50f }
        if (coins.lastOrNull()?.minus(world) ?: 0f < width + 200f) {
            coins.add((coins.lastOrNull() ?: world) + 360f)
        }
    }

    private fun groundY() = height * 0.78f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        p.color = 0xFF101827.toInt()
        canvas.drawColor(p.color)
        p.color = 0xFF18283B.toInt()
        canvas.drawCircle(w * 0.78f, h * 0.20f, h * 0.13f, p)
        p.color = 0xFF22364B.toInt()
        for (i in 0..7) {
            val x = (i * 190f - (world * 0.22f) % 190f)
            val hill = Path().apply {
                moveTo(x - 100f, groundY())
                lineTo(x + 30f, h * (0.34f + (i % 3) * 0.035f))
                lineTo(x + 170f, groundY())
                close()
            }
            canvas.drawPath(hill, p)
        }
        p.color = 0xFF34465A.toInt()
        canvas.drawRect(0f, groundY(), w, h, p)
        p.color = 0xFF6F8498.toInt()
        for (i in 0..18) {
            val x = (i * 95f - world % 95f)
            canvas.drawRect(x, groundY() + 12f, x + 42f, groundY() + 17f, p)
        }
        // Runner silhouette
        drawRunner(canvas, w * 0.25f, groundY())
        // Obstacles
        obstacles.forEach { o ->
            val x = o.x - world
            if (o.type == 0) {
                p.color = 0xFFFF765E.toInt()
                canvas.drawRoundRect(RectF(x, groundY() - 64f, x + 42f, groundY()), 7f, 7f, p)
                p.color = 0xFFFFC0A8.toInt()
                canvas.drawRect(x + 8f, groundY() - 55f, x + 15f, groundY() - 10f, p)
            } else {
                p.color = 0xFF68D7D0.toInt()
                canvas.drawRoundRect(RectF(x, groundY() - 108f, x + 94f, groundY() - 82f), 9f, 9f, p)
                canvas.drawRect(x + 10f, groundY() - 82f, x + 22f, groundY() - 55f, p)
                canvas.drawRect(x + 72f, groundY() - 82f, x + 84f, groundY() - 55f, p)
            }
        }
        coins.forEach { cx ->
            val x = cx - world
            p.color = 0xFFFFD166.toInt()
            canvas.drawCircle(x, groundY() - 85f, 11f, p)
            p.color = 0xFFFFF0B3.toInt()
            canvas.drawCircle(x, groundY() - 85f, 5f, p)
        }
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 25f
        p.typeface = android.graphics.Typeface.create("sans-serif-black", 1)
        canvas.drawText("SHADOW RUN", 28f, 42f, p)
        p.textSize = 18f
        canvas.drawText("DISTANCE  $score", 30f, 72f, p)
        drawControls(canvas, w, h)
        if (!started || dead) {
            p.color = 0xCC0A1220.toInt()
            canvas.drawRoundRect(RectF(w * 0.28f, h * 0.22f, w * 0.72f, h * 0.62f), 22f, 22f, p)
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.textSize = 29f
            canvas.drawText(if (dead) "RUN ENDED" else "READY TO RUN?", w / 2f, h * 0.36f, p)
            p.textSize = 17f
            canvas.drawText(if (dead) "Tap to try again" else "Tap to start • left: jump • right: slide", w / 2f, h * 0.47f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawRunner(canvas: Canvas, x: Float, floor: Float) {
        val lift = if (jumping) 110f * kotlin.math.sin((jumpTime / 0.72f) * Math.PI).toFloat() else 0f
        val crouch = if (sliding) 28f else 0f
        val y = floor - lift
        p.color = 0xFF080D16.toInt()
        p.strokeWidth = 13f
        p.strokeCap = Paint.Cap.ROUND
        if (sliding) {
            canvas.drawLine(x - 22f, y - 28f, x + 45f, y - 20f, p)
            canvas.drawCircle(x + 48f, y - 34f, 16f, p)
            canvas.drawLine(x - 8f, y - 22f, x - 48f, y - 4f, p)
            canvas.drawLine(x + 12f, y - 18f, x + 50f, y - 2f, p)
        } else {
            canvas.drawCircle(x, y - 72f, 17f, p)
            canvas.drawLine(x, y - 53f, x + 5f, y - 15f, p)
            canvas.drawLine(x + 2f, y - 40f, x - 24f, y - 20f, p)
            canvas.drawLine(x + 2f, y - 40f, x + 27f, y - 26f, p)
            val phase = if (jumping) 0.3f else (world / 55f)
            canvas.drawLine(x + 5f, y - 15f, x - 18f + 14f * kotlin.math.sin(phase), y, p)
            canvas.drawLine(x + 5f, y - 15f, x + 26f - 14f * kotlin.math.sin(phase), y, p)
        }
    }

    private fun drawControls(canvas: Canvas, w: Float, h: Float) {
        p.color = 0x553F5C78
        canvas.drawRoundRect(RectF(20f, h - 62f, w / 2f - 10f, h - 15f), 18f, 18f, p)
        canvas.drawRoundRect(RectF(w / 2f + 10f, h - 62f, w - 20f, h - 15f), 18f, 18f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = 16f
        canvas.drawText("JUMP  ↑", w * 0.25f, h - 33f, p)
        canvas.drawText("SLIDE  ↓", w * 0.75f, h - 33f, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!started || dead) {
                if (dead) reset()
                started = true
                last = System.currentTimeMillis()
            } else if (event.x < width / 2f) {
                if (!jumping) {
                    jumping = true
                    jumpTime = 0f
                    sliding = false
                }
            } else {
                sliding = true
                slideTime = 0f
                jumping = false
            }
            invalidate()
            return true
        }
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(loop)
        super.onDetachedFromWindow()
    }
}
