package com.af010.creator.docreader.ui.main.scan.fragment

import android.content.res.ColorStateList
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentScanCameraBinding
import com.af010.creator.docreader.ui.dialog.MessageDialog
import com.af010.creator.docreader.ui.main.scan.ScanActivity
import com.af010.creator.docreader.ui.main.scan.ScanSessionViewModel
import com.af010.creator.docreader.ui.main.scan.detection.AutoCaptureTrigger
import com.af010.creator.docreader.ui.main.scan.detection.CornerStabilizer
import com.af010.creator.docreader.ui.main.scan.detection.DocumentCorners
import com.af010.creator.docreader.ui.main.scan.detection.DocumentEdgeDetector
import com.af010.creator.docreader.ui.main.scan.detection.DocumentImageProcessor
import com.af010.creator.docreader.ui.main.scan.detection.YPlaneMatConverter
import com.af010.creator.docreader.utils.PermissionSettingsWatcher
import com.af010.creator.docreader.utils.extensions.setCompoundDrawablesSized
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.bumptech.glide.Glide
import com.ui.baselib.extensions.click
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.opencv.core.Point

/**
 * Live camera with the document's edges drawn over the preview. In Auto mode the photo is taken
 * once the edges hold still; either way the shot goes to the crop screen with the edges found.
 */
@OptIn(TransformExperimental::class)
class ScanCameraFragment :
    BaseAppFragment<FragmentScanCameraBinding>(FragmentScanCameraBinding::inflate) {
    override val isFullSc: Boolean
        get() = true

    private val viewModel: ScanSessionViewModel by activityViewModel()

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    private val detector = DocumentEdgeDetector()
    private val stabilizer = CornerStabilizer()
    private val autoCapture = AutoCaptureTrigger()
    private val isCapturing = AtomicBoolean(false)

    @Volatile
    private var autoMode = true
    private var flashOn = false

    @Volatile
    private var liveCorners: DocumentCorners? = null

    @Volatile
    private var liveRotationDegrees = 0

    private val imageTransformFactory = ImageProxyTransformFactory().apply {
        isUsingCropRect = false
        isUsingRotationDegrees = false
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val askedByUser = permissionAskedByUser
            permissionAskedByUser = false
            when {
                granted -> showCamera()
                // The system no longer asks (denied twice): only the app's settings can grant it.
                askedByUser && !couldAskAgain && !canAskAgain() -> openAppSettings()
                else -> showPermissionPanel(true)
            }
        }

    // onResume picks up the result.
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private var permissionAskedByUser = false
    private var couldAskAgain = false

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importFromGallery(uri)
        }

    override fun FragmentScanCameraBinding.initView() {
        topBar.updatePadding(top = statusBarHeight)
        isCapturing.set(false)
        liveCorners = null
        setControlsEnabled(true)
        setAutoMode(autoMode)
        updateFlashIcon()
        showHint(holding = false)

        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            showScannedPages(pages.lastOrNull()?.output, pages.size)
        }

        if (hasCameraPermission()) previewView.post { startCamera() }
        else requestCameraPermission(byUser = false)
    }

    override fun onResume() {
        super.onResume()
        // Back from the app's settings with the permission granted.
        val waiting = bindingOrNull?.layoutPermission?.isVisible == true
        if (waiting && hasCameraPermission()) showCamera()
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        appContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun canAskAgain() = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

    private fun requestCameraPermission(byUser: Boolean) {
        permissionAskedByUser = byUser
        couldAskAgain = canAskAgain()
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showCamera() {
        showPermissionPanel(false)
        startCamera()
    }

    /** Without the camera the screen stays open: the gallery still works. */
    private fun showPermissionPanel(show: Boolean) {
        val binding = bindingOrNull ?: return
        binding.layoutPermission.isVisible = show
        binding.scanHint.isVisible = !show
    }

    private fun openAppSettings() {
        val activity = activity ?: return
        settingsLauncher.launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", activity.packageName, null))
        )
        PermissionSettingsWatcher.watch(activity) { hasCameraPermission() }
    }

    override fun FragmentScanCameraBinding.onClick() {
        btnClose.click { close() }
        thumbnailContainer.click { openPages() }
        btnFlash.click { toggleFlash() }
        btnAllowCamera.click { requestCameraPermission(byUser = true) }
        btnAuto.click { setAutoMode(true) }
        btnManual.click { setAutoMode(false) }
        captureButton.click { capture() }
        btnGallery.click {
            if (!isCapturing.get()) {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    override fun onBackPressed(): Boolean {
        close()
        return true
    }

    /** Leaves the scanner; pages already scanned are only dropped once the user agrees. */
    private fun close() {
        if (viewModel.pageList.isEmpty()) return leave()
        MessageDialog(
            requireContext(), R.string.discard_changes_title, R.string.scanner_discard_message,
            R.string.discard, R.string.file_action_cancel
        ) { leave() }.show()
    }

    private fun leave() {
        viewModel.clear()
        activity?.finish()
    }

    private fun openPages() {
        if (isCapturing.get()) return
        viewModel.retakeIndex = null
        navigateTo(ScanActivity.ROUTE_FILTERS)
    }

    private fun setAutoMode(enabled: Boolean) {
        autoMode = enabled
        autoCapture.reset()
        binding.btnAuto.setModeSelected(enabled)
        binding.btnManual.setModeSelected(!enabled)
    }

    private fun TextView.setModeSelected(selected: Boolean) {
        isSelected = selected
    }

    /** The pill over the preview: "Hold still…" with a green dot, or a plain prompt. */
    private fun showHint(holding: Boolean, capturing: Boolean = false) {
        val hint = bindingOrNull?.scanHint ?: return
        val context = hint.context
        hint.setText(
            when {
                capturing -> R.string.scanner_hint_scanning
                holding -> R.string.scan_edges_found
                else -> R.string.scanner_hint_searching
            }
        )
        // A green dot while the page holds still and the scan is about to happen.
        hint.setCompoundDrawablesWithIntrinsicBounds(if (holding || capturing) R.drawable.ic_dot_green else 0, 0, 0, 0)
    }

    private fun toggleFlash() {
        flashOn = !flashOn
        imageCapture?.flashMode = currentFlashMode()
        updateFlashIcon()
    }

    private fun currentFlashMode() =
        if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    private fun updateFlashIcon() {
        binding.btnFlash.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), if (flashOn) R.color.accent else R.color.white)
        )
        binding.btnFlash.isSelected = flashOn
    }

    private fun showScannedPages(lastPage: File?, count: Int) {
        binding.thumbnailContainer.isVisible = lastPage != null && count > 0
        if (lastPage == null || count <= 0) return
        binding.tvPageCount.text = count.toString()
        Glide.with(this).load(lastPage).centerCrop().into(binding.ivThumbnail)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(appContext())
        providerFuture.addListener({
            val binding = bindingOrNull ?: return@addListener
            try {
                val provider = providerFuture.get()
                val rotation = binding.previewView.display?.rotation ?: Surface.ROTATION_0
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(currentFlashMode())
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .build()
                imageCapture = captureUseCase
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(cameraExecutor) { image -> analyze(image) }
                    }
                provider.unbindAll()
                provider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, captureUseCase, analysis
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to start CameraX preview", exception)
                toast(R.string.scanner_capture_failed)
            }
        }, ContextCompat.getMainExecutor(appContext()))
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (isCapturing.get()) {
                autoCapture.reset()
                return
            }
            val mat = YPlaneMatConverter.toMat(image)
            try {
                val detected = try {
                    detector.detect(mat)
                } catch (exception: Exception) {
                    Log.e(TAG, "Document detection failed for frame", exception)
                    null
                }
                val stable = stabilizer.update(detected)
                val shown = stable?.expandedBy()
                liveCorners = shown
                liveRotationDegrees = image.imageInfo.rotationDegrees
                val holding = detected != null && stable != null
                val shouldCapture = autoMode &&
                        autoCapture.update(if (holding) stable else null, SystemClock.elapsedRealtime())

                val sourceTransform = imageTransformFactory.getOutputTransform(image)
                val overlay = bindingOrNull?.documentOverlay ?: return
                overlay.post {
                    val binding = bindingOrNull ?: return@post
                    if (!isCapturing.get()) showHint(holding)
                    if (shouldCapture) capture()
                    if (shown == null) {
                        binding.documentOverlay.setMappedPoints(null)
                        return@post
                    }
                    mapToPreview(shown, sourceTransform, 0)
                }
            } finally {
                mat.release()
            }
        } finally {
            image.close()
        }
    }

    private fun mapToPreview(corners: DocumentCorners, sourceTransform: OutputTransform, attempt: Int) {
        val binding = bindingOrNull ?: return
        val targetTransform = binding.previewView.outputTransform
        if (targetTransform == null) {
            // The preview is not laid out yet: try again shortly, a few times.
            if (attempt < 10) {
                binding.documentOverlay.postDelayed({
                    mapToPreview(corners, sourceTransform, attempt + 1)
                }, 50L)
            }
            return
        }
        val mapped = FloatArray(8)
        corners.points.forEachIndexed { index, point ->
            mapped[index * 2] = point.x.toFloat()
            mapped[index * 2 + 1] = point.y.toFloat()
        }
        CoordinateTransform(sourceTransform, targetTransform).mapPoints(mapped)
        binding.documentOverlay.setMappedPoints(mapped)
    }

    private fun capture() {
        if (!hasCameraPermission()) {
            requestCameraPermission(byUser = true)
            return
        }
        val capture = imageCapture ?: return
        if (!isCapturing.compareAndSet(false, true)) return
        val cropCorners = liveCorners?.toUprightFractions(liveRotationDegrees)
        setControlsEnabled(false)
        showHint(holding = false, capturing = true)

        val original = newScanFile()
        val options = ImageCapture.OutputFileOptions.Builder(original).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(appContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    openCrop(original, cropCorners)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exception)
                    if (isSafeToUpdateUI) showCaptureError()
                }
            })
    }

    private fun importFromGallery(uri: Uri) {
        if (!isCapturing.compareAndSet(false, true)) return
        setControlsEnabled(false)
        val resolver = appContext().contentResolver
        val original = newScanFile()
        viewLifecycleOwner.lifecycleScope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { input ->
                        original.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }.onFailure { Log.e(TAG, "Unable to import gallery image", it) }.getOrDefault(false)
            }
            if (copied) openCrop(original, null) else showCaptureError()
        }
    }

    private fun openCrop(original: File, cropCorners: List<Point>?) {
        val owner = viewLifecycleOwnerLiveData.value ?: return
        owner.lifecycleScope.launch {
            val pending = withContext(Dispatchers.Default) {
                runCatching { DocumentImageProcessor().prepareCrop(original, cropCorners) }
                    .onFailure { Log.e(TAG, "Unable to read captured photo", it) }
                    .getOrNull()
            }
            if (pending == null) {
                original.delete()
                showCaptureError()
                return@launch
            }
            viewModel.pendingCrop = pending
            navigateTo(ScanActivity.ROUTE_CROP)
        }
    }

    private fun showCaptureError() {
        toast(R.string.scanner_capture_failed)
        setControlsEnabled(true)
        isCapturing.set(false)
        showHint(holding = false)
    }

    private fun newScanFile(): File =
        File(ScanActivity.scanDirectory(appContext()), "original_${System.currentTimeMillis()}.jpg")

    private fun setControlsEnabled(enabled: Boolean) {
        val binding = bindingOrNull ?: return
        binding.captureButton.isEnabled = enabled
        binding.btnGallery.isEnabled = enabled
        binding.btnManual.isEnabled = enabled
        binding.btnAuto.isEnabled = enabled
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private companion object {
        const val TAG = "ScanCamera"
    }
}
