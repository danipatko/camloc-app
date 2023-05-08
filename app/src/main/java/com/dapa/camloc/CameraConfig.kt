package com.dapa.camloc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size
import android.util.SizeF
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.PI
import kotlin.math.atan

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraConfig(context: Context, cameraInfo: CameraInfo, res: Size) {
    // assume no distortion
    var distortionCoefficients: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    val cameraMatrix: FloatArray
        get() = floatArrayOf(focalLengthX, 0f, cX, 0f, focalLengthY, cY, 0f, 0f, 1f)

    val focalLengthMM: Float

    // in px
    val focalLengthX: Float
    val focalLengthY: Float

    // in deg
    val fovX: Float
    val fovY: Float

    // principal point (center of image)
    val cX: Float get() = resolution.width.toFloat() / 2
    val cY: Float get() = resolution.height.toFloat() / 2

    val sensorSize: SizeF
    val resolution: Size

    init {
        resolution = res

        val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
        val cameraManager = context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        // use factory-calibrated values if available (sadly only a few manufacturers implement this)
        // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_DISTORTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            characteristics.get(CameraCharacteristics.LENS_DISTORTION)?.let {
                distortionCoefficients = it
            }
        }

        // these should be available on all devices with a camera
        // throws exception if not
        focalLengthMM = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!![0]
        sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!

        // get focal length in pixels
        focalLengthX = (focalLengthMM / sensorSize.width) * res.width.toFloat()
        focalLengthY = (focalLengthMM / sensorSize.height) * res.height.toFloat()

        // calculate field of view in deg
        fovX = (2 * atan(sensorSize.width / (2 * focalLengthMM))) * (180 / PI).toFloat()
        fovY = (2 * atan(sensorSize.height / (2 * focalLengthMM))) * (180 / PI).toFloat()
    }

    companion object {
        val TAG = "CamLocCameraConfig"
    }
}