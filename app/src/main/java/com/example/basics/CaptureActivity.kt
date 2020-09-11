package com.example.basics

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.basics.tensorflow.Classifier
import com.example.basics.tensorflow.TensorFlowImageClassifier
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.wonderkiln.camerakit.CameraKitImage
import kotlinx.android.synthetic.main.activity_capture.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class CaptureActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CaptureActivity"
        private const val INPUT_WIDTH = 300
        private const val INPUT_HEIGHT = 300
        private const val IMAGE_MEAN = 128
        private const val IMAGE_STD = 128f
        private const val INPUT_NAME = "Mul"
        private const val OUTPUT_NAME = "final_result"
        private const val MODEL_FILE = "file:///android_asset/rounded_graph.pb"
        private const val LABEL_FILE = "file:///android_asset/retrained_labels.txt"
    }

    private var classifier: Classifier? = null
    private var initializeJob: Job? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setContentView(R.layout.activity_capture)
        initializeTensorClassifier()
        buttonRecognize.setOnClickListener {
            setVisibilityOnCaptured(false)
            cameraView.captureImage {
                onImageCaptured(it)
            }
        }
    }

    private fun onImageCaptured(it: CameraKitImage) {
        val bitmap = Bitmap.createScaledBitmap(it.bitmap, INPUT_WIDTH, INPUT_HEIGHT, false)
        showCapturedImage(bitmap)

        classifier?.let {
            try {
                showRecognizedResult(it.recognizeImage(bitmap))
            } catch (e: java.lang.RuntimeException) {
                Log.e(TAG, "Crashing due to classification.closed() before the recognizer finishes!")
            }
        }
    }

    private fun showRecognizedResult(results: MutableList<Classifier.Recognition>) {
        runOnUiThread {
            setVisibilityOnCaptured(true)
            if (results.isEmpty()) {
                textResult.text = getString(R.string.result_no_hero_found)
            } else {
                val hero = results[0].title
                val confidence = results[0].confidence
                textResult.text = when {
                    confidence > 0.95 -> getString(R.string.result_confident_hero_found, hero)
                    confidence > 0.85 -> getString(R.string.result_think_hero_found, hero)
                    else -> getString(R.string.result_maybe_hero_found, hero)
                }
            }
        }
    }

    private fun showCapturedImage(bitmap: Bitmap?) {
        runOnUiThread {
            imageCaptured.visibility = View.VISIBLE
            imageCaptured.setImageBitmap(bitmap)
        }
    }

    private fun setVisibilityOnCaptured(isDone: Boolean) {
        buttonRecognize.isEnabled = isDone
        if (isDone) {
            imageCaptured.visibility = View.VISIBLE
            textResult.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
        } else {
            imageCaptured.visibility = View.GONE
            textResult.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun initializeTensorClassifier() {
        initializeJob = GlobalScope.launch {
            try {
                classifier = TensorFlowImageClassifier.create(
                    assets, MODEL_FILE, LABEL_FILE, INPUT_WIDTH, INPUT_HEIGHT,
                    IMAGE_MEAN, IMAGE_STD, INPUT_NAME, OUTPUT_NAME)

                runOnUiThread {
                    buttonRecognize.isEnabled = true
                }
            } catch (e: Exception) {
                throw RuntimeException("Error initializing TensorFlow!", e)
            }
        }
    }

    private fun clearTensorClassifier() {
        initializeJob?.cancel()
        classifier?.close()
    }

    override fun onResume() {
        super.onResume()
        cameraView.start()
    }

    override fun onPause() {
        super.onPause()
        cameraView.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearTensorClassifier()
    }

   /* private var lastLocation: Location? = null
    private lateinit var resultReceiver: AddressResultReceiver

    // ...

    private fun startIntentService() {

        val intent = Intent(this, FetchAddressIntentService::class.java).apply {
            putExtra(FetchAddressIntentService.Constants.RECEIVER, resultReceiver)
            putExtra(FetchAddressIntentService.Constants.LOCATION_DATA_EXTRA, lastLocation)
        }
        startService(intent)
    }*/
}
