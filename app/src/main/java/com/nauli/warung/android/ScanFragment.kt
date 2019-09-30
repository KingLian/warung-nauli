package com.nauli.warung.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.util.forEach
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.nauli.warung.android.databinding.FragmentScanBinding
import kotlinx.android.synthetic.main.fragment_scan.*


class ScanFragment : Fragment() {

    companion object {
        const val TAG = "ScanFragment"
        const val REQUEST_PERMISSION_CAMERA = 1729
    }

    private lateinit var binding: FragmentScanBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        FragmentScanBinding.inflate(inflater, container, false).also {
            binding = it
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cameraPreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
                startCameraPreview(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {

            }

            override fun surfaceCreated(holder: SurfaceHolder?) {

            }
        })
    }

    override fun onStart() {
        super.onStart()

        if (ContextCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION_CAMERA)
        } else {
            // TODO: Start live camera feed
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && requestCode == REQUEST_PERMISSION_CAMERA) {
            // TODO: Start live camera feed
        } else {
            NavHostFragment.findNavController(this).navigateUp()
        }
    }

    private fun startCameraPreview(desiredWidth: Int, desiredHeight: Int) {
        try {
            val cameraBkgHandler = Handler()

            val cameraManager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            cameraManager.cameraIdList.find {
                val characteristics = cameraManager.getCameraCharacteristics(it)
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)

                return@find cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_BACK
            }?.let {
                val cameraStateCallback = object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        val barcodeDetector = BarcodeDetector.Builder(context!!)
                            .setBarcodeFormats(Barcode.DATA_MATRIX or Barcode.PRODUCT or Barcode.CODABAR or Barcode.QR_CODE)
                            .build()

                        if (!barcodeDetector.isOperational) {
                            // TODO: Barcode detector is not operational!
                        }

                        val imageReader = ImageReader.newInstance(desiredWidth, desiredHeight, ImageFormat.JPEG, 1)
                        imageReader.setOnImageAvailableListener({ reader ->
                            val cameraImage = reader.acquireNextImage()

                            val buffer = cameraImage.planes.first().buffer
                            val bytes = ByteArray(buffer.capacity())
                            buffer.get(bytes)

                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.count(), null)
                            val frameToProcess = Frame.Builder().setBitmap(bitmap).build()
                            val barcodeResults = barcodeDetector.detect(frameToProcess)

                            if (barcodeResults.size() > 0) {
                                Log.d(TAG, "Barcode detected")
                                textBarcode.text = barcodeResults.valueAt(0).rawValue
                            } else {
                                Log.d(TAG, "No barcode found")
                                textBarcode.text = ""
                            }

                            cameraImage.close()

                        }, cameraBkgHandler)


                        val captureStateCallback = object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                builder.addTarget(binding.cameraPreview.holder.surface)
                                builder.addTarget(imageReader.surface)
                                session.setRepeatingRequest(builder.build(), null, null)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.d(TAG, "onConfigureFailed")
                            }
                        }

                        camera.createCaptureSession(
                            listOf(binding.cameraPreview.holder.surface, imageReader.surface),
                            captureStateCallback,
                            cameraBkgHandler
                        )
                    }

                    override fun onClosed(camera: CameraDevice) {
                        Log.d(TAG, "onClosed")
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.d(TAG, "onDisconnected")
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.d(TAG, "onError")
                    }
                }

                cameraManager.openCamera(it, cameraStateCallback, cameraBkgHandler)
                return
            }

            // TODO: No available camera found case

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.message)
        } catch (e: SecurityException) {
            Log.e(TAG, e.message)
        }
    }

}
