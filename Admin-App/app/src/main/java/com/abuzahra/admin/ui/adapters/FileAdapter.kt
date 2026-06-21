package com.abuzahra.admin.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.data.model.RemoteFile
import com.abuzahra.admin.databinding.ItemFileCardBinding

/**
 * Adapter for the Files fragment. Mirrors the web's `FileViewer`:
 *  - Groups by kind (image/video/audio/file) via icon + tint
 *  - Each row shows: name, meta (size + device + uploaded_at), expiry badge
 *  - View + Download actions
 */
class FileAdapter(
    private val onView: (RemoteFile) -> Unit,
    private val onDownload: (RemoteFile) -> Unit
) : ListAdapter<RemoteFile, FileAdapter.FileViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(
        private val binding: ItemFileCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onView(getItem(pos))
            }
            binding.btnDownload.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onDownload(getItem(pos))
            }
        }

        fun bind(file: RemoteFile) {
            binding.tvFileName.text = file.displayName.ifEmpty { file.filename }
            binding.tvFileIcon.text = iconFor(file)
            binding.tvFileMeta.text = buildString {
                append(file.displaySize)
                if (file.uploadedAt != null) {
                    append(" • ")
                    append(formatTime(file.uploadedAt))
                }
                if (file.retrieved) {
                    append(" • تم الجلب")
                }
            }
        }

        private fun iconFor(file: RemoteFile): String {
            return when (file.fileType.lowercase()) {
                "photo", "screenshot", "camera" -> "🖼️"
                "video" -> "🎬"
                "audio" -> "🎵"
                else -> "📄"
            }
        }

        private fun formatTime(iso: String): String {
            return try {
                val sdf = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.getDefault()
                )
                val date = sdf.parse(iso) ?: return iso
                java.text.SimpleDateFormat(
                    "dd/MM HH:mm",
                    java.util.Locale.getDefault()
                ).format(date)
            } catch (_: Exception) {
                iso
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<RemoteFile>() {
        override fun areItemsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem == newItem
    }
}
