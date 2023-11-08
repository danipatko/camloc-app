package com.dapa.camloc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.atan

data class CameraParams(val id: String, val lensFacing: Int, val fovX: Float, val fovY: Float)

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraConfig(context: Context) {
    private lateinit var cameraParams: Map<String, CameraParams>

    init {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()

            cameraParams = buildMap {
                provider.availableCameraInfos.forEach {
                    getCameraParams(context, it).apply {
                        this@buildMap[this.id] = this
                    }
                }
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private fun getCameraParams(context:Context, cameraInfo: CameraInfo): CameraParams {
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

    companion object {
        const val TAG = "CamLocCameraConfig"
    }
}