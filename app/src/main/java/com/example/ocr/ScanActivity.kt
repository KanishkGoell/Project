package com.application.ocr

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.application.ocr.databinding.ActivityScanBinding
import com.application.ocr.utils.toBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScanActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    /* ───────────────────────── FIELDS ────────────────────────── */
    private lateinit var binding: ActivityScanBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null                    // needed for flash
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /* ──────────────────────── PERMISSION LAUNCHER ─────────────── */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) startCamera()
            else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    /* ───────────────────────── CROP LAUNCHER ──────────────────── */
    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                UCrop.getOutput(result.data!!)?.let { runOcr(it) }
            }
            showLoading(false)
        }

    /* ───────────────────────── GALLERY PICKER ─────────────────── */
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { startCrop(it) }
        }

    /* ───────────────────────── ACTIVITY LIFECYCLE ─────────────── */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) startCamera() else requestPermissions()
        setupUi()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    /* ───────────────────────── UI HELPERS ─────────────────────── */
    private fun setupUi() = binding.apply {
        btnCapture.setOnClickListener { takePhoto() }
        btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        btnClose.setOnClickListener { finish() }
        btnFlash.setOnClickListener { toggleFlash() }
    }

    private fun toggleFlash() {
        camera?.let {
            val enable = !it.cameraInfo.torchState.value!!.equals(TorchState.ON)
            it.cameraControl.enableTorch(enable)
            binding.btnFlash.setImageResource(
                if (enable) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        }
    }

    private fun showLoading(show: Boolean) = binding.apply {
        scanOverlay.alpha = if (show) 0.7f else 0.3f
        btnCapture.isEnabled = !show
        btnGallery.isEnabled = !show
        if (show) Toast.makeText(this@ScanActivity, R.string.processing, Toast.LENGTH_SHORT).show()
    }

    /* ───────────────────────── CAMERA SETUP ───────────────────── */
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /* ───────────────────────── CAPTURE PHOTO ──────────────────── */
    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OCRScanner")
        }

        val output = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .build()

        showLoading(true)
        capture.takePicture(
            output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    showLoading(false)
                    Toast.makeText(this@ScanActivity, exc.message, Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                    out.savedUri?.let { startCrop(it) }
                }
            })
    }

    /* ───────────────────────── CROP & OCR ─────────────────────── */
    private fun startCrop(src: Uri) {
        val dst = Uri.fromFile(File(cacheDir, "CROP_${System.currentTimeMillis()}.jpg"))
        val opts = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setToolbarColor(ContextCompat.getColor(this@ScanActivity, R.color.color_green))
            setStatusBarColor(ContextCompat.getColor(this@ScanActivity, R.color.color_green))
        }
        val intent = UCrop.of(src, dst)
            .withOptions(opts)
            .withAspectRatio(0f, 0f)
            .getIntent(this)

        cropLauncher.launch(intent)
    }

    private fun runOcr(uri: Uri) {
        val bmp = uri.toBitmap(this) ?: run {
            Toast.makeText(this, "Cannot decode image", Toast.LENGTH_SHORT).show(); return
        }
        val image = InputImage.fromBitmap(bmp, 0)
        showLoading(true)

        recognizer.process(image)
            .addOnSuccessListener { res ->
                showLoading(false)
                if (res.text.isNotEmpty())
                    navigateToDocumentViewer(res.text, uri.toString())
                else Toast.makeText(this, "No text detected", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "OCR failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /* ───────────────────────── NAVIGATION ─────────────────────── */
    private fun navigateToDocumentViewer(text: String, imgPath: String) {
        startActivity(
            Intent(this, DocumentViewerActivity::class.java).apply {
                putExtra(DocumentViewerActivity.EXTRA_TEXT_CONTENT, text)
                putExtra(DocumentViewerActivity.EXTRA_IMAGE_PATH, imgPath)
                putExtra(DocumentViewerActivity.EXTRA_IS_NEW_DOCUMENT, true)
            }
        )
        finish()
    }

    /* ───────────────────────── PERMISSIONS ────────────────────── */
    private fun requestPermissions() = requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
