package com.abuzahra.admin.ui.device

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abuzahra.admin.data.api.ApiException
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.api.SendCommandRequest
import com.abuzahra.admin.data.model.*
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class DeviceDetailViewModel(private val preferences: Preferences) : ViewModel() {

    companion object {
        private const val TAG = "DeviceDetailVM"
    }

    private val _device = MutableLiveData<Device>()
    val device: MutableLiveData<Device> = _device

    private val _commandHistory = MutableLiveData<Result<List<Command>>>()
    val commandHistory: MutableLiveData<Result<List<Command>>> = _commandHistory

    private val _events = MutableLiveData<Result<List<Event>>>()
    val events: MutableLiveData<Result<List<Event>>> = _events

    private val _commandResult = MutableLiveData<Result<String>>()
    val commandResult: MutableLiveData<Result<String>> = _commandResult

    private val _currentCategory = MutableLiveData(CommandDefinitions.Category.DATA)
    val currentCategory: MutableLiveData<CommandDefinitions.Category> = _currentCategory

    // Debug log entries — shown in the UI log panel
    private val _debugLogs = MutableLiveData<MutableList<String>>(mutableListOf())
    val debugLogs: MutableLiveData<MutableList<String>> = _debugLogs

    private var deviceId: String = ""

    // ─── Debug logging ──────────────────────────────────────────

    private fun log(level: String, message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val entry = "[$timestamp] $level: $message"
        Log.d(TAG, entry)

        val logs = _debugLogs.value ?: mutableListOf()
        logs.add(0, entry)  // newest first
        // Keep max 50 entries
        if (logs.size > 50) {
            val excess = logs.subList(50, logs.size)
            excess.clear()
        }
        _debugLogs.postValue(logs)
    }

    private fun logInfo(msg: String) = log("ℹ️", msg)
    private fun logError(msg: String) = log("❌", msg)
    private fun logSuccess(msg: String) = log("✅", msg)
    private fun logWarning(msg: String) = log("⚠️", msg)

    // ─── Device setup ───────────────────────────────────────────

    fun setDevice(device: Device) {
        _device.value = device
        deviceId = device.id
        logInfo("تم فتح جهاز: ${device.name.ifEmpty { device.model }} (ID: ${device.id})")
        logInfo("الحالة: ${if (device.isOnline) "متصل" else "غير متصل"} | البطارية: ${device.batteryLevel}% | آخر ظهور: ${device.lastSeen}")
        logInfo("الخادم: ${preferences.serverUrl}")
        logInfo("الرمز: ${if (preferences.token.isNullOrEmpty()) "غير موجود - ستحتاج لتسجيل الدخول" else "موجود (${preferences.token!!.take(8)}...)"}")
        // Don't load old commands/events on device open - only load when user navigates to those tabs
        _commandHistory.value = Result.Success(emptyList())
        _events.value = Result.Success(emptyList())
    }

    fun loadData() {
        loadCommandHistory()
        loadEvents()
    }

    // ─── Data loading ───────────────────────────────────────────

    private fun loadCommandHistory() {
        if (deviceId.isBlank()) {
            logError("لا يوجد معرف جهاز")
            return
        }
        viewModelScope.launch {
            try {
                logInfo("جاري تحميل سجل الأوامر...")
                val api = preferences.getApiService()
                val commands = api.getCommands(deviceId)
                logSuccess("تم تحميل ${commands.size} أمر")
                _commandHistory.postValue(Result.Success(commands))
            } catch (e: Exception) {
                val errorMsg = translateError(e, "تحميل سجل الأوامر")
                logError(errorMsg)
                _commandHistory.postValue(Result.Error(errorMsg))
            }
        }
    }

    private fun loadEvents() {
        viewModelScope.launch {
            try {
                logInfo("جاري تحميل الأحداث...")
                val api = preferences.getApiService()
                val allEvents = api.getEvents()
                val deviceEvents = allEvents.filter { it.deviceId == deviceId }
                logSuccess("تم تحميل ${deviceEvents.size} حدث")
                _events.postValue(Result.Success(deviceEvents))
            } catch (e: Exception) {
                val errorMsg = translateError(e, "تحميل الأحداث")
                logError(errorMsg)
                _events.postValue(Result.Error(errorMsg))
            }
        }
    }

    // ─── Category ───────────────────────────────────────────────

    fun setCategory(category: CommandDefinitions.Category) {
        _currentCategory.value = category
        val cmds = getCommandsForCategory()
        logInfo("التصنيف: ${category.displayName} (${cmds.size} أمر)")
    }

    fun getCommandsForCategory(): List<CommandDefinitions.CommandDef> {
        return CommandDefinitions.commandsByCategory[currentCategory.value]
            ?: CommandDefinitions.commandsByCategory[CommandDefinitions.Category.DATA]
            ?: emptyList()
    }

    // ─── Command sending ────────────────────────────────────────

    fun sendCommand(commandKey: String, params: Map<String, String> = emptyMap()) {
        if (deviceId.isBlank()) {
            logError("لا يوجد معرف جهاز - لا يمكن إرسال الأمر")
            _commandResult.postValue(Result.Error("لا يوجد جهاز محدد"))
            return
        }

        val device = _device.value
        if (device != null && !device.isOnline) {
            logWarning("⚠️ الجهاز غير متصل! الأمر سيُرسل لكن قد لا يُنفذ حتى يتصل الجهاز")
        }

        viewModelScope.launch {
            _commandResult.postValue(Result.Loading)
            logInfo("📦 إرسال أمر: [$commandKey] إلى الجهاز: $deviceId")
            if (params.isNotEmpty()) {
                logInfo("   المعاملات: $params")
            }

            try {
                val api = preferences.getApiService()
                val request = SendCommandRequest(commandKey, params)
                logInfo("   POST /api/web/send_command {command: \"$commandKey\", device_id: \"$deviceId\", params: $params}")

                val response = api.sendCommand(deviceId, request)

                logInfo("   استجابة الخادم: ok=${response.ok}, status=${response.status}, message=${response.message}")

                if (response.ok) {
                    logSuccess("✅ تم إرسال الأمر بنجاح: $commandKey")
                    if (response.command_id.isNotEmpty()) {
                        logInfo("   معرف الأمر: ${response.command_id}")
                    }
                    _commandResult.postValue(Result.Success("تم إرسال الأمر بنجاح: $commandKey"))
                    loadCommandHistory()
                } else {
                    val msg = response.message.ifEmpty { "فشل إرسال الأمر" }
                    logError("فشل إرسال الأمر: $msg (status: ${response.status})")
                    _commandResult.postValue(Result.Error(msg))
                }
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val body = try { e.response()?.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                val msg = when (code) {
                    401 -> "انتهت الجلسة - يرجى تسجيل الدخول مرة أخرى (401)"
                    403 -> "ليس لديك صلاحية لهذا الجهاز (403)"
                    404 -> "الجهاز غير موجود أو ليس لك (404)"
                    429 -> "تم تجاوز حد الإرسال - انتظر قليلاً (429)"
                    else -> "خطأ الخادم: HTTP $code"
                }
                logError("$msg | الجسم: $body")
                _commandResult.postValue(Result.Error(msg, code))
            } catch (e: SocketTimeoutException) {
                logError("انتهت مهلة الاتصال بالخادم - تحقق من اتصال الإنترنت")
                _commandResult.postValue(Result.Error("انتهت مهلة الاتصال بالخادم"))
            } catch (e: UnknownHostException) {
                logError("لا يمكن الوصول إلى الخادم: ${preferences.serverUrl}")
                _commandResult.postValue(Result.Error("لا يمكن الوصول إلى الخادم"))
            } catch (e: SSLException) {
                logError("خطأ في شهادة الأمان (SSL): ${e.message}")
                _commandResult.postValue(Result.Error("خطأ في شهادة الأمان"))
            } catch (e: ApiException) {
                logError("خطأ في API: ${e.message}")
                _commandResult.postValue(Result.Error(e.message ?: "خطأ في API"))
            } catch (e: Exception) {
                logError("خطأ غير متوقع: ${e.javaClass.simpleName}: ${e.message}")
                _commandResult.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    // ─── Quick actions ──────────────────────────────────────────

    fun takeScreenshot() {
        logInfo("📸 إجراء سريع: لقطة شاشة")
        sendCommand("screenshot")
    }

    fun getLocation() {
        logInfo("📍 إجراء سريع: الموقع الحالي")
        sendCommand("location")
    }

    fun getBatteryInfo() {
        logInfo("🔋 إجراء سريع: معلومات البطارية")
        sendCommand("battery")
    }

    // ─── Error translation ──────────────────────────────────────

    private fun translateError(e: Exception, operation: String): String {
        return when (e) {
            is retrofit2.HttpException -> {
                val code = e.code()
                when (code) {
                    401 -> "$operation: انتهت الجلسة (HTTP 401)"
                    403 -> "$operation: غير مصرح (HTTP 403)"
                    404 -> "$operation: غير موجود (HTTP 404)"
                    else -> "$operation: خطأ الخادم HTTP $code"
                }
            }
            is SocketTimeoutException -> "$operation: انتهت مهلة الاتصال"
            is UnknownHostException -> "$operation: لا يمكن الوصول إلى الخادم"
            is SSLException -> "$operation: خطأ SSL"
            is ApiException -> "$operation: ${e.message}"
            else -> "$operation: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}