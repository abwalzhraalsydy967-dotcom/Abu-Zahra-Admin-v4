package com.abuzahra.admin.ui.users

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