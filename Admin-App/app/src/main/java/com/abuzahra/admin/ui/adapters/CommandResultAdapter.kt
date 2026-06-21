package com.abuzahra.admin.ui.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abuzahra.admin.R
import com.abuzahra.admin.data.model.Command
import com.google.gson.JsonParser

/**
 * Adapter for the Results fragment's command-result list.
 *
 * Each row expands on tap to show the parsed result. Parsing mirrors the web's
 * `command-results.tsx` `parseResult` + `renderResultContent` logic:
 *  - empty → "لا توجد نتيجة"
 *  - base64 image (JPEG/PNG marker) → ImageView
 *  - JSON array of objects → table-like rows
 *  - JSON array of primitives → bullet list
 *  - JSON object with lat/lng → mini OSM map (WebView) + meta
 *  - JSON object → key/value rows
 *  - text → preformatted
 *  - primitive → string form
 */
class CommandResultAdapter :
    ListAdapter<Command, CommandResultAdapter.ResultViewHolder>(Diff) {

    private val expandedIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatusBadge)
        private val tvCommand: TextView = itemView.findViewById(R.id.tvCommand)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvExpand: TextView = itemView.findViewById(R.id.tvExpandIcon)
        private val resultContainer: View = itemView.findViewById(R.id.resultContainer)
        private val resultContent: LinearLayout = itemView.findViewById(R.id.resultContent)
        private val card: View = itemView.findViewById(R.id.cardResult)

        init {
            card.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = getItem(pos)
                    if (expandedIds.contains(item.id)) {
                        expandedIds.remove(item.id)
                    } else {
                        expandedIds.add(item.id)
                    }
                    notifyItemChanged(pos)
                }
            }
        }

        fun bind(cmd: Command) {
            // Status badge
            val (label, color) = statusInfo(cmd.status)
            tvStatus.text = label
            tvStatus.setTextColor(color)
            tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                blendColor(color, 0.15f)
            )

            tvCommand.text = cmd.command
            tvTime.text = "${cmd.displayTime}${if (!cmd.completedAt.isNullOrEmpty()) " • أكمل: ${cmd.displayTime}" else ""}"

            val expanded = expandedIds.contains(cmd.id)
            tvExpand.text = if (expanded) "▲" else "▼"
            resultContainer.visibility = if (expanded) View.VISIBLE else View.GONE

            if (expanded) {
                resultContent.removeAllViews()
                renderResult(cmd.result, resultContent)
            }
        }

        private fun renderResult(rawResult: String?, container: LinearLayout) {
            if (rawResult.isNullOrBlank()) {
                addText(container, "لا توجد نتيجة", italic = true, color = R.color.text_hint)
                return
            }

            val trimmed = rawResult.trim()

            // 1. base64 JPEG / PNG image
            if (trimmed.startsWith("/9j/") || trimmed.startsWith("iVBORw0KGgo")) {
                try {
                    val bytes = Base64.decode(trimmed, Base64.DEFAULT)
                    val bmp: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val iv = ImageView(container.context).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(bmp)
                            background = container.context.getDrawable(R.drawable.bg_role_chip)
                            setPadding(4, 4, 4, 4)
                        }
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        iv.layoutParams = lp
                        container.addView(iv)
                        return
                    }
                } catch (_: Exception) { /* fall through */ }
            }

            // 2. Try to parse as JSON
            val parsed = tryParseJson(trimmed)
            when (parsed) {
                is ParsedResult.Array -> renderArray(parsed.items, container)
                is ParsedResult.Object -> renderObject(parsed.obj, container)
                is ParsedResult.Primitive -> addText(container, parsed.value.toString())
                null -> addText(container, trimmed)
            }
        }

        private fun renderArray(items: List<Any?>, container: LinearLayout) {
            if (items.isEmpty()) {
                addText(container, "قائمة فارغة", italic = true, color = R.color.text_hint)
                return
            }
            if (items.all { it is Map<*, *> }) {
                // Build a simple key/value table per item
                val keys = items.flatMap { (it as Map<*, *>).keys.map { k -> k.toString() } }.distinct()
                for (item in items) {
                    val map = item as Map<*, *>
                    val row = LinearLayout(container.context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8, 8, 8, 8)
                        background = container.context.getDrawable(R.drawable.bg_role_chip)
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = 6
                    row.layoutParams = lp
                    for (k in keys) {
                        val v = map[k]
                        val pair = LinearLayout(container.context).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }
                        val kView = TextView(container.context).apply {
                            text = "$k:"
                            setTextColor(container.context.getColor(R.color.text_secondary))
                            textSize = 11f
                            val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
                            lp2.marginEnd = 8
                            layoutParams = lp2
                        }
                        val vView = TextView(container.context).apply {
                            text = formatCell(v)
                            setTextColor(container.context.getColor(R.color.text_primary))
                            textSize = 11f
                            val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
                            layoutParams = lp2
                        }
                        pair.addView(kView)
                        pair.addView(vView)
                        row.addView(pair)
                    }
                    container.addView(row)
                }
            } else {
                // Primitive list
                for (item in items) {
                    addText(container, "• ${item ?: "-"}")
                }
            }
        }

        private fun renderObject(obj: Map<String, Any?>, container: LinearLayout) {
            if (obj.isEmpty()) {
                addText(container, "كائن فارغ", italic = true, color = R.color.text_hint)
                return
            }

            // Location with lat/lng → show mini OSM map
            val lat = obj["lat"] ?: obj["latitude"]
            val lng = obj["lng"] ?: obj["lon"] ?: obj["longitude"]
            if (lat != null && lng != null) {
                try {
                    val latD = lat.toString().toDouble()
                    val lngD = lng.toString().toDouble()
                    val accuracy = obj["accuracy"]
                    val address = obj["address"]
                    addText(container, "📍 الموقع الجغرافي", bold = true,
                        color = R.color.secondary)
                    addText(container, "$latD, $lngD", mono = true)

                    // WebView with OpenStreetMap embed (matches web's map view)
                    val html = """
                        <html><body style="margin:0;padding:0">
                        <iframe width="100%" height="200" frameborder="0" scrolling="no" marginheight="0" marginwidth="0"
                          src="https://www.openstreetmap.org/export/embed.html?bbox=${lngD - 0.005},${latD - 0.005},${lngD + 0.005},${latD + 0.005}&layer=mapnik&marker=$latD,$lngD"
                          style="border:1px solid #1A3C3440;border-radius:8px"></iframe>
                        </body></html>
                    """.trimIndent()
                    val wv = WebView(container.context).apply {
                        settings.javaScriptEnabled = false
                        loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        220
                    )
                    lp.topMargin = 8
                    lp.bottomMargin = 8
                    wv.layoutParams = lp
                    container.addView(wv)

                    if (accuracy != null) addText(container, "الدقة: ~$accuracy متر")
                    if (address != null) addText(container, "العنوان: $address")
                    return
                } catch (_: Exception) { /* fall through */ }
            }

            // Generic object → key/value rows
            for ((k, v) in obj) {
                val pair = LinearLayout(container.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 6, 8, 6)
                    background = container.context.getDrawable(R.drawable.bg_role_chip)
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 4
                pair.layoutParams = lp
                val kView = TextView(container.context).apply {
                    text = "$k:"
                    setTextColor(container.context.getColor(R.color.text_secondary))
                    textSize = 11f
                    val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
                    lp2.marginEnd = 8
                    layoutParams = lp2
                }
                val vView = TextView(container.context).apply {
                    text = formatCell(v)
                    setTextColor(container.context.getColor(R.color.text_primary))
                    textSize = 11f
                    val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
                    layoutParams = lp2
                }
                pair.addView(kView)
                pair.addView(vView)
                container.addView(pair)
            }
        }

        // ── Helpers ───────────────────────────────────────────────
        private fun addText(
            container: LinearLayout,
            text: String,
            italic: Boolean = false,
            bold: Boolean = false,
            mono: Boolean = false,
            color: Int = R.color.text_primary
        ) {
            val tv = TextView(container.context).apply {
                this.text = text
                setTextColor(container.context.getColor(color))
                textSize = 11f
                if (italic) {
                    paint.textSkewX = -0.2f
                }
                if (bold) {
                    paint.isFakeBoldText = true
                }
                if (mono) {
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                setPadding(8, 4, 8, 4)
            }
            container.addView(tv)
        }

        private fun formatCell(value: Any?): String = when (value) {
            null -> "-"
            is Map<*, *> -> com.google.gson.Gson().toJson(value)
            is List<*> -> com.google.gson.Gson().toJson(value)
            else -> value.toString()
        }

        private fun statusInfo(status: String): Pair<String, Int> {
            val ctx = itemView.context
            return when (status.lowercase()) {
                "completed", "success" -> "✓ مكتمل" to ctx.getColor(R.color.online_color)
                "failed", "error" -> "✗ فشل" to ctx.getColor(R.color.error)
                "pending", "queued" -> "⏳ قيد الانتظار" to ctx.getColor(R.color.warning)
                "sent" -> "↗ تم الإرسال" to ctx.getColor(R.color.info)
                else -> status to ctx.getColor(R.color.text_secondary)
            }
        }

        private fun blendColor(base: Int, alpha: Float): Int {
            val r = android.graphics.Color.red(base)
            val g = android.graphics.Color.green(base)
            val b = android.graphics.Color.blue(base)
            val a = (255 * alpha).toInt()
            return android.graphics.Color.argb(a, r, g, b)
        }
    }

    // ── JSON parsing helpers ──────────────────────────────────────
    private sealed class ParsedResult {
        data class Array(val items: List<Any?>) : ParsedResult()
        data class Object(val obj: Map<String, Any?>) : ParsedResult()
        data class Primitive(val value: Any) : ParsedResult()
    }

    private fun tryParseJson(text: String): ParsedResult? {
        return try {
            val el = JsonParser.parseString(text)
            when {
                el.isJsonArray -> {
                    val list = el.asJsonArray.map { element ->
                        when {
                            element.isJsonObject -> element.asJsonObject.entrySet()
                                .associate { e -> e.key to e.value.let { v ->
                                    if (v.isJsonPrimitive) v.asString
                                    else if (v.isJsonArray) v.toString()
                                    else v.toString()
                                } }
                            element.isJsonPrimitive -> {
                                val p = element.asJsonPrimitive
                                if (p.isNumber) p.asNumber
                                else if (p.isBoolean) p.asBoolean
                                else p.asString
                            }
                            else -> null
                        }
                    }
                    ParsedResult.Array(list)
                }
                el.isJsonObject -> {
                    val map = el.asJsonObject.entrySet().associate { e ->
                        e.key to e.value.let { v ->
                            if (v.isJsonPrimitive) {
                                val p = v.asJsonPrimitive
                                if (p.isNumber) p.asNumber
                                else if (p.isBoolean) p.asBoolean
                                else p.asString
                            } else v.toString()
                        }
                    }
                    ParsedResult.Object(map)
                }
                el.isJsonPrimitive -> {
                    val p = el.asJsonPrimitive
                    val v: Any = when {
                        p.isNumber -> p.asNumber
                        p.isBoolean -> p.asBoolean
                        else -> p.asString
                    }
                    ParsedResult.Primitive(v)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    object Diff : DiffUtil.ItemCallback<Command>() {
        override fun areItemsTheSame(oldItem: Command, newItem: Command): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Command, newItem: Command): Boolean =
            oldItem.status == newItem.status && oldItem.result == newItem.result
    }
}
