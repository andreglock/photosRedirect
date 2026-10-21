package io.github.andreglock.photosredirect

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class RedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("target", "").orEmpty()
        val photo = intent.data
        val type = intent.type ?: photo?.let { contentResolver.getType(it) }

        val forward = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(photo, type)
            setPackage(target)
            // Pass along permission to read the photo
            photo?.let { clipData = ClipData.newRawUri(null, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            if (target.isBlank()) error("open Photo Redirect and choose an app first")
            startActivity(forward)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Couldn't redirect: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
        finish() // must finish right away with the NoDisplay theme
    }
}
