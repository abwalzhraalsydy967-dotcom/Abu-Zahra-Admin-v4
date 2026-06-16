package com.abuzahra.admin.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.databinding.ActivityRegisterBinding
import com.abuzahra.admin.ui.dashboard.DashboardActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RegisterActivity"
    }

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(Preferences.getInstance(this))
    }

    private lateinit var auth: FirebaseAuth

    // Data to hold from the server registration result for Firebase auth
    private var pendingEmail: String? = null
    private var pendingPassword: String? = null
    private var pendingUsername: String? = null
    private var pendingLinkCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

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

        // Store for later use after server registration
        pendingEmail = email
        pendingPassword = password
        pendingUsername = username

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
                    val code = result.data.permanentCode
                    pendingLinkCode = code

                    // Now create Firebase Auth user and send verification email
                    createFirebaseUserAndVerify()
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

    private fun createFirebaseUserAndVerify() {
        val email = pendingEmail ?: return
        val password = pendingPassword ?: return

        Log.d(TAG, "إنشاء حساب FirebaseAuth للبريد: $email")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d(TAG, "✅ تم إنشاء حساب Firebase: uid=${user?.uid}")

                    // Send email verification
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verifyTask ->
                            if (verifyTask.isSuccessful) {
                                Log.d(TAG, "✅ تم إرسال رسالة التحقق إلى $email")
                                showSuccessDialog(verificationSent = true)
                            } else {
                                Log.w(TAG, "⚠️ فشل إرسال رسالة التحقق: ${verifyTask.exception?.message}")
                                showSuccessDialog(verificationSent = false)
                            }
                        }
                } else {
                    val exception = task.exception
                    Log.w(TAG, "⚠️ فشل إنشاء حساب Firebase: ${exception?.message}")

                    // Even if Firebase fails, the server registration succeeded
                    // Show the success dialog anyway
                    showSuccessDialog(verificationSent = false)
                }
            }
    }

    private fun showSuccessDialog(verificationSent: Boolean) {
        binding.progressBar.visibility = View.GONE
        binding.btnRegister.isEnabled = true

        val code = pendingLinkCode
        val message = if (verificationSent) {
            if (!code.isNullOrEmpty()) {
                "تم إنشاء حسابك بنجاح!\n\n" +
                "📧 تم إرسال رسالة تحقق إلى بريدك الإلكتروني. يرجى فحص بريدك والضغط على رابط التحقق.\n\n" +
                "🔗 كود الربط الدائم:\n$code\n\n" +
                "احفظ هذا الكود لربط الأجهزة. هذا الكود صالح مدى الحياة."
            } else {
                "تم إنشاء حسابك بنجاح!\n\n" +
                "📧 تم إرسال رسالة تحقق إلى بريدك الإلكتروني. يرجى فحص بريدك والضغط على رابط التحقق."
            }
        } else {
            if (!code.isNullOrEmpty()) {
                "تم إنشاء حسابك بنجاح!\n\n" +
                "🔗 كود الربط الدائم:\n$code\n\n" +
                "احفظ هذا الكود لربط الأجهزة. هذا الكود صالح مدى الحياة.\n\n" +
                "⚠️ لم يتم إرسال رسالة التحقق - يمكنك تسجيل الدخول مباشرة."
            } else {
                "تم إنشاء حسابك بنجاح!\n\nيمكنك الآن تسجيل الدخول."
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("🎉 تم إنشاء الحساب")
            .setMessage(message)
            .setPositiveButton("تسجيل الدخول") { _, _ ->
                navigateToDashboard()
            }
            .setCancelable(false)
            .show()
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
