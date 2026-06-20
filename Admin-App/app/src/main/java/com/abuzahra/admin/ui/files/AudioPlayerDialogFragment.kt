package com.abuzahra.admin.ui.files

import android.app.Dialog
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.util.ImageLoader
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

/**
 * AudioPlayerDialogFragment — a minimal audio player dialog using the
 * built-in [MediaPlayer]. Because MediaPlayer needs a URL/file path and
 * cannot attach Authorization headers, we first stream the audio into a
 * cache file via [ImageLoader.downloadToCache] and then play from there.
 *
 * Usage:
 * ```
 * AudioPlayerDialogFragment.newInstance(fileId, "recording_123.mp3")
 *     .show(supportFragmentManager, "audio")
 * ```
 */
class AudioPlayerDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "AudioPlayerDialog"
        private const val ARG_FILE_ID = "file_id"
        private const val ARG_FILE_NAME = "file_name"

        fun newInstance(fileId: String, fileName: String = ""): AudioPlayerDialogFragment {
            return AudioPlayerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_ID, fileId)
                    putString(ARG_FILE_NAME, fileName)
                }
            }
        }
    }

    private lateinit var prefs: Preferences
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var prepared = false
    private var cacheFile: File? = null

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrent: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvFileName: TextView
    private lateinit var tvError: TextView
    private lateinit var loading: ProgressBar

    private val updateRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer ?: return
            if (prepared) {
                try {
                    val pos = mp.currentPosition
                    seekBar.progress = pos
                    tvCurrent.text = formatTime(pos)
                } catch (_: Exception) { }
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        prefs = Preferences.getInstance(requireContext())
        val fileId = arguments?.getString(ARG_FILE_ID).orEmpty()
        val fileName = arguments?.getString(ARG_FILE_NAME).orEmpty()

        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_audio_player, null)
        tvFileName = view.findViewById(R.id.tvFileName)
        tvFileName.text = fileName.ifEmpty { "تسجيل صوتي" }
        loading = view.findViewById(R.id.loading)
        tvError = view.findViewById(R.id.tvError)
        seekBar = view.findViewById(R.id.seekBar)
        tvCurrent = view.findViewById(R.id.tvCurrent)
        tvTotal = view.findViewById(R.id.tvTotal)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnStop = view.findViewById(R.id.btnStop)

        btnPlayPause.isEnabled = false
        seekBar.isEnabled = false

        btnPlayPause.setOnClickListener {
            val mp = mediaPlayer
            if (mp == null || !prepared) return@setOnClickListener
            if (mp.isPlaying) {
                mp.pause()
                btnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                mp.start()
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }

        btnStop.setOnClickListener {
            mediaPlayer?.let { mp ->
                if (prepared) {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
            }
            mediaPlayer = null
            prepared = false
            dismiss()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && prepared) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .setOnCancelListener { cleanup() }
            .create()
        dialog.setCanceledOnTouchOutside(false)

        loadAudio(fileId)
        return dialog
    }

    private fun loadAudio(fileId: String) {
        if (fileId.isBlank()) {
            tvError.text = "معرّف الملف غير متوفر"
            tvError.visibility = View.VISIBLE
            return
        }
        loading.visibility = View.VISIBLE
        val ext = ".mp3"
        val target = File(requireContext().cacheDir, "audio_${fileId}$ext")
        cacheFile = target

        lifecycleScope.launch {
            val ok = try {
                ImageLoader.downloadToCache(
                    prefs.serverUrl, prefs.token ?: "", fileId, target
                )
            } catch (e: Exception) {
                Log.e(TAG, "Audio download failed", e)
                false
            }
            loading.visibility = View.GONE
            if (!ok || !target.exists() || target.length() == 0L) {
                tvError.text = "تعذر تحميل الصوت"
                tvError.visibility = View.VISIBLE
                return@launch
            }
            preparePlayer(target)
        }
    }

    private fun preparePlayer(file: File) {
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { m ->
                    prepared = true
                    val total = m.duration
                    seekBar.max = total
                    tvTotal.text = formatTime(total)
                    btnPlayPause.isEnabled = true
                    seekBar.isEnabled = true
                    m.start()
                    btnPlayPause.setImageResource(R.drawable.ic_pause)
                    handler.post(updateRunnable)
                }
                setOnCompletionListener {
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                    seekBar.progress = 0
                }
                setOnErrorListener { _, _, _ ->
                    tvError.text = "تعذر تشغيل الصوت"
                    tvError.visibility = View.VISIBLE
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer prepare failed", e)
            tvError.text = "خطأ في تشغيل الصوت: ${e.message}"
            tvError.visibility = View.VISIBLE
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) { }
        }
        mediaPlayer = null
        prepared = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cleanup()
    }
}
