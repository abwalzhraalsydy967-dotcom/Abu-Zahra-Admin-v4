# Task 10-A — Features Adder

## Task
عارض الخرائط للموقع (Feature 1) + مدير ملفات محسّن (Feature 2) في Admin-App Android.

## Files Read (Context)
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultActivity.kt` — polling + result renderer (parser dispatches to renderLocation / renderBattery / renderImage / etc.)
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/device/CommandResultParser.kt` — ParsedResult sealed class with Location(lat,lng,accuracy,extras)
- `Admin-App/app/src/main/res/layout/view_location_result.xml` — original simple text + single "open in maps" button
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/files/{FilesActivity,RequestedFilesActivity,FileListAdapter,RequestedFileAdapter}.kt` — existing file browser + uploaded-file list
- `Admin-App/app/src/main/res/layout/{activity_files,activity_requested_files,item_file,item_requested_file}.xml`
- `Admin-App/app/src/main/java/com/abuzahra/admin/data/api/ApiService.kt` + `ApiClient.kt` — Bearer-auth Retrofit + OkHttp with trust-all SSL
- `Admin-App/app/src/main/java/com/abuzahra/admin/data/model/RemoteFile.kt` — has id, file_type, size, displayName, displaySize, extension
- `Admin-App/app/src/main/java/com/abuzahra/admin/util/Preferences.kt` — getApiService() + token getter
- `Admin-App/app/src/main/AndroidManifest.xml`
- `Admin-App/app/src/main/res/values/{strings,colors,dimens}.xml`
- `agent-ctx/8-B-results-viewer-builder.md` — Phase 8 context (CommandResultActivity)

## Files Created

### Kotlin
- `util/ImageLoader.kt` (~210 lines) — authenticated image/thumbnail loader:
  - `loadFileThumbnail(serverUrl, token, fileId, size=80)` — fetches `/api/files/{id}` with Bearer, decodes small RGB_565 bitmap via `inSampleSize`
  - `loadFileFull(serverUrl, token, fileId)` — used by ImageViewerActivity (up to 2048px)
  - `downloadToCache(serverUrl, token, fileId, destFile)` — streams to disk for VideoView/MediaPlayer
  - In-memory LruCache keyed by `fileId:size`
- `ui/files/ImageViewerActivity.kt` (~143 lines) — fullscreen image with pinch-zoom (1x–5x) + drag via ScaleGestureDetector + Matrix
- `ui/files/VideoViewerActivity.kt` (~100 lines) — fullscreen VideoView (downloads to cache first because VideoView can't attach auth headers)
- `ui/files/AudioPlayerDialogFragment.kt` (~180 lines) — MediaPlayer + SeekBar + play/pause + 250ms Handler updates

### Layouts
- `layout/activity_image_viewer.xml` — black fullscreen + toolbar + ProgressBar + ImageView
- `layout/activity_video_viewer.xml` — black fullscreen + VideoView
- `layout/dialog_audio_player.xml` — title + seekbar + play/pause + stop
- `layout/item_requested_file_grid.xml` — 120dp grid cell with thumbnail + name + meta
- `layout/item_breadcrumb.xml` — clickable TextView for path segments

### Menus & IDs
- `menu/menu_requested_files.xml` — toggle_view + sort
- `menu/menu_files.xml` — sort + select_all
- `values/ids.xml` — action_download_selected + action_delete_selected

### Drawables (vector icons)
- `ic_file_image`, `ic_file_video`, `ic_file_audio`, `ic_file_apk`, `ic_file_document`, `ic_file_archive`, `ic_file_play`, `ic_apk`
- `ic_grid`, `ic_list`, `ic_check_box`, `ic_check_box_outline`, `ic_delete`, `ic_select_all`, `ic_sort`
- `ic_play`, `ic_pause`, `ic_chevron_left`, `ic_chevron_right`

## Files Modified

### Feature 1 — Map Viewer
- `layout/view_location_result.xml`: added `mapWebView` (WebView) + `mapPlaceholder` (LinearLayout "موقع غير متاح") + `btnCopyCoords` button
- `ui/device/CommandResultActivity.kt` (`renderLocation`):
  - Added WebView + WebSettings + WebChromeClient + WebViewClient imports
  - Validates lat∈[-90,90], lng∈[-180,180], non-zero → loads OpenStreetMap embed `https://www.openstreetmap.org/export/embed.html?bbox={lng-0.005},{lat-0.005},{lng+0.005},{lat+0.005}&layer=mapnik&marker={lat},{lng}`
  - "فتح في خرائط Google" → `geo:lat,lng?q=lat,lng` intent with 3-level fallback (Google Maps app → generic geo intent → Google Maps web)
  - "نسخ الإحداثيات" → ClipboardManager + Snackbar confirmation
  - "موقع غير متاح" placeholder shown when coords are 0/out-of-range

### Feature 2 — Enhanced File Manager

#### RequestedFilesActivity
- Added toolbar menu (toggle_view, sort)
- Added search bar (TextInputLayout + EditText + TextWatcher)
- Added TypeFilter ChipGroup (All/Images/Videos/Audio/Files)
- Sort modes: DATE (default) / NAME / SIZE
- Click handlers per file type:
  - photo/camera/screenshot → ImageViewerActivity
  - video → VideoViewerActivity
  - audio → AudioPlayerDialogFragment
  - else → ACTION_VIEW via FileProvider

#### FilesActivity
- Added breadcrumbs (HorizontalScrollView + LinearLayout) with clickable path segments
- Sort modes: NAME (default) / SIZE_DESC / DATE_DESC (always dirs first)
- Multi-select via long-press → ActionMode context bar with "تحميل المحددد"
- `downloadSelected()` re-fetches files from server, matches by path, downloads all to /Download

#### Adapters
- `RequestedFileAdapter`: now supports LIST and GRID view modes, async thumbnail loading for IMAGE files, multi-select with checkboxes
- `FileListAdapter`: now supports multi-select, file-type icons with color coding, human-readable sizes, B/KB/MB/GB formatting

#### Layouts
- `item_requested_file.xml`: added 80x80dp thumbnail FrameLayout (ivThumbnail + ivFileIcon fallback + thumbProgress) + ivCheck for selection
- `item_file.xml`: added ivCheck for selection + tinted file-type icon + LTR text direction for paths
- `activity_requested_files.xml`: added search bar + TypeFilter chips + menu
- `activity_files.xml`: added breadcrumb HorizontalScrollView + menu

### Manifest & Strings
- `AndroidManifest.xml`: registered ImageViewerActivity (exported=false) + VideoViewerActivity (landscape)
- `strings.xml`: added ~35 Arabic strings for map viewer + file manager

## Verification
- ✅ `mapWebView` present in `view_location_result.xml:78`
- ✅ `openstreetmap` URL loaded in `CommandResultActivity.kt:537`
- ✅ `ImageViewerActivity` + `VideoViewerActivity` registered in `AndroidManifest.xml:63,69`
- ✅ `loadFileThumbnail` defined in `ImageLoader.kt:78`, used in `RequestedFileAdapter.kt:194,267`
- ✅ All 9 Kotlin files: braces balanced + parens balanced
- ✅ All 14 XML files: well-formed
- ✅ `bun run lint | grep "^/home/z/my-project/src/" | wc -l` = 2 (only pre-existing `<img>` warnings, no new errors)

## Stage Summary

### Feature 1: Map Viewer for Location Results
Real OpenStreetMap embed in WebView (no API key required) with marker at the parsed lat/lng, plus "Open in Google Maps" intent button and "Copy coordinates" clipboard button. Handles invalid/zero coordinates with "موقع غير متاح" placeholder.

### Feature 2: Enhanced File Manager
- **RequestedFilesActivity**: list ↔ grid toggle, async authenticated thumbnails for images, instant search, type filter chips, sort by date/name/size, fullscreen ImageViewerActivity (pinch-zoom) / VideoViewerActivity / AudioPlayerDialogFragment, multi-select.
- **FilesActivity**: clickable breadcrumbs, sort by name/size/date, color-coded file-type icons (image/video/audio/apk/document/archive/other), human-readable sizes, multi-select via long-press with ActionMode "Download selected".
- **ImageLoader**: shared helper for authenticated `/api/files/{id}` downloads with LruCache + inSampleSize + RGB_565 for memory efficiency.

### New Activities
- ImageViewerActivity — fullscreen image with pinch-zoom
- VideoViewerActivity — fullscreen landscape VideoView
- AudioPlayerDialogFragment — MediaPlayer + SeekBar dialog (no manifest entry needed)

### Architecture Notes
- All media viewers fetch via `ImageLoader` which attaches `Authorization: Bearer …` header (because VideoView/MediaPlayer/ImageView can't do authenticated HTTP natively)
- For video/audio: bytes are first streamed to a cache file, then played from disk
- For images: bytes are decoded with `inSampleSize` to fit the target size, cached in LruCache (1/8 of maxMemory)
- Trust-all SSL is replicated in `ImageLoader` to match the rest of the app (server uses self-signed cert in dev)
- All Arabic strings; RTL preserved; dark theme (surface_variant/emerald) consistent with existing app
