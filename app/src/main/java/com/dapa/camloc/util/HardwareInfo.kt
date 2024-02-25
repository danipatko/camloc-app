package com.dapa.camloc.util

import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.atan

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class HardwareInfo(val context: Context) {
    data class CameraParams(val id: String, val lensFacing: Int, val fovX: Float, val fovY: Float)

    private lateinit var cameraParams: Map<String, CameraParams>
    private var mCameraId: String = "0"
    private val currentCamera: CameraParams get() = cameraParams[mCameraId]!!

    // i32: current camera (front/back/ultrawide)
    // f32: horizontal FOV
    // f32: vertical FOV
    // i32: device battery percentage
    val bytes: ByteArray get() =
        ByteBuffer.allocate(Float.SIZE_BYTES * 2 + Int.SIZE_BYTES * 2).apply {
            putInt(currentCamera.lensFacing)
            putFloat(currentCamera.fovX)
            putFloat(currentCamera.fovY)
            putInt(batteryPercentage)
        }.array()

    init {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()

            cameraParams = buildMap {
                provider.availableCameraInfos.forEach {
                    getCameraParams(it).apply {
                        this@buildMap[this.id] = this
                    }
                }
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun changeCamera(cameraInfo: CameraInfo) {
        mCameraId = Camera2CameraInfo.from(cameraInfo).cameraId
        // mChangeListener.onConfigChange(this.config)
    }

    private fun getCameraParams(cameraInfo: CameraInfo): CameraParams {
        val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId

        val cameraManager = context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        // these should be available on all devices with a camera
        // throws exception if not
        val focalLengthMM = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!![0]
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!

        // calculate field of view in deg
        val fovX = (2 * atan(sensorSize.width / (2 * focalLengthMM))) * (180 / PI).toFloat()
        val fovY = (2 * atan(sensorSize.height / (2 * focalLengthMM))) * (180 / PI).toFloat()

        Log.d(TAG, "$cameraId ${cameraInfo.lensFacing} | $fovX $fovY")

        return CameraParams(cameraId, cameraInfo.lensFacing, fovX, fovY)
    }

    val batteryPercentage: Int get() {
        val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    companion object {
        const val TAG = "CamlocCameraConfig"
    }
}