package com.dapa.camloc.activities

import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.dapa.camloc.R
import com.dapa.camloc.databinding.ActivityTrackerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TrackerActivity : CameraBase() {
    private lateinit var binding: ActivityTrackerBinding
    private lateinit var mScaleDetector : ScaleGestureDetector

    private val resolutionsDisplay = arrayOf<CharSequence>("640x360 nHD", "960x540 qHD", "1280x720 HD", "1600x900 HD+", "1920x1080 HFD", "2560x1440 QHD")
    private val resolutions = arrayOf(Size(640, 360), Size(960, 540), Size(1280,720), Size(1600,900), Size(1920,1080), Size(2560,1440))
    private var currentResolution = 2

    private val cameraSelectorDisplay = arrayOf<CharSequence>("Camera facing back", "Ultra-wide lens", "Camera facing front")

    // native function declarations
    external fun stringFromJNI(): String
    private external fun detectMarkers(matAddress: Long, drawAddress: Long): Double
    private external fun trackMarker(matAddress: Long): Float
    private external fun setParams(fx: Float, fy: Float, cx:Float, cy:Float)

    // private val draw = Mat()

    override fun onBind(): PreviewView = binding.cameraLayout.viewFinder

    override fun onFrame(image: ImageProxy) {
        val x = trackMarker(mat.nativeObjAddr)
        binding.cameraLayout.overlay.drawX(x, mCameraIndex == 2)

        // https://github.com/opencv/opencv/issues/8813

        /*if(cameraConfig != null) {
            val rx = detectMarkers(mat.nativeObjAddr, draw.nativeObjAddr)
            // Log.d(TAG, "$rx")
            if(!rx.isNaN()) {
                runOnUiThread {
                    // val bm: Bitmap = Bitmap.createBitmap(mResolution.width, mResolution.height, Bitmap.Config.ARGB_8888)
                    // Utils.matToBitmap(draw, bm)
                    // binding.rotationIndicator.setImageBitmap(bm)
                    binding.rotationIndicator.rotationY = -rx.toFloat()
                }
            }
        }*/
        image.close()
    }

    override fun onCameraStarted() {
        if(cameraConfig != null)
            setParams(cameraConfig!!.focalLengthX, cameraConfig!!.focalLengthY, cameraConfig!!.cX, cameraConfig!!.cY)
    }

    // ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mScaleDetector = ScaleGestureDetector(this, scaleListener)
        binding.cameraLayout.currentZoomRatio.text = String.format("%.1fX", mZoomRatio)

        binding.cameraLayout.switchCameraButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Select camera")
                .setIcon(R.drawable.ic_outline_cameraswitch_24)
                .setSingleChoiceItems(cameraSelectorDisplay, mCameraIndex) { dialog, which ->
                    mCameraIndex = which
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
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
            // clamp between 1x and 5x zoom
            mZoomRatio = 1f.coerceAtLeast((mZoomRatio * detector.scaleFactor).coerceAtMost(5.0f))
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