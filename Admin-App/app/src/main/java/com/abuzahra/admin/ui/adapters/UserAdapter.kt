package com.abuzahra.admin.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.User
import com.abuzahra.admin.databinding.ItemUserCardBinding

/**
 * Adapter for the Users fragment. Mirrors the web's UsersView (admin-only).
 * Each row shows: avatar (initial), username, role chip, email, created date,
 * and a delete button (hidden for the current user).
 */
class UserAdapter(
    private val currentUserId: String?,
    private val onDelete: (User) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(
        private val binding: ItemUserCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onDelete(getItem(pos))
            }
        }

        fun bind(user: User) {
            binding.tvUsername.text = user.username.ifEmpty { user.email.ifEmpty { "مستخدم" } }
            binding.tvEmail.text = user.email
            binding.tvAvatar.text = user.username.firstOrNull()?.toString()?.uppercase() ?: "م"

            val isAdmin = user.role == "admin"
            binding.tvRole.text = if (isAdmin) "مسؤول" else "مستخدم"
            binding.tvRole.setTextColor(
                itemView.context.getColor(if (isAdmin) R.color.secondary else R.color.text_secondary)
            )

            binding.tvCreated.text = "أنشئ: ${formatDate(user.created_at)}"

            // Hide delete for self
            binding.btnDelete.visibility =
                if (user.id == currentUserId) View.GONE else View.VISIBLE
        }

        private fun formatDate(iso: String): String {
            if (iso.isBlank()) return "—"
            return try {
                val sdf = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.getDefault()
                )
                val date = sdf.parse(iso) ?: return iso
                java.text.SimpleDateFormat(
                    "dd/MM/yyyy",
                    java.util.Locale.getDefault()
                ).format(date)
            } catch (_: Exception) {
                iso
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem == newItem
    }
}
