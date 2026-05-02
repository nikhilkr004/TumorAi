package com.example.tumorai.ui

import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.tumorai.databinding.ActivityResultBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private val db = FirebaseFirestore.getInstance()
    private var imageBitmap: Bitmap? = null
    
    private var resultText: String = ""
    private var confidenceValue: Float = 0f
    private var isTumorDetected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resultText = intent.getStringExtra("EXTRA_RESULT") ?: "Unknown"
        confidenceValue = intent.getFloatExtra("EXTRA_CONFIDENCE", 0.0f)
        isTumorDetected = intent.getBooleanExtra("EXTRA_IS_TUMOR", false)
        val imageUriStr = intent.getStringExtra("EXTRA_IMAGE_URI")

        binding.tvResult.text = resultText
        val confPercent = (confidenceValue * 100).toInt()
        binding.tvConfidenceText.text = "${confPercent}%"
        binding.progressConfidence.progress = confPercent

        if (imageUriStr != null) {
            try {
                val uri = Uri.parse(imageUriStr)
                imageBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                binding.ivMriScan.setImageBitmap(imageBitmap)
                binding.ivMriScan.visibility = View.VISIBLE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isTumorDetected) {
            binding.ivResultIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            binding.ivResultIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(com.example.tumorai.R.color.tumor_detected))
            binding.tvResult.setTextColor(getColor(com.example.tumorai.R.color.tumor_detected))
            binding.progressConfidence.setIndicatorColor(getColor(com.example.tumorai.R.color.tumor_detected))
            binding.tvStage.visibility = View.VISIBLE
            binding.tvDetails.visibility = View.VISIBLE
        } else {
            binding.ivResultIcon.setImageResource(android.R.drawable.checkbox_on_background)
            binding.ivResultIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(com.example.tumorai.R.color.no_tumor))
            binding.tvResult.setTextColor(getColor(com.example.tumorai.R.color.no_tumor))
            binding.progressConfidence.setIndicatorColor(getColor(com.example.tumorai.R.color.no_tumor))
            binding.tvStage.visibility = View.GONE
            binding.tvDetails.visibility = View.GONE
        }

        saveToFirestore(resultText, confidenceValue)

        binding.btnGeneratePdf.setOnClickListener {
            generatePdfReport()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun generatePdfReport() {
        lifecycleScope.launch {
            try {
                binding.btnGeneratePdf.isEnabled = false
                binding.btnGeneratePdf.text = "Generating PDF..."
                
                val pdfFile = withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    val paint = Paint()
                    val titlePaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 24f
                        color = Color.BLACK
                    }
                    val headingPaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 16f
                        color = Color.DKGRAY
                    }
                    val bodyPaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        textSize = 14f
                        color = Color.BLACK
                    }

                    // Draw Header
                    canvas.drawText("TumorAI Analysis Report", 50f, 60f, titlePaint)
                    canvas.drawLine(50f, 75f, 545f, 75f, paint)

                    var currentY = 120f

                    // Draw Image if available
                    imageBitmap?.let { bitmap ->
                        // Scale bitmap to fit nicely in PDF
                        val scaledWidth = 200
                        val scaledHeight = (bitmap.height.toFloat() / bitmap.width.toFloat() * scaledWidth).toInt()
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                        canvas.drawBitmap(scaledBitmap, 50f, currentY, paint)
                        currentY += scaledHeight + 40f
                    }

                    // Draw Results
                    canvas.drawText("Result: $resultText", 50f, currentY, headingPaint)
                    currentY += 30f
                    canvas.drawText("Confidence: ${String.format("%.2f", confidenceValue * 100)}%", 50f, currentY, bodyPaint)
                    currentY += 40f

                    if (isTumorDetected) {
                        canvas.drawText("Tumor Stage: Unspecified", 50f, currentY, headingPaint)
                        currentY += 20f
                        canvas.drawText("(Requires further clinical diagnosis such as MRI with contrast or biopsy)", 50f, currentY, bodyPaint)
                        currentY += 40f

                        canvas.drawText("Next Steps / Recommendations:", 50f, currentY, headingPaint)
                        currentY += 25f
                        canvas.drawText("• Consult a neurologist or oncologist immediately.", 60f, currentY, bodyPaint)
                        currentY += 20f
                        canvas.drawText("• Do not panic, this is an AI screening and must be verified.", 60f, currentY, bodyPaint)
                        currentY += 20f
                        canvas.drawText("• Bring this report to your doctor.", 60f, currentY, bodyPaint)
                        currentY += 20f
                        canvas.drawText("• Follow doctor's orders for biopsy or further scans.", 60f, currentY, bodyPaint)
                        currentY += 40f
                    }

                    canvas.drawLine(50f, currentY, 545f, currentY, paint)
                    currentY += 25f
                    
                    val disclaimerPaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                        textSize = 10f
                        color = Color.GRAY
                    }
                    canvas.drawText("Disclaimer: This automated analysis is for guidance only.", 50f, currentY, disclaimerPaint)
                    currentY += 15f
                    canvas.drawText("A qualified medical professional must verify all findings.", 50f, currentY, disclaimerPaint)

                    pdfDocument.finishPage(page)

                    val reportsDir = File(cacheDir, "reports")
                    if (!reportsDir.exists()) reportsDir.mkdirs()
                    
                    val file = File(reportsDir, "TumorAI_Report_${System.currentTimeMillis()}.pdf")
                    pdfDocument.writeTo(FileOutputStream(file))
                    pdfDocument.close()
                    file
                }

                sharePdf(pdfFile)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ResultActivity, "Error generating PDF", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnGeneratePdf.isEnabled = true
                binding.btnGeneratePdf.text = "Export PDF Report"
            }
        }
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Open or Share PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "No application found to open PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToFirestore(result: String, confidence: Float) {
        val scan = hashMapOf(
            "result" to result,
            "confidence" to confidence,
            "timestamp" to Date()
        )

        db.collection("scans")
            .add(scan)
            .addOnSuccessListener {
                Toast.makeText(this, "Result saved to history", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving result: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
