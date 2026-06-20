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
import com.abuzahra.admin.databinding.ItemFileBinding
import com.abuzahra.admin.util.FileUtils
import com.abuzahra.admin.util.ImageLoader
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileListAdapter(
    private val onFileClick: (RemoteFile) -> Unit,
    private val onDownloadClick: (RemoteFile) -> Unit,
    private val onParentClick: () -> Unit,
    private val onLongClick: (RemoteFile) -> Unit = {},
    private val onSelectionToggle: (RemoteFile) -> Unit = {}
) : ListAdapter<RemoteFile, FileListAdapter.FileViewHolder>(FileDiffCallback()) {

    private var showParent = false
    private var selectionMode = false
    private val selectedPaths: MutableSet<String> = mutableSetOf()
    private val thumbScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    fun setShowParent(show: Boolean) {
        val oldShow = showParent
        showParent = show
        if (oldShow != show) {
            notifyItemInserted(0)
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode != enabled) {
            selectionMode = enabled
            if (!enabled) selectedPaths.clear()
            notifyDataSetChanged()
        }
    }

    fun isInSelectionMode(): Boolean = selectionMode

    fun toggleSelection(file: RemoteFile) {
        if (selectedPaths.contains(file.path)) selectedPaths.remove(file.path)
        else selectedPaths.add(file.path)
        if (selectedPaths.isEmpty()) selectionMode = false
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedPaths.clear()
        selectedPaths.addAll(currentList.filter { !it.isDirectory }.map { it.path })
        selectionMode = true
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedPaths.clear()
        selectionMode = false
        notifyDataSetChanged()
    }

    fun getSelectedPaths(): Set<String> = selectedPaths.toSet()
    fun getSelectedCount(): Int = selectedPaths.size

    override fun getItemCount(): Int {
        return super.getItemCount() + if (showParent) 1 else 0
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && showParent) TYPE_PARENT else TYPE_FILE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding, viewType)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        if (position == 0 && showParent) {
            holder.bindParent()
        } else {
            val filePos = if (showParent) position - 1 else position
            holder.bind(getItem(filePos))
        }
    }

    inner class FileViewHolder(
        private val binding: ItemFileBinding,
        private val viewType: Int
    ) : RecyclerView.ViewHolder(binding.root) {

        private var thumbJob: Job? = null

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (pos == 0 && showParent) {
                    onParentClick()
                    return@setOnClickListener
                }
                if (viewType == TYPE_FILE) {
                    val filePos = if (showParent) pos - 1 else pos
                    if (filePos < 0 || filePos >= currentList.size) return@setOnClickListener
                    val file = getItem(filePos)
                    if (selectionMode) {
                        onSelectionToggle(file)
                        toggleSelection(file)
                    } else if (file.isDirectory) {
                        onFileClick(file)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                if (pos == 0 && showParent) return@setOnLongClickListener false
                if (viewType == TYPE_FILE) {
                    val filePos = if (showParent) pos - 1 else pos
                    if (filePos < 0 || filePos >= currentList.size) return@setOnLongClickListener false
                    val file = getItem(filePos)
                    if (!file.isDirectory) {
                        onLongClick(file)
                        toggleSelection(file)
                        true
                    } else false
                } else false
            }

            binding.ivDownload.setOnClickListener {
                val pos = bindingAdapterPosition
                if (viewType == TYPE_FILE && pos != RecyclerView.NO_POSITION) {
                    val filePos = if (showParent) pos - 1 else pos
                    if (filePos >= 0 && filePos < currentList.size) {
                        onDownloadClick(getItem(filePos))
                    }
                }
            }
        }

        fun bind(file: RemoteFile) {
            binding.tvFileName.text = file.name

            val ext = FileUtils.extensionOf(file)
            val icon = if (file.isDirectory) R.drawable.ic_folder
                       else FileUtils.iconForExtension(ext)
            binding.ivFileIcon.setImageResource(icon)
            binding.ivFileIcon.setColorFilter(
                ContextCompat.getColor(
                    itemView.context,
                    if (file.isDirectory) R.color.warning
                    else FileUtils.iconTintForExtension(ext)
                )
            )

            binding.tvFileDetails.text = buildFileDetails(file, ext)

            binding.ivDownload.visibility = if (file.isDirectory || selectionMode) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Selection checkbox
            val selected = selectedPaths.contains(file.path)
            binding.ivCheck.visibility = if (selectionMode && !file.isDirectory) View.VISIBLE else View.GONE
            binding.ivCheck.setImageResource(
                if (selected) R.drawable.ic_check_box
                else R.drawable.ic_check_box_outline
            )

            // Thumbnail slot — only for non-directory files that have a
            // server-side id (so we have a URL to fetch from). For images
            // we load a downscaled bitmap; for videos we extract a frame;
            // for everything else we leave the slot hidden and rely on the
            // leading file-type icon.
            bindThumbnail(file, ext)
        }

        /**
         * Load (or reset) the right-hand thumbnail slot for [file].
         *
         * Image files → [ImageLoader.loadFileThumbnail].
         * Video files → [ImageLoader.loadVideoThumbnail] (downloads once,
         * caches, extracts a frame, overlays a play badge).
         * Other files (or files with no server id) → slot hidden.
         *
         * The previous thumbnail job (if any) is cancelled before kicking
         * off a new one so scrolling doesn't bleed bitmaps into the wrong
         * row.
         */
        private fun bindThumbnail(file: RemoteFile, ext: String) {
            thumbJob?.cancel()
            val slot = binding.thumbSlot
            val isImage = !file.isDirectory && file.id.isNotBlank() && FileUtils.isImageExtension(ext)
            val isVideo = !file.isDirectory && file.id.isNotBlank() && FileUtils.isVideoExtension(ext)
            if (!isImage && !isVideo) {
                slot.visibility = View.GONE
                return
            }
            slot.visibility = View.VISIBLE
            binding.ivThumbnail.setImageDrawable(null)
            binding.thumbProgress.visibility = View.VISIBLE
            binding.ivPlayBadge.visibility = if (isVideo) View.VISIBLE else View.GONE

            val ctx = itemView.context
            val prefs = Preferences.getInstance(ctx)
            val serverUrl = prefs.serverUrl
            val token = prefs.token ?: ""

            thumbJob = thumbScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    if (isImage) {
                        ImageLoader.loadFileThumbnail(serverUrl, token, file.id, 96)
                    } else {
                        ImageLoader.loadVideoThumbnail(
                            serverUrl, token, file.id, ctx.cacheDir, 160, file.size
                        )
                    }
                }
                // Guard against the view being recycled before the load
                // finishes — bindingAdapterPosition is the safe check.
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@launch
                if (bmp != null) {
                    binding.ivThumbnail.setImageBitmap(bmp)
                    binding.thumbProgress.visibility = View.GONE
                } else {
                    // Hide the slot on failure — fall back to the icon.
                    slot.visibility = View.GONE
                }
            }
        }

        private fun buildFileDetails(file: RemoteFile, ext: String): String {
            if (file.isDirectory) return "مجلد"
            val parts = mutableListOf<String>()
            // Always show the type label first (صورة / فيديو / صوت / ...)
            parts.add(FileUtils.typeLabel(ext))
            if (file.size > 0) parts.add(FileUtils.formatFileSize(file.size))
            if (!file.modified.isNullOrEmpty()) parts.add(file.modified)
            return parts.joinToString("  •  ")
        }

        fun bindParent() {
            thumbJob?.cancel()
            binding.tvFileName.text = ".."
            binding.ivFileIcon.setImageResource(R.drawable.ic_folder)
            binding.ivFileIcon.setColorFilter(
                ContextCompat.getColor(itemView.context, R.color.warning)
            )
            binding.tvFileDetails.text = itemView.context.getString(R.string.parent_dir)
            binding.ivDownload.visibility = View.GONE
            binding.ivCheck.visibility = View.GONE
            binding.thumbSlot.visibility = View.GONE
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<RemoteFile>() {
        override fun areItemsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem.path == newItem.path

        override fun areContentsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem == newItem
    }

    companion object {
        const val TYPE_PARENT = 0
        const val TYPE_FILE = 1
    }
}
