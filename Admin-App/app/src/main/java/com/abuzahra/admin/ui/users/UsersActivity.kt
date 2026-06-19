package com.abuzahra.admin.ui.users

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.CreateUserRequest
import com.abuzahra.admin.databinding.ActivityUsersBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class UsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupFab()
        setupRegenerateCode()
        setupSwipeRefresh()
        loadUsers()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "إدارة المستخدمين"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupFab() {
        binding.fabAddUser.setOnClickListener {
            showCreateUserDialog()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadUsers()
        }
    }

    private fun setupRegenerateCode() {
        // Regenerates the CURRENT admin's permanent link code via
        // POST /api/web/regenerate_code (admin only).
        binding.btnRegenerateCode.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("تجديد كود الربط الدائم")
                .setMessage("سيتم إلغاء الكود الحالي وتوليد كود جديد. الأجهزة المرتبطة بالكود القديم ستحتاج لإعادة الربط. هل تريد المتابعة؟")
                .setPositiveButton("تجديد") { _, _ ->
                    regenerateCode()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun regenerateCode() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val prefs = Preferences.getInstance(this@UsersActivity)
                val api = prefs.getApiService()
                val response = api.regenerateCode()
                if (response.ok && response.code.isNotEmpty()) {
                    // Persist the new code locally so the dashboard shows it.
                    prefs.permanentCode = response.code
                    showNewCodeDialog(response.code)
                } else {
                    Toast.makeText(
                        this@UsersActivity,
                        "فشل تجديد الكود",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@UsersActivity,
                    "خطأ: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showNewCodeDialog(code: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("تم تجديد الكود")
            .setMessage("كود الربط الدائم الجديد:\n\n$code\n\nاحفظ هذا الكود لربط الأجهزة الجديدة.")
            .setPositiveButton("نسخ") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("link_code", code))
                Toast.makeText(this, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("حسناً", null)
            .setCancelable(false)
            .show()
    }

    private fun loadUsers() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = Preferences.getInstance(this@UsersActivity).getApiService()
                val users = api.getUsers()

                val adapter = UserAdapter(
                    onDeleteClick = { user ->
                        MaterialAlertDialogBuilder(this@UsersActivity)
                            .setTitle("حذف مستخدم")
                            .setMessage("هل تريد حذف المستخدم: ${user.username}?")
                            .setPositiveButton("حذف") { _, _ ->
                                deleteUser(user.id)
                            }
                            .setNegativeButton("إلغاء", null)
                            .show()
                    }
                )
                adapter.submitList(users)
                binding.rvUsers.layoutManager = LinearLayoutManager(this@UsersActivity)
                binding.rvUsers.adapter = adapter
                binding.tvEmpty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun deleteUser(userId: String) {
        lifecycleScope.launch {
            try {
                val api = Preferences.getInstance(this@UsersActivity).getApiService()
                api.deleteUser(userId)
                Toast.makeText(this@UsersActivity, "تم حذف المستخدم", Toast.LENGTH_SHORT).show()
                loadUsers()
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateUserDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_user, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.etUsername)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle("إنشاء مستخدم جديد")
            .setView(dialogView)
            .setPositiveButton("إنشاء") { _, _ ->
                val username = etUsername.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (username.isBlank() || email.isBlank() || password.isBlank()) {
                    Toast.makeText(this, "جميع الحقول مطلوبة", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        val api = Preferences.getInstance(this@UsersActivity).getApiService()
                        api.createUser(CreateUserRequest(username, password, email))
                        Toast.makeText(this@UsersActivity, "تم إنشاء المستخدم", Toast.LENGTH_SHORT).show()
                        loadUsers()
                    } catch (e: Exception) {
                        Toast.makeText(this@UsersActivity, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
