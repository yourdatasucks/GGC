package com.example.getgnomed

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.PermissionChecker
import android.content.ContentValues
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.content.res.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide system UI for immediate fullscreen
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        setContent {
            GnomeCameraApp()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // This will be called instead of recreating activity
        // if we add configChanges to manifest
    }
}

@Composable
fun GnomeCameraApp() {
    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }
    var isFlashActive by remember { mutableStateOf(false) }
    var showGnomeVideo by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var isRecordingReaction by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Permission launcher for multiple permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        isLoading = false // Stop loading when permissions are handled
    }

    // Check permissions on launch
    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ))
        } else {
            isLoading = false // Stop loading if permissions already granted
        }
    }

    // Flash animation effect
    LaunchedEffect(isFlashActive) {
        if (isFlashActive) {
            delay(200) // Flash duration
            isFlashActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Show black screen while loading
        if (isLoading) {
            // Just black background - no loading indicators
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        } else if (hasCameraPermission && hasAudioPermission) {
            // Camera Preview with Recording
            CameraPreviewWithRecording(
                isFrontCamera = isFrontCamera,
                isRecording = isRecordingReaction,
                onRecordingStarted = { isRecordingReaction = true },
                onRecordingStopped = { isRecordingReaction = false },
                modifier = Modifier.fillMaxSize()
            )

            // Gnome Video Overlay
            if (showGnomeVideo) {
                // white background to block camera preview
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    GnomeVideoOverlay(
                        modifier = Modifier.fillMaxSize(),
                        onVideoFinished = {
                            showGnomeVideo = false
                            // Stop recording when gnome video ends
                            isRecordingReaction = false
                        }
                    )
                }
            }

            // Flash overlay
            if (isFlashActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }

            // Camera Controls (hide during gnome video)
            if (!showGnomeVideo) {
                CameraControls(
                    onCaptureClick = {
                        isRecordingReaction = true
                        isFlashActive = true
                        // Start gnome reveal and reaction recording after short delay
                        coroutineScope.launch {
                            delay(10) // start video while flash is active
                            showGnomeVideo = true
                        }
                    },
                    onFlipCamera = { isFrontCamera = !isFrontCamera },
                    onGalleryClick = {
                        // Try to open with gallery apps (image/* usually works better for gallery apps)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_DEFAULT)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback: Try with broader media type
                            try {
                                val mediaIntent = Intent(Intent.ACTION_VIEW).apply {
                                    type = "*/*"
                                    addCategory(Intent.CATEGORY_DEFAULT)
                                    putExtra("android.intent.extra.MIME_TYPES", arrayOf("image/*", "video/*"))
                                }
                                context.startActivity(mediaIntent)
                            } catch (e2: Exception) {
                                Toast.makeText(context, "Look for 'gnome_reaction_' videos in your gallery app", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                )
            }
        } else {
            // Permission denied message
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera and microphone permissions are required",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        ))
                    }
                ) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Composable
fun CameraPreviewWithRecording(
    isFrontCamera: Boolean,
    isRecording: Boolean,
    onRecordingStarted: () -> Unit,
    onRecordingStopped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var recording: Recording? by remember { mutableStateOf(null) }

    // Handle recording state changes
    LaunchedEffect(isRecording) {
        if (isRecording && recording == null && videoCapture != null) {
            // Get actual display rotation when recording starts
            val activity = context as ComponentActivity
            val rotation = activity.display?.rotation ?: android.view.Surface.ROTATION_0

            // Debug: Show what rotation we detected
            val rotationName = when (rotation) {
                android.view.Surface.ROTATION_0 -> "Portrait (0°)"
                android.view.Surface.ROTATION_90 -> "Landscape (90°)"
                android.view.Surface.ROTATION_180 -> "Portrait Upside Down (180°)"
                android.view.Surface.ROTATION_270 -> "Landscape Reversed (270°)"
                else -> "Unknown ($rotation)"
            }

            videoCapture!!.targetRotation = rotation

            // Start recording - save to public Movies directory
            val fileName = "gnome_reaction_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GnomeReactions")
            }

            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            recording = videoCapture!!.output
                .prepareRecording(context, mediaStoreOutput)
                .apply { withAudioEnabled() }
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            onRecordingStarted()
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                Toast.makeText(context, "Reaction saved to Movies/GnomeReactions!", Toast.LENGTH_LONG).show()
                            }
                            recording = null
                            onRecordingStopped()
                        }
                    }
                }
        } else if (!isRecording && recording != null) {
            // Stop recording
            recording?.stop()
            recording = null
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(surfaceProvider)
                    }

                    // Set up video capture
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build()
                    val videoCaptureLocal = VideoCapture.withOutput(recorder)
                    videoCapture = videoCaptureLocal

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            videoCaptureLocal
                        )
                    } catch (exc: Exception) {
                        // Handle camera binding errors
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier
    )
}

@Composable
fun GnomeVideoOverlay(
    modifier: Modifier = Modifier,
    onVideoFinished: () -> Unit = {}
) {
    val context = LocalContext.current

    // Create ExoPlayer that survives configuration changes
    val exoPlayer = remember(key1 = "gnome_player") {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.gnomed_cut}")
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
            volume = 1.0f

            // Listen for when video finishes
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onVideoFinished()
                    }
                }
            })
        }
    }

    // Save and restore playback position across configuration changes
    var playbackPosition by rememberSaveable { mutableLongStateOf(0L) }
    var isPlayerReady by rememberSaveable { mutableStateOf(false) }

    // Update position periodically while playing
    LaunchedEffect(exoPlayer, isPlayerReady) {
        if (isPlayerReady) {
            while (exoPlayer.isPlaying) {
                playbackPosition = exoPlayer.currentPosition
                delay(100) // Update every 100ms
            }
        }
    }

    // Restore position after configuration change
    LaunchedEffect(exoPlayer, playbackPosition) {
        if (playbackPosition > 0L && !isPlayerReady) {
            exoPlayer.seekTo(playbackPosition)
            isPlayerReady = true
        } else if (playbackPosition == 0L) {
            isPlayerReady = true
        }
    }

    // Clean up player when overlay is dismissed
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Video player overlay
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setKeepContentOnPlayerReset(false)
                setBackgroundColor(android.graphics.Color.parseColor("#FFFAFAFA"))
            }
        },
        update = { playerView ->
            // Reassign player after configuration change
            playerView.player = exoPlayer
        },
        modifier = modifier
    )
}

@Composable
fun CameraControls(
    onCaptureClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flip Camera Button
        IconButton(
            onClick = onFlipCamera,
            modifier = Modifier
                .size(56.dp)
                .background(
                    Color.White.copy(alpha = 0.3f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Default.Cameraswitch,
                contentDescription = "Flip Camera",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Capture Button
        IconButton(
            onClick = onCaptureClick,
            modifier = Modifier
                .size(80.dp)
                .background(Color.White, CircleShape)
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = "Take Photo",
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }

        // Gallery Button
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    Color.White.copy(alpha = 0.3f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Default.Photo,
                contentDescription = "Gallery",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}