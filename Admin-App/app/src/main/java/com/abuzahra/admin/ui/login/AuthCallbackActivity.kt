package com.abuzahra.admin.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.LoginResponse
import com.abuzahra.admin.util.Preferences

/**
 * Handles deep link callback from Chrome Custom Tab Google Sign-In.
 * Receives: abuzahra://auth-callback?token=XXX&email=YYY&username=ZZZ
 */
class AuthCallbackActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AuthCallback"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = intent?.data
        if (uri == null) {
            Log.e(TAG, "No data in intent")
            finish()
            return
        }

        Log.d(TAG, "Received deep link: $uri")

        val token = uri.getQueryParameter("token")
        val email = uri.getQueryParameter("email")
        val username = uri.getQueryParameter("username")

        if (token.isNullOrEmpty()) {
            Log.e(TAG, "No token in callback")
            finish()
            return
        }

        Log.d(TAG, "Token: ${token.take(16)}..., Email: $email, Username: $username")

        // Save session
        val prefs = Preferences.getInstance(this)
        prefs.token = token
        prefs.userEmail = email ?: ""
        prefs.userName = username ?: email?.split("@")?.getOrNull(0) ?: ""
        prefs.userRole = "admin"
        prefs.userId = ""

        // Navigate to dashboard
        val dashboardIntent = Intent(this, com.abuzahra.admin.ui.dashboard.DashboardActivity::class.java)
        dashboardIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(dashboardIntent)
        finish()
    }
}