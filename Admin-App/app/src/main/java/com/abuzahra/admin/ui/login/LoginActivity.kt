package com.abuzahra.admin.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(Preferences.getInstance(this))
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            showError("فشل تسجيل الدخول بحساب جوجل")
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

        if (viewModel.isLoggedIn) {
            navigateToDashboard()
            return
        }

        setupGoogleSignIn()
        setupViews()
        observeViewModel()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
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
            attemptLogin()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }

        binding.btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilEmail.error = null
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilPassword.error = null
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
        if (hasError) return

        viewModel.login(email, password)
    }

    private fun startGoogleSignIn() {
        showLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    viewModel.loginWithFirebase(
                        email = user?.email ?: "",
                        displayName = user?.displayName ?: "",
                        idToken = account.idToken ?: ""
                    )
                } else {
                    showError("فشل المصادقة بحساب جوجل")
                    showLoading(false)
                }
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
    }

    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    showLoading(true)
                }
                is Result.Success -> {
                    showLoading(false)
                    val code = result.data.permanentCode
                    if (!code.isNullOrEmpty()) {
                        showLinkCodeDialog(code)
                    } else {
                        navigateToDashboard()
                    }
                }
                is Result.Error -> {
                    showLoading(false)
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
        MaterialAlertDialogBuilder(this)
            .setTitle("كود الربط الخاص بك")
            .setMessage("كود الربط الدائم لحسابك:\n\n$code\n\nاحفظ هذا الكود لربط الأجهزة المستهدفة. هذا الكود صالح مدى الحياة.")
            .setPositiveButton("نسخ الكود") { _, _ ->
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("link_code", code)
                clipboard.setPrimaryClip(clip)
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