package io.github.andreglock.photosredirect

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        val photoAccess = Button(this).apply {
            text = "1. Allow photo access"
            setOnClickListener {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                    ),
                    1,
                )
            }
        }
        val accessibility = Button(this).apply {
            text = "2. Turn on the redirect service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val label = TextView(this).apply {
            text = "3. Package name of the app photos should open in:"
        }
        input = EditText(this).apply {
            hint = "com.example.gallery"
            isSingleLine = true
            setText(prefs.getString("target", ""))
        }
        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.edit().putString("target", input.text.toString().trim()).apply()
                updateStatus()
            }
        }
        status = TextView(this).apply { setPadding(0, 32, 0, 0) }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 200, 48, 48)
                listOf(photoAccess, accessibility, label, input, save, status).forEach(::addView)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onRequestPermissionsResult(
        code: Int,
        perms: Array<out String>,
        results: IntArray,
    ) {
        super.onRequestPermissionsResult(code, perms, results)
        updateStatus()
    }

    private fun updateStatus() {
        val target = input.text.toString().trim()
        val photosOk = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED
        val serviceOn = Settings.Secure
            .getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("$packageName/") == true
        val targetOk = canOpenPhotos(target)

        status.text = buildString {
            appendLine(
                if (photosOk) "✓ Photo access allowed"
                else "✗ Photo access needed (choose \"Allow all\")",
            )
            appendLine(if (serviceOn) "✓ Redirect service on" else "✗ Redirect service off")
            append(
                if (targetOk) "✓ Photos will open in $target"
                else "✗ Enter an installed app that can open photos",
            )
        }
    }

    private fun canOpenPhotos(pkg: String): Boolean {
        if (pkg.isEmpty() || pkg == packageName) return false
        val probe = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://media/external/images/media/0"), "image/*")
            .setPackage(pkg)
        return packageManager.queryIntentActivities(probe, 0).isNotEmpty()
    }
}
