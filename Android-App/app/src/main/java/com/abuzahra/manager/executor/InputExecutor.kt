package com.abuzahra.manager.executor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.inputmethod.InputMethodManager

/**
 * InputExecutor — keyboard + clipboard + input injection commands.
 *
 * Input injection (input_text, input_key, paste_clipboard) requires either
 * an AccessibilityService (preferred) or shell 'input' command (rooted).
 * We attempt to use the AccessibilityService if available; otherwise we
 * return an honest "not possible without accessibility" error.
 */
object InputExecutor {

    private const val TAG = "InputExecutor"

    // ===== SHOW KEYBOARD =====
    fun showKeyboard(context: Context): Map<String, Any> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return mapOf("error" to "InputMethodManager unavailable")
            // Will only show keyboard if a focused EditText exists
            imm.showSoftInput(null, InputMethodManager.SHOW_FORCED)
            mapOf("status" to "ok", "message" to "Show keyboard requested (requires focused EditText)")
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "showKeyboard error"))
        }
    }

    // ===== HIDE KEYBOARD =====
    fun hideKeyboard(context: Context): Map<String, Any> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return mapOf("error" to "InputMethodManager unavailable")
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
            mapOf("status" to "ok", "message" to "Hide keyboard requested")
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "hideKeyboard error"))
        }
    }

    // ===== INPUT TEXT (via AccessibilityService) =====
    fun inputText(context: Context, params: Map<String, Any>): Map<String, Any> {
        val text = params["text"]?.toString() ?: params["arg"]?.toString() ?: ""
        if (text.isBlank()) return mapOf("error" to "text param required")
        return try {
            val acc = com.abuzahra.manager.service.MyAccessibilityService.getInstance()
            if (acc == null) {
                return mapOf(
                    "error" to "Accessibility service not enabled",
                    "hint" to "Enable ${context.packageName} in Settings > Accessibility"
                )
            }
            // Try to find the focused node and paste text via clipboard
            val pasteResult = setClipboard(context, text)
            // Simulate paste action via AccessibilityNodeInfo.ACTION_PASTE
            val ok = acc.pasteIntoFocusedNode()
            if (ok) {
                mapOf("status" to "ok", "text" to text, "method" to "accessibility_paste")
            } else {
                mapOf(
                    "status" to "partial",
                    "text" to text,
                    "message" to "Copied to clipboard but no focused EditText to paste into",
                    "clipboard_set" to pasteResult
                )
            }
        } catch (e: Exception) {
            mapOf("error" to "input_text failed: ${e.message}")
        }
    }

    // ===== INPUT KEY =====
    fun inputKey(context: Context, params: Map<String, Any>): Map<String, Any> {
        val keyCode = (params["key_code"] as? Number)?.toInt()
            ?: params["arg"]?.toString()?.toIntOrNull()
            ?: return mapOf("error" to "key_code param required (integer keycode, e.g. 66 = ENTER)")
        return try {
            val acc = com.abuzahra.manager.service.MyAccessibilityService.getInstance()
            if (acc == null) {
                return mapOf(
                    "error" to "Accessibility service not enabled; cannot inject key events",
                    "hint" to "Enable ${context.packageName} in Settings > Accessibility"
                )
            }
            val ok = acc.performGlobalKey(keyCode)
            mapOf(
                "status" to if (ok) "ok" else "failed",
                "key_code" to keyCode,
                "message" to if (ok) "Key injected" else "Key injection not supported (Accessibility may not expose this API)"
            )
        } catch (e: Exception) {
            mapOf("error" to "input_key failed: ${e.message}")
        }
    }

    // ===== PASTE CLIPBOARD =====
    fun pasteClipboard(context: Context): Map<String, Any> {
        return try {
            val acc = com.abuzahra.manager.service.MyAccessibilityService.getInstance()
            if (acc == null) {
                return mapOf(
                    "error" to "Accessibility service not enabled",
                    "hint" to "Enable ${context.packageName} in Settings > Accessibility"
                )
            }
            val ok = acc.pasteIntoFocusedNode()
            mapOf("status" to if (ok) "ok" else "no_focus", "message" to if (ok) "Pasted into focused field" else "No focused EditText to paste into")
        } catch (e: Exception) {
            mapOf("error" to "paste_clipboard failed: ${e.message}")
        }
    }

    // ===== CLEAR CLIPBOARD =====
    fun clearClipboard(context: Context): Map<String, Any> {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return mapOf("error" to "ClipboardManager unavailable")
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
            mapOf("status" to "ok", "message" to "Clipboard cleared")
        } catch (e: Exception) {
            mapOf("error" to "clear_clipboard failed: ${e.message}")
        }
    }

    // ===== SET CLIPBOARD TEXT =====
    fun setClipboardText(context: Context, params: Map<String, Any>): Map<String, Any> {
        val text = params["text"]?.toString() ?: params["arg"]?.toString() ?: ""
        if (text.isBlank()) return mapOf("error" to "text param required")
        val ok = setClipboard(context, text)
        return mapOf("status" to if (ok) "ok" else "failed", "text" to text, "length" to text.length)
    }

    // Helper: set the primary clip
    private fun setClipboard(context: Context, text: String): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            val clip = ClipData.newPlainText("text", text)
            cm.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Log.w(TAG, "setClipboard failed", e)
            false
        }
    }
}
