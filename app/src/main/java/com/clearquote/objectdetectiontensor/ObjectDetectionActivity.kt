package com.clearquote.objectdetectiontensor

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.clearquote.objectdetectiontensor.databinding.ActivityObjectDetectionBinding
import com.clearquote.objectdetectiontensor.ml.SsdMobilenet
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class ObjectDetectionActivity : AppCompatActivity() {

    private lateinit var previewImage: ImageView
    private lateinit var detectionImageView: ImageView
    private lateinit var objectDetectionModel: SsdMobilenet
    private lateinit var imagePicker: ActivityResultLauncher<String>
    private val imageResizer = ImageProcessor.Builder()
        .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    private var _binding: ActivityObjectDetectionBinding? = null
    private val binding get() = _binding!!

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //    setContentView(R.layout.activity_main)
        _binding = ActivityObjectDetectionBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())

        objectDetectionModel = SsdMobilenet.newInstance(this)

        previewImage = binding.ivPreviewImage
        detectionImageView = binding.ivDetectImage

        binding.mbBack.setOnClickListener {
            finish()
        }

        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri ->
            imageUri?.let {
                val selectedBitmap = loadBitmapFromUri(it)
                if (selectedBitmap != null) {
                    previewImage.setImageBitmap(selectedBitmap)
                    processImage(selectedBitmap)
                }else {
                    finish()
                }
            }
        }
        imagePicker.launch("image/*")
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            imagePicker.launch("image/*")
        } else {
            Log.e("MainActivity", "Permission denied to read external storage")
        }
    }

    private fun loadBitmapFromUri(imageUri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading image: ${e.message}")
            null
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        objectDetectionModel.close()
    }

    private fun processImage(bitmap: Bitmap) {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val resizedImage = imageResizer.process(tensorImage)
        val detectionOutputs = objectDetectionModel.process(resizedImage)

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val imageHeight = mutableBitmap.height
        val imageWidth = mutableBitmap.width

        val colorList = listOf(Color.BLACK, Color.RED, Color.GREEN, Color.BLUE)
        val paintStyle = Paint().apply {
            style = Paint.Style.STROKE
            textSize = imageHeight / 18f
            strokeWidth = imageHeight / 150f
        }

        detectionOutputs.scoresAsTensorBuffer.floatArray.forEachIndexed { index, score ->
            if (score > 0.5) {
                val locationIndex = index * 4
                paintStyle.color = colorList[index % colorList.size]
                canvas.drawRect(
                    RectF(
                        detectionOutputs.locationsAsTensorBuffer.floatArray[locationIndex + 1] * imageWidth,
                        detectionOutputs.locationsAsTensorBuffer.floatArray[locationIndex] * imageHeight,
                        detectionOutputs.locationsAsTensorBuffer.floatArray[locationIndex + 3] * imageWidth,
                        detectionOutputs.locationsAsTensorBuffer.floatArray[locationIndex + 2] * imageHeight
                    ), paintStyle
                )
                paintStyle.color = Color.WHITE
                canvas.drawText(
                    "${
                        FileUtil.loadLabels(
                            this,
                            "labels.txt"
                        )[detectionOutputs.classesAsTensorBuffer.floatArray[index].toInt()]
                    } $score %",
                    detectionOutputs.locationsAsTensorBuffer.floatArray[locationIndex + 1] * imageWidth,
                    detectionOutputs.locationsAsTensorBuffer.floatArray[locationIndex] * imageHeight,
                    paintStyle
                )
            }
        }
        detectionImageView.setImageBitmap(mutableBitmap)
    }
}