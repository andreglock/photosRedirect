package io.github.andreglock.photosredirect

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PhotosWatcherService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage: String? = null
    private var lastRedirectAt = 0L
    private data class Launch(val target: String, val uri: Uri, val mime: String)
    private var pending: Launch? = null
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in IGNORED || pkg == packageName) return

        Log.d(TAG, "Foreground: $pkg")
        val cameFromCamera = lastPackage == CAMERA
        lastPackage = pkg

        // Only act when Photos opens straight from the camera,
        // not when you open Photos yourself from the home screen.
        if (pkg != PHOTOS || !cameFromCamera) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectAt < 2000) return
        lastRedirectAt = now
        redirect()
    }

    private fun redirect() {
        val target = getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("target", "").orEmpty()
        if (target.isBlank()) {
            Log.w(TAG, "No target app saved")
            return
        }
        val (uri, mime) = newestMedia() ?: run {
            Log.w(TAG, "No photo found. Is photo access allowed?")
            return
        }

        // Close Photos (returns to the camera), then open your app on top
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                setPackage(target)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
                Log.i(TAG, "Opened $uri in $target")
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't open in $target", e)
            }
        }, 250)
    }

    /** Newest image or video on the device, with its MIME type. */
    private fun newestMedia(): Pair<Uri, String>? = runCatching {
        val image = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val video = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.MediaColumns.MIME_TYPE,
            ),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            arrayOf(image.toString(), video.toString()),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.Files.FileColumns._ID} DESC",
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val isVideo = c.getInt(1) == video
            val base = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val mime = c.getString(2) ?: if (isVideo) "video/*" else "image/*"
            ContentUris.withAppendedId(base, c.getLong(0)) to mime
        }
    }.onFailure { Log.e(TAG, "Media query failed", it) }.getOrNull()

    override fun onInterrupt() {}

    companion object {
        const val TAG = "PhotoRedirect"
        const val CAMERA = "com.google.android.GoogleCamera"
        const val PHOTOS = "com.google.android.apps.photos"

        // Windows that pop up without meaning you switched apps
        val IGNORED = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
        )
    }
}
