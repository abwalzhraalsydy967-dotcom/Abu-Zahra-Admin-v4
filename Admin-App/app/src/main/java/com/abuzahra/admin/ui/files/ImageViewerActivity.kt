package com.abuzahra.admin.ui.files

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.util.ImageLoader
import com.abuzahra.admin.util.Preferences
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/**
 * ImageViewerActivity — fullscreen image viewer with pinch-to-zoom and
 * drag, loaded from the server's authenticated `/api/files/{id}` endpoint.
 *
 * Implementation notes:
 * - We use a [ScaleGestureDetector] to capture pinch gestures and update
 *   a scale factor on a [Matrix] applied to the ImageView. We also handle
 *   drag ( ACTION_MOVE ) when the image is zoomed in so the user can pan.
 * - We deliberately avoid third-party libraries like PhotoView — the
 *   built-in Matrix + ScaleGestureDetector combo is enough for our needs.
 */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ImageViewerActivity"
        const val EXTRA_FILE_ID = "extra_file_id"
        const val EXTRA_FILE_NAME = "extra_file_name"

        fun newIntent(context: Context, fileId: String, fileName: String = ""): Intent {
            return Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_ID, fileId)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
        }
    }

    private enum class TouchMode { NONE, DRAG }

    private lateinit var prefs: Preferences
    private lateinit var ivImage: ImageView
    private lateinit var loading: ProgressBar
    private lateinit var tvError: TextView

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private var scale = 1f
    private val minScale = 1f
    private val maxScale = 5f

    private var lastX = 0f
    private var lastY = 0f
    private var mode = TouchMode.NONE

    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        prefs = Preferences.getInstance(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val name = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        toolbar.title = name.ifEmpty { "عارض الصور" }
        toolbar.setNavigationOnClickListener { finish() }

        ivImage = findViewById(R.id.ivImage)
        loading = findViewById(R.id.loading)
        tvError = findViewById(R.id.tvError)

        scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    mode = TouchMode.DRAG
                    savedMatrix.set(matrix)
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor = detector.scaleFactor
                    val newScale = scale * factor
                    if (newScale in minScale..maxScale) {
                        scale = newScale
                        matrix.postScale(factor, factor,
                            detector.focusX, detector.focusY)
                        ivImage.imageMatrix = matrix
                    }
                    return true
                }
            }
        )

        ivImage.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mode = TouchMode.DRAG
                    lastX = event.x
                    lastY = event.y
                    savedMatrix.set(matrix)
                    true
                }
                MotionEvent.ACTION_MOVE -> if (mode == TouchMode.DRAG && scale > 1f) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    matrix.set(savedMatrix)
                    matrix.postTranslate(dx, dy)
                    ivImage.imageMatrix = matrix
                    true
                } else false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = TouchMode.NONE
                    true
                }
                else -> false
            }
        }

        load()
    }

    private fun load() {
        val fileId = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        if (fileId.isBlank()) {
            tvError.text = "معرّف الملف غير متوفر"
            tvError.visibility = View.VISIBLE
            return
        }
        loading.visibility = View.VISIBLE
        ivImage.visibility = View.GONE

        lifecycleScope.launch {
            val bmp = try {
                ImageLoader.loadFileFull(prefs.serverUrl, prefs.token ?: "", fileId)
            } catch (e: Exception) {
                Log.e(TAG, "Image load failed", e)
                null
            }
            loading.visibility = View.GONE
            if (bmp != null) {
                ivImage.setImageBitmap(bmp)
                ivImage.imageMatrix = matrix
                ivImage.visibility = View.VISIBLE
            } else {
                tvError.text = "تعذر تحميل الصورة"
                tvError.visibility = View.VISIBLE
            }
        }
    }
}
