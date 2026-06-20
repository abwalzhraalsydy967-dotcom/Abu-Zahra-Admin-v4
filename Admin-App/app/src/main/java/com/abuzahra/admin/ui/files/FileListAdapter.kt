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

            val icon = if (file.isDirectory) {
                R.drawable.ic_folder
            } else {
                getFileIcon(file.extension, file.name)
            }
            binding.ivFileIcon.setImageResource(icon)
            binding.ivFileIcon.setColorFilter(
                ContextCompat.getColor(itemView.context, iconTintFor(file))
            )

            binding.tvFileDetails.text = buildFileDetails(file)

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
        }

        private fun buildFileDetails(file: RemoteFile): String {
            if (file.isDirectory) return "مجلد"
            val parts = mutableListOf<String>()
            if (file.size > 0) parts.add(humanReadableSize(file.size))
            if (!file.modified.isNullOrEmpty()) parts.add(file.modified)
            return parts.joinToString("  •  ")
        }

        private fun humanReadableSize(bytes: Long): String {
            if (bytes <= 0) return "—"
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
                else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
            }
        }

        private fun iconTintFor(file: RemoteFile): Int {
            return if (file.isDirectory) R.color.warning
            else when (extension(file).lowercase()) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> R.color.secondary
                "mp4", "avi", "mkv", "mov", "3gp" -> R.color.info
                "mp3", "wav", "ogg", "flac", "aac" -> R.color.secondary_variant
                "apk" -> R.color.warning
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "log", "csv" -> R.color.text_secondary
                "zip", "rar", "7z", "tar", "gz" -> R.color.pending_color
                else -> R.color.text_hint
            }
        }

        private fun extension(file: RemoteFile): String {
            return file.extension.ifEmpty {
                val lastDot = file.name.lastIndexOf('.')
                if (lastDot >= 0 && lastDot < file.name.length - 1) file.name.substring(lastDot + 1)
                else ""
            }
        }

        fun bindParent() {
            binding.tvFileName.text = ".."
            binding.ivFileIcon.setImageResource(R.drawable.ic_folder)
            binding.ivFileIcon.setColorFilter(
                ContextCompat.getColor(itemView.context, R.color.warning)
            )
            binding.tvFileDetails.text = "المجلد السابق"
            binding.ivDownload.visibility = View.GONE
            binding.ivCheck.visibility = View.GONE
        }

        private fun getFileIcon(extension: String, name: String): Int {
            val ext = extension.ifEmpty {
                val lastDot = name.lastIndexOf('.')
                if (lastDot >= 0 && lastDot < name.length - 1) name.substring(lastDot + 1) else ""
            }
            return when (ext.lowercase()) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> R.drawable.ic_file_image
                "mp4", "avi", "mkv", "mov", "3gp" -> R.drawable.ic_file_video
                "mp3", "wav", "ogg", "flac", "aac" -> R.drawable.ic_file_audio
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "txt", "log", "csv", "json", "xml" -> R.drawable.ic_file_document
                "apk" -> R.drawable.ic_file_apk
                "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_file_archive
                else -> R.drawable.ic_file
            }
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
