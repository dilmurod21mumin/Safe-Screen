package com.example.safescreen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.safescreen.ui.theme.SafeScreenTheme

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "onCreate started")

            setContent {
                SafeScreenTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                            NSFWDetectionScreen()
                     }
                }
            }

            Log.d(TAG, "onCreate completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @Composable
    fun ErrorScreen(error: String) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Error: $error", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Please check logcat for more details")
        }
    }

    @Composable
    @Preview
    fun NSFWDetectionScreen() {
        var imageUri by remember { mutableStateOf<Uri?>(null) }
        var resultText by remember { mutableStateOf("Rasm tanlang!") }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        val context = LocalContext.current

        // Initialize detector safely
        val nsfwDetector = remember {
            try {
                NSFWDetector(context.assets)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NSFWDetector: ${e.message}", e)
                showToast("Failed to initialize NSFWDetector: ${e.message}")
                null
            }
        }

        val selectImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            try {
                imageUri = uri
                uri?.let {
                    val selectedBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    bitmap = selectedBitmap

                    if (nsfwDetector != null) {
                        val imageData = ImageProcessor.processImage(selectedBitmap)
                        val nsfwScore = nsfwDetector.predict(imageData)
                        resultText = if (nsfwScore > 0.5) "Yomon rasm! Xavfli!" else "Xavfsiz rasm!"
                        showToast("nsfw score: $nsfwScore")
                        bitmap=if (nsfwScore < 0.5) selectedBitmap else null
                    } else {
                        resultText = "NSFW detector not available"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}", e)
                resultText = "Error: ${e.message}"
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(resultText, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(onClick = {
                try {
                    selectImageLauncher.launch("image/*")
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching image picker: ${e.message}", e)
                    resultText = "Error: ${e.message}"
                }
            }) {
                Text("Tanlash")
            }
        }
    }
}