package com.example.data.ocr

import android.content.Context
import android.net.Uri
import com.example.domain.OcrParseResult
import com.example.domain.OcrPayrollParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine(private val context: Context) {

    /**
     * Performs 100% on-device OCR using bundled ML Kit Text Recognition.
     * No image or salary data leaves the device.
     */
    suspend fun scanImageUri(uri: Uri): Result<OcrParseResult> = withContext(Dispatchers.Default) {
        runCatching {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val visionText = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) cont.resume(result)
                        }
                        .addOnFailureListener { error ->
                            if (cont.isActive) cont.resumeWithException(error)
                        }
                }
                // Build structured lines sorted top-to-bottom for better table row extraction
                val orderedLines = visionText.textBlocks
                    .flatMap { it.lines }
                    .sortedWith(
                        compareBy(
                            { (it.boundingBox?.top ?: 0) / 18 },
                            { it.boundingBox?.left ?: 0 }
                        )
                    )
                    .map { it.text }

                val normalizedText = if (orderedLines.isNotEmpty()) {
                    orderedLines.joinToString("\n")
                } else {
                    visionText.text
                }
                OcrPayrollParser.parseRecognizedText(normalizedText)
            } finally {
                recognizer.close()
            }
        }
    }
}
