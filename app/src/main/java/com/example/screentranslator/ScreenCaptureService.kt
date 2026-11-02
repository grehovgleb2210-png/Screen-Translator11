package com.example.screentranslator

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val ACTION_STOP = "com.example.screentranslator.STOP"
        private const val CHANNEL_ID = "screen_translation"
        private const val NOTIFICATION_ID = 71
        private const val FRAME_INTERVAL_MS = 1300L
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null
    private var translator: Translator? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)

    @Volatile private var modelReady = false
    @Volatile private var serviceStopped = false
    private var lastTranslatedSourceText = ""
    private var lastFrameTime = 0L
    private var screenWidth = 0
    private var screenHeight = 0
    private var densityDpi = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = getProjectionIntent(intent)
        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startCapture(resultCode, data)
        } catch (e: Exception) {
            showText("Не удалось начать захват экрана: ${e.localizedMessage ?: "ошибка"}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun getProjectionIntent(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_DATA)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels.coerceAtLeast(1)
        screenHeight = metrics.heightPixels.coerceAtLeast(1)
        densityDpi = metrics.densityDpi

        worker = HandlerThread("screen-translator-worker").also { it.start() }
        handler = Handler(worker!!.looper)

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection недоступен")

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenTranslatorDisplay",
            screenWidth,
            screenHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        ) ?: throw IllegalStateException("Не удалось создать виртуальный экран")

        setupOverlay()
        setupTranslator()

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameTime < FRAME_INTERVAL_MS || !busy.compareAndSet(false, true)) {
                val skipped = reader.acquireLatestImage()
                skipped?.close()
                return@setOnImageAvailableListener
            }
            lastFrameTime = now
            val image = reader.acquireLatestImage()
            if (image == null) {
                busy.set(false)
                return@setOnImageAvailableListener
            }
            val bitmap = try {
                imageToBitmap(image)
            } catch (_: Exception) {
                null
            } finally {
                image.close()
            }
            if (bitmap == null) {
                busy.set(false)
            } else {
                recognize(bitmap)
            }
        }, handler)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        val paddedWidth = screenWidth + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, screenWidth, screenHeight)
        padded.recycle()
        return cropped
    }

    private fun setupTranslator() {
        translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage("en")
                .setTargetLanguage("ru")
                .build()
        )
        showText("Загрузка модели перевода… Первый запуск требует интернета.")
        translator?.downloadModelIfNeeded()
            ?.addOnSuccessListener {
                modelReady = true
                showText("Переводчик готов. Наведите на экран английский текст.")
            }
            ?.addOnFailureListener {
                modelReady = false
                showText("Не удалось загрузить модель. Проверьте интернет и перезапустите перевод.")
            }
    }

    private fun recognize(bitmap: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                bitmap.recycle()
                // Keep likely English subtitles; ignore other screen text and our Russian overlay.
                val text = result.textBlocks
                    .filter { block ->
                        val bounds = block.boundingBox ?: return@filter false
                        val centerY = bounds.centerY().toFloat() / screenHeight
                        val centerX = bounds.centerX().toFloat() / screenWidth
                        val raw = block.text.trim()
                        val latinLetters = raw.count { it in 'A'..'Z' || it in 'a'..'z' }
                        val allLetters = raw.count { it.isLetter() }
                        centerY in 0.55f..0.90f &&
                            centerX in 0.08f..0.92f &&
                            latinLetters >= 4 &&
                            allLetters > 0 &&
                            latinLetters.toFloat() / allLetters >= 0.65f &&
                            raw.length <= 240
                    }
                    .sortedBy { it.boundingBox?.top ?: 0 }
                    .joinToString(" ") { it.text.replace("\\n", " ").trim() }
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (text.length < 4 || text == lastTranslatedSourceText) {
                    busy.set(false)
                    return@addOnSuccessListener
                }
                if (!modelReady) {
                    showText("Распознан английский текст. Ожидание модели перевода…")
                    busy.set(false)
                    return@addOnSuccessListener
                }
                translator?.translate(text)
                    ?.addOnSuccessListener { translated ->
                        if (translated.isNotBlank()) {
                            lastTranslatedSourceText = text
                            showText(translated)
                        }
                        busy.set(false)
                    }
                    ?.addOnFailureListener {
                        showText("Ошибка перевода. Попробуйте ещё раз.")
                        busy.set(false)
                    } ?: run {
                    showText("Переводчик недоступен. Перезапустите службу.")
                    busy.set(false)
                }
            }
            .addOnFailureListener {
                bitmap.recycle()
                busy.set(false)
            }
    }

    private fun setupOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            showText("Нет разрешения на показ поверх окон.")
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = TextView(this).apply {
            text = "Запуск переводчика…"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xE8172033.toInt())
            setPadding(22, 16, 22, 16)
            maxLines = 7
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = (24 * resources.displayMetrics.density).toInt()
        }
        try {
            windowManager?.addView(overlay, params)
        } catch (e: Exception) {
            overlay = null
            throw e
        }
    }

    private fun showText(text: String) {
        mainHandler.post {
            if (!serviceStopped) overlay?.text = text
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Перевод экрана",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Переводчик экрана работает")
            .setContentText("Английский → русский")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Остановить",
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        serviceStopped = true
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        translator?.close()
        translator = null
        recognizer.close()
        overlay?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlay = null
        worker?.quitSafely()
        worker = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        super.onDestroy()
    }
}
