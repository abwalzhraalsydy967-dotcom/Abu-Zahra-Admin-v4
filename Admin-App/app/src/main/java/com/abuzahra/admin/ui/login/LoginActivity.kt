package com.abuzahra.admin.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.databinding.ActivityLoginBinding
import com.abuzahra.admin.ui.dashboard.DashboardActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val MOBILE_AUTH_URL = "https://alsydyabwalzhra.online/mobile-auth"
    }

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(Preferences.getInstance(this))
    }

    private lateinit var debugLogText: TextView
    private lateinit var debugLogScroll: ScrollView
    private val debugLogs = mutableListOf<String>()

    private fun addLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val entry = "[$timestamp] $msg"
        Log.d(TAG, entry)
        debugLogs.add(0, entry)
        if (debugLogs.size > 50) {
            val excess = debugLogs.subList(50, debugLogs.size)
            excess.clear()
        }
        if (::debugLogText.isInitialized) {
            val sb = StringBuilder()
            for (log in debugLogs) sb.append(log).append("\n")
            debugLogText.text = sb.toString()
            debugLogScroll.post { debugLogScroll.scrollTo(0, 0) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDebugLogPanel()

        addLog("تطبيق أبو زهرة - الإدارة v3.0 (Chrome Custom Tab)")
        addLog("الخادم: ${Preferences.getInstance(this).serverUrl}")

        if (viewModel.isLoggedIn) {
            addLog("✅ جلسة موجودة مسبقاً - الانتقال للوحة التحكم")
            navigateToDashboard()
            return
        }

        addLog("لا توجد جلسة - عرض شاشة تسجيل الدخول")
        setupViews()
        observeViewModel()
    }

    private fun setupDebugLogPanel() {
        debugLogText = binding.root.findViewById(R.id.debugLogText)
        debugLogScroll = binding.root.findViewById(R.id.debugLogScroll)
        if (debugLogs.isNotEmpty()) {
            val sb = StringBuilder()
            for (log in debugLogs) sb.append(log).append("\n")
            debugLogText.text = sb.toString()
        }
    }

    private fun setupViews() {
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        binding.btnLogin.setOnClickListener {
            addLog("تم الضغط على: تسجيل الدخول بالبريد")
            attemptLogin()
        }

        // Google Sign-In via Chrome Custom Tab (bypasses SHA1 issue)
        binding.btnGoogleSignIn.setOnClickListener {
            addLog("تم الضغط على: تسجيل الدخول بجوجل (Chrome Custom Tab)")
            startGoogleSignInViaChromeTab()
        }

        binding.btnCreateAccount.setOnClickListener {
            addLog("تم الضغط على: إنشاء حساب جديد")
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilEmail.error = null
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilPassword.error = null
        }
    }

    /**
     * Opens Google Sign-In via Chrome Custom Tab.
     * This bypasses Google Play Services SHA1 verification entirely.
     * The web page handles Google Sign-In via GIS and redirects back
     * to the app via deep link (abuzahra://auth-callback).
     */
    private fun startGoogleSignInViaChromeTab() {
        showLoading(true)
        addLog("جاري فتح Chrome Custom Tab لتسجيل الدخول بجوجل...")
        addLog("   URL: $MOBILE_AUTH_URL")

        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setShowTitle(true)
                .build()

            customTabsIntent.launchUrl(this, Uri.parse(MOBILE_AUTH_URL))
            addLog("✅ تم فتح Chrome Custom Tab")
        } catch (e: Exception) {
            addLog("❌ فشل فتح Chrome Custom Tab: ${e.javaClass.simpleName}: ${e.message}")
            // Fallback: open in regular browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(MOBILE_AUTH_URL))
                startActivity(browserIntent)
                addLog("✅ تم فتح في المتصفح الافتراضي")
            } catch (e2: Exception) {
                addLog("❌ فشل فتح المتصفح أيضاً: ${e2.message}")
                showError("فشل فتح المتصفح: ${e2.message}")
                showLoading(false)
            }
        }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        var hasError = false
        if (email.isBlank()) {
            binding.tilEmail.error = "يرجى إدخال البريد الإلكتروني"
            hasError = true
        }
        if (password.isBlank()) {
            binding.tilPassword.error = "يرجى إدخال كلمة المرور"
            hasError = true
        }
        if (hasError) {
            addLog("❌ بيانات غير مكتملة")
            return
        }

        addLog("محاولة تسجيل الدخول: $email")
        viewModel.login(email, password)
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnGoogleSignIn.isEnabled = !loading
        if (loading) {
            binding.tvError.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        addLog("⚠️ خطأ معروض: $message")
    }

    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    showLoading(true)
                }
                is Result.Success -> {
                    showLoading(false)
                    addLog("✅ تم تسجيل الدخول بنجاح!")
                    addLog("   المستخدم: ${result.data.username}")
                    addLog("   البريد: ${result.data.email}")
                    addLog("   الدور: ${result.data.role}")
                    addLog("   الرمز: ${result.data.token.take(16)}...")
                    addLog("   كود الربط: ${result.data.permanentCode ?: "غير متوفر"}")

                    val code = result.data.permanentCode
                    if (!code.isNullOrEmpty()) {
                        showLinkCodeDialog(code)
                    } else {
                        navigateToDashboard()
                    }
                }
                is Result.Error -> {
                    showLoading(false)
                    addLog("❌ فشل تسجيل الدخول: ${result.message} (code=${result.code})")
                    if (result.code == 401) {
                        showError("البريد الإلكتروني أو كلمة المرور غير صحيحة")
                    } else {
                        showError(result.message)
                    }
                }
            }
        }
    }

    private fun showLinkCodeDialog(code: String) {
        addLog("📋 عرض كود الربط: $code")
        MaterialAlertDialogBuilder(this)
            .setTitle("🔗 كود الربط الخاص بك")
            .setMessage("كود الربط الدائم لحسابك:\n\n$code\n\nاحفظ هذا الكود لربط الأجهزة المستهدفة. هذا الكود صالح مدى الحياة.")
            .setPositiveButton("نسخ الكود") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("link_code", code)
                clipboard.setPrimaryClip(clip)
                addLog("✅ تم نسخ الكود")
                navigateToDashboard()
            }
            .setNegativeButton("متابعة", null)
            .setCancelable(false)
            .show()
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}

class LoginViewModelFactory(private val preferences: Preferences) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}