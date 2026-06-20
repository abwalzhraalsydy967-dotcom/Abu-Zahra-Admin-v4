# Task 8-B — Results Viewer Builder

## Summary
Built a complete command results viewer for the Abu-Zahra Admin Android app.

## Files Created
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultActivity.kt` — polling result viewer activity
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultParser.kt` — JSON → typed ParsedResult parser
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultAdapters.kt` — Sms/Contact/Call/KeyValue/FileList/Notification/AppList adapters
- `Admin-App/app/src/main/res/layout/activity_command_result.xml` — main viewer layout (toolbar, status card, refresh/copy, results host)
- `Admin-App/app/src/main/res/layout/item_sms.xml`, `item_contact.xml`, `item_call.xml`, `item_generic_key_value.xml`
- `Admin-App/app/src/main/res/layout/view_location_result.xml`, `view_battery_result.xml`, `view_image_result.xml`

## Files Modified
- `CommandDefinitions.kt` — added `DATA_RETRIEVAL_KEYS` set + `isDataRetrievalCommand(key)` helper
- `ApiService.kt` (CommandResponse) — added `command: Command?` field (server returns nested `{"ok": true, "command": {id, ...}}`)
- `ApiClient.kt` (ApiServiceImpl.sendCommand) — extracts `command_id` from nested `command.id` when flat field is empty
- `DeviceDetailViewModel.kt` — added `commandSent: MutableLiveData<SentCommand?>`, emits after successful send; added `deviceId()` accessor; added `clearCommandSent()`
- `DeviceDetailActivity.kt` — observes `commandSent`; if `isDataRetrievalCommand(key)` → launches `CommandResultActivity` with device_id, command_id, command_key
- `DataActivity.kt` — after successful data-command send, offers to launch `CommandResultActivity` directly
- `AndroidManifest.xml` — registered `.ui.device.CommandResultActivity` (exported=false, parent=DeviceDetailActivity)
- `strings.xml` — added 28 new Arabic strings for the viewer (titles, labels, call types, polling hints, etc.)

## Flow
1. Admin taps a command (e.g. "sms")
2. DeviceDetailViewModel.sendCommand("sms") → server returns `{ok, command: {id, ...}}`
3. CommandResponse.command_id is populated from nested command.id
4. _commandSent emits SentCommand("sms", "cmd_xxx")
5. DeviceDetailActivity observes commandSent → checks isDataRetrievalCommand("sms") = true → launches CommandResultActivity
6. CommandResultActivity.onResume → startPolling() → every 2s calls api.getCommands(deviceId)
7. Finds command by command_id; if status == "completed" → CommandResultParser.parse("get_sms", result) → ParsedResult.SmsList
8. Renders in RecyclerView using SmsAdapter + item_sms.xml
9. User can pull-to-refresh, tap "تحديث" for one fetch, or tap "نسخ" to copy raw JSON

## Parsers Built
- SMS list (address/body/date — handles bare arrays + {items:[...]}/{sms:[...]} shapes)
- Contacts list (name/phone/email)
- Call log (number/type normalized to incoming/outgoing/missed/rejected/duration/date)
- Location (lat/lng/accuracy + "open in maps" button using geo: intent)
- Notifications (app/title/text/time)
- Apps (name/package/version/system flag)
- Battery (level/charging + extras)
- Images (base64 JPEG → Bitmap → ImageView + save to gallery)
- File listings (name/path/size/dir flag/modified)
- Generic key-value (device_info, wifi_info, network_info, sim_info, storage_info, calendar, app_usage, all_data, browser_history)
- Clipboard (text/content)
- Raw JSON fallback (scrollable monospace TextView)

## Verification
- Brace balance: all 8 modified/created Kotlin files have balanced {} and ()
- All 8 new XML layouts + AndroidManifest + strings.xml parse as well-formed XML
- `grep -n "CommandResultActivity" AndroidManifest.xml` → line 48 registered ✓
- `bun run lint` → 0 new errors (only pre-existing skills/ + 2 <img> warnings in src/)
- isDataRetrievalCommand correctly classifies sms/get_sms/contacts/get_contacts/etc. as data, send_sms/lock_phone/delete_file as actions
