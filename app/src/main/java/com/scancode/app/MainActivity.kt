package com.scancode.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView
    private lateinit var overlay: ScanOverlay
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var permissionButton: MaterialButton
    private lateinit var galleryButton: MaterialButton
    private lateinit var torchButton: MaterialButton
    private lateinit var zoom: Slider
    private lateinit var zoomValue: TextView
    private lateinit var history: History

    private val worker = Executors.newSingleThreadExecutor()
    private val scannerClient = lazy { BarcodeScanning.getClient() }
    private val scanner by scannerClient
    private val preferences by lazy { getSharedPreferences("scanner", MODE_PRIVATE) }
    private val uiExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private var camera: LifecycleCameraController? = null
    private var streamReady = false
    private var cameraFailed = false
    private var activityResumed = false
    private var blocked = false
    private var pickerOpen = false
    private var photoUri: Uri? = null
    private var currentResult: ScanResult? = null
    private var lastValue: String? = null
    private var lastDismissAt = 0L
    private var torchOn = false

    @Volatile
    private var acceptFrames = false

    private val cameraTimeout = Runnable {
        if (activityResumed && !streamReady && hasCameraPermission()) showCameraFailure()
    }

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else renderCamera()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            pickerOpen = false
            if (uri == null) setBlocked(false) else scanPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setInsetLayout(R.layout.activity_main)
        preview = findViewById(R.id.previewView)
        overlay = findViewById(R.id.scanOverlay)
        status = findViewById(R.id.cameraStatus)
        hint = findViewById(R.id.cameraHint)
        permissionButton = findViewById(R.id.permissionButton)
        galleryButton = findViewById(R.id.galleryButton)
        torchButton = findViewById(R.id.torchButton)
        zoom = findViewById(R.id.zoomSlider)
        zoomValue = findViewById(R.id.zoomValue)
        history = History(this)

        setupLayout()
        setupControls()
        renderCamera()

        pickerOpen = savedInstanceState?.getBoolean("picker") == true
        if (pickerOpen) setBlocked(true)
        val resultValue = savedInstanceState?.getString("resultValue")
        val resultFormat = savedInstanceState?.getString("resultFormat")
        val activePhoto = savedInstanceState?.getString("photoUri")
        when {
            resultValue != null && resultFormat != null -> present(
                ScanResult(resultValue, resultFormat, savedInstanceState.getLong("resultTime")),
            )
            activePhoto != null -> scanPhoto(activePhoto.toUri())
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (hasCameraPermission()) {
            if (!cameraFailed) startCamera()
        } else {
            releaseCamera()
            cameraFailed = false
        }
        renderCamera()
        if (!streamReady && !cameraFailed && hasCameraPermission()) scheduleCameraTimeout()
    }

    override fun onPause() {
        activityResumed = false
        acceptFrames = false
        preview.removeCallbacks(cameraTimeout)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("picker", pickerOpen)
        outState.putString("photoUri", photoUri?.toString())
        currentResult?.let {
            outState.putString("resultValue", it.value)
            outState.putString("resultFormat", it.format)
            outState.putLong("resultTime", it.time)
        }
    }

    override fun onDestroy() {
        releaseCamera()
        worker.shutdown()
        if (scannerClient.isInitialized()) scanner.close()
        super.onDestroy()
    }

    private fun setupLayout() {
        val root = findViewById<View>(R.id.root)
        val card = findViewById<View>(R.id.cameraCard)
        card.clipToOutline = true
        val footer = findViewById<View>(R.id.cameraFooter)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.scanTitle), true)
        root.limitContentWidth(findViewById(R.id.content), 660)
        root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val available = view.height - view.paddingTop - view.paddingBottom
            val desired = (available - view.dp(345)).coerceIn(view.dp(230), view.dp(530))
            if (card.layoutParams.height != desired) card.updateLayoutParams { height = desired }
        }

        val updateReticle = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            overlay.setReservedSpace(status.bottom, footer.height)
        }
        footer.addOnLayoutChangeListener(updateReticle)
        status.addOnLayoutChangeListener(updateReticle)
        overlay.shape = FrameShape.entries.firstOrNull {
            it.name == preferences.getString("frame", null)
        } ?: FrameShape.SQUARE
    }

    private fun setupControls() {
        permissionButton.setOnClickListener { enableLiveCamera() }
        galleryButton.setOnClickListener { pickPhoto() }
        torchButton.setOnClickListener { camera?.enableTorch(!torchOn) }
        findViewById<View>(R.id.frameButton).setOnClickListener { chooseFrame() }
        findViewById<View>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.privacyButton).setOnClickListener {
            setBlocked(true)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_body)
                .setNeutralButton(R.string.privacy_contact) { _, _ ->
                    openExternal(Intent(Intent.ACTION_SENDTO, "mailto:josearias210@gmail.com".toUri()))
                }
                .setPositiveButton(R.string.close, null)
                .setOnDismissListener { setBlocked(false) }
                .show()
        }

        zoom.value = 0f
        zoomValue.text = getString(R.string.zoom_value, 1f)
        zoom.addOnChangeListener { _, value, fromUser ->
            if (fromUser) camera?.setLinearZoom(value)
        }
        preview.previewStreamState.observe(this) { state ->
            streamReady = camera != null && state == PreviewView.StreamState.STREAMING
            if (streamReady) {
                preview.removeCallbacks(cameraTimeout)
                cameraFailed = false
            } else if (camera != null && activityResumed && !cameraFailed) {
                scheduleCameraTimeout()
            }
            renderCamera()
        }
    }

    private fun chooseFrame() {
        setBlocked(true)
        val shapes = FrameShape.entries
        val choices = shapes.map { getString(it.label) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.frame_size)
            .setSingleChoiceItems(choices, overlay.shape.ordinal) { dialog, position ->
                val shape = shapes[position]
                overlay.shape = shape
                preferences.edit { putString("frame", shape.name) }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { setBlocked(false) }
            .show()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun enableLiveCamera() {
        when {
            hasCameraPermission() -> {
                cameraFailed = false
                renderCamera()
                startCamera()
            }
            permissionNeedsSettings() -> openExternal(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            else -> {
                preferences.edit { putBoolean("askedCamera", true) }
                requestCamera.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun permissionNeedsSettings() = preferences.getBoolean("askedCamera", false) &&
        !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

    private fun startCamera() {
        if (camera != null) return
        val controller = LifecycleCameraController(this)
        camera = controller
        val analyzer = MlKitAnalyzer(
            listOf(scanner),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            uiExecutor,
        ) { result ->
            if (acceptFrames) onCameraCodes(result.getValue(scanner).orEmpty())
        }
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build(),
        )
        controller.setImageAnalysisAnalyzer(worker, object : ImageAnalysis.Analyzer {
            override fun getTargetCoordinateSystem() = analyzer.targetCoordinateSystem

            override fun updateTransform(matrix: Matrix?) = analyzer.updateTransform(matrix)

            override fun analyze(image: ImageProxy) {
                if (acceptFrames) analyzer.analyze(image) else image.close()
            }
        })

        controller.initializationFuture.addListener({
            if (camera !== controller || isDestroyed) return@addListener
            try {
                controller.initializationFuture.get()
                controller.cameraSelector = when {
                    controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> {
                        showCameraFailure()
                        return@addListener
                    }
                }
                controller.bindToLifecycle(this)
                preview.controller = controller
                controller.zoomState.observe(this) { state ->
                    if (state != null) {
                        zoom.value = state.linearZoom.coerceIn(0f, 1f)
                        zoomValue.text = getString(R.string.zoom_value, state.zoomRatio)
                        renderCamera()
                    }
                }
                controller.torchState.observe(this) { state ->
                    torchOn = state == TorchState.ON
                    torchButton.setText(if (torchOn) R.string.torch_on else R.string.torch)
                    torchButton.isSelected = torchOn
                }
            } catch (error: Exception) {
                Log.w("ScanCode", "Camera initialization failed", error)
                showCameraFailure()
            }
        }, uiExecutor)
        scheduleCameraTimeout()
    }

    private fun scheduleCameraTimeout() {
        preview.removeCallbacks(cameraTimeout)
        preview.postDelayed(cameraTimeout, 12_000)
    }

    private fun releaseCamera() {
        acceptFrames = false
        streamReady = false
        preview.removeCallbacks(cameraTimeout)
        val controller = camera ?: return
        camera = null
        preview.controller = null
        controller.zoomState.removeObservers(this)
        controller.torchState.removeObservers(this)
        controller.clearImageAnalysisAnalyzer()
        controller.unbind()
        torchOn = false
        torchButton.setText(R.string.torch)
        torchButton.isSelected = false
        zoom.value = 0f
        zoomValue.text = getString(R.string.zoom_value, 1f)
    }

    private fun showCameraFailure() {
        cameraFailed = true
        releaseCamera()
        renderCamera()
    }

    private fun renderCamera() {
        val granted = hasCameraPermission()
        val hardware = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val reading = photoUri != null
        status.setText(
            when {
                reading -> R.string.photo_scanning
                streamReady -> R.string.camera_ready
                granted && !cameraFailed && hardware -> R.string.camera_starting
                else -> R.string.camera_off
            },
        )
        hint.setText(
            when {
                !hardware || cameraFailed -> R.string.camera_unavailable
                !granted -> R.string.permission_hint
                else -> R.string.align_code
            },
        )
        permissionButton.isVisible = hardware && (!granted || cameraFailed) && !reading
        permissionButton.setText(
            when {
                granted -> R.string.retry_camera
                permissionNeedsSettings() -> R.string.camera_settings
                else -> R.string.enable_camera
            },
        )
        overlay.live = streamReady
        galleryButton.isEnabled = !reading
        torchButton.isEnabled = streamReady && camera?.cameraInfo?.hasFlashUnit() == true
        zoom.isEnabled = streamReady && camera?.zoomState?.value?.let { it.maxZoomRatio > it.minZoomRatio } == true
        acceptFrames = activityResumed && streamReady && !blocked
    }

    private fun setBlocked(value: Boolean) {
        blocked = value
        acceptFrames = activityResumed && streamReady && !value
    }

    private fun onCameraCodes(barcodes: List<Barcode>) {
        val cooldown = SystemClock.elapsedRealtime() - lastDismissAt < 2400
        val result = barcodes.mapNotNull { code ->
            val box = code.boundingBox ?: return@mapNotNull null
            if (!overlay.containsCenter(box) || cooldown && code.rawValue == lastValue) return@mapNotNull null
            code.toResult()?.let { it to overlay.distanceFromCenter(box) }
        }.minByOrNull { it.second }?.first ?: return
        preview.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        present(result)
    }

    private fun pickPhoto() {
        if (blocked) return
        setBlocked(true)
        pickerOpen = true
        try {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            pickerOpen = false
            setBlocked(false)
            message(R.string.photo_picker_unavailable)
        }
    }

    private fun scanPhoto(uri: Uri) {
        setBlocked(true)
        photoUri = uri
        renderCamera()
        val resolver = contentResolver
        worker.execute {
            val bitmap = runCatching { PhotoDecoder.read(resolver, uri) }.getOrNull()
            uiExecutor.execute deliver@{
                if (isDestroyed) {
                    bitmap?.recycle()
                    return@deliver
                }
                if (bitmap == null) {
                    finishPhoto(null)
                } else {
                    scanner.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                        bitmap.recycle()
                        if (!isDestroyed) finishPhoto(if (task.isSuccessful) task.result else null)
                    }
                }
            }
        }
    }

    private fun finishPhoto(barcodes: List<Barcode>?) {
        photoUri = null
        renderCamera()
        val results = barcodes.orEmpty().mapNotNull { it.toResult() }.distinctBy { it.value }
        when (results.size) {
            0 -> {
                setBlocked(false)
                message(if (barcodes == null) R.string.photo_unreadable else R.string.no_code_in_image)
            }
            1 -> present(results.single())
            else -> {
                var selected = false
                val labels = results.map { it.summary(this) }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.choose_detected_code)
                    .setItems(labels) { _, position ->
                        selected = true
                        present(results[position])
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { if (!selected) setBlocked(false) }
                    .show()
            }
        }
    }

    private fun present(result: ScanResult) {
        setBlocked(true)
        currentResult = result
        showResult(result, history, onDismiss = {
            currentResult = null
            lastValue = result.value
            lastDismissAt = SystemClock.elapsedRealtime()
            setBlocked(false)
        })
    }
}
