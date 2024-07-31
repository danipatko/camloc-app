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
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.dapa.camloc.MainActivity
import com.dapa.camloc.R
import com.dapa.camloc.databinding.ActivityTrackerBinding
import com.dapa.camloc.services.NetworkService
import com.dapa.camloc.util.HardwareInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread


class TrackerActivity : CameraBase() {
    private lateinit var binding: ActivityTrackerBinding
    private lateinit var mScaleDetector : ScaleGestureDetector
    private val hardwareInfo = HardwareInfo(this)

    private var currentResolution = 2

    // service
    private lateinit var mNetworkService: NetworkService
    private var mBound: Boolean = false

    // native function declarations
    private external fun trackMarker(matAddress: Long): Float
    private external fun setParams(fx: Float, fy: Float, cx:Float, cy:Float)

    override fun onBind(): PreviewView = binding.cameraLayout.viewFinder

    override fun onFrame(image: ImageProxy) {
        thread {
            val x = trackMarker(mat.nativeObjAddr)
            binding.cameraLayout.overlay.drawX(x, mCameraIndex == 2)

            // why is pose estimation unreliable?
            // https://github.com/opencv/opencv/issues/8813
            image.close()
        }
    }

    private var currentFOV = -1F

    override fun onCameraChanged(cameraInfo: CameraInfo) {
        currentFOV = HardwareInfo.getFOV(this, cameraInfo)
        mNetworkService.cameraSet(currentFOV, mCameraIndex, currentResolution, mZoomRatio)
    }

    override fun onZoomChanged(zoom: Float) {
        mNetworkService.cameraSet(currentFOV, mCameraIndex, currentResolution, mZoomRatio)
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
            mZoomRatio = (mZoomRatio * detector.scaleFactor).coerceIn(0.1F, 5F)
            binding.cameraLayout.currentZoomRatio.text = String.format("%.1fX", mZoomRatio)
            return true
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(ev)
        return true
    }

    override fun onStart() {
        super.onStart()
        isActive = true
        Intent(this, NetworkService::class.java).also { intent ->
            intent.putExtra("type", MainActivity.TAG)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        mNetworkService.trackingClosed()
        isActive = false
        unbindService(connection)
        mBound = false
    }

    // ---

    val serviceHandler = object : NetworkService.TrackerEventHandler {
        override fun onCameraSet(cameraIndex: Int, resolution: Int, focus: Float) {

            if(this@TrackerActivity.mCameraIndex != cameraIndex)
                this@TrackerActivity.mCameraIndex = cameraIndex

            if(this@TrackerActivity.currentResolution != resolution)
                this@TrackerActivity.currentResolution = resolution

            if(this@TrackerActivity.mZoomRatio != focus)
                this@TrackerActivity.mZoomRatio = focus
        }

        override fun onStateSet(state: Byte) {
            if(state == 0.toByte()) {
                finish()
            }
        }

        override fun onFlash() {
            thread {
                this@TrackerActivity.flash(true)
                Thread.sleep(1000)
                this@TrackerActivity.flash(true)
            }
        }
    }

    // service binding
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            val binder = service as NetworkService.LocalBinder
            mNetworkService = binder.getService()
            mNetworkService.trackerHandler = serviceHandler

            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }


    companion object {
        val resolutionsDisplay = arrayOf<CharSequence>("640x360 nHD", "960x540 qHD", "1280x720 HD", "1600x900 HD+", "1920x1080 HFD", "2560x1440 QHD")
        val resolutions = arrayOf(Size(640, 360), Size(960, 540), Size(1280,720), Size(1600,900), Size(1920,1080), Size(2560,1440))
        val cameraSelectorDisplay = arrayOf<CharSequence>("Camera facing back", "Ultra-wide lens", "Camera facing front")

        init {
            System.loadLibrary("camloc")
        }

        var isActive = false

        private const val TAG = "CamLocTrackerActivity"
    }
}