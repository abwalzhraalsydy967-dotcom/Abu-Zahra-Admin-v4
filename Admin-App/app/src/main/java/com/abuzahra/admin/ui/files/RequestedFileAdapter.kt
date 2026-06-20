package com.abuzahra.admin.ui.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.R
import com.abuzahra.admin.data.model.RemoteFile
import com.abuzahra.admin.databinding.ItemRequestedFileBinding
import com.abuzahra.admin.databinding.ItemRequestedFileGridBinding
import com.abuzahra.admin.util.FileUtils
import com.abuzahra.admin.util.ImageLoader
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter for the "Requested Files" list — files that devices have uploaded
 * to the server (photos, screenshots, recordings, etc.) and that auto-expire
 * after 1 hour.
 *
 * Supports two layouts:
 * - LIST (default): wide card with thumbnail + name + meta + view/download
 *   buttons.
 * - GRID: square cells with thumbnail (or type icon for non-image) + name
 *   underneath. Useful for browsing many images.
 *
 * For IMAGE files it asynchronously fetches a small thumbnail via
 * [ImageLoader.loadFileThumbnail] using the Bearer token from
 * [Preferences]. Failed or non-image files fall back to a static icon.
 *
 * In multi-select mode the row shows a checkbox on the leading edge and
 * the View/Download buttons are hidden.
 */
class RequestedFileAdapter(
    private val onViewClick: (RemoteFile) -> Unit,
    private val onDownloadClick: (RemoteFile) -> Unit,
    private val onLongClick: (RemoteFile) -> Unit = {},
    private val onSelectionToggle: (RemoteFile) -> Unit = {}
) : ListAdapter<RemoteFile, RecyclerView.ViewHolder>(DiffCallback()) {

    enum class ViewMode { LIST, GRID }

    private var viewMode: ViewMode = ViewMode.LIST
    private var selectionMode: Boolean = false
    private val selectedIds: MutableSet<String> = mutableSetOf()
    private val thumbScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    fun setViewMode(mode: ViewMode) {
        if (viewMode != mode) {
            viewMode = mode
            notifyDataSetChanged()
        }
    }

    fun getViewMode(): ViewMode = viewMode

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode != enabled) {
            selectionMode = enabled
            if (!enabled) selectedIds.clear()
            notifyDataSetChanged()
        }
    }

    fun isInSelectionMode(): Boolean = selectionMode

    fun toggleSelection(file: RemoteFile) {
        if (selectedIds.contains(file.id)) selectedIds.remove(file.id)
        else selectedIds.add(file.id)
        if (selectedIds.isEmpty()) {
            selectionMode = false
        }
        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()
    fun getSelectedCount(): Int = selectedIds.size

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(currentList.map { it.id })
        selectionMode = true
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        selectionMode = false
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (viewMode == ViewMode.GRID) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridViewHolder(ItemRequestedFileGridBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemRequestedFileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = getItem(position)
        when (holder) {
            is ListViewHolder -> holder.bind(file)
            is GridViewHolder -> holder.bind(file)
        }
    }

    inner class ListViewHolder(private val binding: ItemRequestedFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var thumbJob: Job? = null

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= currentList.size) return@click
                val f = getItem(pos)
                if (selectionMode) {
                    onSelectionToggle(f)
                    toggleSelection(f)
                } else {
                    onViewClick(f)
                }
            }
            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= currentList.size) return@setOnLongClickListener false
                val f = getItem(pos)
                onLongClick(f)
                toggleSelection(f)
                true
            }
            binding.btnView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < currentList.size) {
                    onViewClick(getItem(pos))
                }
            }
            binding.btnDownload.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < currentList.size) {
                    onDownloadClick(getItem(pos))
                }
            }
            binding.ivCheck.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < currentList.size) {
                    val f = getItem(pos)
                    onSelectionToggle(f)
                    toggleSelection(f)
                }
            }
        }

        fun bind(file: RemoteFile) {
            binding.tvFileName.text = file.displayName
            binding.tvFileMeta.text = buildMeta(file)
            binding.tvFileTime.text = file.uploadedAt ?: file.expiresAt ?: ""

            // Selection UI
            val selected = selectedIds.contains(file.id)
            binding.ivCheck.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.actionButtons.visibility = if (selectionMode) View.GONE else View.VISIBLE
            binding.ivCheck.setImageResource(
                if (selected) R.drawable.ic_check_box
                else R.drawable.ic_check_box_outline
            )
            binding.root.setBackgroundColor(
                if (selected) ContextCompat.getColor(itemView.context, R.color.surface_variant)
                else 0
            )

            // Thumbnail loading: images load a downscaled bitmap, videos
            // get an extracted frame (with a play badge overlay). Other
            // types fall back to a static icon.
            thumbJob?.cancel()
            val isImage = isImageType(file.fileType)
            val isVideo = isVideoType(file.fileType)
            if (isImage || isVideo) {
                binding.ivFileIcon.visibility = View.GONE
                binding.ivThumbnail.visibility = View.VISIBLE
                binding.thumbProgress.visibility = View.VISIBLE
                binding.ivThumbnail.setImageDrawable(null)
                thumbJob = thumbScope.launch {
                    val ctx = itemView.context
                    val prefs = Preferences.getInstance(ctx)
                    val bmp = withContext(Dispatchers.IO) {
                        if (isImage) {
                            ImageLoader.loadFileThumbnail(
                                prefs.serverUrl, prefs.token ?: "", file.id, 160
                            )
                        } else {
                            ImageLoader.loadVideoThumbnail(
                                prefs.serverUrl, prefs.token ?: "", file.id,
                                ctx.cacheDir, 160, file.size
                            )
                        }
                    }
                    if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@launch
                    if (bmp != null) {
                        binding.ivThumbnail.setImageBitmap(bmp)
                        binding.thumbProgress.visibility = View.GONE
                    } else {
                        // fallback
                        binding.ivThumbnail.visibility = View.GONE
                        binding.ivFileIcon.visibility = View.VISIBLE
                        binding.ivFileIcon.setImageResource(iconForType(file.fileType))
                        binding.thumbProgress.visibility = View.GONE
                    }
                }
            } else {
                binding.ivThumbnail.visibility = View.GONE
                binding.ivFileIcon.visibility = View.VISIBLE
                binding.ivFileIcon.setImageResource(iconForType(file.fileType))
                binding.thumbProgress.visibility = View.GONE
            }
        }
    }

    inner class GridViewHolder(private val binding: ItemRequestedFileGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var thumbJob: Job? = null

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= currentList.size) return@click
                val f = getItem(pos)
                if (selectionMode) {
                    onSelectionToggle(f)
                    toggleSelection(f)
                } else {
                    onViewClick(f)
                }
            }
            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= currentList.size) return@setOnLongClickListener false
                val f = getItem(pos)
                onLongClick(f)
                toggleSelection(f)
                true
            }
        }

        fun bind(file: RemoteFile) {
            binding.tvFileName.text = file.displayName
            binding.tvFileMeta.text = typeLabel(file.fileType)
            binding.tvFileTime.text = file.uploadedAt ?: file.expiresAt ?: ""

            val selected = selectedIds.contains(file.id)
            binding.ivCheck.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.ivCheck.setImageResource(
                if (selected) R.drawable.ic_check_box
                else R.drawable.ic_check_box_outline
            )

            thumbJob?.cancel()
            val isImage = isImageType(file.fileType)
            val isVideo = isVideoType(file.fileType)
            if (isImage || isVideo) {
                binding.ivFileIcon.visibility = View.GONE
                binding.ivThumbnail.visibility = View.VISIBLE
                binding.thumbProgress.visibility = View.VISIBLE
                binding.ivThumbnail.setImageDrawable(null)
                thumbJob = thumbScope.launch {
                    val ctx = itemView.context
                    val prefs = Preferences.getInstance(ctx)
                    val bmp = withContext(Dispatchers.IO) {
                        if (isImage) {
                            ImageLoader.loadFileThumbnail(
                                prefs.serverUrl, prefs.token ?: "", file.id, 240
                            )
                        } else {
                            ImageLoader.loadVideoThumbnail(
                                prefs.serverUrl, prefs.token ?: "", file.id,
                                ctx.cacheDir, 240, file.size
                            )
                        }
                    }
                    if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@launch
                    if (bmp != null) {
                        binding.ivThumbnail.setImageBitmap(bmp)
                        binding.thumbProgress.visibility = View.GONE
                    } else {
                        binding.ivThumbnail.visibility = View.GONE
                        binding.ivFileIcon.visibility = View.VISIBLE
                        binding.ivFileIcon.setImageResource(iconForType(file.fileType))
                        binding.thumbProgress.visibility = View.GONE
                    }
                }
            } else {
                binding.ivThumbnail.visibility = View.GONE
                binding.ivFileIcon.visibility = View.VISIBLE
                binding.ivFileIcon.setImageResource(iconForType(file.fileType))
                binding.thumbProgress.visibility = View.GONE
            }
        }
    }

    private fun buildMeta(file: RemoteFile): String {
        val parts = mutableListOf<String>()
        parts.add(typeLabel(file.fileType))
        if (file.size > 0) parts.add(FileUtils.formatFileSize(file.size))
        if (!file.caption.isNullOrEmpty()) parts.add("«${file.caption}»")
        return parts.joinToString(" • ")
    }

    fun typeLabel(type: String): String = when (type.lowercase()) {
        "photo", "camera", "screenshot", "image" -> "صورة"
        "video" -> "فيديو"
        "audio" -> "صوت"
        "file" -> "ملف"
        else -> type.ifEmpty { "ملف" }
    }

    private fun isImageType(type: String): Boolean = when (type.lowercase()) {
        "photo", "camera", "screenshot", "image" -> true
        else -> false
    }

    private fun isVideoType(type: String): Boolean =
        type.lowercase() == "video"

    private fun iconForType(type: String): Int = when (type.lowercase()) {
        "photo", "camera", "screenshot", "image" -> R.drawable.ic_file_image
        "video" -> R.drawable.ic_file_video
        "audio" -> R.drawable.ic_file_audio
        // For plain "file" types, prefer the extension-derived icon if we
        // can derive one (e.g. a PDF or APK that came in as a generic file).
        else -> R.drawable.ic_file
    }

    private class DiffCallback : DiffUtil.ItemCallback<RemoteFile>() {
        override fun areItemsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem == newItem
    }

    companion object {
        const val TYPE_LIST = 0
        const val TYPE_GRID = 1
    }
}
