package com.dapa.camloc.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.internal.Camera2CameraInfoImpl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
import kotlin.math.PI
import kotlin.math.atan

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

    data class CamInfo(val focalLength: Float, val horizontalFov: Float, val verticalFov: Float)
    var mCamInfo: CamInfo? = null

    var mCameraIndex : Int = 0
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
            val cameraSelector: CameraSelector = when(mCameraIndex) {
                1 -> getUltraWideCamera()
                2 -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup)
                camera.cameraControl.setZoomRatio(mZoomRatio)
                initialized = true
                checkCameraCharacteristics()
                onCameraStarted()
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

    // https://stackoverflow.com/a/74150628
    @SuppressLint("RestrictedApi")
    fun getUltraWideCamera(): CameraSelector =
        CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                // filter back cameras with minimum sensor pixel size
                val backCameras = cameraInfos.filterIsInstance<Camera2CameraInfoImpl>()
                    .filter {
                        val pixelWidth = it.cameraCharacteristicsCompat.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.width ?: 0
                        it.lensFacing == CameraSelector.LENS_FACING_BACK && pixelWidth >= 2000 // arbitrary number resolved empirically
                    }
                // try to find wide lens camera, if not present, default to general backCameras
                backCameras.minByOrNull {
                    val focalLengths = it.cameraCharacteristicsCompat.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    focalLengths?.getOrNull(0) ?: 0f
                }
                    ?.let { listOf(it) } ?: backCameras
            }
            .build()

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun checkCameraCharacteristics() {
        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val cameraManager = this.getSystemService(CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        // this is device specific, would be great if samsung implemented it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val distCoeffs = characteristics.get(CameraCharacteristics.LENS_DISTORTION)
            Log.d(TAG, "Lens distortion: $distCoeffs")
        }
        // these 2 should be available on all devices with a camera
        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)

        mCamInfo = if(focalLengths != null && focalLengths.isNotEmpty() && size != null) {
            val fovX = (2 * atan(size.width / (2 * focalLengths[0]))) * (180 / PI)
            val fovY = (2 * atan(size.height / (2 * focalLengths[0]))) * (180 / PI)
            CamInfo(focalLengths[0], fovX.toFloat(), fovY.toFloat())
        } else {
            null
        }
    }

    // ---

    abstract fun onFrame(image: ImageProxy)

    // called on binding SurfaceView to the Preview use case
    abstract fun onBind(): PreviewView

    // called after successful camera launch
    open fun onCameraStarted() {}

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