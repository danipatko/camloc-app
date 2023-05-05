package com.dapa.camloc

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.core.Preview.SurfaceProvider
import com.dapa.camloc.activities.CameraBase
import com.dapa.camloc.databinding.ActivityMainBinding

class MainActivity : CameraBase() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mScaleDetector :ScaleGestureDetector

    // native function declarations
    external fun stringFromJNI(): String
    private external fun detectMarkers(matAddress: Long): Array<Marker>

    override fun onBind(): SurfaceProvider = binding.viewFinder.surfaceProvider

    override fun onFrame(image: ImageProxy) {
        val p = detectMarkers(mat.nativeObjAddr)
        binding.overlay.draw(p, Size(image.width, image.height))
        image.close()
    }

    // ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mScaleDetector = ScaleGestureDetector(this, scaleListener)

        binding.switchCameraButton.setOnClickListener {
            mCameraSelector =
                if(mCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
        }

        binding.chipResGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            mResolution = when(checkedIds[0]) {
                R.id.chip_res_1 -> Size(960, 540)
                R.id.chip_res_3 -> Size(1920, 1080)
                else -> Size(1280, 720) // default
            }
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
        private const val TAG = "CameraXApp"
    }
}