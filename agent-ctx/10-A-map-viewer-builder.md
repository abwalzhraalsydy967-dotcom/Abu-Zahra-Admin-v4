# Task 10-A — Map Viewer Builder

## Task
إضافة عارض الخرائط التفاعلي للموقع في Web Dashboard و Admin Android App — استبدال عرض النص فقط بخريطة OpenStreetMap مدمجة مع علامة على الموقع.

## Files Read (Context)
- `worklog.md` — project context (Task 9 clone, Task 10 server inspection, Task 11-a web audit)
- `agent-ctx/10-A-features-adder.md` — previous agent's work (Android map view already partially done: 240dp WebView, delta 0.005, single Google Maps button)
- `src/components/dashboard/command-results.tsx` — web result renderer (had simple text-only location block with `📍` emoji and `accuracy` text)
- `Admin-App/app/src/main/res/layout/view_location_result.xml` — Android location layout (had 240dp WebView + 2 buttons: open maps, copy coords)
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultActivity.kt` — `renderLocation()` (WebView loaded OSM embed with delta 0.005 + raw commas)
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultParser.kt` — confirmed `Location(lat,lng,accuracy,extras)` shape; altitude/speed/bearing arrive inside `extras` map
- `Admin-App/app/src/main/res/values/strings.xml` — Arabic strings; added new ones for map buttons
- `Admin-App/app/src/main/res/values/dimens.xml` — `radius_md=12dp` used for map corner radius

## Files Modified

### Web Dashboard (`src/components/dashboard/command-results.tsx`)
1. Added Lucide icon imports: `MapPin`, `Navigation`, `ExternalLink`, `Mountain`, `Gauge`, `Compass`.
2. Replaced the inline text-only location block inside `renderResultContent()` with a call to a new dedicated `<LocationResultView data={obj} />` component.
3. Added `toNumber()` helper for safe numeric coercion (handles string/number/null).
4. Added `LocationResultView` component (~130 lines) which:
   - Validates lat∈[-90,90], lng∈[-180,180], non-zero; renders a red "موقع غير متاح" card when invalid.
   - Embeds an OpenStreetMap `<iframe>` (h-64 sm:h-80, w-full, rounded-lg, emerald border) using the official embed URL:
     `https://www.openstreetmap.org/export/embed.html?bbox={lng-0.01}%2C{lat-0.01}%2C{lng+0.01}%2C{lat+0.01}&layer=mapnik&marker={lat}%2C{lng}`
   - Shows coordinates (`lat.toFixed(6), lng.toFixed(6)`, LTR) below the map.
   - Shows meta line with icons for: accuracy (Gauge), altitude (Mountain), speed (Navigation), bearing (Compass) — each only when present.
   - Shows address (if present).
   - Two external-link buttons: "فتح في خرائط Google" (`https://maps.google.com/?q=lat,lng`) and "فتح في OpenStreetMap" (`https://www.openstreetmap.org/?mlat=lat&mlon=lng#map=16/lat/lng`).

### Android — Layout (`Admin-App/app/src/main/res/layout/view_location_result.xml`)
- Changed `FrameLayout` (map container) height: **240dp → 200dp** (matches task spec).
- Added `android:id="@+id/mapFrame"` + `android:background="@drawable/bg_map_rounded"` + `android:clipToOutline="true"` so the WebView corners are rounded.
- Added a new `TextView` `tvAltitude` (above `tvExtras`) for the altitude/speed/bearing line.
- Replaced the single "open in maps" button row with TWO buttons side-by-side: `btnOpenMaps` (Google Maps, tonal) + `btnOpenOsm` (OpenStreetMap, outlined).
- Moved `btnCopyCoords` (نسخ الإحداثيات) to a full-width TextButton below the two map buttons.
- Added `app:icon` to all buttons.

### Android — Drawable (`Admin-App/app/src/main/res/drawable/bg_map_rounded.xml`)
- New `<shape>` rectangle with `surface_variant` fill + `radius_md` (12dp) corners. Provides a visible background while the WebView is loading and acts as the clip source.

### Android — Kotlin (`Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultActivity.kt`)
- Added imports: `android.graphics.Outline`, `android.view.ViewOutlineProvider`, `android.widget.FrameLayout`.
- `renderLocation()` rewritten:
  - Binds new views: `tvAltitude`, `btnOsm`, `mapFrame`.
  - **Rounded corners**: sets a `ViewOutlineProvider` on `mapFrame` returning a rounded-rect outline (`setRoundRect(0,0,w,h, radius_md)`) + `clipToOutline = true`.
  - **Altitude/speed/bearing extraction**: pulls `altitude|alt`, `speed`, `bearing|heading` out of `loc.extras` (with `toDoubleOrNull()`), formats them with Arabic labels, and shows them in the dedicated `tvAltitude` line. The remaining extras (provider, timestamp, etc.) are shown in `tvExtras` as before.
  - **URL format updated**: delta 0.005 → **0.01** and raw commas → **%2C** (URL-encoded) per task spec.
  - **New "OpenStreetMap" button**: `btnOsm` opens `https://www.openstreetmap.org/?mlat=lat&mlon=lng#map=16/lat/lng` in the system browser via `ACTION_VIEW` intent (with snackbar fallback if no browser).
  - **Google Maps button**: kept the 3-level fallback chain (Google Maps app → generic geo: intent → Google Maps web).
  - **Invalid location handling**: when coords are 0/out-of-range, hides WebView, shows `mapPlaceholder`, sets `tvCoords` to "موقع غير متاح" + `tvAccuracy` to the invalid-hint string, hides altitude + extras.
  - Snackbar messages now use string resources instead of hardcoded Arabic.

### Android — Strings (`Admin-App/app/src/main/res/values/strings.xml`)
- Added: `open_in_google_maps` = "خرائط Google"
- Added: `open_in_osm` = "OpenStreetMap"
- Added: `altitude_format` = "الارتفاع: %.0f م"
- Added: `location_invalid_hint` = "الإحداثيات المستلمة غير صالحة أو فارغة"

## Verification
- ✅ `bun run lint 2>&1 | grep -c "^/home/z/my-project/src/"` = **2** (only pre-existing `<img>` warnings in `command-results.tsx:138` and `file-viewer.tsx:499` — no new errors or warnings introduced).
- ✅ TypeScript type check (`npx tsc --noEmit --skipLibCheck`) exits 0.
- ✅ Kotlin `CommandResultActivity.kt` braces balanced (148/148) + parens balanced (398/398) using string/char/comment-aware parser.
- ✅ `view_location_result.xml`, `bg_map_rounded.xml`, `strings.xml` all XML well-formed (xml.dom.minidom parse OK).
- ✅ `curl -s -o /dev/null -w '%{http_code}' http://localhost:3000` = **200** (dev server running, page compiles cleanly).
- ✅ OpenStreetMap embed URL returns HTTP 200 with valid "OpenStreetMap Embedded" HTML payload.
- ✅ Google Maps link (`https://maps.google.com/?q=24.7136,46.6753`) returns HTTP 302 (redirect, normal).
- ✅ OpenStreetMap mlat/mlon link returns HTTP 200.

## Stage Summary

### Web Dashboard map embed
The `get_location` result is no longer rendered as plain coordinates text. It now shows an interactive OpenStreetMap iframe (16:9-ish, 256–320px tall, responsive) with a marker at the device's coordinates, plus a metadata block (accuracy/altitude/speed/bearing with icons) and two buttons to open the same point in Google Maps or OpenStreetMap web. Invalid coordinates (0,0 or out of range) render a clean red "موقع غير متاح" card. No external libraries needed — pure `<iframe>` embed with no API key.

### Admin Android App map view
The previous agent's 240dp WebView with delta-0.005/raw-commas URL was refined to the task spec: 200dp height, 12dp rounded corners (via `ViewOutlineProvider` + `clipToOutline`), URL-encoded `%2C` separators, ±0.01° bbox. A second button was added to open OpenStreetMap in the system browser (alongside the existing Google Maps geo-intent button with 3-level fallback). Altitude/speed/bearing are now extracted from the extras map and surfaced in a dedicated line above the raw extras dump. Invalid location handling preserved (placeholder + "موقع غير متاح" text + hidden extras).
