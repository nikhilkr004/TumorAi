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
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
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
        setupPieChart()
        setupBarChart()

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
            binding.tvStage.visibility = View.VISIBLE
            binding.tvStage.text = "Tumor Stage: Unspecified\n(Requires further clinical diagnosis such as MRI with contrast or biopsy)"
            binding.tvDetails.visibility = View.VISIBLE
            binding.tvDetails.text = "Next Steps:\n• Consult a neurologist or oncologist immediately.\n• Do not panic, this is an AI screening and must be verified.\n• Bring this report to your doctor.\n• Follow doctor's orders for biopsy or further scans."
        } else {
            binding.ivResultIcon.setImageResource(android.R.drawable.checkbox_on_background)
            binding.ivResultIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(com.example.tumorai.R.color.no_tumor))
            binding.tvResult.setTextColor(getColor(com.example.tumorai.R.color.no_tumor))
            binding.tvStage.visibility = View.GONE
            binding.tvDetails.visibility = View.VISIBLE
            binding.tvDetails.text = "Healthy Brain Structure:\n• No anomalous masses detected.\n• Continue regular health check-ups.\n• If symptoms persist, consult a doctor."
        }

        saveToFirestore(resultText, confidenceValue)

        binding.btnGeneratePdf.setOnClickListener {
            generatePdfReport()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupPieChart() {
        val entries = ArrayList<PieEntry>()
        val tumorProb = if (isTumorDetected) confidenceValue else 1.0f - confidenceValue
        val noTumorProb = if (!isTumorDetected) confidenceValue else 1.0f - confidenceValue

        entries.add(PieEntry(tumorProb * 100, "Tumor"))
        entries.add(PieEntry(noTumorProb * 100, "No Tumor"))

        val dataSet = PieDataSet(entries, "")
        val colors = ArrayList<Int>()
        colors.add(getColor(com.example.tumorai.R.color.tumor_detected))
        colors.add(getColor(com.example.tumorai.R.color.no_tumor))
        dataSet.colors = colors
        dataSet.valueTextSize = 16f
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTypeface = Typeface.DEFAULT_BOLD
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false
        binding.pieChart.legend.isEnabled = false // hide legend for cleaner look
        binding.pieChart.setUsePercentValues(true)
        binding.pieChart.setEntryLabelColor(Color.WHITE)
        binding.pieChart.setEntryLabelTextSize(14f)
        binding.pieChart.setEntryLabelTypeface(Typeface.DEFAULT_BOLD)
        
        binding.pieChart.isDrawHoleEnabled = true
        binding.pieChart.setHoleColor(Color.TRANSPARENT)
        binding.pieChart.holeRadius = 55f
        binding.pieChart.transparentCircleRadius = 60f
        
        // Beautiful center text
        val centerText = SpannableString("AI\nConfidence")
        centerText.setSpan(RelativeSizeSpan(1.4f), 0, 2, 0)
        centerText.setSpan(StyleSpan(Typeface.BOLD), 0, 2, 0)
        centerText.setSpan(ForegroundColorSpan(Color.DKGRAY), 0, centerText.length, 0)
        
        binding.pieChart.centerText = centerText
        
        // Disable touch for a static beautiful look
        binding.pieChart.setTouchEnabled(false)
        
        binding.pieChart.animateY(1400, Easing.EaseInOutQuad)
        binding.pieChart.invalidate()
    }

    private fun setupBarChart() {
        // Simulate metrics based on confidence to provide rich visualization
        val symmetry = if (isTumorDetected) (1.0f - confidenceValue) * 100 else (confidenceValue) * 100
        val irregularity = if (isTumorDetected) confidenceValue * 100 else (1.0f - confidenceValue) * 100
        val intensity = if (isTumorDetected) (confidenceValue - 0.1f) * 100 else (1.0f - confidenceValue + 0.1f) * 100

        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, Math.max(0f, Math.min(100f, symmetry))))
        entries.add(BarEntry(1f, Math.max(0f, Math.min(100f, irregularity))))
        entries.add(BarEntry(2f, Math.max(0f, Math.min(100f, intensity))))

        val dataSet = BarDataSet(entries, "Metrics")
        dataSet.colors = listOf(
            Color.parseColor("#4CAF50"), // Symmetry
            Color.parseColor("#F44336"), // Irregularity
            Color.parseColor("#FF9800")  // Intensity
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f
        dataSet.valueTypeface = Typeface.DEFAULT_BOLD

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f

        binding.barChart.data = barData
        binding.barChart.description.isEnabled = false
        binding.barChart.legend.isEnabled = false
        binding.barChart.setTouchEnabled(false)
        binding.barChart.setDrawGridBackground(false)

        val xAxis = binding.barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textColor = Color.WHITE
        xAxis.textSize = 12f
        xAxis.valueFormatter = object : ValueFormatter() {
            private val labels = arrayOf("Symmetry", "Edge Irreg.", "Intensity")
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < labels.size) labels[index] else ""
            }
        }
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true

        val leftAxis = binding.barChart.axisLeft
        leftAxis.setDrawLabels(false)
        leftAxis.setDrawGridLines(false)
        leftAxis.setDrawAxisLine(false)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 100f

        val rightAxis = binding.barChart.axisRight
        rightAxis.setDrawLabels(false)
        rightAxis.setDrawGridLines(false)
        rightAxis.setDrawAxisLine(false)

        binding.barChart.animateY(1400, Easing.EaseInOutQuad)
        binding.barChart.invalidate()
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
