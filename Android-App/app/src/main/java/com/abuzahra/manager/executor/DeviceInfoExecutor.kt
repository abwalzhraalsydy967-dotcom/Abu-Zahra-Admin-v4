package com.abuzahra.manager.executor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Locale

/**
 * DeviceInfoExecutor — implements device-identity / telephony / network-IP commands.
 *
 * Many of these (IMEI, IMSI, serial, phone number) are restricted on
 * Android 10+ and require READ_PRIVILEGED_PHONE_STATE (system-signature).
 * READ_PHONE_STATE only returns subset on Android 10+. We attempt the
 * call and gracefully return an honest "permission-restricted" error.
 */
object DeviceInfoExecutor {

    private const val TAG = "DeviceInfoExecutor"

    private fun telephony(context: Context): TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private fun hasPhoneState(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasReadPhoneNumbers(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ===== DEVICE ID (Settings.Secure.ANDROID_ID — stable per-app-signing-key on Android 8+) =====
    fun getDeviceId(context: Context): Map<String, Any> {
        return try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
            mapOf(
                "android_id" to androidId,
                "source" to "Settings.Secure.ANDROID_ID",
                "note" to "Per-app-signing-key stable; not the same as TelephonyManager.getDeviceId()"
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to read ANDROID_ID"))
        }
    }

    // ===== IMEI =====
    @SuppressLint("HardwareIds")
    fun getImei(context: Context): Map<String, Any> {
        if (!hasPhoneState(context)) {
            return mapOf(
                "error" to "READ_PHONE_STATE permission required",
                "hint" to "On Android 10+ IMEI requires READ_PRIVILEGED_PHONE_STATE (system-signature only)"
            )
        }
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val imei = try { tm.imei } catch (e: SecurityException) { null }
                if (imei != null) {
                    return mapOf("imei" to imei, "source" to "TelephonyManager.getImei()")
                }
                mapOf(
                    "error" to "IMEI not accessible on Android 10+ without READ_PRIVILEGED_PHONE_STATE",
                    "hint" to "Requires system-signature app or device-owner"
                )
            } else {
                @Suppress("DEPRECATION")
                mapOf("imei" to (tm.deviceId ?: ""), "source" to "TelephonyManager.getDeviceId()")
            }
        } catch (e: SecurityException) {
            mapOf("error" to "IMEI access denied: ${e.message}")
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "IMEI error"))
        }
    }

    // ===== IMSI =====
    @SuppressLint("HardwareIds")
    fun getImsi(context: Context): Map<String, Any> {
        if (!hasPhoneState(context)) {
            return mapOf("error" to "READ_PHONE_STATE permission required")
        }
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            val imsi = try {
                @Suppress("DEPRECATION")
                tm.subscriberId
            } catch (e: SecurityException) { null }
            if (imsi != null) {
                mapOf("imsi" to imsi)
            } else {
                mapOf(
                    "error" to "IMSI not accessible on Android 10+ without READ_PRIVILEGED_PHONE_STATE",
                    "hint" to "SubscriberId is restricted to system apps"
                )
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "IMSI error"))
        }
    }

    // ===== PHONE NUMBER =====
    @SuppressLint("HardwareIds")
    fun getPhoneNumber(context: Context): Map<String, Any> {
        if (!hasReadPhoneNumbers(context)) {
            return mapOf(
                "error" to "READ_PHONE_NUMBERS permission required (Android 8+)",
                "hint" to "On older Android, READ_PHONE_STATE suffices"
            )
        }
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    tm.line1Number
                } catch (e: SecurityException) { null }
            } else {
                @Suppress("DEPRECATION")
                try { tm.line1Number } catch (e: SecurityException) { null }
            }
            if (number.isNullOrBlank()) {
                mapOf(
                    "phone_number" to "",
                    "message" to "Number not provisioned on SIM or not readable"
                )
            } else {
                mapOf("phone_number" to number)
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Phone number error"))
        }
    }

    // ===== SERIAL NUMBER =====
    @SuppressLint("HardwareIds")
    fun getSerial(context: Context): Map<String, Any> {
        return try {
            val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    if (hasPhoneState(context)) Build.getSerial() else "READ_PHONE_STATE required"
                } catch (e: SecurityException) {
                    "restricted (requires READ_PRIVILEGED_PHONE_STATE on Android 10+)"
                } catch (e: Exception) {
                    "unknown"
                }
            } else {
                @Suppress("DEPRECATION") Build.SERIAL ?: "unknown"
            }
            mapOf("serial" to serial)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Serial error"))
        }
    }

    // ===== MAC ADDRESS =====
    @SuppressLint("HardwareIds")
    fun getMacAddress(context: Context): Map<String, Any> {
        // On Android 6+ WifiManager.getConnectionInfo().getMacAddress() returns 02:00:00:00:00:00.
        // Try to walk network interfaces for a real MAC.
        return try {
            var mac = "02:00:00:00:00:00"
            var source = "WifiManager placeholder (Android 6+ restriction)"
            try {
                val interfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
                for (nif in interfaces) {
                    if (!nif.isUp || nif.isLoopback) continue
                    val raw = nif.hardwareAddress ?: continue
                    if (raw.size != 6) continue
                    val formatted = raw.joinToString(":") { "%02X".format(it) }
                    if (nif.name.startsWith("wlan", ignoreCase = true)) {
                        mac = formatted
                        source = "NetworkInterface '${nif.name}'"
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "NetworkInterface MAC scan failed", e)
            }
            mapOf("mac_address" to mac, "interface_source" to source)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "MAC error"))
        }
    }

    // ===== IP ADDRESS (IPv4) =====
    fun getIpAddress(context: Context): Map<String, Any> {
        return try {
            var ip = ""
            var interfaceName = ""
            val interfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val s = addr.hostAddress ?: ""
                        if (s.contains(":")) continue // skip IPv6 here
                        ip = s
                        interfaceName = nif.name
                        break
                    }
                }
                if (ip.isNotEmpty()) break
            }
            if (ip.isEmpty()) {
                // Fallback to WifiManager DHCP info
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val dhcp = wifiManager?.dhcpInfo
                if (dhcp != null) {
                    ip = String.format(
                        "%d.%d.%d.%d",
                        dhcp.ipAddress and 0xff,
                        (dhcp.ipAddress shr 8) and 0xff,
                        (dhcp.ipAddress shr 16) and 0xff,
                        (dhcp.ipAddress shr 24) and 0xff
                    )
                    interfaceName = "wifi (dhcp)"
                }
            }
            mapOf("ip_address" to ip, "interface" to interfaceName)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "IP error"))
        }
    }

    // ===== IPv6 ADDRESS =====
    fun getIpv6Address(): Map<String, Any> {
        return try {
            var ip = ""
            var interfaceName = ""
            val interfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet6Address) {
                        ip = addr.hostAddress ?: ""
                        interfaceName = nif.name
                        break
                    }
                }
                if (ip.isNotEmpty()) break
            }
            if (ip.isEmpty()) {
                mapOf("ipv6_address" to "", "message" to "No global IPv6 address found")
            } else {
                mapOf("ipv6_address" to ip, "interface" to interfaceName)
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "IPv6 error"))
        }
    }

    // ===== NETWORK OPERATOR =====
    fun getNetworkOperator(context: Context): Map<String, Any> {
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            mapOf(
                "operator_name" to (tm.networkOperatorName ?: ""),
                "operator_code" to (tm.networkOperator ?: ""),
                "is_roaming" to try { tm.isNetworkRoaming } catch (_: SecurityException) { false }
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Network operator error"))
        }
    }

    // ===== SIM OPERATOR =====
    fun getSimOperator(context: Context): Map<String, Any> {
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            mapOf(
                "sim_operator_name" to (tm.simOperatorName ?: ""),
                "sim_operator_code" to (tm.simOperator ?: "")
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "SIM operator error"))
        }
    }

    // ===== SIM COUNTRY =====
    fun getSimCountry(context: Context): Map<String, Any> {
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            val iso = tm.simCountryIso ?: ""
            val name = if (iso.isNotBlank()) Locale("", iso).displayCountry else ""
            mapOf("sim_country_iso" to iso, "sim_country_name" to name)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "SIM country error"))
        }
    }

    // ===== NETWORK COUNTRY =====
    fun getNetworkCountry(context: Context): Map<String, Any> {
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            val iso = tm.networkCountryIso ?: ""
            val name = if (iso.isNotBlank()) Locale("", iso).displayCountry else ""
            mapOf("network_country_iso" to iso, "network_country_name" to name)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Network country error"))
        }
    }

    // ===== PHONE TYPE =====
    fun getPhoneType(context: Context): Map<String, Any> {
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            val type = try { tm.phoneType } catch (_: SecurityException) { TelephonyManager.PHONE_TYPE_NONE }
            val typeStr = when (type) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                else -> "NONE"
            }
            mapOf("phone_type" to typeStr, "phone_type_code" to type)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Phone type error"))
        }
    }

    // ===== SIM STATE =====
    fun getSimState(context: Context): Map<String, Any> {
        return try {
            val tm = telephony(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            val state = try { tm.simState } catch (_: SecurityException) { TelephonyManager.SIM_STATE_UNKNOWN }
            val stateStr = when (state) {
                TelephonyManager.SIM_STATE_READY -> "READY"
                TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
                else -> "UNKNOWN"
            }
            mapOf("sim_state" to stateStr, "sim_state_code" to state)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "SIM state error"))
        }
    }

    // ===== DATA STATE =====
    fun getDataState(context: Context): Map<String, Any> {
        return try {
            val tm = telephonyManagerCompat(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            // TelephonyManager.getDataState() was deprecated in API 26
            val state = try {
                @Suppress("DEPRECATION")
                tm.dataState
            } catch (_: Exception) { -1 }
            val stateStr = when (state) {
                0 -> "DISCONNECTED"
                1 -> "CONNECTING"
                2 -> "CONNECTED"
                3 -> "SUSPENDED"
                else -> "UNKNOWN"
            }
            // Also try ConnectivityManager for a real-time check
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            var connected = false
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(net)
                connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
            mapOf(
                "data_state" to stateStr,
                "data_state_code" to state,
                "mobile_data_connected" to connected
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Data state error"))
        }
    }

    // ===== DATA ACTIVITY =====
    fun getDataActivity(context: Context): Map<String, Any> {
        return try {
            val tm = telephonyManagerCompat(context) ?: return mapOf("error" to "TelephonyManager unavailable")
            val activity = try {
                @Suppress("DEPRECATION")
                tm.dataActivity
            } catch (_: Exception) { -1 }
            val actStr = when (activity) {
                0 -> "NONE"
                1 -> "DIN"  // Data In
                2 -> "DOUT" // Data Out
                3 -> "DINOUT"
                4 -> "DORMANT"
                else -> "UNKNOWN"
            }
            mapOf("data_activity" to actStr, "data_activity_code" to activity)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Data activity error"))
        }
    }

    private fun telephonyManagerCompat(context: Context): TelephonyManager? {
        return try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (_: Exception) { null }
    }
}
