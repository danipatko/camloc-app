package com.dapa.camloc

import android.os.Bundle
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.dapa.camloc.activities.CameraBase
import com.dapa.camloc.databinding.ActivityMainBinding

class MainActivity : CameraBase() {

    private lateinit var binding: ActivityMainBinding

    // native function declarations
    external fun stringFromJNI(): String
    private external fun detectMarkers(matAddress: Long): Array<Marker>

    private lateinit var preview: Preview

    override fun onStartCamera(): UseCase {
        preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
        return preview
    }

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
    }

    // ---

    companion object {
        init {
            System.loadLibrary("camloc")
        }
        private const val TAG = "CameraXApp"
    }
}