package com.dapa.camloc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.atan

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraConfig(context: Context) {
    data class CameraParams(val id: String, val lensFacing: Int, val fovX: Float, val fovY: Float)

    private val context: Context
    private lateinit var cameraParams: Map<String, CameraParams>

    var positionX: Float = 0f
    var positionY: Float = 0f
    var rotation: Float = 0f

    val config: FloatArray
        get() {
            return floatArrayOf(positionX, positionY, rotation, cameraParams["0"]?.fovX ?: 0f)
        }

    init {
        this.context = context

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

        // load shared preferences
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE).let {
            positionX = it.getFloat(SHARED_POSITION_X, 0f)
            positionY = it.getFloat(SHARED_POSITION_Y, 0f)
            rotation = it.getFloat(SHARED_ROTATION, 0f)

            Log.d(TAG, "$positionX $positionY $rotation")
        }
    }

    fun set(positionX: Float, positionY: Float, rotation: Float) {
        this.positionX = positionX
        this.positionY = positionY
        this.rotation = rotation
        save()
    }

    fun set(payload: ByteArray) {
        ByteBuffer.allocate(payload.size).put(payload).apply {
            positionX = getFloat(0)
            positionY = getFloat(4)
            rotation = getFloat(8)
            save()
        }
    }

    // save preferences
    fun save() {
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE).apply{
            edit {
                putFloat(SHARED_POSITION_X, positionX)
                putFloat(SHARED_POSITION_Y, positionY)
                putFloat(SHARED_ROTATION, rotation)
                apply()
                Toast.makeText(context, "Config saved", Toast.LENGTH_SHORT).show()
            }
        }
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
        const val SHARED_PREFS_NAME = "CamlocCameraConfig"
        const val SHARED_POSITION_X = "posx"
        const val SHARED_POSITION_Y = "posy"
        const val SHARED_ROTATION = "rot"
    }
}