# Performance & Architecture Task Plan

Repository: `amresalehin/emrexplore`
Working branch: `ChatGPT`

This file is the actionable task list for the performance work.  
`PERFORMANCE_TRACKER.md` is the implementation log and decision record.

---

## 0. Baseline & Guardrails

- [x] Record the current architecture before major changes.
- [x] Add `PERFORMANCE_TRACKER.md`.
- [x] Keep changes incremental and reversible.
- [ ] Establish a clean, reproducible Android build in Android Studio/Codespaces.
- [ ] Capture baseline performance measurements on a real Android device.
- [ ] Record baseline:
  - [ ] cold app startup
  - [ ] first visible folder
  - [ ] opening a large folder
  - [ ] scrolling a large folder
  - [ ] opening Gallery
  - [ ] first visible gallery item
  - [ ] scrolling 10k / 50k / 100k media items
  - [ ] memory/RAM usage
  - [ ] CPU usage
  - [ ] storage I/O
  - [ ] thumbnail decode activity

## 1. Startup Responsiveness

Goal: show usable UI immediately without waiting for storage-wide work.

- [ ] Ensure no full-storage scan blocks startup.
- [ ] Ensure Gallery startup does not load the complete MediaStore dataset.
- [ ] Delay nonessential indexing until after first interactive UI.
- [ ] Make background indexing yield aggressively when the user is interacting.
- [ ] Review ViewModel initialization for unnecessary work.
- [ ] Review caches created during startup for memory pressure.
- [ ] Add startup timing instrumentation.
- [ ] Verify cold-start behavior on low-end hardware.

## 2. File Explorer Scalability

Goal: opening a folder should depend primarily on that folder, not total storage size.

- [x] Preserve paged file loading.
- [x] Keep folder/stat caches.
- [x] Keep background indexing separate from interactive folder loading.
- [ ] Verify very large directories (10k+ files).
- [ ] Verify repeated folder navigation does not trigger unnecessary disk reads.
- [ ] Verify sorting/filtering does not materialize the whole storage tree.
- [ ] Review file metadata/stat calls for unnecessary per-item I/O.
- [ ] Improve cancellation when leaving a folder before a load finishes.
- [ ] Measure and optimize scroll/jank on large directories.
- [ ] Add stress tests for nested directories and mixed file types.

## 3. Gallery Scalability — Paging 3

Goal: Gallery must remain responsive with very large media libraries.

### Timeline

- [x] Add AndroidX Paging 3 dependencies.
- [x] Add `MediaStorePagingSource`.
- [x] Add `MediaRepository`.
- [x] Expose gallery Paging flow from `UnifiedViewModel`.
- [x] Replace the main ALL/PHOTOS/VIDEOS timeline grid with Paging.
- [x] Use bounded Paging memory (`maxSize = 360`).
- [ ] Compile and fix any API/import issues.
- [ ] Test Paging against real MediaStore data.
- [ ] Verify refresh behavior.
- [ ] Verify filter switching.
- [ ] Verify pagination while rapidly scrolling.
- [ ] Verify behavior with 100k+ media items.
- [ ] Verify process recreation / configuration changes.

### Albums

- [x] Remove album drill-down dependency on the complete `allMediaItems` list.
- [x] Build album discovery separately from Gallery media item materialization.
- [x] Create a dedicated MediaStore album repository.
- [ ] Load album counts lazily or with provider-side aggregation where practical.
- [x] Open an album through a filtered Paging source.
- [x] Preserve bounded Paging memory for album contents.
- [ ] Test devices with hundreds/thousands of albums.

### Favorites

- [x] Define a scalable favorite source backed by the app’s Room favorite paths.
- [x] Avoid rebuilding the entire MediaStore dataset just to display favorites.
- [x] Convert favorites to a Paging-compatible source.
- [ ] Ensure favorite/unfavorite updates invalidate only affected data.
- [ ] Test large favorite collections.

### Fullscreen Viewer

- [x] Stop relying on the currently loaded Paging snapshot as the complete viewer dataset.
- [x] Design and implement a dedicated bounded adjacent-item loader.
- [x] Support previous/next across unloaded pages.
- [x] Add bounded 5-item fullscreen window loading and wire it to the viewer UI.
- [x] Keep fullscreen memory bounded.
- [ ] Avoid decoding full-resolution images until needed.

## 4. Thumbnail Pipeline

Goal: thumbnails should be cheap, asynchronous, cancellable, and bounded.

- [x] Use fixed thumbnail request sizing.
- [x] Keep hardware bitmaps where appropriate.
- [x] Keep crossfade disabled for performance.
- [ ] Verify Coil requests are cancelled when grid items leave the viewport.
- [ ] Verify thumbnail cache sizing.
- [ ] Avoid duplicate requests for the same URI.
- [ ] Avoid unnecessary EXIF/metadata reads during thumbnail display.
- [ ] Prefer MediaStore/thumbnail APIs where they reduce decoding work.
- [ ] Review video thumbnail generation separately.
- [ ] Test rapid fling scrolling.
- [ ] Test mixed photo/video grids.
- [ ] Measure decode time and memory churn.
- [ ] Add thumbnail performance counters to the tracker.

## 5. Background I/O Scheduler

Goal: background work must never make interactive storage operations feel slow.

- [x] Keep `IoPriorityCoordinator.yieldIfInteractive()`.
- [x] Keep background indexing out of the main UI path.
- [ ] Audit every storage-heavy background operation.
- [ ] Add cooperative cancellation.
- [ ] Prioritize foreground folder/gallery work over indexing.
- [ ] Prevent multiple background scans from competing for storage.
- [ ] Add a single scheduler/queue for expensive background filesystem tasks.
- [ ] Limit concurrency based on device characteristics.
- [ ] Measure I/O contention during scrolling and folder navigation.

## 6. Search

Goal: fast search without requiring an enormous always-hot database.

- [ ] Audit the current search implementation.
- [ ] Separate filename search from metadata/semantic search.
- [ ] Avoid scanning the entire filesystem on every query.
- [ ] Reuse existing indexed information when available.
- [ ] Make search incremental/cancellable.
- [ ] Add debouncing.
- [ ] Return visible results progressively.
- [ ] Keep search memory bounded.
- [ ] Benchmark small, medium, and huge storage collections.

## 7. Metadata Architecture

Goal: metadata enrichment must not become a prerequisite for basic file/gallery browsing.

- [ ] Separate core filesystem metadata from optional enrichment.
- [ ] Keep EXIF parsing off the critical browsing path.
- [ ] Keep media dimensions/duration retrieval lightweight.
- [ ] Make expensive metadata extraction asynchronous.
- [ ] Persist only metadata that provides measurable UX value.
- [ ] Avoid creating a giant database solely to make basic browsing possible.
- [ ] Define cache invalidation rules.
- [ ] Define metadata versioning/migration rules.

## 8. Memory Management

Goal: predictable RAM usage even with 100k+ files/media.

- [ ] Audit all large in-memory collections.
- [ ] Remove unnecessary duplicate representations of the same dataset.
- [ ] Bound Paging windows.
- [ ] Bound thumbnail caches.
- [ ] Avoid retaining full MediaStore results.
- [ ] Avoid retaining full folder trees.
- [ ] Profile heap usage during rapid scrolling.
- [ ] Profile after repeated navigation.
- [ ] Test process recreation under memory pressure.
- [ ] Check for leaked Activity/Context references.

## 9. Cancellation & Concurrency

Goal: stale work should stop as soon as the user changes context.

- [ ] Audit coroutine scopes.
- [ ] Cancel folder loads when navigation changes.
- [ ] Cancel stale Gallery queries when filters change.
- [ ] Preserve coroutine cancellation instead of converting cancellation into ordinary errors.
- [ ] Cancel thumbnail requests when items leave composition.
- [ ] Prevent duplicate concurrent loads for the same folder/media query.
- [ ] Test rapid tab switching.
- [ ] Test rapid folder navigation.
- [ ] Test rapid Gallery filter changes.

## 10. Permissions & Storage Access

Goal: use the simplest permission model that gives fast, reliable access.

- [ ] Audit current storage permissions.
- [ ] Separate permission requirements for filesystem browsing and MediaStore.
- [ ] Verify Android 13+ media permissions behavior.
- [ ] Verify All Files Access behavior where applicable.
- [ ] Avoid requesting permissions that are not required for the active feature.
- [ ] Ensure permission checks do not trigger expensive rescans.
- [ ] Test denied/revoked permissions.
- [ ] Test removable/secondary storage if supported.

## 11. UI/Jank

Goal: keep Compose work small and predictable.

- [ ] Audit recompositions in File Explorer.
- [ ] Audit recompositions in Gallery.
- [ ] Use stable keys everywhere appropriate.
- [ ] Avoid passing large mutable collections through frequently recomposed UI.
- [ ] Keep expensive transformations outside composition.
- [ ] Verify LazyGrid item content types.
- [ ] Measure frame timing during rapid scrolling.
- [ ] Fix avoidable allocations in item rendering.

## 12. Stress Testing

Required test datasets:

- [ ] 1k files / media
- [ ] 10k files / media
- [ ] 50k files / media
- [ ] 100k files / media
- [ ] Mixed photos + videos
- [ ] Very large single directory
- [ ] Deep directory tree
- [ ] Large album count
- [ ] Large favorites collection
- [ ] Low-RAM device
- [ ] Slow storage
- [ ] Fast storage

For every dataset record:

- [ ] startup time
- [ ] first visible content
- [ ] folder/gallery open latency
- [ ] scroll FPS/jank
- [ ] peak RAM
- [ ] CPU
- [ ] storage reads
- [ ] thumbnail decode time
- [ ] cancellation behavior
- [ ] crash/ANR behavior

## 13. Current Known Technical Follow-ups

These are specifically related to the current Paging implementation.

- [ ] Compile the current branch.
- [x] Add repository-side fullscreen window loading that reuses the canonical PagingSources.
- [x] Allow PagingSources to honor small load sizes for bounded fullscreen windows.
- [ ] Verify `MediaStorePagingSource` query behavior on Android 10+.
- [ ] Verify API <26 query fallback.
- [ ] Verify `BUCKET_ID` / `BUCKET_DISPLAY_NAME` availability.
- [ ] Review use of deprecated `DATA` column and reduce path dependency where possible.
- [x] Replace path-based Paging keys with stable URI keys.
- [ ] Replace numeric video-ID offset with a typed/stable key strategy.
- [x] Preserve `CancellationException` in PagingSource loads.
- [ ] Verify Paging refresh keys preserve expected scroll position.
- [x] Remove unnecessary legacy `loadMedia()` calls from Gallery/file-operation paths.
- [x] Confirm album/favorite legacy loading is isolated from timeline browsing.

## 14. Definition of Done

The performance architecture is considered ready only when:

- [ ] App opens to usable UI without a storage-wide scan.
- [ ] Opening a folder does not depend on total storage size.
- [ ] Gallery timeline does not load the complete media library into memory.
- [ ] Gallery remains usable with 100k+ media items.
- [ ] Albums do not require a complete media list in memory.
- [ ] Favorites do not require a complete media list in memory.
- [x] Fullscreen navigation works beyond the currently loaded Paging window.
- [ ] Thumbnail memory and decoding remain bounded.
- [ ] Background indexing yields to interactive work.
- [ ] Search is cancellable and does not freeze the UI.
- [ ] Large datasets do not produce avoidable GC/jank.
- [ ] No major operation requires a giant always-hot database.
- [ ] Permissions are minimal and predictable.
- [ ] Real-device stress tests pass without ANR/crash.
- [ ] Performance measurements are recorded before and after optimization.

---

## Current Priority Queue

1. Compile and validate the current Paging implementation.
2. Finish album count strategy and device-test album discovery.
3. Device-test Favorites Paging and refine invalidation.
4. Fix fullscreen navigation across unloaded pages.
5. Audit cancellation and stale-work behavior.
6. Optimize thumbnail/cache behavior under rapid scrolling.
7. Establish 10k/50k/100k real-device benchmarks.
8. Optimize startup/background indexing.
9. Implement Gallery search foundation and benchmark search scalability.
10. Complete memory/I/O stress testing.

Last updated: 2026-09-28

Fullscreen implementation note: repository support now loads a 5-item window around an absolute position from the same timeline/favorites/album PagingSources; UI/ViewModel wiring remains the next step.

Static validation pass: removed all normal runtime calls to the legacy full-library `loadMedia()` path; Gallery browsing now relies on Paging/album discovery.

Implementation note: Favorites now select the Room-backed Paging source through the same `galleryPagingFlow`, so the normal timeline source is not collected in parallel. Album refresh is also isolated from legacy full-library loading.


## 0.2 Environment & Secret Hygiene

- [x] Remove the Secrets Gradle Plugin.
- [x] Remove the committed `.env.example` API-key template.
- [x] Remove obsolete Firebase AI / App Check / Google Services build integration.
- [x] Remove the server-side Gemini capability declaration.
- [x] Update local setup documentation so no API key is required.
- [x] Ignore local signing keystores and environment files.
- [ ] Run a full Android Gradle build after the cleanup.
- [ ] Re-scan the complete Git history for previously committed secrets if repository history exposure is suspected.


## 15. Aves-Class Gallery Capability Roadmap

The Gallery is now a media manager, not merely a thumbnail grid. The target is Aves-class capability while retaining this app's MediaStore + Paging performance architecture.

### Gallery foundation
- [x] Timeline for images + videos
- [x] Albums with independent Paging
- [x] Favorites with independent Paging
- [x] Photos / Videos / All filters
- [x] Bounded thumbnail loading
- [x] Bounded fullscreen viewer window
- [x] Absolute viewer navigation across unloaded pages
- [x] Stable multi-selection model for media
- [ ] Sort/group controls (date, name, size, type)
- [ ] Rich media categories (Screenshots, Camera, Downloads, GIF, RAW, etc.)

### Aves-style media management
- [ ] Immersive swipe-first viewer
- [ ] Video playback controls in viewer
- [ ] Share / open-with / wallpaper actions
- [ ] Delete / trash / restore from Gallery
- [ ] Move / copy / rename from Gallery
- [ ] Rotate / crop / basic editing actions
- [ ] Slideshow
- [x] Multi-select action bar
- [x] Batch share / favorite / delete
- [ ] Batch move / copy

### Metadata-first media details
- [x] EXIF / IPTC / XMP parsing infrastructure exists
- [x] Metadata inspector exists
- [ ] Integrate metadata inspector directly into Gallery actions
- [ ] GPS/location display and map entry point
- [ ] Safe metadata editing from Gallery
- [ ] Metadata-aware sorting/filtering

### Search and organization
- [x] Gallery search by filename (provider-side Paging)
- [x] Gallery search across filename, folder/album and path
- [x] Gallery search operators: type:, album:/folder:, name:, after:, before:, year:
- [x] Debounced and cancellable search query flow
- [ ] Gallery metadata/EXIF search
- [ ] Semantic/AI search
- [x] Date/year/month filtering
- [x] Location filtering (GPS presence + near:lat,lon,radius)
- [x] Metadata/tag filtering (camera, model, lens, ISO, aperture, focal length, description/EXIF text)
- [ ] Duplicate/similar-media views
- [ ] Large files / long videos views

### Intelligence layer (after deterministic Gallery)
- [ ] OCR
- [ ] Object tagging
- [ ] Captioning
- [ ] People/face grouping
- [ ] Embeddings / semantic search
- [ ] AI-derived tags written to metadata where safe
- [ ] Personal-memory/knowledge layer over enriched media

**Architecture rule:** every new Gallery feature must preserve bounded loading and must not reintroduce a full-library `List<MediaItem>` into the normal browsing path.

Last updated: 2026-09-27

### Gallery Selection Milestone — 2026-09-28
- [x] Long-press media to enter selection mode.
- [x] Tap additional loaded media while selection mode is active.
- [x] Selected-state overlay and contextual action bar.
- [x] Batch share using MediaStore content URIs.
- [x] Batch favorite.
- [x] Batch delete to existing Recycle Bin flow.
- [x] Selection state remains bounded to explicitly selected media; no library-wide materialization.
- [ ] Batch move/copy through FileOperationManager.
- [ ] Select-all semantics backed by provider queries rather than Paging snapshot.


### Gallery Metadata Search — 2026-09-28
- [x] Provider-side filename, album, folder/path and media-type search.
- [x] Capture-date search using MediaStore DATE_TAKEN (date:, taken:, year:, month:, after:, before:).
- [x] EXIF camera make/model/lens filtering.
- [x] EXIF ISO, aperture and focal-length filtering.
- [x] EXIF description/caption/tag/keyword text search.
- [x] GPS presence filtering (gps:true).
- [x] Radius-based GPS search (near:lat,lon,radiusKm).
- [x] Media location permission for unredacted EXIF GPS access.
- [x] Search remains Paging 3 bounded; EXIF is opened only when an EXIF/GPS operator is present.
- [ ] Persist extracted EXIF metadata for faster repeated metadata searches.
- [ ] Add reverse-geocoded place-name indexing (e.g. city/country).
- [ ] Add semantic/AI search over enriched metadata.

- [x] Replace per-search EXIF opening with persistent lazy Room metadata caching.
- [x] Add query geocoding cache for location:Place searches.
- [x] Search within Favorites through the same PagingSource.
- [x] Use a search-specific fullscreen source so viewer navigation stays within filtered results.
- [x] Remove legacy full-library Gallery media materialization from FileRepository.
- [x] Request ACCESS_MEDIA_LOCATION only for GPS/location search.


### Home Screen Simplification — 2026-09-28
- [x] Remove the storage overview card from the default Home dashboard.
- [x] Remove the standalone Categories section from the default Home dashboard.
- [x] Remove Favorites & Starred from the default Home dashboard.
- [x] Remove Recycle Bin from the default Home dashboard.
- [x] Remove Preferences & Room Index status from the default Home dashboard.
- [x] Remove Home search category filter chips so the default screen contains only the search bar, Recent Items, and Quick Tiles.
- [x] Keep existing search result behavior and category drill-down navigation intact.
- [x] Rename the recent section to "Recent Items".
- [x] Present the existing file categories as "Quick Tiles".


## 2026-09-28 — Gallery compact header + performance pass
- [x] Replace the large always-visible Gallery search field with a compact single-line Search button.
- [x] Keep search as an in-place dropdown with the full-width input, recent searches, suggestions, and quick filters.
- [x] Make Filter, Sort, and Group controls contextual: show the active media/date/GPS filter, current sort, and current grouping.
- [x] Make Gallery search suggestion and quick-filter chips toggleable: tapping an active filter removes it instead of re-applying it.
- [x] Reduce Gallery Paging initial/page size from 120 to 60, keep a bounded three-page window, and lower prefetch distance.
- [x] Stabilize Coil memory/disk cache keys for Gallery thumbnails.
- [ ] Device-measure Gallery open latency before/after this pass.
- [ ] Validate Gallery search/filter/group/sort interactions on a real device.

## File Explorer — Adaptive header plan
- [ ] Replace the File Explorer title/search-heavy header with the same compact single-line control pattern.
- [ ] Search: compact button -> in-place full-width search dropdown for folder-scoped search.
- [ ] Sort: adaptive button showing the active SortOption instead of a generic icon.
- [ ] Group: add an adaptive grouping control for Name/Type/Date/Size where supported without changing the storage-first architecture.
- [ ] Filters: expose contextual file-type/hidden/favorites filters as a compact adaptive control; tapping an active filter toggles it off.
- [ ] View mode: retain the current list/grid/compact modes while making the control reflect the active mode.
- [ ] Preserve breadcrumbs and selection mode as separate navigation/action surfaces.
- [ ] Keep File Explorer page loading and folder/stat caches; do not introduce a whole-storage pre-index just for header controls.
- [ ] Add the equivalent scroll-reactive header collapse after the Gallery version is device-validated.
