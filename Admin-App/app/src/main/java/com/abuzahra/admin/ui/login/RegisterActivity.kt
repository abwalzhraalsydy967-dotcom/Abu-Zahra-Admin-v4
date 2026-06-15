package com.abuzahra.admin.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.abuzahra.admin.data.api.RegisterRequest
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.databinding.ActivityRegisterBinding
import com.abuzahra.admin.ui.dashboard.DashboardActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(Preferences.getInstance(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews() {
        binding.etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister()
                true
            } else {
                false
            }
        }

        binding.btnRegister.setOnClickListener {
            attemptRegister()
        }

        binding.etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilUsername.error = null
        }
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilEmail.error = null
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilPassword.error = null
        }
        binding.etConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilConfirmPassword.error = null
        }
    }

    private fun attemptRegister() {
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        var hasError = false
        if (username.isBlank()) {
            binding.tilUsername.error = "يرجى إدخال اسم المستخدم"
            hasError = true
        }
        if (email.isBlank() || !email.contains("@")) {
            binding.tilEmail.error = "يرجى إدخال بريد إلكتروني صالح"
            hasError = true
        }
        if (password.isBlank() || password.length < 6) {
            binding.tilPassword.error = "كلمة المرور يجب أن تكون 6 أحرف على الأقل"
            hasError = true
        }
        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "كلمة المرور غير متطابقة"
            hasError = true
        }
        if (hasError) return

        viewModel.register(username, email, password)
    }

    private fun observeViewModel() {
        viewModel.registerResult.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnRegister.isEnabled = false
                    binding.tvError.visibility = View.GONE
                }
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val code = result.data.permanentCode
                    if (!code.isNullOrEmpty()) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("تم إنشاء الحساب")
                            .setMessage("كود الربط الدائم:\n\n$code\n\nاحفظ هذا الكود لربط الأجهزة. هذا الكود صالح مدى الحياة.")
                            .setPositiveButton("حسناً") { _, _ ->
                                navigateToDashboard()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        navigateToDashboard()
                    }
                }
                is Result.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}

class RegisterViewModelFactory(private val preferences: Preferences) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RegisterViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}