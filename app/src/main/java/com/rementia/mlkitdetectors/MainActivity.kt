package com.rementia.mlkitdetectors

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Bitmap
import android.widget.Toast
import com.rementia.mlkitdetectors.ml.FrameSimilarityGate
import com.rementia.mlkitdetectors.ml.YuvToRgbConverter
 

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DetectorScreen()
                }
            }
        }
    }
}

@Composable
fun DetectorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Requesting camera permission…") }
        return
    }

    val previewView = remember { PreviewView(context) }

    // UI state for simple accuracy/debug visualization
    var detected by rememberSaveable { mutableStateOf(false) }
    var facesCount by rememberSaveable { mutableStateOf(0) }
    var fps by rememberSaveable { mutableStateOf(0.0) }
    var lastLatencyMs by rememberSaveable { mutableStateOf(0L) }
    var gateSim by rememberSaveable { mutableStateOf<Float?>(null) }
    var gateAccepted by rememberSaveable { mutableStateOf<Boolean?>(null) }

    // Connection gate (demo): run ML only when not connected, like main app
    var isConnected by rememberSaveable { mutableStateOf(false) }
    val frameGate = remember { FrameSimilarityGate(context) }
    val lastAcceptedFrameRef = remember { AtomicReference<Bitmap?>(null) }
    val yuvConverter = remember { YuvToRgbConverter() }

    // Pose state (shared with overlay)
    var poseDetected by rememberSaveable { mutableStateOf(false) } // raw (per frame)
    var poseLandmarks by rememberSaveable { mutableStateOf(0) }
    var poseStable by rememberSaveable { mutableStateOf(false) }    // debounced/stable
    var poseValidCount by rememberSaveable { mutableStateOf(0) }
    var poseInvalidCount by rememberSaveable { mutableStateOf(0) }
    // Only consecutive-frame debouncing; keep ML Kit defaults
    val POSE_CONSECUTE_VALID = 3
    val POSE_CONSECUTE_INVALID = 3

    // Wake removed in demo

    LaunchedEffect(hasPermission, isConnected) {
        if (!hasPermission || isConnected) return@LaunchedEffect
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // ML Kit Face detector (fast mode, tracking on) + Pose detector
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
        val detector = FaceDetection.getClient(options)
        val poseDetector = com.google.mlkit.vision.pose.PoseDetection.getClient(
            com.google.mlkit.vision.pose.defaults.PoseDetectorOptions.Builder()
                .setDetectorMode(com.google.mlkit.vision.pose.defaults.PoseDetectorOptions.STREAM_MODE)
                .build()
        )

        val executor = Executors.newSingleThreadExecutor()

        val frameCounter = AtomicLong(0)
        val startNs = System.nanoTime()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { ia ->
                ia.setAnalyzer(executor) { image: ImageProxy ->
                    val began = System.nanoTime()
                    try {
                        val media = image.image
                        if (media != null) {
                            val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
                            val faceTask = detector.process(input)
                                .addOnSuccessListener { faces ->
                                    val cnt = faces.size
                                    facesCount = cnt
                                    detected = cnt > 0
                                }
                                .addOnFailureListener { detected = false }

                            val poseTask = poseDetector.process(input)
                                .addOnSuccessListener { pose ->
                                    val all = pose.allPoseLandmarks
                                    poseLandmarks = all.size
                                    val isValid = all.isNotEmpty()
                                    poseDetected = isValid

                                    // Debounce/hysteresis to achieve stability
                                    if (isValid) {
                                        poseValidCount += 1
                                        poseInvalidCount = 0
                                        if (poseValidCount >= POSE_CONSECUTE_VALID) poseStable = true
                                    } else {
                                        poseInvalidCount += 1
                                        poseValidCount = 0
                                        if (poseInvalidCount >= POSE_CONSECUTE_INVALID) poseStable = false
                                    }
                                }
                                .addOnFailureListener {
                                    poseDetected = false
                                    poseInvalidCount += 1
                                    poseValidCount = 0
                                    if (poseInvalidCount >= POSE_CONSECUTE_INVALID) poseStable = false
                                }

                            com.google.android.gms.tasks.Tasks.whenAllComplete(faceTask, poseTask)
                                .addOnCompleteListener {
                                    try {
                                        if ((detected || poseStable) && image.image != null) {
                                            val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                                            try { yuvConverter.convert(image.image!!, bmp); lastAcceptedFrameRef.set(bmp) } catch (_: Throwable) {}
                                        }
                                    } finally {
                                        lastLatencyMs = (System.nanoTime() - began) / 1_000_000
                                        val count = frameCounter.incrementAndGet()
                                        val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
                                        if (elapsedSec > 0) fps = count / elapsedSec
                                        image.close()
                                    }
                                }
                        } else {
                            image.close()
                        }
                    } catch (t: Throwable) {
                        image.close()
                    }
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        } catch (_: Exception) {}
    }

    val statusColor = when {
        isConnected -> Color(0xFF22C55E)
        detected && facesCount > 0 -> Color(0xFF22C55E)
        !detected && facesCount == 0 -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }

    // Minimal overlay UI over full-screen camera preview
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview occupies the whole screen
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Top-left: ML Kit tiny chips (Face & Pose)
        val faceLabel by remember { derivedStateOf { if (detected) "Face ✓ ($facesCount)" else "No face" } }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(if (detected) Color(0xFF22C55E) else Color(0xFFEF4444), CircleShape))
                    Text(faceLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                // Pose line
                val poseText = if (poseStable) "Pose ✓ ($poseLandmarks)" else "Pose – none"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF06B6D4), CircleShape))
                    Text(
                        text = poseText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Wake overlay removed

        // Bottom-center: Connect floating button（Disconnectは非表示）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isConnected) {
                Button(
                    onClick = {
                        val bmp = lastAcceptedFrameRef.get()
                        val ok = bmp?.let { frameGate.shouldAccept(it) } ?: true
                        gateAccepted = ok
                        gateSim = if (!ok) 0.95f else -1f
                        if (ok) isConnected = true else Toast.makeText(context, "同じシーンと判断したため、通話をスキップしました", Toast.LENGTH_SHORT).show()
                    },
                    enabled = hasPermission && (detected || poseStable),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Connect", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }

        // Bottom-left: Gate result chip
        val gateText = when (gateAccepted) {
            null -> "Gate: –"
            true -> "Gate: Accept (sim=${'$'}{gateSim?.let { String.format("%.3f", it) } ?: "-"})"
            false -> "Gate: Skip (sim=${'$'}{gateSim?.let { String.format("%.3f", it) } ?: "-"})"
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(gateText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }

    // Wake removed
}
