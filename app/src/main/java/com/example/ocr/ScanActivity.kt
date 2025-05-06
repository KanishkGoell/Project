package com.application.ocr

/*  ──────────────────────────────────────────────────────────────────────────
 *  ScanActivity  – supports TEXT · MATH · RECEIPT modes
 *  ────────────────────────────────────────────────────────────────────────── */

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
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
import androidx.lifecycle.lifecycleScope
import com.application.ocr.databinding.ActivityScanBinding
import com.application.ocr.model.ReceiptRow
import com.application.ocr.utils.toBitmap
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCAN_TYPE = "extra_scan_type"
        const val SCAN_TYPE_DOCUMENT = 0
        const val SCAN_TYPE_RECEIPT = 1
        private const val TAG = "ScanActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()
    }

    /* ───────────────────── 3-mode enum ───────────────────── */
    private enum class Mode { TEXT, MATH, RECEIPT }
    private val priceRe = Regex("""\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?""")

    private var currentMode = Mode.TEXT

    /* ────────────── ML Kit & Tesseract ───────────── */
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var tess: TessBaseAPI
    private var tessInitialized = false
    private val trainedDataFiles = arrayOf("equ.traineddata", "eng.traineddata")

    /* ────────────── CameraX ───────────── */
    private lateinit var binding: ActivityScanBinding
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    /* ────────────── Permission & Activity-result launchers ───────────── */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) startCamera() else {
                toast(getString(R.string.camera_permission_required))
                finish()
            }
        }

    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                UCrop.getOutput(result.data!!)?.let { uri -> runOcr(uri) }
            } else showLoading(false)
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { startCrop(it) }
        }

    /* ───────────────────────── lifecycle ───────────────────────── */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) startCamera() else requestPermissions()
        setupUi()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // init Tesseract in background
        lifecycleScope.launch(Dispatchers.IO) { initTesseract() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::tess.isInitialized && tessInitialized) tess.end()
    }

    /* ───────────────────────── UI setup ───────────────────────── */
    private fun setupUi() = binding.apply {
        btnCapture.setOnClickListener { takePhoto() }
        btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        btnClose.setOnClickListener { finish() }
        btnFlash.setOnClickListener { toggleFlash() }

        toggleMode.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            currentMode = when (id) {
                R.id.btnMath    -> Mode.MATH
                R.id.btnReceipt -> Mode.RECEIPT
                else            -> Mode.TEXT
            }
            Snackbar.make(root,
                "${currentMode.name.lowercase().replaceFirstChar { it.uppercase() }} mode",
                Snackbar.LENGTH_SHORT
            ).show()

            if (currentMode == Mode.MATH && !tessInitialized) {
                Snackbar.make(root, "Initializing math engine…", Snackbar.LENGTH_LONG).show()
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!initTesseract()) withContext(Dispatchers.Main) {
                        toggleMode.check(R.id.btnText)
                        currentMode = Mode.TEXT
                        Snackbar.make(root,
                            "Math engine failed – reverted to Text mode",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /* ─────────────────────── CameraX helpers ─────────────────────── */
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use-case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash() {
        camera?.let {
            val enable = it.cameraInfo.torchState.value != TorchState.ON
            it.cameraControl.enableTorch(enable)
            binding.btnFlash.setImageResource(if (enable) R.drawable.ic_flash_on
            else R.drawable.ic_flash_off)
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
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
        capture.takePicture(output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    showLoading(false)
                    toast(exc.message ?: "Capture error")
                }
                override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                    cameraProvider?.unbindAll()
                    camera = null
                    out.savedUri?.let { startCrop(it) }
                }
            })
    }

    private fun startCrop(src: Uri) {
        val dst = Uri.fromFile(File(cacheDir, "CROP_${System.currentTimeMillis()}.jpg"))
        val opts = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setToolbarColor(ContextCompat.getColor(this@ScanActivity, R.color.color_green))
            setStatusBarColor(ContextCompat.getColor(this@ScanActivity, R.color.color_green))
        }
        cropLauncher.launch(
            UCrop.of(src, dst)
                .withOptions(opts)
                .withAspectRatio(0f, 0f)
                .getIntent(this)
        )
    }

    /* ─────────────────────── OCR dispatch ─────────────────────── */
    private fun runOcr(uri: Uri) {
        val bmp = uri.toBitmap(this) ?: run { toast("Cannot decode image"); return }
        val argb = ensureArgb8888(bmp)

        when (currentMode) {
            Mode.TEXT    -> runMlKitOcr(argb, uri)
            Mode.MATH    -> runMathOcr(argb, uri)
            Mode.RECEIPT -> runReceiptOcr(argb, uri)
        }

        if (argb != bmp) bmp.recycle()
    }

    /* ──────────────────────── TEXT OCR ──────────────────────── */
    private fun runMlKitOcr(bmp: Bitmap, uri: Uri) {
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener {
                showLoading(false)
                if (it.text.isNotEmpty()) navigateToDocumentViewer(it.text, uri.toString())
                else toast("No text detected")
            }
            .addOnFailureListener { e ->
                showLoading(false)
                toast("OCR failed: ${e.message}")
            }
    }

    /* ──────────────────────── MATH OCR ──────────────────────── */
    private fun runMathOcr(bmp: Bitmap, uri: Uri) = lifecycleScope.launch(Dispatchers.IO) {
        if (!tessInitialized) {
            withContext(Dispatchers.Main) { runMlKitOcr(bmp, uri) }
            return@launch
        }

        val latex = withContext(Dispatchers.Default) {
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
            tess.setImage(preprocessImageForMathOcr(bmp))
            tess.utF8Text?.trim().orEmpty()
        }

        withContext(Dispatchers.Main) {
            showLoading(false)
            if (latex.isBlank()) runMlKitOcr(bmp, uri)
            else navigateToDocumentViewer(formatMathResult(latex), uri.toString())
        }
    }

    /* ──────────────────────── RECEIPT OCR ──────────────────────── */
    private fun runReceiptOcr(bmp: Bitmap, uri: Uri) {
        showLoading(true)
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { result ->
                val rows = extractRows(result)
                showLoading(false)
                if (rows.isEmpty()) toast("Couldn’t find a price column")
                else navigateToReceiptViewer(rows, uri.toString())
            }
            .addOnFailureListener { e ->
                showLoading(false)
                toast("Receipt OCR error: ${e.message}")
            }
    }

    /** Normalize and parse a string like “1.234,56” or “1,234.56” → Double */
    private fun String.toPriceDouble(): Double =
        this
            .replace(Regex("""[.,](?=\d{3}(?:[.,]|$))"""), "")
            .replace(',', '.')
            .toDoubleOrNull() ?: 0.0

    private fun parseReceipt(raw: String): ArrayList<ReceiptRow> {
        val rows = arrayListOf<ReceiptRow>()
        raw.lines().forEach { ln ->
            val parts = ln.trim().split(Regex("\\s+"))
            if (parts.size >= 2 && priceRe.matches(parts.last())) {
                val item = parts.dropLast(1).joinToString(" ")
                val price = parts.last().toPriceDouble()
                if (price > 0.0) {
                    rows += ReceiptRow(item, price)
                }
            }
        }
        return rows
    }

    private fun extractRows(txt: com.google.mlkit.vision.text.Text): ArrayList<ReceiptRow> {
        data class Token(val text: String, val box: Rect)

        // 1. flatten ML-Kit hierarchy into individual tokens
        val tokens = mutableListOf<Token>()
        txt.textBlocks.forEach { blk ->
            blk.lines.forEach { ln ->
                ln.elements.forEach { el ->
                    tokens += Token(el.text, el.boundingBox ?: Rect())
                }
            }
        }

        // 2. cluster tokens by approximate vertical position
        val bandH = 25
        val bands = mutableMapOf<Int, MutableList<Token>>()
        tokens.forEach { t ->
            val key = bands.keys.firstOrNull { abs(it - t.box.top) < bandH }
                ?: t.box.top
            bands.getOrPut(key) { mutableListOf() } += t
        }

        // 3. build ReceiptRow for each band
        val rows = arrayListOf<ReceiptRow>()
        bands.values
            .sortedBy { it.first().box.top }
            .forEach { band ->
                val sorted = band.sortedBy { it.box.left }
                val lastTok = sorted.lastOrNull() ?: return@forEach
                if (!priceRe.matches(lastTok.text)) return@forEach

                val item = sorted.dropLast(1).joinToString(" ") { it.text }
                val price = lastTok.text.toPriceDouble()
                if (item.isNotBlank() && price > 0.0) {
                    rows += ReceiptRow(item, price)
                }
            }

        return rows
    }

    /* ─────────────────────── navigation ─────────────────────── */
    private fun navigateToDocumentViewer(text: String, imgPath: String) {
        Intent(this, DocumentViewerActivity::class.java).apply {
            putExtra(DocumentViewerActivity.EXTRA_TEXT_CONTENT, text)
            putExtra(DocumentViewerActivity.EXTRA_IMAGE_PATH, imgPath)
            putExtra(DocumentViewerActivity.EXTRA_IS_NEW_DOCUMENT, true)
        }.also { startActivity(it) }
        finish()
    }

    private fun navigateToReceiptViewer(rows: ArrayList<ReceiptRow>, imgPath: String) {
        Intent(this, ReceiptViewerActivity::class.java).apply {
            putExtra(ReceiptViewerActivity.EXTRA_TABLE, rows)
            putExtra(ReceiptViewerActivity.EXTRA_IMAGE_PATH, imgPath)
            putExtra(ReceiptViewerActivity.EXTRA_IS_NEW_RECEIPT, true)
        }.also { startActivity(it) }
    }

    /* ─────────────────────── helpers ─────────────────────── */
    private fun ensureArgb8888(src: Bitmap): Bitmap =
        if (src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, true)

    private fun showLoading(show: Boolean) = binding.apply {
        scanOverlay.alpha = if (show) 0.7f else 0.3f
        btnCapture.isEnabled = !show
        btnGallery.isEnabled = !show
        if (show) toast(getString(R.string.processing))
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun requestPermissions() =
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }

    /* ─────────────────────── Tesseract init & math helpers ─────────────────────── */
    private fun initTesseract(): Boolean = try {
        val tessDir = File(filesDir, "tessdata").apply { mkdirs() }
        trainedDataFiles.forEach { name ->
            val dst = File(tessDir, name)
            if (!dst.exists()) assets.open("tessdata/$name")
                .use { it.copyTo(dst.outputStream()) }
        }
        tess = TessBaseAPI()
        val ok = tess.init(filesDir.absolutePath, "equ") ||
                tess.init(filesDir.absolutePath, "eng")
        tess.setVariable("debug_file", "/dev/null")
        tessInitialized = ok
        ok
    } catch (e: Exception) {
        tessInitialized = false
        false
    }

    private fun preprocessImageForMathOcr(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(
            ColorMatrix().apply { setSaturation(0f) }
        )
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    private fun formatMathResult(raw: String) =
        raw.replace(" ", "")
            .replace("—", "-")
            .replace("–", "-")
            .replace("x", "×")
            .replace("X", "×")
}
