package com.tnibler.cryptocam.video

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

            // --- НАЧАЛО ИЗМЕНЕНИЙ (Новый API для Android 11+) ---

            // На Android 11+ используем новый API, чтобы увидеть скрытые камеры
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Этот вызов требует Executor.
                cameraManager.registerAvailabilityCallback(Executors.newSingleThreadExecutor(),
                    object : CameraManager.AvailabilityCallback() {
                        override fun onCameraAccessPrioritiesChanged() {
                            // Нам это не нужно, но метод нужно переопределить
                        }

                        // Этот метод будет вызван со списком ВСЕХ камер
                        override fun onCameraAvailable(cameraId: String) {
                            super.onCameraAvailable(cameraId)
                            // Мы не можем обновить UI из этого потока, поэтому делаем это в основном
                            activity?.runOnUiThread {
                                updateCameraList(cameraManager)
                            }
                        }
                    })
            }

            // Для всех версий сначала показываем то, что доступно сразу
            updateCameraList(cameraManager)
            // --- КОНЕЦ ИЗМЕНЕНИЙ ---

            // Создаем ListView для отображения результата
            val listView = ListView(requireContext()).apply {
                id = android.R.id.list // Даем ID, чтобы его можно было найти
            }
            return listView

        } catch (e: Exception) {
            textView.text = "Error accessing camera info:\n${e.message}"
            return textView
        }
    }

    // Выносим логику обновления списка в отдельную функцию
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