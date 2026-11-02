package com.example.screentranslator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_CAPTURE = 4102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 32)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }

        val title = TextView(this).apply {
            text = "Переводчик экрана"
            textSize = 26f
            setTextColor(0xFF172033.toInt())
        }
        val description = TextView(this).apply {
            text = "Переводит английский текст с экрана на русский и показывает результат поверх приложений. " +
                "Распознавание и перевод выполняются на устройстве. Для первого перевода потребуется интернет, " +
                "чтобы загрузить языковую модель."
            textSize = 16f
            setTextColor(0xFF48536A.toInt())
            setPadding(0, 22, 0, 28)
        }
        val start = Button(this).apply {
            text = "Запустить перевод"
            setOnClickListener { requestStart() }
        }
        val stop = Button(this).apply {
            text = "Остановить перевод"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                Toast.makeText(this@MainActivity, "Перевод остановлен", Toast.LENGTH_SHORT).show()
            }
        }
        val note = TextView(this).apply {
            text = "Важно: Android каждый раз показывает системный запрос на разрешение захвата экрана. " +
                "Это обязательная защита Android; приложение не может начать запись скрытно."
            textSize = 13f
            setTextColor(0xFF687386.toInt())
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(description)
        root.addView(start)
        root.addView(stop)
        root.addView(note)
        setContentView(root)
    }

    private fun requestStart() {
        if (!Settings.canDrawOverlays(this)) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(settingsIntent)
            Toast.makeText(
                this,
                "Разрешите показ поверх других приложений, вернитесь сюда и нажмите запуск ещё раз.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Kept for compatibility with the simple Activity result flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Захват экрана не разрешён", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось запустить службу: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
