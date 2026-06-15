package com.abuzahra.admin.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.databinding.ActivityLoginBinding
import com.abuzahra.admin.ui.dashboard.DashboardActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(Preferences.getInstance(this))
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    // Debug log
    private lateinit var debugLogText: TextView
    private lateinit var debugLogScroll: ScrollView

    private val debugLogs = mutableListOf<String>()

    private fun addLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val entry = "[$timestamp] $msg"
        Log.d(TAG, entry)
        debugLogs.add(0, entry)
        if (debugLogs.size > 30) debugLogs.removeRange(30, debugLogs.size)
        if (debugPanelReady && ::debugLogText.isInitialized && ::debugLogScroll.isInitialized) {
            val sb = StringBuilder()
            for (log in debugLogs) sb.append(log).append("\n")
            debugLogText.text = sb.toString()
            debugLogScroll.post { debugLogScroll.scrollTo(0, 0) }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        addLog("نتيجة تسجيل جوجل: resultCode=${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            addLog("✅ تم الحصول على حساب جوجل: ${account.email}")
            addLog("   ID Token: ${if (account.idToken != null) "موجود (${account.idToken!!.take(20)}...)" else "غير موجود!"}")
            addLog("   DisplayName: ${account.displayName}")
            addLog("   GivenName: ${account.givenName}")
            addLog("   FamilyName: ${account.familyName}")
            addLog("   PhotoUrl: ${account.photoUrl}")
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            val errorMsg = translateGoogleError(e.statusCode)
            addLog("❌ فشل تسجيل جوجل: $errorMsg (code=${e.statusCode})")
            showError(errorMsg)
            binding.progressBar.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            binding.btnGoogleSignIn.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        setupDebugLogPanel()

        addLog("تطبيق أبو ظهرا - الإدارة v2.0")
        addLog("الخادم: ${Preferences.getInstance(this).serverUrl}")

        if (viewModel.isLoggedIn) {
            addLog("✅ جلسة موجودة مسبقاً - الانتقال للوحة التحكم")
            navigateToDashboard()
            return
        }

        addLog("لا توجد جلسة - عرض شاشة تسجيل الدخول")
        checkGooglePlayServices()
        setupGoogleSignIn()
        setupViews()
        observeViewModel()
    }

    private var debugPanelReady = false

    private fun setupDebugLogPanel() {
        // The root is CoordinatorLayout — find the inner LinearLayout or add directly
        val rootLayout = binding.root as? androidx.coordinatorlayout.widget.CoordinatorLayout
        if (rootLayout == null) {
            debugPanelReady = false
            return
        }

        debugLogScroll = ScrollView(this).apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = true
            setBackgroundColor(ContextCompat.getColor(this@LoginActivity, R.color.surface))
            setPadding(16, 8, 16, 8)
            val lp = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = android.view.Gravity.BOTTOM
            layoutParams = lp
        }

        debugLogText = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.text_primary))
            setLineSpacing(2f, 1f)
        }

        debugLogScroll.addView(debugLogText)
        rootLayout.addView(debugLogScroll)
        debugPanelReady = true

        // Show any logs that were accumulated before the panel was ready
        if (debugLogs.isNotEmpty()) {
            val sb = StringBuilder()
            for (log in debugLogs) sb.append(log).append("\n")
            debugLogText.text = sb.toString()
        }
    }

    private fun checkGooglePlayServices() {
        val availability = GoogleApiAvailability.getInstance()
        val code = availability.isGooglePlayServicesAvailable(this)
        if (code == ConnectionResult.SUCCESS) {
            addLog("✅ خدمات Google Play متوفرة (v${availability.getApkVersion(this)})")
        } else {
            addLog("❌ خدمات Google Play غير متوفرة! رمز الخطأ: $code")
            if (availability.isUserResolvableError(code)) {
                addLog("   يمكن إصلاح المشكلة - سيتم طلب التحديث")
                availability.showErrorDialogFragment(this, code, 1) { _, _ ->
                    addLog("تم رفض تحديث خدمات Google Play")
                }
            } else {
                addLog("   الجهاز لا يدعم خدمات Google Play - لا يمكن استخدام تسجيل جوجل")
            }
        }
    }

    private fun setupGoogleSignIn() {
        try {
            val webClientId = getString(R.string.default_web_client_id)
            addLog("إعداد Google Sign-In...")
            addLog("   Web Client ID: ${webClientId.take(30)}...")

            if (webClientId.contains("placeholder") || webClientId.isBlank()) {
                addLog("❌ Web Client ID غير صحيح - تحقق من google-services.json")
            }

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            addLog("✅ تم إعداد Google Sign-In بنجاح")
        } catch (e: Exception) {
            addLog("❌ فشل إعداد Google Sign-In: ${e.javaClass.simpleName}: ${e.message}")
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
            addLog("تم الضغط على: تسجيل الدخول بجوجل")
            startGoogleSignIn()
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

        // Long-press on error text to show debug log
        binding.tvError.setOnLongClickListener {
            debugLogScroll.visibility = if (debugLogScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            true
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

    private fun startGoogleSignIn() {
        showLoading(true)
        addLog("جاري فتح نافذة اختيار حساب جوجل...")

        try {
            // Check for silent sign-in first
            val account = GoogleSignIn.getLastSignedInAccount(this)
            if (account != null) {
                addLog("   يوجد حساب جوجل سابق: ${account.email}")
            }

            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            addLog("❌ فشل فتح نافذة جوجل: ${e.javaClass.simpleName}: ${e.message}")
            showError("فشل فتح نافذة جوجل: ${e.message}")
            showLoading(false)
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        addLog("جاري المصادقة بـ Firebase...")
        addLog("   Email: ${account.email}")
        addLog("   ID Token موجود: ${account.idToken != null}")
        addLog("   ID Token طول: ${account.idToken?.length ?: 0}")

        val idToken = account.idToken
        if (idToken == null) {
            addLog("❌ ID Token غير موجود! لا يمكن المتابعة")
            addLog("   الأسباب المحتملة:")
            addLog("   1. مفتاح SHA1 غير مطابق في Firebase Console")
            addLog("   2. google-services.json غير صحيح")
            addLog("   3. OAuth Client ID غير مُعرّف")
            showError("فشل الحصول على رمز المصادقة - تأكد من إضافة SHA1 الصحيح في Firebase Console")
            showLoading(false)
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    addLog("✅ تمت مصادقة Firebase بنجاح!")
                    addLog("   Firebase UID: ${user?.uid}")
                    addLog("   Firebase Email: ${user?.email}")
                    addLog("   Firebase DisplayName: ${user?.displayName}")
                    addLog("   جاري إرسال البيانات للخادم...")
                    viewModel.loginWithFirebase(
                        email = user?.email ?: "",
                        displayName = user?.displayName ?: "",
                        idToken = idToken
                    )
                } else {
                    val exception = task.exception
                    addLog("❌ فشلت مصادقة Firebase!")
                    if (exception != null) {
                        addLog("   النوع: ${exception.javaClass.simpleName}")
                        addLog("   الرسالة: ${exception.message}")
                        // Parse common Firebase Auth errors
                        val errorMsg = translateFirebaseError(exception)
                        addLog("   التفسير: $errorMsg")
                        showError(errorMsg)
                    } else {
                        showError("فشل المصادقة بحساب جوجل")
                    }
                    showLoading(false)
                }
            }
    }

    private fun translateGoogleError(statusCode: Int): String {
        return when (statusCode) {
            7 -> "الشبكة غير متاحة - تحقق من الاتصال بالإنترنت"
            8 -> "خطأ داخلي في خدمات Google"
            10 -> "تم إلغاء العملية من قبل المستخدم"
            12 -> "تم إلغاء تسجيل الدخول"
            13 -> "خطأ في الاتصال - حاول مرة أخرى"
            16 -> "خطأ داخلي"
            17 -> "العميل غير صالح - تحقق من OAuth Client ID"
            18, 12501 -> "لم يتم العثور على تطبيق Google على الجهاز - ثبت تطبيق Google"
            20 -> "حساب جوجل غير متاح"
            21 -> "الجهاز غير مدعوم"
            22 -> "حدث خطأ في الشبكة"
            12500 -> "حدث خطأ أثناء تسجيل الدخول - قد يكون SHA1 غير مطابق"
            12501 -> "تم الإلغاء من المستخدم"
            12502 -> "خطأ في الاتصال بالشبكة"
            else -> "خطأ غير معروف ($statusCode) - قد تحتاج لإضافة SHA1 الصحيح في Firebase Console"
        }
    }

    private fun translateFirebaseError(exception: Exception): String {
        val msg = exception.message ?: ""
        return when {
            msg.contains("INVALID_ID_TOKEN", ignoreCase = true) ->
                "رمز المصادقة غير صالح - SHA1 في Firebase لا يتطابق مع مفتاح التوقيع"
            msg.contains("WEAK_PASSWORD", ignoreCase = true) ->
                "كلمة المرور ضعيفة"
            msg.contains("EMAIL_EXISTS", ignoreCase = true) ->
                "البريد الإلكتروني مسجل مسبقاً"
            msg.contains("USER_NOT_FOUND", ignoreCase = true) ->
                "المستخدم غير موجود"
            msg.contains("TOO_MANY_REQUESTS", ignoreCase = true) ->
                "محاولات كثيرة - انتظر قليلاً"
            msg.contains("NETWORK", ignoreCase = true) ->
                "خطأ في الشبكة"
            else -> "فشل المصادقة: ${msg.take(100)}"
        }
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
            .setTitle("كود الربط الخاص بك")
            .setMessage("كود الربط الدائم لحسابك:\n\n$code\n\nاحفظ هذا الكود لربط الأجهزة المستهدفة. هذا الكود صالح مدى الحياة.")
            .setPositiveButton("نسخ الكود") { _, _ ->
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("link_code", code)
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