package com.alarmx.smartalarm.playback

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FlashlightController(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val cameraId: String? = findTorchCamera()
    private var job: Job? = null
    private var torchOn = false

    val isAvailable: Boolean get() = cameraId != null

    fun start(scope: CoroutineScope, enabled: Boolean) {
        if (!enabled || cameraId == null) return
        stop()
        job = scope.launch {
            while (isActive) {
                setTorch(true)
                delay(180)
                setTorch(false)
                delay(420)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        setTorch(false)
    }

    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        if (torchOn == on) return
        try {
            cameraManager?.setTorchMode(id, on)
            torchOn = on
        } catch (_: Exception) {
            torchOn = false
        }
    }

    private fun findTorchCamera(): String? {
        return try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }
}
