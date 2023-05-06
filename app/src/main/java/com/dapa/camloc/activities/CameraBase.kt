package com.dapa.camloc.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// loads opencv and handles camera permissions
abstract class CameraBase : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService

    private val loader: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    System.loadLibrary("opencv_java4")
                }
                else -> super.onManagerConnected(status)
            }
        }
    }

    var mat = Mat()

    private lateinit var camera: Camera
    private var initialized: Boolean = false

    var mCameraSelector : CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        set(value) {
            field = value
            // if camera is running, restart
            if(initialized) startCamera()
        }

    var mResolution: Size = Size(1280, 720)
        set(value) {
            field = value
            if(initialized) startCamera()
        }

    // note: 4:3 resolution would be pointless for our application
    private var aspectRatio: AspectRatioStrategy = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        set(value) {
            field = value
            if(initialized) startCamera()
        }

    var mZoomRatio: Float = 0.5F
        set(value) {
            field = value
            if(initialized) camera.cameraControl.setZoomRatio(value)
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val previewView = onBind()
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // prep use cases
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspectRatio)
                        .setResolutionStrategy(ResolutionStrategy(mResolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER))
                        .build()
                )
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspectRatio)
                        .setResolutionStrategy(ResolutionStrategy(mResolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER))
                        .build()
                )
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            previewView.setOnTouchListener { _, ev ->
                return@setOnTouchListener handlePreviewTouch(ev, previewView.width.toFloat(), previewView.height.toFloat())
            }

            val useCaseGroup = UseCaseGroup.Builder().addUseCase(imageAnalyzer).addUseCase(preview).build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, mCameraSelector, useCaseGroup)
                if(!initialized) {
                    camera.cameraControl.setZoomRatio(mZoomRatio)
                }
                initialized = true
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            Utils.bitmapToMat(image.toBitmap(), mat)
            onFrame(image)
        }
    }

    // ---

    abstract fun onFrame(image: ImageProxy)

    // called on camera startup (or restart)
    abstract fun onBind(): PreviewView

    // ---

    private fun handlePreviewTouch(ev: MotionEvent, w: Float, h: Float): Boolean {
        return when (ev.action) {
            MotionEvent.ACTION_DOWN -> true
            MotionEvent.ACTION_UP -> {
                val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(w, h)
                val autoFocusPoint = factory.createPoint(ev.x, ev.y)
                camera.cameraControl.startFocusAndMetering(
                    FocusMeteringAction.Builder(autoFocusPoint, FocusMeteringAction.FLAG_AF) // flag as fuck
                        .apply { disableAutoCancel() }
                        .build()
                )
                true
            }
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onResume() {
        super.onResume()
        System.loadLibrary("opencv_java4")

        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV loaded successfully")
            loader.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        } else {
            Log.d(TAG, "OpenCV not loaded")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, loader)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // ---

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if(allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CamLocCameraBase"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

    }
}