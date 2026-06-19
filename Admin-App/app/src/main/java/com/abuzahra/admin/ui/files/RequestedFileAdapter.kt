package com.abuzahra.admin.ui.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.R
import com.abuzahra.admin.data.model.RemoteFile
import com.abuzahra.admin.databinding.ItemRequestedFileBinding

/**
 * Adapter for the "Requested Files" list — files that devices have uploaded
 * to the server (photos, screenshots, recordings, etc.) and that auto-expire
 * after 1 hour. Each row exposes a View button and a Download button.
 */
class RequestedFileAdapter(
    private val onViewClick: (RemoteFile) -> Unit,
    private val onDownloadClick: (RemoteFile) -> Unit
) : ListAdapter<RemoteFile, RequestedFileAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRequestedFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRequestedFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
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
        }

        fun bind(file: RemoteFile) {
            binding.tvFileName.text = file.displayName
            binding.ivFileIcon.setImageResource(iconForType(file.fileType))
            binding.tvFileMeta.text = buildMeta(file)
            binding.tvFileTime.text = file.uploadedAt ?: file.expiresAt ?: ""
        }

        private fun buildMeta(file: RemoteFile): String {
            val parts = mutableListOf<String>()
            parts.add(typeLabel(file.fileType))
            if (file.size > 0) parts.add(file.displaySize)
            if (!file.caption.isNullOrEmpty()) parts.add("«${file.caption}»")
            return parts.joinToString(" • ")
        }

        private fun typeLabel(type: String): String = when (type.lowercase()) {
            "photo", "camera", "screenshot" -> "صورة"
            "video" -> "فيديو"
            "audio" -> "صوت"
            "file" -> "ملف"
            else -> type.ifEmpty { "ملف" }
        }

        private fun iconForType(type: String): Int = when (type.lowercase()) {
            "photo", "camera", "screenshot" -> R.drawable.ic_screenshot
            "video" -> R.drawable.ic_screenshot
            "audio" -> R.drawable.ic_file
            else -> R.drawable.ic_file
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<RemoteFile>() {
        override fun areItemsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RemoteFile, newItem: RemoteFile): Boolean =
            oldItem == newItem
    }
}
