package com.pantry.manager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    onItemScanned: (String, String) -> Unit = { _, _ -> }
) {
    var manualBarcode by remember { mutableStateOf("") }
    var scannedResult by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnItemScanned by rememberUpdatedState(onItemScanned)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Grocery packaging uses these retail formats; ignoring QR/shipping codes avoids junk items.
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()
        )
    }
    DisposableEffect(Unit) {
        onDispose { barcodeScanner.close() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Barcode Scanner") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    // Paused after a successful read until "Scan Again" clears the result.
                                    if (scannedResult != null || mediaImage == null) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val barcode = barcodes.firstOrNull { it.rawValue != null }
                                            val value = barcode?.rawValue
                                            if (value != null && scannedResult == null) {
                                                val format = when (barcode.format) {
                                                    Barcode.FORMAT_EAN_13 -> "EAN-13"
                                                    Barcode.FORMAT_EAN_8 -> "EAN-8"
                                                    Barcode.FORMAT_UPC_A -> "UPC-A"
                                                    Barcode.FORMAT_UPC_E -> "UPC-E"
                                                    else -> "Unknown"
                                                }
                                                scannedResult = "Scanned: $value\nFormat: $format"
                                                currentOnItemScanned("Scanned Item ($value)", value)
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Camera Viewfinder",
                            modifier = Modifier.size(64.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Camera permission needed", color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Camera Access")
                        }
                    }
                }
            }

            Button(
                enabled = scannedResult != null,
                onClick = { scannedResult = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Again")
            }

            HorizontalDivider()

            Text("Or enter barcode manually:", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = manualBarcode,
                onValueChange = { manualBarcode = it },
                label = { Text("Barcode Digits") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = manualBarcode.isNotBlank(),
                onClick = {
                    scannedResult = "Fetched Item Info for Barcode: $manualBarcode"
                    onItemScanned("Manual Item ($manualBarcode)", manualBarcode)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Fetch Grocery Info from Internet")
            }

            scannedResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
