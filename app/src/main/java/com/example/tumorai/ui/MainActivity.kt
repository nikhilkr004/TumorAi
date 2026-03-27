package com.example.tumorai.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tumorai.databinding.ActivityMainBinding
import com.example.tumorai.ml.TFLiteHelper
import com.example.tumorai.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tfliteHelper: TFLiteHelper
    private var selectedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                selectedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                binding.ivPreview.setImageBitmap(selectedBitmap)
                binding.btnAnalyze.isEnabled = true
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tfliteHelper = TFLiteHelper(this)
        lifecycleScope.launch {
            tfliteHelper.initInterpreter()
        }

        binding.btnSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnAnalyze.setOnClickListener {
            selectedBitmap?.let { bitmap ->
                binding.btnAnalyze.isEnabled = false
                binding.btnAnalyze.text = "Analyzing..."
                binding.progressBar.visibility = android.view.View.VISIBLE
                analyzeImage(bitmap)
            }
        }
    }

    private fun analyzeImage(bitmap: Bitmap) {
        if (!tfliteHelper.isInitialized()) {
            Toast.makeText(this, "Model not loaded. Please ensure 'brain_tumor_model.tflite' exists in assets.", Toast.LENGTH_LONG).show()
            binding.btnAnalyze.isEnabled = true
            binding.btnAnalyze.text = "Analyze"
            binding.progressBar.visibility = android.view.View.GONE
            return
        }
        lifecycleScope.launch {
            try {
                val byteBuffer = ImageUtils.prepareImage(bitmap)
                val result = tfliteHelper.predict(byteBuffer)

                // Debug: Show raw output
                val rawOutput = result.contentToString()
                android.util.Log.d("TumorAI", "Raw Output: $rawOutput")
                withContext(Dispatchers.Main) {
                   Toast.makeText(this@MainActivity, "Raw Output: $rawOutput", Toast.LENGTH_LONG).show()
                }

                val prediction: String
                var confidence: Float
                var isTumor = false

                if (result.size == 1) {
                    // Binary
                    confidence = result[0]
                    if (confidence > 0.5f) {
                        isTumor = true
                        prediction = "Tumor Detected"
                    } else {
                        isTumor = false
                        prediction = "No Tumor"
                        confidence = 1.0f - confidence
                    }
                } else if (result.size >= 2) {
                    val noTumorProb = result[0]
                    val tumorProb = result[1]
                    
                    isTumor = tumorProb > noTumorProb
                    
                    if (isTumor) {
                        prediction = "Tumor Detected"
                        confidence = tumorProb
                    } else {
                        prediction = "No Tumor"
                        confidence = noTumorProb
                    }
                } else {
                    prediction = "Error"
                    confidence = 0f
                }

                val intent = Intent(this@MainActivity, ResultActivity::class.java).apply {
                    putExtra("EXTRA_RESULT", prediction)
                    putExtra("EXTRA_CONFIDENCE", confidence)
                    putExtra("EXTRA_IS_TUMOR", isTumor)
                }
                startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error during analysis: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnAnalyze.isEnabled = true
                binding.btnAnalyze.text = "Analyze"
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tfliteHelper.close()
    }
}
