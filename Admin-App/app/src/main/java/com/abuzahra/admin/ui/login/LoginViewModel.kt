package com.abuzahra.admin.ui.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abuzahra.admin.data.api.*
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.launch

class LoginViewModel(private val preferences: Preferences) : ViewModel() {

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
                val api = ApiClient.create(preferences.serverUrl)
                val response = api.login(LoginRequest(email, password))

                if (response.ok && response.token.isNotEmpty()) {
                    saveSession(response)
                    _loginResult.postValue(Result.Success(response))
                } else {
                    _loginResult.postValue(Result.Error(response.message.ifEmpty { "فشل تسجيل الدخول" }))
                }
            } catch (e: retrofit2.HttpException) {
                when (e.code()) {
                    401 -> _loginResult.postValue(Result.Error("البريد الإلكتروني أو كلمة المرور غير صحيحة", 401))
                    403 -> _loginResult.postValue(Result.Error("تم رفض الوصول", 403))
                    else -> _loginResult.postValue(Result.Error("خطأ في الخادم: ${e.code()}", e.code()))
                }
            } catch (e: java.net.SocketTimeoutException) {
                _loginResult.postValue(Result.Error("انتهت مهلة الاتصال بالخادم"))
            } catch (e: java.net.UnknownHostException) {
                _loginResult.postValue(Result.Error("لا يمكن الوصول إلى الخادم"))
            } catch (e: javax.net.ssl.SSLException) {
                _loginResult.postValue(Result.Error("خطأ في شهادة الأمان"))
            } catch (e: Exception) {
                _loginResult.postValue(Result.Error("خطأ في الاتصال: ${e.message}"))
            }
        }
    }

    fun loginWithFirebase(email: String, displayName: String, idToken: String) {
        if (email.isBlank()) {
            _loginResult.value = Result.Error("فشل الحصول على البريد الإلكتروني من جوجل")
            return
        }

        _loginResult.value = Result.Loading

        viewModelScope.launch {
            try {
                val api = ApiClient.create(preferences.serverUrl)
                val request = FirebaseAuthRequest(
                    email = email,
                    displayName = displayName,
                    idToken = idToken
                )
                val response = api.firebaseAuth(request)

                if (response.ok && response.token.isNotEmpty()) {
                    saveSession(response)
                    _loginResult.postValue(Result.Success(response))
                } else {
                    _loginResult.postValue(Result.Error(response.message.ifEmpty { "فشل تسجيل الدخول" }))
                }
            } catch (e: retrofit2.HttpException) {
                _loginResult.postValue(Result.Error("خطأ في الخادم: ${e.code()}", e.code()))
            } catch (e: Exception) {
                _loginResult.postValue(Result.Error("خطأ: ${e.message}"))
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
    }

    val isLoggedIn: Boolean get() = preferences.isLoggedIn
    val serverUrl: String get() = preferences.serverUrl
}