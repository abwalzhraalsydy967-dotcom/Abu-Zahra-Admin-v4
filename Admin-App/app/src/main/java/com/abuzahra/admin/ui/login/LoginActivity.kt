package com.abuzahra.admin.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.databinding.ActivityLoginBinding
import com.abuzahra.admin.ui.dashboard.DashboardActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(Preferences.getInstance(this))
    }

    private lateinit var debugLogText: TextView
    private lateinit var debugLogScroll: ScrollView
    private val debugLogs = mutableListOf<String>()
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

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
        setupGoogleSignInLauncher()

        addLog("تطبيق أبو زهرة - الإدارة v4.0 (Native Google Sign-In)")
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

    /**
     * Web Client ID for Google Sign-In.
     * Fetched from google-services.json at runtime, with hardcoded fallback.
     */
    private fun getWebClientId(): String? {
        // Try reading from google-services.json first
        try {
            val json = applicationContext.assets.open("google-services.json")
                .bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val clients = obj.getJSONArray("client")
            for (i in 0 until clients.length()) {
                val client = clients.getJSONObject(i)
                val oauthClients = client.optJSONArray("oauth_client")
                if (oauthClients != null) {
                    for (j in 0 until oauthClients.length()) {
                        val oauth = oauthClients.getJSONObject(j)
                        if (oauth.optInt("client_type") == 3) {
                            return oauth.getString("client_id")
                        }
                    }
                }
                val services = client.optJSONObject("services")
                if (services != null) {
                    val invite = services.optJSONObject("appinvite_service")
                    if (invite != null) {
                        val others = invite.optJSONArray("other_platform_oauth_client")
                        if (others != null) {
                            for (j in 0 until others.length()) {
                                val other = others.getJSONObject(j)
                                if (other.optInt("client_type") == 3) {
                                    return other.getString("client_id")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading google-services.json", e)
        }

        // Fallback: hardcoded Web Client ID for project abwalzhraalsydy-62ccf
        return FALLBACK_WEB_CLIENT_ID
    }

    companion object {
        private const val TAG = "LoginActivity"
        /**
         * Fallback Web Client ID extracted from Firebase Console.
         * Project: abwalzhraalsydy-62ccf (159319780620)
         */
        private const val FALLBACK_WEB_CLIENT_ID =
            "159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com"
    }

    private fun setupGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    handleGoogleSignInResult(account)
                } catch (e: ApiException) {
                    addLog("❌ فشل تسجيل الدخول بجوجل: ${e.statusCode} - ${e.message}")
                    when (e.statusCode) {
                        12501 -> showError("تم إلغاء تسجيل الدخول")
                        12500 -> showError("خطأ في اتصال جوجل - تأكد من تثبيت خدمات جوجل")
                        10 -> showError("خطأ في التحقق - تأكد من إضافة SHA-1 إلى Firebase Console")
                        else -> showError("خطأ في تسجيل الدخول بجوجل: ${e.message}")
                    }
                    showLoading(false)
                }
            } else {
                addLog("⚠️ تم إلغاء اختيار الحساب")
                showLoading(false)
            }
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

        binding.btnGoogleSignIn.setOnClickListener {
            addLog("تم الضغط على: تسجيل الدخول بجوجل (Native)")
            startNativeGoogleSignIn()
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

    private fun startNativeGoogleSignIn() {
        val webClientId = getWebClientId()
        if (webClientId.isNullOrEmpty()) {
            addLog("❌ Web Client ID غير متوفر!")
            showError("لم يتم إعداد Firebase Auth بعد. يرجى تفعيل المصادقة في Firebase Console.")
            return
        }

        addLog("Web Client ID: ${webClientId.take(30)}...")
        showLoading(true)

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .requestProfile()
                .build()

            val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(this, gso)
            addLog("جاري فتح قائمة الحسابات...")

            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        } catch (e: Exception) {
            addLog("❌ فشل تهيئة Google Sign-In: ${e.javaClass.simpleName}: ${e.message}")
            showError("خطأ في تهيئة جوجل: ${e.message}")
            showLoading(false)
        }
    }

    private fun handleGoogleSignInResult(account: GoogleSignInAccount) {
        val email = account.email ?: ""
        val displayName = account.displayName ?: ""
        val idToken = account.idToken ?: ""

        addLog("✅ تم اختيار الحساب بنجاح!")
        addLog("   البريد: $email")
        addLog("   الاسم: $displayName")
        addLog("   ID Token: ${idToken.take(20)}... (${idToken.length} chars)")

        if (idToken.isEmpty()) {
            showError("لم يتم الحصول على رمز المصادقة من جوجل")
            showLoading(false)
            return
        }

        if (email.isEmpty()) {
            showError("لم يتم الحصول على البريد الإلكتروني")
            showLoading(false)
            return
        }

        viewModel.loginWithFirebase(email, displayName, idToken)
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