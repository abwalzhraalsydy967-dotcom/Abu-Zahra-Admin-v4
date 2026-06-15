package com.abuzahra.admin.ui.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abuzahra.admin.data.api.*
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.launch

class RegisterViewModel(private val preferences: Preferences) : ViewModel() {

    private val _registerResult = MutableLiveData<Result<LoginResponse>>()
    val registerResult: MutableLiveData<Result<LoginResponse>> = _registerResult

    fun register(username: String, email: String, password: String) {
        _registerResult.value = Result.Loading

        viewModelScope.launch {
            try {
                val api = ApiClient.create(preferences.serverUrl)
                val request = RegisterRequest(
                    username = username,
                    email = email,
                    password = password
                )
                val response = api.register(request)

                if (response.ok && response.token.isNotEmpty()) {
                    preferences.token = response.token
                    preferences.permanentCode = response.permanentCode
                    preferences.userEmail = response.email
                    preferences.userName = response.username
                    preferences.userRole = response.role
                    preferences.userId = response.userId
                    _registerResult.postValue(Result.Success(response))
                } else {
                    _registerResult.postValue(Result.Error(response.message.ifEmpty { "فشل إنشاء الحساب" }))
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 400) {
                    _registerResult.postValue(Result.Error("البريد الإلكتروني أو اسم المستخدم مسجل مسبقاً"))
                } else {
                    _registerResult.postValue(Result.Error("خطأ في الخادم: ${e.code()}"))
                }
            } catch (e: Exception) {
                _registerResult.postValue(Result.Error("خطأ في الاتصال: ${e.message}"))
            }
        }
    }
}