package com.dapa.camloc.activities

import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.dapa.camloc.Marker
import com.dapa.camloc.R
import com.dapa.camloc.databinding.ActivityTrackerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TrackerActivity : CameraBase() {
    private lateinit var binding: ActivityTrackerBinding
    private lateinit var mScaleDetector : ScaleGestureDetector

    private val resolutionsDisplay = arrayOf<CharSequence>("640x360 nHD", "960x540 qHD", "1280x720 HD", "1600x900 HD+", "1920x1080 HFD", "2560x1440 QHD")
    private val resolutions = arrayOf(Size(640, 360), Size(960, 540), Size(1280,720), Size(1600,900), Size(1920,1080), Size(2560,1440))
    private var currentResolution = 3

    // native function declarations
    external fun stringFromJNI(): String
    private external fun detectMarkers(matAddress: Long): Array<Marker>

    override fun onBind(): Preview.SurfaceProvider = binding.cameraLayout.viewFinder.surfaceProvider

    override fun onFrame(image: ImageProxy) {
        val p = detectMarkers(mat.nativeObjAddr)
        binding.cameraLayout.overlay.draw(p, Size(image.width, image.height))
        image.close()
    }

    // ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mScaleDetector = ScaleGestureDetector(this, scaleListener)
        binding.cameraLayout.currentZoomRatio.text = String.format("%.1fX", mZoomRatio)

        binding.cameraLayout.switchCameraButton.setOnClickListener {
            mCameraSelector =
                if(mCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
        }

        binding.cameraLayout.selectResolutionButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Select resolution")
                .setIcon(R.drawable.ic_outline_aspect_ratio_24)
                .setSingleChoiceItems(resolutionsDisplay, currentResolution) { dialog, which ->
                    mResolution = resolutions[which]
                    currentResolution = which
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onResume() {
        super.onResume()

        window.setDecorFitsSystemWindows(false)
        binding.root.windowInsetsController?.let {
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsets.Type.systemBars())
        }
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // clamp between 0.5 ultra-wide and 5x zoom
            mZoomRatio = 0.5f.coerceAtLeast((mZoomRatio * detector.scaleFactor).coerceAtMost(5.0f))
            binding.cameraLayout.currentZoomRatio.text = String.format("%.1fX", mZoomRatio)
            return true
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(ev)
        return true
    }

    // ---

    companion object {
        init {
            System.loadLibrary("camloc")
        }
        private const val TAG = "CamLocTrackerActivity"
    }
}