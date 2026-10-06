package com.example.data.brain

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class LocalImageOcr(private val context: Context) {
    suspend fun extract(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || !file.canRead()) return@withContext ""
        runCatching {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                suspendCancellableCoroutine { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            if (continuation.isActive) continuation.resume(result.text.trim().take(MAX_CHARS))
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume("")
                        }
                }
            } finally {
                recognizer.close()
            }
        }.getOrDefault("")
    }

    companion object {
        private const val MAX_CHARS = 20_000
    }
}
