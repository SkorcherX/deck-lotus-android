package com.decklotus.companion.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

object PhysicalCameraSelector {
    data class PhysicalLensInfo(
        val logicalId: String,
        val physicalId: String,
        val focalLengths: FloatArray,
        val sensorResolution: String
    )

    /**
     * Find available rear camera physical IDs on Pixel 10 Pro.
     */
    fun getRearPhysicalCameras(context: Context): List<PhysicalLensInfo> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val result = mutableListOf<PhysicalLensInfo>()

        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                val physicalIds = chars.physicalCameraIds
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf()
                val pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val resString = if (pixelArray != null) "${pixelArray.width}x${pixelArray.height}" else "Unknown"

                if (physicalIds.isNotEmpty()) {
                    for (pid in physicalIds) {
                        result.add(PhysicalLensInfo(id, pid, focalLengths, resString))
                    }
                } else {
                    result.add(PhysicalLensInfo(id, id, focalLengths, resString))
                }
            }
        }
        return result
    }
}