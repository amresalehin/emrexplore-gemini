# Performance 1.0 — Implementation Tracker

Working branch: `ChatGPT`
Baseline commit: `412398b4854619dba1d671f2b61b9719e76311da`

## Objective

Turn the current Emrexplore Files + Gallery implementation into a storage-first, low-memory, low-I/O Android file manager that remains responsive during browsing, scrolling, search, thumbnail loading, and background indexing.

This file is the running implementation log. Update it after each meaningful change.

**Actionable checklist:** see `task.md`.

## Current architecture snapshot

### File Explorer
- Directory listing already has page-based loading through `getFilesPaged()`.
- `UnifiedViewModel` keeps only the currently loaded file pages in UI state.
- Folder/stat caches already exist in `FileRepository`.
- Background indexing already uses `IoPriorityCoordinator.yieldIfInteractive()`.

### Gallery
- Gallery previously called `getAllMediaData()`.
- `getMediaItems()` queried all MediaStore images and all MediaStore videos.
- The complete result was placed in `UiState.allMediaItems`.
- Filters operated by creating additional in-memory lists.
- Album grouping was performed with `groupBy` over the complete media list.
- Coil thumbnails already use fixed request sizing and disabled crossfade.
- **Main scalability problem:** Gallery was not Paging 3 based.

### Startup/indexing
- Initial file loading is asynchronous.
- Storage indexing can start automatically during ViewModel initialization when enabled.
- Indexing is depth-limited and yields during scanning, but it still competes for storage after startup.

### Performance instrumentation
Existing `PerformanceMonitor` records:
- folder-open latency
- paged-load latency
- first-visible latency
- I/O yields
- folder-cache hits
- stat-cache hits
- avoided disk reads

## Decisions

1. Do not rewrite `FileRepository.kt` wholesale.
2. Preserve existing behavior while introducing new performance architecture incrementally.
3. Keep each optimization independently reversible.
4. Gallery must eventually become Paging 3 based rather than loading the entire media library.
5. MediaStore should remain the primary source for gallery media.
6. Thumbnail generation/loading must remain independent from metadata-heavy work.
7. Background indexing must yield to interactive I/O.
8. No global "scan everything before UI" startup path.

## Work log

### Phase 0 — Baseline / architecture audit
Status: **IN PROGRESS**

Findings:
- File Explorer already has manual page loading.
- Gallery originally remained full-list based.
- Gallery album generation requires the complete media list.
- Background indexing has an I/O-yield mechanism.
- Existing performance metrics provide a useful starting point.

Next:
- Establish repeatable baseline measurements on a real device.
- Record startup, folder-open, gallery-open, scrolling, search, and indexing behavior before major architectural changes.

### Phase 1 — Startup responsiveness
Status: **NOT STARTED**

Target:
- First interactive UI should not depend on storage indexing.
- Defer nonessential indexing and expensive metadata work.
- Verify that folder opening remains responsive while indexing is active.

### Phase 2 — File Explorer scalability
Status: **PARTIALLY COMPLETE**

Already present:
- paged directory loading
- folder/stat caches
- background indexing
- interactive I/O yielding

Remaining:
- validate large-directory behavior
- eliminate redundant directory scans
- improve cancellation when navigation changes
- validate cache invalidation correctness
- measure memory and I/O under large folders

### Phase 3 — Gallery scalability
Status: **IN PROGRESS**

Implemented in this iteration:
- Added AndroidX Paging 3.5.1 runtime + Compose dependencies.
- Added a dedicated `MediaStorePagingSource`.
- Unified image/video queries through `MediaStore.Files` instead of materializing two complete collections.
- Added deterministic `DATE_ADDED DESC, _ID DESC` ordering.
- Added filter-aware MediaStore selection for All / Photos / Videos.
- Added a dedicated `MediaRepository` with bounded Paging configuration.
- Switched the Gallery timeline UI to `LazyPagingItems`.
- Enabled a maximum loaded window of three pages to prevent unbounded Paging memory growth.
- Added refresh/cancellation through Paging's normal lifecycle.
- Added `task.md` as the actionable implementation checklist.
- Added a separate `MediaAlbumRepository` so Albums no longer depend on `getAllMediaData()` in the Gallery screen.
- Added `MediaStoreAlbumPagingSource` and album-scoped Paging configuration for album contents.
- Switched album drill-down UI from `allMediaItems.filter(...)` to the album Paging source.
- Fixed Paging cancellation so `CancellationException` is not converted into an ordinary load error.
- Replaced path-based Compose Paging keys with stable media URI keys.

Known temporary limitations:
- Album discovery now uses a separate metadata-only repository on Android 11+ with MediaStore grouped query arguments; album item counts are intentionally deferred (`-1`) and need a follow-up count strategy.
- Android 10 and earlier use a compatibility album scan because bucket columns were introduced in API 29.
- Favorites now use a Room-backed path page plus batched MediaStore lookup through `FavoriteMediaPagingSource`.
- Favorites still need device validation and a more direct invalidation strategy if favorite ordering changes without a list-state update.
- Fullscreen navigation currently uses the loaded Paging snapshot rather than a dedicated adjacent-item loader.
- The new source has not yet been device-tested in this environment.
- The current branch has not been verified with a full Android Gradle build in this environment.

Target architecture:

```
GalleryScreen
    ↓
GalleryViewModel
    ↓
MediaRepository
    ↓
MediaStorePagingSource
    ↓
MediaStore
```

Requirements:
- Paging 3
- deterministic ordering
- filter-aware queries
- album-aware queries
- bounded in-memory item count
- no `allMediaItems` dependency for normal browsing
- stable item keys
- thumbnail requests sized to rendered cells
- cancellation of obsolete loads

### Phase 3.1 — Gallery Paging source cleanup
Status: **IN PROGRESS**

Completed in this pass:
- Favorites now switch the shared `galleryPagingFlow` to `FavoriteMediaPagingSource` instead of creating a second always-collected Paging stream.
- The normal MediaStore Paging source is no longer collected while Favorites is active.
- Album refresh no longer calls the legacy full-library `loadMedia()` path.
- Album refresh is executed from a Compose coroutine scope so the suspend repository call is lifecycle-aware.

Still blocked on validation:
- Full Android Gradle compile has not been run in this environment.
- Real-device MediaStore/Paging behavior remains unverified.

### Phase 3.2 — Remove legacy full-library Gallery loads
Status: **COMPLETE**

Removed runtime calls to `loadMedia(forceRefresh = true)` from file rename, delete, batch-delete, and trash-restore flows. The legacy full-library Gallery loader is no longer part of normal browsing or file-operation refreshes.

### Phase 4 — Thumbnail pipeline
Status: **PARTIALLY COMPLETE**

Already present:
- Coil
- fixed thumbnail request size
- hardware bitmaps
- crossfade disabled

Remaining:
- visible/near-visible prioritization
- prefetch tuning
- cancellation behavior
- avoid unnecessary duplicate requests
- verify thumbnail cache behavior

### Phase 5 — Background I/O scheduler
Status: **PARTIALLY COMPLETE**

Already present:
- `IoPriorityCoordinator`
- interactive activity tracking
- background yielding
- performance yield counter

Remaining:
- apply consistently to every background scanner/indexer
- improve cancellation
- avoid fixed delays where queue-based coordination is more appropriate
- test contention under sustained indexing

### Phase 6 — Search
Status: **PARTIALLY COMPLETE**

Existing:
- Room-backed fast search option
- live disk search option

Remaining:
- benchmark both paths
- ensure search does not scan unrelated storage
- debounce/cancel stale queries
- verify index freshness

### Phase 7 — Metadata
Status: **NOT STARTED**

Rules:
- no EXIF/XMP parsing during normal folder browsing
- metadata inspection only on demand
- background metadata enrichment must be cancellable
- never block thumbnail display

### Phase 8 — Stress testing
Status: **NOT STARTED**

Required scenarios:
- 100,000+ media items
- 10,000+ files in one directory
- sustained gallery scrolling
- gallery + indexing concurrently
- search during indexing
- rapid folder navigation
- repeated tab switching
- low-memory device
- slow storage

## Current next action

**Phase 0 → establish device baseline; Phase 3 → compile/device-validate the new timeline path, then finish album/favorites paging.**

Do not mark a phase complete without a measurement or test supporting the claim.

## Change log

| Date | Commit | Change | Result |
|---|---|---|---|
| 2026-09-27 | 412398b | Starting point audited | File Explorer paging exists; Gallery remains full-list based |
| 2026-09-27 | 775cb8a | Added implementation tracker | Running performance plan/log established |
| 2026-09-27 | 7f2c9db | Added Paging 3.5.1 dependencies | Paging runtime + Compose available |
| 2026-09-27 | 9afe1a0 | Added MediaStorePagingSource | Unified, bounded MediaStore timeline source |
| 2026-09-27 | e3c6eae | Added MediaRepository | Dedicated paging repository |
| 2026-09-27 | d235c82 | Exposed galleryPagingFlow | ViewModel timeline moved toward Paging |
| 2026-09-27 | cf70e8e | Switched Gallery timeline UI | Compose now renders LazyPagingItems |
| 2026-09-27 | 5758c08 | Capped Paging window | Maximum three pages retained by Paging |
| 2026-09-27 | 5b2fae2 | Added task.md | Actionable master performance checklist |
| 2026-09-27 | caba2f2 | Preserve Paging cancellation | Cancellations now propagate instead of becoming load errors |
| 2026-09-27 | 551b7be | Stable gallery item keys | Compose keys now use media URI |
| 2026-09-27 | db0e2f1 | Album PagingSource | Album contents can load independently by bucket |
| 2026-09-27 | 06cd337 | Album repository | Album discovery no longer requires full MediaItem materialization |
| 2026-09-27 | 94b9a92 | Album Paging repository | MediaRepository exposes bounded album paging |
| 2026-09-27 | 6e98b69 | Album UI migration | Album drill-down uses Paging instead of allMediaItems |
| 2026-09-27 | 368eb75 | Album UI safety fixes | Stable selected-album state and unknown-count rendering |
| 2026-09-27 | 374f82c | Paged favorite paths | Room exposes bounded favorite path pages |
| 2026-09-27 | dc0a88e | Favorite PagingSource | Only favorite paths are resolved through MediaStore |
| 2026-09-27 | 0f23717 | Favorite repository paging | Bounded favorite Paging configuration |
| 2026-09-27 | c801470 | Remove legacy favorite load | Favorites no longer trigger full MediaStore materialization |
| 2026-09-27 | 1e29848 | Favorite UI migration | Favorites grid now uses Paging |
| 2026-09-27 | 821c97a | Remove duplicate favorite Paging collection | Shared gallery Paging flow now selects Favorites directly |
| 2026-09-27 | d22c50e | Fix album refresh coroutine | Album refresh no longer calls suspend repository from click handler |
| 2026-09-27 | 16649bc | Remove legacy Gallery loads | File operations no longer trigger full MediaStore materialization |


### Phase 3.3 — Fullscreen bounded-window migration
Status: **COMPLETE**

Implemented:
- Added a repository-side fullscreen loader that reuses the canonical timeline, Favorites, and album PagingSources.
- Viewer requests are positional and bounded rather than copying the currently loaded gallery snapshot.
- PagingSources now honor small load sizes, allowing a 5-item viewer window instead of forcing a 60–120 item page.

Completed:
- Gallery clicks now identify the correct fullscreen source (timeline filter or album).
- Viewer position is resolved against the underlying MediaStore/Room ordering instead of the current Compose Paging snapshot.
- Initial viewer load fetches only five items around the absolute position.
- Previous/next requests can cross unloaded Paging windows.
- The filmstrip remains bounded to the current five-item window.
- Favorite ordering is deterministic and supports direct position/count lookup.
- Standalone file-manager media opens remain supported without requiring a MediaStore position lookup.

Additional hardening completed:
- Viewer window loads are cancelled when a newer viewer request or close occurs.
- Favorite position lookup uses the Room favorite timestamp rather than media capture date.

Validation still required:
- Full Android Gradle compile.
- Real-device navigation through large timelines, albums, and favorites.
- Rapid previous/next cancellation and provider behavior under storage churn.

This keeps fullscreen navigation on the same query/paging architecture while avoiding a full-library in-memory viewer dataset.


### Phase 0.2 — Environment and secret cleanup
Status: **COMPLETE**

Implemented:
- Removed the Secrets Gradle Plugin from the Android build.
- Removed the `.env.example` template containing `GEMINI_API_KEY`.
- Removed Firebase AI, Firebase App Check, and Google Services build integration from the previous AI Studio configuration.
- Removed the server-side Gemini capability declaration from `metadata.json`.
- Updated README setup instructions so a local `.env` / API key is no longer required.
- Added local signing keystore patterns (`*.jks`, `*.keystore`) to `.gitignore`.
- Release signing remains environment-based through `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`; these are not stored in the repository.

Verification:
- `.env.example` is absent on the ChatGPT branch.
- Secrets Gradle Plugin references are absent.
- Google Services plugin references are absent.
- Firebase/Gemini build references are absent.
- `.env` remains ignored.
- No API-key value was found in the files audited on the ChatGPT branch.

Important:
- This is repository/static configuration validation only. A full Android Gradle build has not yet been run in this environment.
- Removing the AI Studio/Firebase configuration is intentional for the current offline-first architecture. If cloud AI is added later, credentials must be supplied through a secure runtime/backend mechanism rather than committed to the APK or repository.


### Phase 3.4 — Aves-class Gallery direction
Status: **PLANNED / NEXT**

The Gallery target has been explicitly expanded from a fast grid into a media-management surface comparable in capability to Aves. The performance invariant remains unchanged: no normal Gallery feature may materialize the complete media library into a hot in-memory list.

Next capability groups:
- Viewer: immersive swipe-first navigation, video controls, richer actions, slideshow.
- Management: multi-selection, batch share/favorite/move/copy/delete, rename, trash/restore.
- Organization: rich categories, sorting/grouping, date/location/tag filters, search.
- Metadata: direct inspector integration, GPS/location, safe metadata editing.
- Intelligence: OCR, objects, captions, people, embeddings and semantic search only after deterministic browsing is complete.

| 2026-09-27 | fb54089 | Deterministic favorite ordering | Favorite pages and viewer position lookup now share timestamp + path ordering |\n| 2026-09-27 | ef30c4e | Bounded viewer position/count resolution | MediaStore/album/favorite viewers can resolve absolute positions without the gallery snapshot |\n| 2026-09-27 | 50e3b41 | Viewer ViewModel migration | Fullscreen state now stores source, absolute index, bounded window and total count |\n| 2026-09-27 | ba49d2b | Gallery source wiring | Timeline and album clicks pass the correct fullscreen source |\n| 2026-09-27 | fb2c46b | Bounded viewer UI | Filmstrip and navigation operate on window-local items plus absolute positions |\n| 2026-09-27 | d79b462 | MainActivity viewer wiring | Viewer no longer reconstructs navigation from the Paging snapshot |\n| 2026-09-27 | a379aca | Aves-class roadmap | Added media-management capability roadmap and performance invariant |\n| 2026-09-27 | e3c995b | Favorite timestamp lookup | Viewer position now uses the actual favorite ordering key |\n| 2026-09-27 | d512c0f | Favorite position fix | Corrected favorite navigation ordering |\n| 2026-09-27 | 238409e | Viewer cancellation | Stale fullscreen window requests are cancelled |\n
| 2026-09-28 | a1c3920 | Gallery selection state | Added bounded explicit media selection and batch favorite/delete actions |\n| 2026-09-28 | 2b1356e | Selection action bar | Added long-press selection, batch share/favorite/delete controls and selected overlays |\n| 2026-09-28 | 5990bfe | Selection UI hardening | Corrected generated source formatting before further validation |\n| 2026-09-28 | 17fb534 | Share integration | Added Android multi-share content URI integration |\n
### 2026-09-28 Search Fixes Applied
- Latest CI compiler failure traced to malformed Kotlin string syntax; search source was rewritten cleanly.
- EXIF/GPS metadata now uses a persistent Room cache keyed by MediaStore URI and file fingerprint.
- location: queries are geocoded once and cached before GPS matching.
- Favorites now use the metadata-aware search path and expose the search action in the Favorites filter.
- Search fullscreen navigation now stays inside the exact search result set.
- Legacy getMediaItems()/getAllMediaData() Gallery-era materialization was removed; category media queries are bounded.
- ACCESS_MEDIA_LOCATION was removed from initial permissions and is requested only when GPS/location search requires it.


### 2026-09-28 — Gallery header/performance pass
Status: **IMPLEMENTED; build validation pending**

Implemented:
- Replaced the large persistent Gallery search field with a compact horizontal Search control.
- Search remains Aves-inspired and in-place: opening it reveals the full-width input plus recent searches, suggestions, and quick filters.
- Filter, Sort, and Group controls now expose their active state in the header.
- Search suggestion/date/GPS chips now toggle off when tapped while active.
- Gallery Paging startup work reduced to a 60-item initial/page size, 15-item prefetch distance, and a bounded 180-item window.
- Gallery thumbnail requests now use stable URI-based Coil memory/disk cache keys.

Performance invariant preserved:
- Gallery remains Paging 3 + MediaStore based.
- No normal Gallery browse path materializes the complete media library.
- Thumbnail loading remains independent of metadata-heavy work.

Next validation:
- Compare Gallery tab open/first-thumbnail latency on a real device.
- Exercise repeated tab switches, search dropdown open/close, filter toggles, sort/group changes, and long scrolling.
- If device measurements show remaining startup contention, inspect background indexing and non-Gallery ViewModel startup work before adding more caching.

### File Explorer header follow-up plan
The File Explorer header will reuse the same compact control language after this Gallery pass is build-certified: compact Search, adaptive Sort, Group and Filter controls, active-state toggle semantics, preserved breadcrumbs, and the existing paged/cached directory loader.
