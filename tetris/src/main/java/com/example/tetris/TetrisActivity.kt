package com.example.tetris

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

class TetrisActivity : Activity() {
    private lateinit var game: TetrisView
    private lateinit var stats: TextView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var paused = false
    private var gameOver = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!paused && !gameOver) {
                game.tick()
                updateLabels()
            }
            handler.postDelayed(this, (650L - (game.level - 1) * 45L).coerceAtLeast(130L))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF101426.toInt()
        window.navigationBarColor = 0xFF101426.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(0xFF101426.toInt())
        }

        val title = TextView(this).apply {
            text = "BLOCK DROP"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-black", 1)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(42)))

        stats = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFE6E9FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(8))
        }
        root.addView(stats, LinearLayout.LayoutParams(-1, dp(38)))

        status = TextView(this).apply {
            text = "СОБЕРИ ЛИНИИ"
            textSize = 12f
            setTextColor(0xFF8E9BCA.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(24)))

        game = TetrisView(this) { score, lines, level, ended ->
            gameOver = ended
            updateLabels()
        }
        val gameParams = LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(0, dp(5), 0, dp(8))
        }
        root.addView(game, gameParams)

        val row1 = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        row1.addView(makeButton("◀", "Влево") { game.move(-1); updateLabels() }, buttonParams())
        row1.addView(makeButton("▼", "Вниз") { game.softDrop(); updateLabels() }, buttonParams())
        row1.addView(makeButton("▶", "Вправо") { game.move(1); updateLabels() }, buttonParams())
        root.addView(row1, LinearLayout.LayoutParams(-1, dp(62)))

        val row2 = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        row2.addView(makeButton("⟳", "Поворот") { game.rotate(); updateLabels() }, buttonParams(1.25f))
        row2.addView(makeButton("DROP", "Сбросить") { game.hardDrop(); updateLabels() }, buttonParams(1.25f))
        root.addView(row2, LinearLayout.LayoutParams(-1, dp(62)))

        val row3 = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        row3.addView(makeButton("Ⅱ  ПАУЗА", "Пауза") {
            if (!gameOver) {
                paused = !paused
                status.text = if (paused) "ПАУЗА" else "ИГРА ПРОДОЛЖАЕТСЯ"
            }
        }, buttonParams(1.4f))
        row3.addView(makeButton("↻  ЗАНОВО", "Заново") {
            paused = false
            gameOver = false
            game.reset()
            status.text = "СОБЕРИ ЛИНИИ"
            updateLabels()
        }, buttonParams(1.4f))
        root.addView(row3, LinearLayout.LayoutParams(-1, dp(58)))

        setContentView(root)
        updateLabels()
        handler.post(ticker)
    }

    private fun updateLabels() {
        if (!::game.isInitialized) return
        stats.text = "СЧЁТ  ${game.score}       ЛИНИИ  ${game.lines}       УРОВЕНЬ  ${game.level}"
        if (gameOver) status.text = "ИГРА ОКОНЧЕНА  •  НАЖМИ «ЗАНОВО»"
        else if (!paused && status.text.toString() != "СОБЕРИ ЛИНИИ") status.text = "СОБЕРИ ЛИНИИ"
    }

    private fun makeButton(label: String, description: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            contentDescription = description
            textSize = if (label.length > 3) 13f else 21f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF293451.toInt()))
            setOnClickListener { if (!gameOver || description == "Заново") action() }
        }
    }

    private fun buttonParams(weight: Float = 1f) = LinearLayout.LayoutParams(0, -1, weight).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (::game.isInitialized && !gameOver) {
            paused = false
        }
    }

    override fun onPause() {
        paused = true
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}

private class TetrisView(
    context: android.content.Context,
    private val onState: (Int, Int, Int, Boolean) -> Unit
) : View(context) {
    private val columns = 10
    private val rows = 20
    private val board = Array(rows) { IntArray(columns) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random.Default

    private val colors = intArrayOf(
        0xFF35D6E8.toInt(), 0xFF477BFF.toInt(), 0xFFFFA63D.toInt(),
        0xFFFFD447.toInt(), 0xFF54D68A.toInt(), 0xFFB36BFF.toInt(),
        0xFFFF5D83.toInt()
    )
    private val shapes = listOf(
        arrayOf(intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1), intArrayOf(3,1)),
        arrayOf(intArrayOf(0,0), intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1)),
        arrayOf(intArrayOf(2,0), intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1)),
        arrayOf(intArrayOf(0,0), intArrayOf(1,0), intArrayOf(0,1), intArrayOf(1,1)),
        arrayOf(intArrayOf(1,0), intArrayOf(2,0), intArrayOf(0,1), intArrayOf(1,1)),
        arrayOf(intArrayOf(1,0), intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1)),
        arrayOf(intArrayOf(0,0), intArrayOf(1,0), intArrayOf(1,1), intArrayOf(2,1))
    )

    private data class Piece(var cells: Array<IntArray>, var x: Int, var y: Int, val color: Int)
    private var current: Piece? = null
    var score = 0
        private set
    var lines = 0
        private set
    var level = 1
        private set
    private var ended = false

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        reset()
    }

    fun reset() {
        for (r in 0 until rows) board[r].fill(0)
        score = 0
        lines = 0
        level = 1
        ended = false
        spawn()
        invalidate()
        notifyState()
    }

    fun tick() {
        if (ended) return
        if (!moveDown()) lockPiece()
    }

    fun move(direction: Int) {
        val p = current ?: return
        if (!ended && !collides(p.cells, p.x + direction, p.y)) {
            p.x += direction
            invalidate()
        }
    }

    fun softDrop() {
        if (ended) return
        if (moveDown()) {
            score += 1
            notifyState()
        } else lockPiece()
    }

    fun hardDrop() {
        if (ended) return
        val p = current ?: return
        var distance = 0
        while (!collides(p.cells, p.x, p.y + 1)) {
            p.y++
            distance++
        }
        score += distance * 2
        lockPiece()
    }

    fun rotate() {
        val p = current ?: return
        if (ended) return
        val rotated = Array(p.cells.size) { i ->
            intArrayOf(3 - p.cells[i][1], p.cells[i][0])
        }
        val minX = rotated.minOf { it[0] }
        val minY = rotated.minOf { it[1] }
        rotated.forEach { it[0] -= minX; it[1] -= minY }
        val offsets = intArrayOf(0, -1, 1, -2, 2)
        for (offset in offsets) {
            if (!collides(rotated, p.x + offset, p.y)) {
                p.cells = rotated
                p.x += offset
                invalidate()
                return
            }
        }
    }

    private fun moveDown(): Boolean {
        val p = current ?: return false
        if (collides(p.cells, p.x, p.y + 1)) return false
        p.y++
        invalidate()
        return true
    }

    private fun collides(cells: Array<IntArray>, px: Int, py: Int): Boolean {
        for (cell in cells) {
            val x = px + cell[0]
            val y = py + cell[1]
            if (x !in 0 until columns || y >= rows) return true
            if (y >= 0 && board[y][x] != 0) return true
        }
        return false
    }

    private fun lockPiece() {
        val p = current ?: return
        for (cell in p.cells) {
            val x = p.x + cell[0]
            val y = p.y + cell[1]
            if (y in 0 until rows && x in 0 until columns) board[y][x] = p.color
        }
        clearLines()
        spawn()
        invalidate()
        notifyState()
    }

    private fun clearLines() {
        var cleared = 0
        var y = rows - 1
        while (y >= 0) {
            if (board[y].all { it != 0 }) {
                for (r in y downTo 1) board[r] = board[r - 1].clone()
                board[0] = IntArray(columns)
                cleared++
            } else y--
        }
        if (cleared > 0) {
            lines += cleared
            score += when (cleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            } * level
            level = lines / 10 + 1
        }
    }

    private fun spawn() {
        val index = random.nextInt(shapes.size)
        val piece = Piece(Array(shapes[index].size) { shapes[index][it].clone() }, 3, 0, colors[index])
        current = piece
        if (collides(piece.cells, piece.x, piece.y)) ended = true
    }

    private fun notifyState() {
        onState(score, lines, level, ended)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = minOf(width.toFloat() / columns, height.toFloat() / rows)
        if (cell <= 0f) return
        val boardW = cell * columns
        val boardH = cell * rows
        val left = (width - boardW) / 2f
        val top = (height - boardH) / 2f

        paint.color = 0xFF171F35.toInt()
        canvas.drawRoundRect(RectF(left - 5, top - 5, left + boardW + 5, top + boardH + 5), 18f, 18f, paint)

        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val x = left + c * cell
                val y = top + r * cell
                paint.color = 0xFF202A43.toInt()
                canvas.drawRoundRect(RectF(x + 1, y + 1, x + cell - 1, y + cell - 1), cell * 0.12f, cell * 0.12f, paint)
                val color = board[r][c]
                if (color != 0) drawBlock(canvas, x, y, cell, color)
            }
        }

        current?.let { p ->
            for (part in p.cells) {
                val x = left + (p.x + part[0]) * cell
                val y = top + (p.y + part[1]) * cell
                if (p.y + part[1] >= 0) drawBlock(canvas, x, y, cell, p.color)
            }
        }

        if (ended) {
            paint.color = 0xCC0C1222.toInt()
            canvas.drawRoundRect(RectF(left, top + boardH * 0.38f, left + boardW, top + boardH * 0.62f), 12f, 12f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = cell * 0.72f
            paint.typeface = android.graphics.Typeface.create("sans-serif-black", 1)
            canvas.drawText("GAME OVER", left + boardW / 2f, top + boardH * 0.515f, paint)
        }
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, cell: Float, color: Int) {
        val inset = cell * 0.075f
        paint.color = color
        canvas.drawRoundRect(RectF(x + inset, y + inset, x + cell - inset, y + cell - inset), cell * 0.17f, cell * 0.17f, paint)
        paint.color = 0x55FFFFFF
        canvas.drawRoundRect(RectF(x + inset * 1.7f, y + inset * 1.7f, x + cell - inset * 1.7f, y + cell * 0.30f), cell * 0.08f, cell * 0.08f, paint)
    }
}
