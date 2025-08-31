package com.rementia.mlkitdetectors

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors

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

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermission = remember {
        (context as ComponentActivity).registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> hasPermission = granted }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) requestPermission.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Requesting camera permission…")
        }
        return
    }

    val previewView = remember { PreviewView(context) }

    // UI state for simple accuracy/debug visualization
    var detected by remember { mutableStateOf(false) }
    var facesCount by remember { mutableStateOf(0) }
    var fps by remember { mutableStateOf(0.0) }
    var lastLatencyMs by remember { mutableStateOf(0L) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // ML Kit Face detector (fast mode, tracking on)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
        val detector = FaceDetection.getClient(options)

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
                            detector.process(input)
                                .addOnSuccessListener { faces ->
                                    facesCount = faces.size
                                    detected = faces.isNotEmpty()
                                    lastLatencyMs = (System.nanoTime() - began) / 1_000_000
                                    val count = frameCounter.incrementAndGet()
                                    val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
                                    if (elapsedSec > 0) fps = count / elapsedSec
                                }
                                .addOnFailureListener {
                                    detected = false
                                }
                                .addOnCompleteListener {
                                    image.close()
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
        detected && facesCount > 0 -> Color(0xFF22C55E) // green
        !detected && facesCount == 0 -> Color(0xFFEF4444) // red
        else -> Color(0xFFF59E0B) // amber
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Top bar with metrics
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Faces: $facesCount", color = Color.White, fontWeight = FontWeight.Bold)
            Text(String.format("FPS: %.1f", fps), color = Color.White, fontWeight = FontWeight.Medium)
            Text("Latency: ${lastLatencyMs}ms", color = Color.White)
        }

        Spacer(Modifier.height(12.dp))

        // Camera preview
        AndroidView(factory = { previewView }, modifier = Modifier
            .fillMaxWidth()
            .weight(1f))

        Spacer(Modifier.height(12.dp))

        // Status pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(statusColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (detected) "DETECTED" else "NO DETECTION",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
