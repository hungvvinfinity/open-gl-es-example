package com.example.demolivewallpaper // Thay bằng package của bạn

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSetWallpaper: Button = findViewById(R.id.btnSetWallpaper)

        btnSetWallpaper.setOnClickListener {
            openLiveWallpaperChooser()
        }
    }

    private fun openLiveWallpaperChooser() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(
                    applicationContext.packageName,
                    OpenGLWallpaperService::class.java.name
                )
            )
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Không thể mở trình chọn Live Wallpaper.", Toast.LENGTH_SHORT).show()
        }
    }
}