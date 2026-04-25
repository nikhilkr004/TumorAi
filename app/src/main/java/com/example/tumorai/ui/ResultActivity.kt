package com.example.tumorai.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tumorai.databinding.ActivityResultBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = intent.getStringExtra("EXTRA_RESULT") ?: "Unknown"
        val confidence = intent.getFloatExtra("EXTRA_CONFIDENCE", 0.0f)
        val isTumor = intent.getBooleanExtra("EXTRA_IS_TUMOR", false)

        binding.tvResult.text = result
        binding.tvConfidence.text = "Confidence: ${String.format("%.2f", confidence * 100)}%"

        if (isTumor) {
            binding.ivResultIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            binding.ivResultIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(@color/tumor_detected))
            binding.tvResult.setTextColor(getColor(@color/tumor_detected))
        } else {
            binding.ivResultIcon.setImageResource(android.R.drawable.checkbox_on_background)
            binding.ivResultIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(@color/no_tumor))
            binding.tvResult.setTextColor(getColor(@color/no_tumor))
        }

        saveToFirestore(result, confidence)

        binding.btnBack.setOnClickListener {
            finish()
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
