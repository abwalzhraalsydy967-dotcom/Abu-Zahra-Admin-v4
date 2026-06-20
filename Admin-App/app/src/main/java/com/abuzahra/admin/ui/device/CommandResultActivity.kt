package com.abuzahra.admin.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiException
import com.abuzahra.admin.data.model.Command
import com.abuzahra.admin.data.model.CommandDefinitions
import com.abuzahra.admin.databinding.ActivityCommandResultBinding
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * CommandResultActivity — polls the server for the result of a sent command
 * and renders it in a UI suited to the command type (list / image / map
 * link / raw JSON).
 *
 * Flow:
 *  1. Started by DeviceDetailActivity with device_id, command_id and
 *     command_key (e.g. "sms", "screenshot", "location").
 *  2. Polls `GET /api/web/commands?device_id=X` every 2 seconds.
 *  3. Finds the command by command_id and inspects its status.
 *       - pending / sent  → keep polling (server's api_web_commands
 *         endpoint filters out pending/sent commands, so an empty list
 *         also means "still pending").
 *       - completed       → parse the result JSON via [CommandResultParser]
 *         and render it.
 *       - failed / error  → show the failure message.
 *  4. The user can pull-to-refresh or tap "تحديث" to poll once manually.
 *  5. The user can tap "نسخ" to copy the raw result JSON to the clipboard.
 *
 * Auto-polling stops after [MAX_POLL_DURATION_MS] (5 min) to save battery —
 * after that the user must refresh manually. Polling also pauses when the
 * activity is paused (screen-off / app backgrounded).
 */
class CommandResultActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CommandResultActivity"

        /** Polling interval. */
        private const val POLL_INTERVAL_MS = 2000L

        /** Maximum auto-poll duration before falling back to manual refresh. */
        private const val MAX_POLL_DURATION_MS = 5L * 60 * 1000 // 5 min

        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_COMMAND_ID = "extra_command_id"
        const val EXTRA_COMMAND_KEY = "extra_command_key"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        /**
         * Convenience launcher used by DeviceDetailActivity.
         * @param commandKey the server registry key (e.g. "sms") OR the
         *        actual device command (e.g. "get_sms").
         */
        fun newIntent(
            context: Context,
            deviceId: String,
            commandId: String,
            commandKey: String,
            deviceName: String = ""
        ): Intent {
            return Intent(context, CommandResultActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_COMMAND_ID, commandId)
                putExtra(EXTRA_COMMAND_KEY, commandKey)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
        }
    }

    private lateinit var binding: ActivityCommandResultBinding
    private val prefs: Preferences by lazy { Preferences.getInstance(this) }

    private lateinit var deviceId: String
    private lateinit var commandId: String
    private lateinit var commandKey: String
    private var deviceName: String = ""

    /** The latest Command record we got from the server (if any). */
    @Volatile private var currentCommand: Command? = null

    /** True after we've already rendered a completed/failed result. */
    @Volatile private var resultRendered: Boolean = false

    /** Background polling job — cancelled in onPause. */
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read extras
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        commandId = intent.getStringExtra(EXTRA_COMMAND_ID).orEmpty()
        commandKey = intent.getStringExtra(EXTRA_COMMAND_KEY).orEmpty()
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty()

        if (deviceId.isEmpty() || commandId.isEmpty() || commandKey.isEmpty()) {
            finish()
            return
        }

        setupToolbar()
        setupStatusCard()
        setupActionButtons()
        setupSwipeRefresh()
    }

    override fun onResume() {
        super.onResume()
        // Start (or resume) polling if we haven't rendered a final result yet.
        if (!resultRendered && pollJob?.isActive != true) {
            startPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel polling to save battery while the activity is not visible.
        pollJob?.cancel()
        pollJob = null
    }

    // ═══════════════════════════════════════════════════════════════
    // Setup
    // ═══════════════════════════════════════════════════════════════

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        val title = CommandDefinitions.findByKey(commandKey)?.name
            ?: commandKey
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupStatusCard() {
        val def = CommandDefinitions.findByKey(commandKey)
        binding.tvCommandName.text = def?.name ?: commandKey
        binding.tvCommandKey.text = commandId
        binding.ivCommandIcon.setImageResource(R.drawable.ic_command)
        binding.chipStatus.text = getString(R.string.pending)
        binding.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.pending_color)
        )
        binding.tvSentAt.text = "—"
        binding.tvCompletedAt.text = "—"
        binding.tvDuration.text = "—"
    }

    private fun setupActionButtons() {
        binding.btnRefresh.setOnClickListener {
            // Manual refresh always does one immediate fetch.
            fetchOnce(showLoading = true)
        }
        binding.btnCopy.setOnClickListener {
            val raw = currentCommand?.result
            if (raw.isNullOrBlank()) {
                Snackbar.make(binding.coordinator, getString(R.string.empty_result), Snackbar.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("command_result", raw))
                Snackbar.make(binding.coordinator, getString(R.string.copied_raw_result), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            fetchOnce(showLoading = false)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Polling
    // ═══════════════════════════════════════════════════════════════

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            // Immediate first fetch
            fetchOnce(showLoading = true)
            while (isActive && !resultRendered) {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed > MAX_POLL_DURATION_MS) {
                    // Stop auto-polling — let the user refresh manually.
                    withContext(Dispatchers.Main) {
                        if (!resultRendered) {
                            binding.tvPollHint.text = getString(R.string.polling_timeout)
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                    break
                }
                delay(POLL_INTERVAL_MS)
                if (!isActive || resultRendered) break
                fetchOnce(showLoading = false)
            }
        }
    }

    /**
     * Fetch the command list once and dispatch to the result renderer.
     */
    private fun fetchOnce(showLoading: Boolean) {
        if (showLoading && !resultRendered) {
            binding.loadingView.visibility = View.VISIBLE
            binding.errorView.visibility = View.GONE
        }
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                val api = prefs.getApiService()
                val commands = api.getCommands(deviceId)
                val cmd = commands.firstOrNull { it.id == commandId }

                if (cmd == null) {
                    // Command not yet visible — server's api_web_commands
                    // filters out pending/sent commands. Keep polling.
                    if (showLoading && !resultRendered) {
                        binding.tvLoadingMessage.text = getString(R.string.loading_result)
                    }
                    binding.swipeRefresh.isRefreshing = false
                    return@launch
                }

                currentCommand = cmd
                updateStatusCard(cmd)

                when (cmd.status.lowercase()) {
                    "completed", "success" -> {
                        resultRendered = true
                        binding.loadingView.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        renderResult(cmd)
                    }
                    "failed", "error" -> {
                        resultRendered = true
                        binding.loadingView.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        showError(getString(R.string.result_failed),
                            cmd.result ?: cmd.status)
                    }
                    else -> {
                        // pending / sent / delivered — keep polling
                        binding.tvLoadingMessage.text = getString(R.string.result_pending)
                        binding.swipeRefresh.isRefreshing = false
                    }
                }
            } catch (e: retrofit2.HttpException) {
                binding.swipeRefresh.isRefreshing = false
                if (e.code() == 401) {
                    showSessionExpired()
                } else if (!resultRendered) {
                    showError("خطأ HTTP ${e.code()}", e.message ?: "")
                }
            } catch (e: SocketTimeoutException) {
                binding.swipeRefresh.isRefreshing = false
                if (!resultRendered) {
                    showError("انتهت مهلة الاتصال", "تأكد من اتصال الإنترنت وحاول مرة أخرى")
                }
            } catch (e: UnknownHostException) {
                binding.swipeRefresh.isRefreshing = false
                if (!resultRendered) {
                    showError("لا يمكن الوصول إلى الخادم", prefs.serverUrl)
                }
            } catch (e: ApiException) {
                binding.swipeRefresh.isRefreshing = false
                if (!resultRendered) {
                    showError("خطأ في API", e.message ?: "")
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Log.e(TAG, "Poll failed", e)
                if (!resultRendered) {
                    showError("خطأ غير متوقع", "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Rendering
    // ═══════════════════════════════════════════════════════════════

    private fun updateStatusCard(cmd: Command) {
        // Status chip
        val statusLabel = when (cmd.status.lowercase()) {
            "completed", "success" -> getString(R.string.success)
            "failed", "error" -> getString(R.string.failed)
            "delivered" -> getString(R.string.pending) // still executing
            "sent" -> getString(R.string.pending)
            else -> getString(R.string.pending)
        }
        binding.chipStatus.text = statusLabel
        val chipColor = when (cmd.status.lowercase()) {
            "completed", "success" -> R.color.success
            "failed", "error" -> R.color.error
            else -> R.color.pending_color
        }
        binding.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, chipColor)
        )

        // Timing
        binding.tvSentAt.text = formatTime(cmd.createdAt)
        binding.tvCompletedAt.text = formatTime(cmd.completedAt ?: "—")
        binding.tvDuration.text = computeDuration(cmd.createdAt, cmd.completedAt)
    }

    private fun formatTime(input: String): String {
        if (input.isBlank() || input == "—") return "—"
        return try {
            // Server stores ISO format like "2024-01-15T12:34:56.789012"
            // — strip any fractional seconds before parsing.
            val normalized = if (input.contains('.')) {
                input.substringBefore('.') + input.substringAfter('Z', "")
            } else {
                input
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = sdf.parse(normalized) ?: return input
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(date)
        } catch (e: Exception) {
            input
        }
    }

    private fun computeDuration(start: String, end: String?): String {
        if (end.isNullOrBlank()) return "—"
        return try {
            val normalize: (String) -> String = { input ->
                if (input.contains('.')) input.substringBefore('.') else input
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val s = sdf.parse(normalize(start))?.time ?: return "—"
            val e = sdf.parse(normalize(end))?.time ?: return "—"
            val diff = (e - s) / 1000  // seconds
            when {
                diff < 60 -> "${diff}ث"
                diff < 3600 -> "${diff / 60}د ${diff % 60}ث"
                else -> "${diff / 3600}س ${(diff % 3600) / 60}د"
            }
        } catch (e: Exception) {
            "—"
        }
    }

    /**
     * Parse [cmd.result] via [CommandResultParser] and render the
     * [CommandResultParser.ParsedResult] in the resultsHost FrameLayout.
     */
    private fun renderResult(cmd: Command) {
        // The Command.command field holds the ACTUAL device command (e.g.
        // "get_sms") when the device responded. Prefer that for parsing
        // (since CommandResultParser accepts both forms), but fall back
        // to the command_key we were launched with.
        val effectiveKey = cmd.command.ifBlank { commandKey }
        val parsed = CommandResultParser.parse(effectiveKey, cmd.result)

        // Summary line
        val summary = when (parsed) {
            is CommandResultParser.ParsedResult.SmsList ->
                getString(R.string.items_count, parsed.items.size)
            is CommandResultParser.ParsedResult.ContactList ->
                getString(R.string.items_count, parsed.items.size)
            is CommandResultParser.ParsedResult.CallList ->
                getString(R.string.items_count, parsed.items.size)
            is CommandResultParser.ParsedResult.NotificationList ->
                getString(R.string.items_count, parsed.items.size)
            is CommandResultParser.ParsedResult.AppList ->
                getString(R.string.items_count, parsed.items.size)
            is CommandResultParser.ParsedResult.FileList ->
                getString(R.string.items_count, parsed.items.size)
            is CommandResultParser.ParsedResult.KeyValueMap ->
                getString(R.string.items_count, parsed.items.size)
            else -> ""
        }
        if (summary.isNotEmpty()) {
            binding.tvResultSummary.text = summary
            binding.tvResultSummary.visibility = View.VISIBLE
        } else {
            binding.tvResultSummary.visibility = View.GONE
        }

        // Clear previous children
        binding.resultsHost.removeAllViews()

        when (parsed) {
            is CommandResultParser.ParsedResult.Empty -> {
                showEmptyState(getString(R.string.no_result_data))
            }
            is CommandResultParser.ParsedResult.SmsList -> {
                bindRecyclerView(SmsAdapter().apply { submitList(parsed.items) })
            }
            is CommandResultParser.ParsedResult.ContactList -> {
                bindRecyclerView(ContactAdapter().apply { submitList(parsed.items) })
            }
            is CommandResultParser.ParsedResult.CallList -> {
                bindRecyclerView(CallAdapter().apply { submitList(parsed.items) })
            }
            is CommandResultParser.ParsedResult.NotificationList -> {
                bindRecyclerView(NotificationAdapter().apply { submitList(parsed.items) })
            }
            is CommandResultParser.ParsedResult.AppList -> {
                bindRecyclerView(AppListAdapter().apply { submitList(parsed.items) })
            }
            is CommandResultParser.ParsedResult.FileList -> {
                bindRecyclerView(FileListAdapter().apply { submitList(parsed.items) })
            }
            is CommandResultParser.ParsedResult.KeyValueMap -> {
                bindRecyclerView(KeyValueAdapter().apply { submitList(parsed.items) })
            }
            is CommandResultParser.ParsedResult.Location -> renderLocation(parsed)
            is CommandResultParser.ParsedResult.Battery -> renderBattery(parsed)
            is CommandResultParser.ParsedResult.Image -> renderImage(parsed)
            is CommandResultParser.ParsedResult.RawJson -> renderRawJson(parsed.text)
        }
    }

    /**
     * Wrap a RecyclerView in the resultsHost with the standard layout
     * params (match_parent width, wrap_content height, no nested scrolling
     * so it expands inside the NestedScrollView).
     */
    private fun bindRecyclerView(adapter: RecyclerView.Adapter<*>) {
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@CommandResultActivity)
            this.adapter = adapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        binding.resultsHost.addView(rv)
    }

    private fun renderLocation(loc: CommandResultParser.ParsedResult.Location) {
        val view = layoutInflater.inflate(R.layout.view_location_result, null, false)
        val tvCoords = view.findViewById<TextView>(R.id.tvCoords)
        val tvAccuracy = view.findViewById<TextView>(R.id.tvAccuracy)
        val tvAltitude = view.findViewById<TextView>(R.id.tvAltitude)
        val tvExtras = view.findViewById<TextView>(R.id.tvExtras)
        val btnMaps = view.findViewById<MaterialButton>(R.id.btnOpenMaps)
        val btnOsm = view.findViewById<MaterialButton>(R.id.btnOpenOsm)
        val btnCopy = view.findViewById<MaterialButton>(R.id.btnCopyCoords)
        val mapFrame = view.findViewById<FrameLayout>(R.id.mapFrame)
        val mapWebView = view.findViewById<WebView>(R.id.mapWebView)
        val mapPlaceholder = view.findViewById<View>(R.id.mapPlaceholder)

        // Validate the location: must be non-zero and within sane ranges.
        val valid = java.lang.Math.abs(loc.lat) > 0.0001 ||
                    java.lang.Math.abs(loc.lng) > 0.0001
        val latOk = loc.lat in -90.0..90.0
        val lngOk = loc.lng in -180.0..180.0
        val locationValid = valid && latOk && lngOk

        tvCoords.text = String.format(Locale.US, getString(R.string.coords_format), loc.lat, loc.lng)
        tvAccuracy.text = loc.accuracy?.let {
            String.format(Locale.US, getString(R.string.accuracy_format), it)
        } ?: "—"

        // Extract altitude / speed / bearing from the extras map (if present)
        // and surface them in a dedicated line so the user can see them at a
        // glance without scanning the raw extras dump.
        val alt = loc.extras["altitude"]?.toDoubleOrNull()
            ?: loc.extras["alt"]?.toDoubleOrNull()
        val speed = loc.extras["speed"]?.toDoubleOrNull()
        val bearing = loc.extras["bearing"]?.toDoubleOrNull()
            ?: loc.extras["heading"]?.toDoubleOrNull()
        if (alt != null || speed != null || bearing != null) {
            val parts = mutableListOf<String>()
            if (alt != null) parts.add(String.format(Locale.US,
                getString(R.string.altitude_format), alt))
            if (speed != null) parts.add("السرعة: ${"%.1f".format(Locale.US, speed)} م/ث")
            if (bearing != null) parts.add("الاتجاه: ${"%.0f".format(Locale.US, bearing)}°")
            tvAltitude.text = parts.joinToString("  •  ")
            tvAltitude.visibility = View.VISIBLE
        } else {
            tvAltitude.visibility = View.GONE
        }

        // Show any remaining extras (provider, timestamp, etc.) that weren't
        // promoted to the altitude/speed/bearing line.
        val promotedKeys = setOf("altitude", "alt", "speed", "bearing", "heading")
        val remaining = loc.extras.filterKeys { it.lowercase() !in promotedKeys }
        if (remaining.isNotEmpty()) {
            val sb = StringBuilder()
            for ((k, v) in remaining) {
                sb.append("$k: $v\n")
            }
            tvExtras.text = sb.toString().trimEnd('\n')
            tvExtras.visibility = View.VISIBLE
        } else {
            tvExtras.visibility = View.GONE
        }

        // Round the WebView's corners via a ViewOutlineProvider. The FrameLayout
        // already has a rounded background drawable; clipToOutline ensures the
        // WebView and placeholder are clipped to that rounded rectangle.
        val cornerRadius = resources.getDimension(R.dimen.radius_md)
        mapFrame.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        mapFrame.clipToOutline = true

        // Configure the WebView with the OpenStreetMap embed. No API key
        // is required — the embed.html endpoint renders a tile map with a
        // marker and is free to use. The bbox is a small box (±0.01° ≈ ±1.1km
        // at the equator) around the marker so the map zooms in on the point.
        if (locationValid) {
            mapWebView.visibility = View.VISIBLE
            mapPlaceholder.visibility = View.GONE
            mapWebView.apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                    allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = true
                }
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                val dLat = 0.01
                val dLng = 0.01
                // %2C is the URL-encoded comma; OpenStreetMap's embed endpoint
                // expects bbox=minLng,minLat,maxLng,maxLat and marker=lat,lng.
                val bbox = "${loc.lng - dLng}%2C${loc.lat - dLat}%2C" +
                           "${loc.lng + dLng}%2C${loc.lat + dLat}"
                val url = "https://www.openstreetmap.org/export/embed.html" +
                          "?bbox=$bbox&layer=mapnik&marker=${loc.lat}%2C${loc.lng}"
                loadUrl(url)
            }
        } else {
            // Coordinates are zero or out of range — no map to show.
            mapWebView.visibility = View.GONE
            mapPlaceholder.visibility = View.VISIBLE
            tvCoords.text = getString(R.string.location_unavailable)
            tvAccuracy.text = getString(R.string.location_invalid_hint)
            tvAltitude.visibility = View.GONE
            tvExtras.visibility = View.GONE
        }

        // "خرائط Google" button → launch the Google Maps app via a
        // geo: intent, falling back to a web URL if no maps app is installed.
        btnMaps.setOnClickListener {
            if (!locationValid) {
                Snackbar.make(binding.coordinator,
                    getString(R.string.location_unavailable), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uri = Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to a generic geo intent (any maps app)
                val fallback = Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}"))
                try {
                    if (fallback.resolveActivity(packageManager) != null) {
                        startActivity(fallback)
                    } else {
                        // Last-ditch fallback: open Google Maps web in a browser
                        val webIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps?q=${loc.lat},${loc.lng}"))
                        startActivity(webIntent)
                    }
                } catch (e2: Exception) {
                    Snackbar.make(binding.coordinator,
                        getString(R.string.no_maps_app), Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        // "OpenStreetMap" button → open the OpenStreetMap site in a browser
        // at the same coordinates (uses the mlat/mlon marker query params).
        btnOsm.setOnClickListener {
            if (!locationValid) {
                Snackbar.make(binding.coordinator,
                    getString(R.string.location_unavailable), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val osmUrl = "https://www.openstreetmap.org/?mlat=${loc.lat}&mlon=${loc.lng}#map=16/${loc.lat}/${loc.lng}"
            val osmIntent = Intent(Intent.ACTION_VIEW, Uri.parse(osmUrl))
            try {
                startActivity(osmIntent)
            } catch (e: Exception) {
                Snackbar.make(binding.coordinator,
                    getString(R.string.no_maps_app), Snackbar.LENGTH_SHORT).show()
            }
        }

        // "نسخ الإحداثيات" button → copy "lat,lng" to the clipboard.
        btnCopy.setOnClickListener {
            if (!locationValid) {
                Snackbar.make(binding.coordinator,
                    getString(R.string.location_unavailable), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val coords = String.format(Locale.US, "%.6f, %.6f", loc.lat, loc.lng)
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("location", coords))
            Snackbar.make(binding.coordinator,
                getString(R.string.copied_to_clipboard),
                Snackbar.LENGTH_SHORT).show()
        }

        binding.resultsHost.addView(view)
    }

    private fun renderBattery(battery: CommandResultParser.ParsedResult.Battery) {
        val view = layoutInflater.inflate(R.layout.view_battery_result, null, false)
        val tvLevel = view.findViewById<TextView>(R.id.tvBatteryLevel)
        val tvCharging = view.findViewById<TextView>(R.id.tvChargingStatus)
        val tvExtras = view.findViewById<TextView>(R.id.tvBatteryExtras)

        tvLevel.text = String.format(Locale.US, getString(R.string.battery_pct), battery.level)
        tvCharging.text = if (battery.charging) getString(R.string.charging_yes)
                          else getString(R.string.charging_no)
        val colorRes = when {
            battery.level >= 70 -> R.color.battery_high
            battery.level >= 30 -> R.color.battery_medium
            else -> R.color.battery_low
        }
        tvLevel.setTextColor(ContextCompat.getColor(this, colorRes))

        if (battery.extras.isNotEmpty()) {
            val sb = StringBuilder()
            for ((k, v) in battery.extras) {
                sb.append("$k: $v\n")
            }
            tvExtras.text = sb.toString().trimEnd('\n')
            tvExtras.visibility = View.VISIBLE
        } else {
            tvExtras.visibility = View.GONE
        }

        binding.resultsHost.addView(view)
    }

    private fun renderImage(img: CommandResultParser.ParsedResult.Image) {
        val view = layoutInflater.inflate(R.layout.view_image_result, null, false)
        val iv = view.findViewById<ImageView>(R.id.ivResultImage)
        val tvMeta = view.findViewById<TextView>(R.id.tvImageMeta)
        val btnDownload = view.findViewById<MaterialButton>(R.id.btnSaveImage)

        tvMeta.text = "JPEG • ${img.base64.length} حرف (base64)"

        // Decode base64 → Bitmap on a background thread to avoid jank.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(img.base64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                withContext(Dispatchers.Main) {
                    if (bmp != null) {
                        iv.setImageBitmap(bmp)
                        iv.visibility = View.VISIBLE
                        tvMeta.text = "JPEG • ${bmp.width}×${bmp.height} • ${img.base64.length} حرف"
                    } else {
                        iv.visibility = View.GONE
                        tvMeta.text = "تعذر فك تشفير الصورة"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image decode failed", e)
                withContext(Dispatchers.Main) {
                    iv.visibility = View.GONE
                    tvMeta.text = "خطأ في فك التشفير: ${e.message}"
                }
            }
        }

        btnDownload.setOnClickListener {
            // Save the image to the gallery via a media insert.
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bytes = Base64.decode(img.base64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
                    val saved = saveBitmapToGallery(bmp, "abuzahra_${System.currentTimeMillis()}.jpg")
                    withContext(Dispatchers.Main) {
                        if (saved) Snackbar.make(binding.coordinator,
                            "تم حفظ الصورة في المعرض", Snackbar.LENGTH_SHORT).show()
                        else Snackbar.make(binding.coordinator,
                            "تعذر حفظ الصورة", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Save image failed", e)
                }
            }
        }

        binding.resultsHost.addView(view)
    }

    private fun saveBitmapToGallery(bmp: Bitmap, fileName: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AbuZahra")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) return false
                resolver.openOutputStream(uri)?.use { fos2 ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos2)
                } ?: return false
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, fileName)
                java.io.FileOutputStream(file).use { fos2 ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos2)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveBitmapToGallery failed", e)
            false
        }
    }

    private fun renderRawJson(jsonText: String) {
        val tv = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@CommandResultActivity, R.color.text_primary))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(32, 32, 32, 32)
            text = jsonText
            textDirection = View.TEXT_DIRECTION_LTR
            setBackgroundResource(R.color.surface)
            setLineSpacing(2f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // Wrap in a ScrollView in case the JSON is very long
        val scroll = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.margin_xl) * 8 // 256dp
            )
            addView(tv)
        }
        binding.resultsHost.addView(scroll)
    }

    private fun showEmptyState(message: String) {
        val tv = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@CommandResultActivity, R.color.text_secondary))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(64, 64, 64, 64)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        binding.resultsHost.addView(tv)
    }

    private fun showError(title: String, message: String) {
        binding.loadingView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.tvErrorTitle.text = title
        binding.tvErrorMessage.text = message
    }

    private fun showSessionExpired() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.session_expired)
            .setMessage("يرجى تسجيل الدخول مرة أخرى")
            .setPositiveButton(R.string.ok) { _, _ ->
                Preferences.getInstance(this).clear()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
