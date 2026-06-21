package com.abuzahra.admin.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abuzahra.admin.data.api.CommandResponse
import com.abuzahra.admin.data.api.DeviceFilesResponse
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.api.StatsResponse
import com.abuzahra.admin.data.api.User
import com.abuzahra.admin.data.model.Command
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.data.model.RemoteFile
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for all dashboard fragments. Holds:
 *  - devices, stats, events, users (overview cards)
 *  - selectedDevice (commands/results/streaming fragments)
 *  - commands for the selected device (results fragment polling)
 *  - files (files fragment)
 *  - send-command action
 */
class DashboardViewModel(private val preferences: Preferences) : ViewModel() {

    // ── Devices ─────────────────────────────────────────────────
    private val _devices = MutableLiveData<Result<List<Device>>>(Result.Loading)
    val devices: LiveData<Result<List<Device>>> = _devices

    private var allDevices: List<Device> = emptyList()

    // ── Stats ───────────────────────────────────────────────────
    private val _stats = MutableLiveData<Result<StatsResponse>>(Result.Loading)
    val stats: LiveData<Result<StatsResponse>> = _stats

    // ── Events ──────────────────────────────────────────────────
    private val _events = MutableLiveData<Result<List<Event>>>(Result.Loading)
    val events: LiveData<Result<List<Event>>> = _events

    // ── Users (admin only) ──────────────────────────────────────
    private val _users = MutableLiveData<Result<List<User>>>(Result.Loading)
    val users: LiveData<Result<List<User>>> = _users

    // ── Selected device (shared between commands/results/streaming) ──
    private val _selectedDevice = MutableLiveData<Device?>(null)
    val selectedDevice: LiveData<Device?> = _selectedDevice

    // ── Commands (results view polling) ─────────────────────────
    private val _commands = MutableLiveData<Result<List<Command>>>(Result.Loading)
    val commands: LiveData<Result<List<Command>>> = _commands

    // ── Files (files view) ──────────────────────────────────────
    private val _files = MutableLiveData<Result<List<RemoteFile>>>(Result.Loading)
    val files: LiveData<Result<List<RemoteFile>>> = _files

    // ── Send-command result (for toast feedback) ────────────────
    private val _commandSendResult = MutableLiveData<Result<CommandResponse>?>()
    val commandSendResult: LiveData<Result<CommandResponse>?> = _commandSendResult

    // ── Link code ───────────────────────────────────────────────
    private val _linkCode = MutableLiveData<Result<String>?>()
    val linkCode: LiveData<Result<String>?> = _linkCode

    private val _regenerateResult = MutableLiveData<Result<String>?>()
    val regenerateResult: LiveData<Result<String>?> = _regenerateResult

    // ── Device search / filter (devices view) ───────────────────
    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    enum class FilterType { ALL, ONLINE, OFFLINE }
    private val _filterType = MutableLiveData(FilterType.ALL)
    val filterType: LiveData<FilterType> = _filterType

    // ── Initial load ────────────────────────────────────────────
    init {
        loadData()
    }

    fun loadData() {
        loadDevices()
        loadStats()
        loadEvents()
        loadUsers()
    }

    fun refresh() {
        loadData()
    }

    // ── Devices ─────────────────────────────────────────────────
    private fun loadDevices() {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val list = api.getDevices()
                allDevices = list
                _devices.postValue(Result.Success(list))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _devices.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _devices.postValue(Result.Error("خطأ في تحميل الأجهزة: ${e.code()}"))
                }
            } catch (e: Exception) {
                _devices.postValue(Result.Error("خطأ في الاتصال: ${e.message}"))
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val stats = api.getStats()
                _stats.postValue(Result.Success(stats))
            } catch (_: Exception) {
                _stats.postValue(Result.Success(StatsResponse()))
            }
        }
    }

    private fun loadEvents() {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val list = api.getEvents()
                _events.postValue(Result.Success(list))
            } catch (_: Exception) {
                _events.postValue(Result.Success(emptyList()))
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val list = api.getUsers()
                _users.postValue(Result.Success(list))
            } catch (_: Exception) {
                _users.postValue(Result.Success(emptyList()))
            }
        }
    }

    // ── Search / filter ─────────────────────────────────────────
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(type: FilterType) {
        _filterType.value = type
    }

    /** Returns the devices list filtered by current search query and filter type. */
    fun filteredDevices(): List<Device> {
        val q = (_searchQuery.value ?: "").lowercase()
        val f = _filterType.value ?: FilterType.ALL
        return allDevices.filter { d ->
            val matchesSearch = q.isBlank() ||
                    d.name.lowercase().contains(q) ||
                    d.model.lowercase().contains(q) ||
                    d.imei.contains(q) ||
                    d.phoneNumber.contains(q)
            val matchesFilter = when (f) {
                FilterType.ALL -> true
                FilterType.ONLINE -> d.isOnline
                FilterType.OFFLINE -> !d.isOnline
            }
            matchesSearch && matchesFilter
        }
    }

    // ── Selected device ─────────────────────────────────────────
    fun selectDevice(device: Device?) {
        _selectedDevice.value = device
        if (device != null) {
            loadCommands(device.id)
        }
    }

    // ── Commands (results) ──────────────────────────────────────
    fun loadCommands(deviceId: String) {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val list = api.getCommands(deviceId)
                _commands.postValue(Result.Success(list))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _commands.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _commands.postValue(Result.Error("خطأ: ${e.code()}"))
                }
            } catch (e: Exception) {
                _commands.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    // ── Files ───────────────────────────────────────────────────
    fun loadFiles(deviceId: String? = null) {
        viewModelScope.launch {
            _files.value = Result.Loading
            try {
                val api = preferences.getApiService()
                val response: DeviceFilesResponse = api.getRequestedFiles(deviceId)
                _files.postValue(Result.Success(response.files))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _files.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _files.postValue(Result.Error("خطأ: ${e.code()}"))
                }
            } catch (e: Exception) {
                _files.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    // ── Send command ────────────────────────────────────────────
    fun sendCommand(deviceId: String, command: String, params: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val request = com.abuzahra.admin.data.api.SendCommandRequest(
                    command = command,
                    params = params
                )
                val res = api.sendCommand(deviceId, request)
                _commandSendResult.postValue(Result.Success(res))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _commandSendResult.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _commandSendResult.postValue(Result.Error("خطأ: ${e.code()}"))
                }
            } catch (e: Exception) {
                _commandSendResult.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    fun consumeCommandSendResult() {
        _commandSendResult.value = null
    }

    // ── Link code ───────────────────────────────────────────────
    fun generateLinkCode() {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val code = api.getLinkCode()
                preferences.permanentCode = code
                _linkCode.postValue(Result.Success(code))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _linkCode.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _linkCode.postValue(Result.Error("خطأ: ${e.code()}"))
                }
            } catch (e: Exception) {
                _linkCode.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    fun regenerateCode() {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val res = api.regenerateCode()
                if (res.ok && res.code.isNotEmpty()) {
                    preferences.permanentCode = res.code
                    _regenerateResult.postValue(Result.Success(res.code))
                } else {
                    _regenerateResult.postValue(Result.Error("فشل إعادة توليد الكود"))
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _regenerateResult.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _regenerateResult.postValue(Result.Error("خطأ: ${e.code()}"))
                }
            } catch (e: Exception) {
                _regenerateResult.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    fun consumeRegenerateResult() {
        _regenerateResult.value = null
    }

    // ── Users admin actions ─────────────────────────────────────
    private val _userActionResult = MutableLiveData<Result<String>?>()
    val userActionResult: LiveData<Result<String>?> = _userActionResult

    fun createUser(username: String, password: String, email: String, role: String) {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val request = com.abuzahra.admin.data.api.CreateUserRequest(
                    username = username,
                    password = password,
                    email = email,
                    role = role
                )
                val res = api.createUser(request)
                if (res.ok) {
                    _userActionResult.postValue(Result.Success("تم إنشاء المستخدم بنجاح"))
                    loadUsers()
                } else {
                    _userActionResult.postValue(Result.Error(res.message.ifEmpty { "فشل إنشاء المستخدم" }))
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _userActionResult.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _userActionResult.postValue(Result.Error("خطأ: ${e.code()}"))
                }
            } catch (e: Exception) {
                _userActionResult.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            try {
                val api = preferences.getApiService()
                val ok = api.deleteUser(userId)
                if (ok) {
                    _userActionResult.postValue(Result.Success("تم حذف المستخدم"))
                    loadUsers()
                } else {
                    _userActionResult.postValue(Result.Error("فشل حذف المستخدم"))
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    _userActionResult.postValue(Result.Error("انتهت صلاحية الجلسة", 401))
                } else {
                    _userActionResult.postValue(Result.Error("خطأ: ${e.code()}"))
                }
            } catch (e: Exception) {
                _userActionResult.postValue(Result.Error("خطأ: ${e.message}"))
            }
        }
    }

    fun consumeUserActionResult() {
        _userActionResult.value = null
    }

    // ── Stream helpers ──────────────────────────────────────────
    suspend fun fetchStreamFrame(deviceId: String, type: String): com.abuzahra.admin.data.api.StreamFrameResponse? {
        return try {
            val api = preferences.getApiService()
            api.getStreamFrame(deviceId, type)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun startJpegStream(deviceId: String, type: String): Boolean {
        return try {
            val api = preferences.getApiService()
            api.startJpegStream(deviceId, type)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun stopJpegStream(deviceId: String): Boolean {
        return try {
            val api = preferences.getApiService()
            api.stopJpegStream(deviceId)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Snapshot helpers ────────────────────────────────────────
    fun recentDevices(limit: Int = 5): List<Device> = allDevices.take(limit)

    fun onlineDeviceCount(): Int = allDevices.count { it.isOnline }

    fun offlineDeviceCount(): Int = allDevices.count { !it.isOnline }

    fun totalUsersCount(): Int =
        (_users.value as? Result.Success)?.data?.size ?: 0

    fun logout() {
        preferences.clear()
    }

    val prefs: Preferences get() = preferences
}

class DashboardViewModelFactory(private val preferences: Preferences) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
