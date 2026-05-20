package com.smartisanos.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.smartisanos.music.ui.shell.LegacyPortMainShell
import com.smartisanos.music.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {
    private var playbackLaunchRequest by mutableIntStateOf(0)
    private var externalAudioLaunchRequestId by mutableIntStateOf(0)
    private var externalAudioLaunchRequest by mutableStateOf<ExternalAudioLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeLaunchIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            MusicTheme(darkTheme = false, dynamicColor = false) {
                RequestAudioPermissionOnLaunch()
                LegacyPortMainShell(
                    playbackLaunchRequest = playbackLaunchRequest,
                    externalAudioLaunchRequest = externalAudioLaunchRequest,
                    onExternalAudioLaunchConsumed = ::clearExternalAudioLaunchRequest,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        val launchIntent = intent ?: return
        if (isPlaybackLaunchIntent(launchIntent) && !isConsumedPlaybackLaunchIntent(launchIntent)) {
            playbackLaunchRequest += 1
            launchIntent.putExtra(ExtraOpenPlaybackConsumed, true)
            return
        }

        val externalAudioMimeType = launchIntent.resolveType(contentResolver)
        if (
            isExternalAudioLaunchIntent(launchIntent, externalAudioMimeType) &&
            !isConsumedExternalAudioLaunchIntent(launchIntent)
        ) {
            externalAudioLaunchRequestId += 1
            externalAudioLaunchRequest = ExternalAudioLaunchRequest(
                requestId = externalAudioLaunchRequestId,
                uri = requireNotNull(launchIntent.data),
                mimeType = externalAudioMimeType,
                displayName = resolveExternalAudioDisplayName(launchIntent.data),
            )
            launchIntent.putExtra(ExtraExternalAudioConsumed, true)
        }
    }

    private fun clearExternalAudioLaunchRequest(requestId: Int) {
        if (externalAudioLaunchRequest?.requestId == requestId) {
            externalAudioLaunchRequest = null
        }
    }

    private fun resolveExternalAudioDisplayName(uri: Uri?): String? {
        uri ?: return null
        if (uri.scheme == ContentScheme) {
            runCatching {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )
            }.getOrNull()?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) {
                        return cursor.getString(column)?.takeIf(String::isNotBlank)
                    }
                }
            }
        }
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
    }

    companion object {
        private const val ActionOpenPlayback = "com.smartisanos.music.action.OPEN_PLAYBACK"
        private const val ExtraOpenPlayback = "com.smartisanos.music.extra.OPEN_PLAYBACK"
        private const val ExtraOpenPlaybackConsumed = "com.smartisanos.music.extra.OPEN_PLAYBACK_CONSUMED"
        private const val ExtraExternalAudioConsumed = "com.smartisanos.music.extra.EXTERNAL_AUDIO_CONSUMED"
        private const val ContentScheme = "content"
        private const val FileScheme = "file"
        fun createOpenPlaybackIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = ActionOpenPlayback
                putExtra(ExtraOpenPlayback, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        private fun isPlaybackLaunchIntent(intent: Intent?): Boolean {
            return intent?.action == ActionOpenPlayback ||
                intent?.getBooleanExtra(ExtraOpenPlayback, false) == true
        }

        private fun isConsumedPlaybackLaunchIntent(intent: Intent?): Boolean {
            return intent?.getBooleanExtra(ExtraOpenPlaybackConsumed, false) == true
        }

        private fun isExternalAudioLaunchIntent(intent: Intent?, mimeType: String?): Boolean {
            val uri = intent?.data ?: return false
            val normalizedMimeType = mimeType?.lowercase() ?: return false
            return intent.action == Intent.ACTION_VIEW &&
                uri.scheme in setOf(ContentScheme, FileScheme) &&
                (
                    normalizedMimeType.startsWith("audio/") ||
                        normalizedMimeType == "application/ogg" ||
                        normalizedMimeType == "application/x-ogg" ||
                        normalizedMimeType == "application/itunes"
                    )
        }

        private fun isConsumedExternalAudioLaunchIntent(intent: Intent?): Boolean {
            return intent?.getBooleanExtra(ExtraExternalAudioConsumed, false) == true
        }
    }
}

@Composable
private fun RequestAudioPermissionOnLaunch() {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(permission) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        }
    }
}
