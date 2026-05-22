package com.spop.poverlay

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.spop.poverlay.releases.ReleaseChecker
import com.spop.poverlay.ui.theme.PTONOverlayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ConfigurationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel =
            ConfigurationViewModel(
                application, ConfigurationRepository(applicationContext, this),
                ReleaseChecker()
            )
        viewModel.finishActivity.observe(this) {
            finish()
        }
        viewModel.requestOverlayPermission.observe(this) {
            requestScreenPermission()
        }
        viewModel.requestHeartRatePermissions.observe(this) {
            heartRatePermissionRequest.launch(
                com.spop.poverlay.sensor.hr.HeartRatePermissions.requiredPermissions()
            )
        }
        viewModel.requestGpxImport.observe(this) {
            launchGpxImport()
        }
        viewModel.requestRestart.observe(this) {
            restartGrupetto()
        }
        viewModel.infoPopup.observe(this) {
            Toast.makeText(
                this,
                it,
                Toast.LENGTH_LONG
            ).show()
        }
        setContent {
            PTONOverlayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConfigurationPage(
                        viewModel
                    )
                }
            }
        }
        lifecycleScope.launchWhenResumed {
            viewModel.onResume()
        }
    }

    private fun restartGrupetto() {
        Toast.makeText(
            this@MainActivity,
            HtmlCompat.fromHtml("<big>Restarting Switchback</big>", HtmlCompat.FROM_HTML_MODE_LEGACY),
            Toast.LENGTH_LONG
        )
            .apply { setGravity(Gravity.CENTER, 0, 0) }
            .show()

        CoroutineScope(Dispatchers.IO).launch {
            delay(1500L)
            val pm: PackageManager = applicationContext.packageManager
            val intent = pm.getLaunchIntentForPackage(applicationContext.packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
            applicationContext.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    private val overlayPermissionRequest =
        registerForActivityResult(StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= 23) {
                viewModel.onOverlayPermissionRequestCompleted(
                    Settings.canDrawOverlays(this)
                )
            }
        }

    private val heartRatePermissionRequest =
        registerForActivityResult(RequestMultiplePermissions()) { grants ->
            viewModel.onHeartRatePermissionsRequestCompleted(grants.values.all { it })
        }

    private val gpxImportRequest =
        registerForActivityResult(OpenDocument()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }
            importGpx(uri)
        }

    private fun launchGpxImport() {
        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        if (pickerIntent.resolveActivity(packageManager) == null) {
            viewModel.importGpxRoutesFromDropFolder()
            return
        }
        runCatching {
            gpxImportRequest.launch(arrayOf("application/gpx+xml", "text/xml", "application/xml", "*/*"))
        }.onFailure {
            viewModel.importGpxRoutesFromDropFolder()
        }
    }

    private fun importGpx(uri: Uri) {
        runCatching {
            val fileName = displayName(uri)
                ?.substringBeforeLast(".")
                ?.takeIf { it.isNotBlank() }
                ?: "Imported Route"
            val gpxText = contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Could not read selected GPX file")
            viewModel.importGpxRoute(fileName, gpxText)
        }.onFailure {
            Toast.makeText(
                this,
                "Could not read selected GPX file",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun displayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null &&
                cursor.moveToFirst()
            ) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    cursor.getString(index)
                } else {
                    null
                }
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    private fun requestScreenPermission() = Intent(
        "android.settings.action.MANAGE_OVERLAY_PERMISSION",
        Uri.parse("package:${packageName}")
    ).apply {
        overlayPermissionRequest.launch(this)
    }
}
