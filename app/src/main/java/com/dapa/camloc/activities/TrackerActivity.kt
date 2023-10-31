package com.dapa.camloc.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.dapa.camloc.R
import com.dapa.camloc.databinding.ActivityTrackerBinding
import com.dapa.camloc.services.NetworkService
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TrackerActivity : CameraBase() {
    private lateinit var binding: ActivityTrackerBinding
    private lateinit var mScaleDetector : ScaleGestureDetector

    private val resolutionsDisplay = arrayOf<CharSequence>("640x360 nHD", "960x540 qHD", "1280x720 HD", "1600x900 HD+", "1920x1080 HFD", "2560x1440 QHD")
    private val resolutions = arrayOf(Size(640, 360), Size(960, 540), Size(1280,720), Size(1600,900), Size(1920,1080), Size(2560,1440))
    private var currentResolution = 2
    private val cameraSelectorDisplay = arrayOf<CharSequence>("Camera facing back", "Ultra-wide lens", "Camera facing front")

    // service
    private var mBound: Boolean = false
    private lateinit var mService: NetworkService

    // native function declarations
    private external fun trackMarker(matAddress: Long): Float
    private external fun setParams(fx: Float, fy: Float, cx:Float, cy:Float)

    override fun onBind(): PreviewView = binding.cameraLayout.viewFinder

    override fun onFrame(image: ImageProxy) {
        val x = trackMarker(mat.nativeObjAddr)
        binding.cameraLayout.overlay.drawX(x, mCameraIndex == 2)
        // why is pose estimation unreliable?
        // https://github.com/opencv/opencv/issues/8813
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

    override fun onResume() {
        super.onResume()
        Intent(this, NetworkService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            binding.root.windowInsetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {

            // We've bound to com.dapa.camloc.service.LocalService, cast the IBinder and get com.dapa.camloc.service.LocalService instance.
            val binder = service as NetworkService.NetBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    // ---

    companion object {
        init {
            System.loadLibrary("camloc")
        }
        private const val TAG = "CamLocTrackerActivity"
    }
}