package com.tnibler.system.video

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import java.util.concurrent.Executors

class CameraDebugFragment : Fragment() {

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val textView = TextView(requireContext()).apply {
            text = "Searching for cameras..."
            setPadding(32, 32, 32, 32)
        }

        try {
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // На Android 11+ используем новый API, чтобы увидеть скрытые камеры
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cameraManager.registerAvailabilityCallback(Executors.newSingleThreadExecutor(),
                    object : CameraManager.AvailabilityCallback() {
                        override fun onCameraAccessPrioritiesChanged() {
                        }

                        override fun onCameraAvailable(cameraId: String) {
                            super.onCameraAvailable(cameraId)
                            activity?.runOnUiThread {
                                updateCameraList(cameraManager)
                            }
                        }
                    })
            }

            // Для всех версий сначала показываем то, что доступно сразу
            updateCameraList(cameraManager)


            // Создаем ListView для отображения результата
            val listView = ListView(requireContext()).apply {
                id = android.R.id.list
            }
            return listView

        } catch (e: Exception) {
            textView.text = "Error accessing camera info:\n${e.message}"
            return textView
        }
    }

    private fun updateCameraList(cameraManager: CameraManager) {
        val cameraIds = cameraManager.cameraIdList

        if (cameraIds.isEmpty()) {
            view?.findViewById<TextView>(android.R.id.text1)?.text = "No cameras found on this device."
            return
        }

        val cameraInfoItems = cameraIds.map { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val lensFacing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }

            "ID: $id | Facing: $lensFacing | Focal: ${focalLengths?.minOrNull() ?: "N/A"}"
        }

        val listView = view?.findViewById<ListView>(android.R.id.list)
        if (listView != null) {
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cameraInfoItems)
            listView.adapter = adapter
        }
    }
}