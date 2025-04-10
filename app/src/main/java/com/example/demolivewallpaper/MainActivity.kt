package com.example.demolivewallpaper // Package name đã đổi

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity // Use AppCompatActivity for better compatibility

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout defined in activity_main.xml
        setContentView(R.layout.activity_main)

        // Find the button in the layout by its ID
        val btnSetWallpaper: Button = findViewById(R.id.btnSetWallpaper)

        // Set a click listener on the button
        btnSetWallpaper.setOnClickListener {
            // Call the function to open the live wallpaper chooser
            openLiveWallpaperChooser()
        }
    }

    /**
     * Creates an Intent to open the system's Live Wallpaper chooser,
     * pre-selecting this application's wallpaper service.
     */
    private fun openLiveWallpaperChooser() {
        // Intent to specifically change the live wallpaper
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            // Add an extra to specify which live wallpaper component should be shown/selected
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(
                    applicationContext.packageName, // Get the current app's package name
                    OpenGLWallpaperService::class.java.name // Get the fully qualified name of our service class
                )
            )
            // Ensure the intent can be handled by external activities if needed
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            // Attempt to start the live wallpaper chooser activity
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Handle the (rare) case where the chooser activity doesn't exist
            Toast.makeText(
                this,
                "Không thể mở trình chọn Live Wallpaper.", // "Cannot open Live Wallpaper chooser."
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
