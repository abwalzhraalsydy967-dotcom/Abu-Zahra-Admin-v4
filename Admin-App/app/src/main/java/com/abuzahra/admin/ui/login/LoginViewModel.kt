package com.abuzahra.admin.ui.login

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abuzahra.admin.data.api.*
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class LoginViewModel(private val preferences: Preferences) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val _loginResult = MutableLiveData<Result<LoginResponse>>()
    val loginResult: MutableLiveData<Result<LoginResponse>> = _loginResult

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginResult.value = Result.Error("يرجى إدخال البريد الإلكتروني وكلمة المرور")
            return
        }

        _loginResult.value = Result.Loading

        viewModelScope.launch {
            try {
                Log.d(TAG, "تسجيل دخول بالبريد: $email → ${preferences.serverUrl}")
                val api = ApiClient.create(preferences.serverUrl)
                val response = api.login(LoginRequest(email, password))

                Log.d(TAG, "استجابة تسجيل الدخول: ok=${response.ok}, token=${response.token.take(16)}")

                if (response.ok && response.token.isNotEmpty()) {
                    saveSession(response)
                    _loginResult.postValue(Result.Success(response))
                } else {
                    Log.e(TAG, "فشل تسجيل الدخول: ${response.message}")
                    _loginResult.postValue(Result.Error(response.message.ifEmpty { "فشل تسجيل الدخول" }))
                }
            } catch (e: retrofit2.HttpException) {
                Log.e(TAG, "خطأ HTTP ${e.code()}: ${e.message}")
                when (e.code()) {
                    401 -> _loginResult.postValue(Result.Error("البريد الإلكتروني أو كلمة المرور غير صحيحة", 401))
                    403 -> _loginResult.postValue(Result.Error("تم رفض الوصول", 403))
                    else -> _loginResult.postValue(Result.Error("خطأ في الخادم: ${e.code()}", e.code()))
                }
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "انتهت مهلة الاتصال", e)
                _loginResult.postValue(Result.Error("انتهت مهلة الاتصال بالخادم"))
            } catch (e: UnknownHostException) {
                Log.e(TAG, "لا يمكن الوصول إلى الخادم: ${preferences.serverUrl}", e)
                _loginResult.postValue(Result.Error("لا يمكن الوصول إلى الخادم: ${preferences.serverUrl}"))
            } catch (e: SSLException) {
                Log.e(TAG, "خطأ SSL", e)
                _loginResult.postValue(Result.Error("خطأ في شهادة الأمان (SSL)"))
            } catch (e: Exception) {
                Log.e(TAG, "خطأ غير متوقع", e)
                _loginResult.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    /**
     * Google Sign-In flow.
     *
     * The Google OAuth idToken is forwarded to the server's `/api/web/firebase_auth`
     * endpoint. The server verifies the idToken against Google's tokeninfo
     * endpoint (and Firebase identitytoolkit as a fallback), creates or finds
     * the matching user, and returns a session token — exactly the same shape
     * as a password login response.
     *
     * This is a SEPARATE flow from [login]: it does NOT fall back to password
     * authentication on failure. Errors are reported with Google-specific
     * messages so the user can tell the difference between "wrong password"
     * and "Google verification failed".
     */
    fun loginWithFirebase(email: String, displayName: String, idToken: String) {
        if (email.isBlank()) {
            _loginResult.value = Result.Error("فشل الحصول على البريد الإلكتروني من جوجل")
            return
        }

        _loginResult.value = Result.Loading

        viewModelScope.launch {
            try {
                Log.d(TAG, "Firebase Auth: email=$email, server=${preferences.serverUrl}")
                Log.d(TAG, "   idToken length: ${idToken.length}")

                val api = ApiClient.create(preferences.serverUrl)
                val request = FirebaseAuthRequest(
                    email = email,
                    displayName = displayName,
                    idToken = idToken
                )
                val response = api.firebaseAuth(request)

                Log.d(TAG, "استجابة Firebase Auth: ok=${response.ok}, token=${response.token.take(16)}")

                if (response.ok && response.token.isNotEmpty()) {
                    saveSession(response)
                    _loginResult.postValue(Result.Success(response))
                } else {
                    Log.e(TAG, "فشل Firebase Auth: ${response.message}")
                    _loginResult.postValue(
                        Result.Error(response.message.ifEmpty { "فشل التحقق من حساب جوجل" })
                    )
                }
            } catch (e: retrofit2.HttpException) {
                Log.e(TAG, "Firebase Auth HTTP ${e.code()}: ${e.message}", e)
                when (e.code()) {
                    401 -> _loginResult.postValue(
                        Result.Error("تعذّر التحقق من حساب جوجل. تأكّد من أن البريد مرتبط بحساب إداري ثم حاول مجدداً.", 401)
                    )
                    403 -> _loginResult.postValue(
                        Result.Error("حساب جوجل غير مصرّح له بالدخول إلى لوحة الإدارة.", 403)
                    )
                    else -> _loginResult.postValue(
                        Result.Error("خطأ في الخادم أثناء التحقق من جوجل: ${e.code()}", e.code())
                    )
                }
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Firebase Auth مهلة", e)
                _loginResult.postValue(Result.Error("انتهت مهلة الاتصال بالخادم"))
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Firebase Auth لا يمكن الوصول: ${preferences.serverUrl}", e)
                _loginResult.postValue(Result.Error("لا يمكن الوصول إلى الخادم"))
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Auth خطأ", e)
                _loginResult.postValue(Result.Error("خطأ في تسجيل الدخول بجوجل: ${e.message}"))
            }
        }
    }

    private fun saveSession(response: LoginResponse) {
        preferences.token = response.token
        preferences.permanentCode = response.permanentCode
        preferences.userEmail = response.email
        preferences.userName = response.username
        preferences.userRole = response.role
        preferences.userId = response.userId
        Log.d(TAG, "تم حفظ الجلسة: user=${response.username}, code=${response.permanentCode}")
    }

    val isLoggedIn: Boolean get() = preferences.isLoggedIn
    val serverUrl: String get() = preferences.serverUrl
}