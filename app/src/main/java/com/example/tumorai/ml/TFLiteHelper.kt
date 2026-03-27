package com.example.tumorai.ml

import android.content.Context
import android.content.res.AssetFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val MODEL_FILE = "brain_tumor_model.tflite"
    private var outputShape: IntArray = intArrayOf(1, 2)

    suspend fun initInterpreter() = withContext(Dispatchers.IO) {
        try {
            val buffer = loadModelFile(MODEL_FILE)
            interpreter = Interpreter(buffer)
            
            // Inspect output shape
            val outputTensor = interpreter?.getOutputTensor(0)
            outputShape = outputTensor?.shape() ?: intArrayOf(1, 2)
            
            println("TumorAI: Model Loaded. Output Shape: ${outputShape.contentToString()}")
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(filename: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun predict(inputBuffer: ByteBuffer): FloatArray = withContext(Dispatchers.Default) {
        // Allocate buffer based on model output shape
        // If shape is [1, 1], we need size 1. If [1, 2], size 2.
        val outputSize = outputShape.last() 
        val output = Array(1) { FloatArray(outputSize) }
        
        interpreter?.run(inputBuffer, output)
        
        return@withContext output[0]
    }

    fun isInitialized(): Boolean {
        return interpreter != null
    }

    fun close() {
        interpreter?.close()
    }
}
