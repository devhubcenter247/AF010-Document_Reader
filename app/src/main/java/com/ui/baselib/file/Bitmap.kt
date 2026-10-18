package com.ui.baselib.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.util.TypedValueCompat.dpToPx
import androidx.exifinterface.media.ExifInterface
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================================
// region Bitmap - Base64 Conversion
// ============================================================================

fun Bitmap.toBase64(
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100
): String {
    return ByteArrayOutputStream().use { stream ->
        compress(format, quality, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }
}

fun String.toBitmap(): Bitmap? = runCatching {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

fun String.toBitmapOrThrow(): Bitmap {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalArgumentException("Invalid base64 image string")
}

fun File.toBase64(): String {
    FileInputStream(this).use {
        val byte = it.readBytes()
        return Base64.encodeToString(byte, Base64.NO_WRAP)
    }
}

fun Uri.uriToBase64(context: Context): String {
    return try {
        val bytes = context.contentResolver.openInputStream(this)?.use { it.readBytes() } ?: return ""
        Base64.encodeToString(bytes, Base64.DEFAULT)
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

// endregion

// ============================================================================
// region Bitmap - Encode to Base64 with Scaling
// ============================================================================

/**
 * Encode a drawable resource to Base64.
 */
fun encodeResourceToBase64Simple(context: Context, resId: Int): String? {
    return try {
        val bitmap = BitmapFactory.decodeResource(context.resources, resId)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Encode a file path image to Base64.
 */
fun encodeFileToBase64Simple(path: String): String? {
    return try {
        val bitmap = BitmapFactory.decodeFile(path)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Download an image from URL and encode to Base64.
 */
suspend fun downloadUrlAsBase64Simple(url: String): String? = withContext(Dispatchers.IO) {
    try {
        // Same timeouts/cleanup as downloadBytes(); without them a stalled server hangs forever.
        val bytes = downloadBytes(url) ?: return@withContext null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Encode a drawable resource to Base64 after resizing.
 * @param maxDimPx the maximum of (width, height) after scaling (keeps aspect)
 */
fun encodeResourceToBase64(
    context: Context,
    resId: Int,
    maxDimPx: Int = 512,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 85,
    maxSizeKB: Int? = null
): String? = runCatching {
    decodeResourceScaled(context, resId, maxDimPx)?.let { bmp ->
        encodeBitmapToBase64(bmp, format, quality, maxSizeKB)
            .also { bmp.recycle() }
    }
}.getOrNull()

/**
 * Encode a file path image to Base64 after resizing.
 */
fun encodeFileToBase64(
    path: String,
    maxDimPx: Int = 512,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 85,
    maxSizeKB: Int? = null
): String? = runCatching {
    decodeFileScaled(path, maxDimPx)?.let { bmp ->
        val rotated = applyExifOrientationIfNeeded(path, bmp)
        if (rotated !== bmp) bmp.recycle()
        encodeBitmapToBase64(rotated, format, quality, maxSizeKB)
            .also { rotated.recycle() }
    }
}.getOrNull()

/**
 * Default WEBP compress format, using the non-deprecated lossy variant on API 30+
 * and falling back to the legacy [Bitmap.CompressFormat.WEBP] on older devices.
 */
@Suppress("DEPRECATION")
private fun defaultWebpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Bitmap.CompressFormat.WEBP_LOSSY
    else
        Bitmap.CompressFormat.WEBP

/**
 * Download an image (public URL), resize, then encode to Base64.
 */
suspend fun downloadUrlAsBase64(
    url: String,
    maxDimPx: Int = 512,
    format: Bitmap.CompressFormat = defaultWebpFormat(),
    quality: Int = 85,
    maxSizeKB: Int? = null
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = downloadBytes(url) ?: return@runCatching null
        decodeBytesScaled(bytes, maxDimPx)?.let { bmp ->
            encodeBitmapToBase64(bmp, format, quality, maxSizeKB)
                .also { bmp.recycle() }
        }
    }.getOrNull()
}

// endregion

// ============================================================================
// region Bitmap - Decode Helpers (Private)
// ============================================================================

private fun decodeResourceScaled(
    context: Context,
    resId: Int,
    maxDimPx: Int
): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(context.resources, resId, opts)
    val sample = computeInSampleSize(opts.outWidth, opts.outHeight, maxDimPx)
    val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
    val sampled = BitmapFactory.decodeResource(context.resources, resId, opts2) ?: return null
    return scaleDownIfNeeded(sampled, maxDimPx)
}

private fun decodeFileScaled(path: String, maxDimPx: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    val sample = computeInSampleSize(opts.outWidth, opts.outHeight, maxDimPx)
    val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
    val sampled = BitmapFactory.decodeFile(path, opts2) ?: return null
    return scaleDownIfNeeded(sampled, maxDimPx)
}

private fun decodeBytesScaled(bytes: ByteArray, maxDimPx: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    val sample = computeInSampleSize(opts.outWidth, opts.outHeight, maxDimPx)
    val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
    val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2) ?: return null
    return scaleDownIfNeeded(sampled, maxDimPx)
}

/** inSampleSize so that min(width, height) ≤ maxDimPx and memory stays low. */
private fun computeInSampleSize(w: Int, h: Int, maxDimPx: Int): Int {
    if (w <= 0 || h <= 0) return 1
    var sample = 1
    val maxSide = max(w, h)
    while (maxSide / (sample * 2) >= maxDimPx) {
        sample *= 2
    }
    return sample
}

/** If still larger than maxDimPx after sampling, scale precisely. */
private fun scaleDownIfNeeded(sampled: Bitmap, maxDimPx: Int): Bitmap {
    val w = sampled.width
    val h = sampled.height
    val maxSide = max(w, h)
    if (maxSide <= maxDimPx) return sampled
    val ratio = maxDimPx.toFloat() / maxSide.toFloat()
    val dstW = ceil(w * ratio).toInt().coerceAtLeast(1)
    val dstH = ceil(h * ratio).toInt().coerceAtLeast(1)
    val scaled = sampled.scale(dstW, dstH)
    if (scaled !== sampled) sampled.recycle()
    return scaled
}

/** Encode bitmap to Base64, optionally shrinking quality to fit maxSizeKB. */
private fun encodeBitmapToBase64(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    quality: Int,
    maxSizeKB: Int?
): String {
    val baos = ByteArrayOutputStream()
    var q = quality.coerceIn(0, 100)
    fun compressToStream(): Int {
        baos.reset()
        bitmap.compress(format, q, baos)
        return baos.size()
    }
    compressToStream()
    if (maxSizeKB != null) {
        val limit = max(1, maxSizeKB) * 1024
        while (baos.size() > limit && q > 40) {
            q = (q * 0.85f).toInt().coerceAtLeast(40)
            compressToStream()
        }
    }
    val bytes = baos.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/** Apply EXIF orientation for camera images loaded from file. */
private fun applyExifOrientationIfNeeded(path: String, src: Bitmap): Bitmap {
    return runCatching {
        val exif = ExifInterface(path)
        val ori = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (ori) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return src
        }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }.getOrDefault(src)
}

/** Simple byte downloader for public URLs. */
private fun downloadBytes(url: String): ByteArray? {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }
        conn.connect()
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.use { it.readBytes() }
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

// endregion

// ============================================================================
// region Uri - Bitmap Decoding
// ============================================================================

/** Decode full-size bitmap from Uri (careful with large images - OOM risk) */
fun Uri.decodeUriToBitmap(context: Context): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(context.contentResolver, this)
        ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, this)
    }
} catch (e: Exception) {
    e.printStackTrace(); null
}

/** Decode scaled bitmap from Uri (avoids OOM) */
fun Uri.decodeScaledBitmap(
    context: Context,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? = try {
    context.contentResolver.openInputStream(this)?.use { input ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(BufferedInputStream(input), null, opts)
        val sample = calculateInSampleSize(opts.outWidth, opts.outHeight, reqWidth, reqHeight)
        context.contentResolver.openInputStream(this)?.use { input2 ->
            val opts2 = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(BufferedInputStream(input2), null, opts2)
        }
    }
} catch (e: Exception) {
    e.printStackTrace(); null
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight &&
            (halfWidth / inSampleSize) >= reqWidth
        ) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

// endregion

// ============================================================================
// region Bitmap - Resize & Transform
// ============================================================================

fun Bitmap.resizeToWidth(targetWidth: Int): Bitmap {
    val ratio = targetWidth.toFloat() / width
    val newHeight = (height * ratio).roundToInt().coerceAtLeast(1)
    return scale(targetWidth, newHeight)
}

fun Bitmap.resizeToHeight(targetHeight: Int): Bitmap {
    val ratio = targetHeight.toFloat() / height
    val newWidth = (width * ratio).roundToInt().coerceAtLeast(1)
    return scale(newWidth, targetHeight)
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.flip(horizontal: Boolean = true): Bitmap {
    val matrix = Matrix().apply {
        if (horizontal) {
            preScale(-1f, 1f)
        } else {
            preScale(1f, -1f)
        }
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.crop(x: Int, y: Int, cropWidth: Int, cropHeight: Int): Bitmap {
    val safeX = x.coerceIn(0, width - 1)
    val safeY = y.coerceIn(0, height - 1)
    val safeWidth = cropWidth.coerceIn(1, width - safeX)
    val safeHeight = cropHeight.coerceIn(1, height - safeY)
    return Bitmap.createBitmap(this, safeX, safeY, safeWidth, safeHeight)
}

fun Bitmap.cropCenter(cropWidth: Int, cropHeight: Int): Bitmap {
    val x = (width - cropWidth) / 2
    val y = (height - cropHeight) / 2
    return crop(x, y, cropWidth, cropHeight)
}

fun Bitmap.cropToSquare(): Bitmap {
    val size = minOf(width, height)
    return cropCenter(size, size)
}

// endregion

// ============================================================================
// region Bitmap - File Operations
// ============================================================================

fun Bitmap.saveToFile(
    file: File,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100
): Result<File> = runCatching {
    FileOutputStream(file).use { stream ->
        compress(format, quality, stream)
        stream.flush()
    }
    file
}

fun Bitmap.saveToCacheDir(
    context: Context,
    fileName: String = "image_${System.currentTimeMillis()}",
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100
): Result<File> {
    val extension = when {
        format == Bitmap.CompressFormat.JPEG -> ".jpg"
        format == Bitmap.CompressFormat.PNG -> ".png"
        format.name.startsWith("WEBP") -> ".webp"
        else -> ".png"
    }
    val file = File(context.cacheDir, "$fileName$extension")
    return saveToFile(file, format, quality)
}

fun File.toBitmap(): Bitmap? = runCatching {
    BitmapFactory.decodeFile(absolutePath)
}.getOrNull()

fun File.toBitmap(options: BitmapFactory.Options): Bitmap? = runCatching {
    BitmapFactory.decodeFile(absolutePath, options)
}.getOrNull()

// endregion

// ============================================================================
// region Bitmap - From Resources
// ============================================================================

fun Context.bitmapFromDrawable(@DrawableRes resId: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(this, resId)
        ?: throw IllegalArgumentException("Drawable resource $resId not found")
    return drawable.toBitmap()
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }

    val width = if (intrinsicWidth > 0) intrinsicWidth else 1
    val height = if (intrinsicHeight > 0) intrinsicHeight else 1

    return createBitmap(width, height).also { bitmap ->
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
    }
}

fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
    return createBitmap(width, height).also { bitmap ->
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
    }
}

// endregion

// ============================================================================
// region View - Capture to Bitmap
// ============================================================================

fun View.capture(): Bitmap {
    require(width > 0 && height > 0) { "View must have positive dimensions" }
    return createBitmap(width, height).also { bitmap ->
        draw(Canvas(bitmap))
    }
}

fun View.captureOrNull(): Bitmap? {
    if (width <= 0 || height <= 0) return null
    return createBitmap(width, height).also { bitmap ->
        draw(Canvas(bitmap))
    }
}

fun View.captureWithBackground(backgroundColor: Int): Bitmap {
    require(width > 0 && height > 0) { "View must have positive dimensions" }
    return createBitmap(width, height).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)
        draw(canvas)
    }
}

inline fun View.captureAfterLayout(crossinline onCaptured: (Bitmap?) -> Unit) {
    if (width > 0 && height > 0) {
        onCaptured(captureOrNull())
        return
    }

    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            onCaptured(captureOrNull())
        }
    })
}

inline fun View.captureAfterLayout(
    context: Context,
    crossinline onFileCaptured: (Result<File>) -> Unit
) {
    captureAfterLayout { bitmap ->
        if (bitmap == null) {
            onFileCaptured(Result.failure(IllegalStateException("Failed to capture view")))
        } else {
            onFileCaptured(bitmap.saveToCacheDir(context, "captured_view_${System.currentTimeMillis()}"))
        }
    }
}

// endregion

// ============================================================================
// region ViewBinding - Capture to Bitmap
// ============================================================================

@MainThread
inline fun <T : ViewBinding> Context.captureBitmap(
    crossinline bindingFactory: (LayoutInflater) -> T,
    widthPx: Int? = null,
    heightPx: Int? = null,
    crossinline onBind: T.() -> Unit = {}
): Bitmap {
    val binding = bindingFactory(LayoutInflater.from(this)).apply(onBind)
    val root = binding.root
    val dm = resources.displayMetrics
    val fallback = (48 * dm.density).toInt()

    val desiredW = widthPx ?: root.layoutParams?.width?.takeIf { it > 0 } ?: fallback
    val desiredH = heightPx ?: root.layoutParams?.height?.takeIf { it > 0 } ?: fallback

    val parent = FrameLayout(this).apply {
        addView(root, ViewGroup.LayoutParams(desiredW, desiredH))
    }

    val wSpec = View.MeasureSpec.makeMeasureSpec(desiredW, View.MeasureSpec.EXACTLY)
    val hSpec = View.MeasureSpec.makeMeasureSpec(desiredH, View.MeasureSpec.EXACTLY)
    parent.measure(wSpec, hSpec)
    parent.layout(0, 0, desiredW, desiredH)

    return createBitmap(desiredW, desiredH).apply {
        density = dm.densityDpi
        Canvas(this).apply { parent.draw(this) }
    }
}

@MainThread
inline fun <T : ViewBinding> Context.captureBitmapScaled(
    crossinline bindingFactory: (LayoutInflater) -> T,
    maxWidthDp: Int = 32,
    maxHeightDp: Int = 40,
    crossinline onBind: T.() -> Unit = {}
): Bitmap {
    val dm = resources.displayMetrics
    val binding = bindingFactory(LayoutInflater.from(this)).apply(onBind)
    val root = binding.root

    val xmlW = root.layoutParams?.width?.takeIf { it > 0 }
        ?: dpToPx(maxWidthDp.toFloat(), dm).toInt()
    val xmlH = root.layoutParams?.height?.takeIf { it > 0 }
        ?: dpToPx(maxHeightDp.toFloat(), dm).toInt()

    val wSpec = View.MeasureSpec.makeMeasureSpec(xmlW, View.MeasureSpec.EXACTLY)
    val hSpec = View.MeasureSpec.makeMeasureSpec(xmlH, View.MeasureSpec.EXACTLY)
    root.measure(wSpec, hSpec)

    val w = root.measuredWidth.coerceAtLeast(1)
    val h = root.measuredHeight.coerceAtLeast(1)
    root.layout(0, 0, w, h)

    val original = createBitmap(w, h).apply {
        density = dm.densityDpi
        Canvas(this).apply { root.draw(this) }
    }

    val maxW = dpToPx(maxWidthDp.toFloat(), dm)
    val maxH = dpToPx(maxHeightDp.toFloat(), dm)
    val scale = min(maxW / w, maxH / h).coerceAtMost(1f)

    return if (scale < 1f) {
        val sw = (w * scale).roundToInt().coerceAtLeast(1)
        val sh = (h * scale).roundToInt().coerceAtLeast(1)
        original.scale(sw, sh).apply { density = dm.densityDpi }
    } else {
        original
    }
}

// endregion

// ============================================================================
// region Bitmap - Memory & Utils
// ============================================================================

val Bitmap.sizeInBytes: Int
    get() = allocationByteCount

val Bitmap.sizeInKB: Float
    get() = allocationByteCount / 1024f

val Bitmap.sizeInMB: Float
    get() = allocationByteCount / (1024f * 1024f)

fun Bitmap.copy(): Bitmap = copy(config ?: Bitmap.Config.ARGB_8888, true)

fun Bitmap.toMutable(): Bitmap {
    return if (isMutable) this else copy()
}

inline fun Bitmap.use(block: (Bitmap) -> Unit) {
    try {
        block(this)
    } finally {
        if (!isRecycled) recycle()
    }
}

// endregion

// ============================================================================
// region Bitmap - Scale with Type
// ============================================================================

enum class ScaleType {
    WIDTH,      // Scale based on width
    HEIGHT,     // Scale based on height
    MIN,        // Scale based on smaller side
    MAX,        // Scale based on larger side
    BOTH        // Force width = height = targetSize
}

fun Bitmap.scaleBitmap(
    targetSize: Int,
    scaleType: ScaleType = ScaleType.BOTH
): Bitmap {
    val width = this.width
    val height = this.height

    val (newWidth, newHeight) = when (scaleType) {
        ScaleType.WIDTH -> {
            val scale = targetSize.toFloat() / width
            Pair(targetSize, (height * scale).toInt())
        }
        ScaleType.HEIGHT -> {
            val scale = targetSize.toFloat() / height
            Pair((width * scale).toInt(), targetSize)
        }
        ScaleType.MIN -> {
            val minSide = min(width, height)
            val scale = targetSize.toFloat() / minSide
            Pair((width * scale).toInt(), (height * scale).toInt())
        }
        ScaleType.MAX -> {
            val maxSide = max(width, height)
            val scale = targetSize.toFloat() / maxSide
            Pair((width * scale).toInt(), (height * scale).toInt())
        }
        ScaleType.BOTH -> {
            Pair(targetSize, targetSize)
        }
    }

    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

// endregion
