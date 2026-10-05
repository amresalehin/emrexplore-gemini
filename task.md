## P0 — users will hit these

### 1. Files “Move to Trash?” permanently deletes

Confirm dialog: *“Move this item to Trash? You can restore it later.”*  
`deletePathsAfterConfirmation(..., toTrash = true)` then `FileOperationManager` **ignores `toTrash`** and calls `File.delete()` / `deleteRecursively()`.

Gallery delete uses the real trash path. Explorer does not. Recycle Bin on Home is disconnected from the main Files tab.

### 2. Videos do not play

`FullscreenMediaViewer` draws a Play overlay on an `AsyncImage`. Tap toggles chrome. No `ExoPlayer`, no `Intent.ACTION_VIEW`. Gallery’s video path is a poster frame.

### 3. Copy / move / delete do not update derived stores

Explorer paste goes through `FileOperationManager`, which only invalidates an in-memory folder cache. After a move:

- Live listing looks fine
- `indexed_files`, favorites, recents, bookmarks keep old paths
- Brain sync is a deferred WorkManager job that often **no-ops for 5 minutes** (`indexStorage` skip window) with `refreshStorageIndex = false`

This is exactly the still-open P0 in `task.md` §3.

### 4. First-run indexing races permission

ViewModel `init` calls `indexStorage()` with no permission check. If storage is unreadable, it indexes `filesDir`. After grant, `onPermissionsGranted()` latches `permissionsInitialized = true` forever — later grants (runtime media after All Files) are ignored, and gallery is not refreshed.

### 5. Gallery Back dumps you on Home

Explorer clears search/selection first. Gallery only handles Back inside an album. Search, filters, and multi-select fall through to `MainActivity` → `setTab(HOME)`. Query and selection stay dirty.

### 6. Home category drill-down shows the previous category

`selectCategory` swaps the title immediately, does not clear `categoryFiles`, has no loading flag. Images → Videos shows Videos chrome over Images rows until the coroutine finishes.

---

## UI / UX

**What’s working:** four-tab Material shell (Home / Files / Gallery / Brain), sequenced All Files vs runtime permission dialogs, Brain-first settings copy, model-readiness empty states, dirty text-editor Back prompt, operation banner with pause/cancel/conflict.

**What’s not:**

| Issue | Why it matters |
|---|---|
| Recycle Bin from Files teleports to Home | `openRecycleBin()` forces `currentTab = HOME`. Unused `RecycleBinDialog.kt` exists. Back returns to Home, not Files. |
| Nested Home searches ignore Back | Recycle Bin / category search are local `remember` state; Back closes the page. |
| Albums never refresh | Screen fetches `MediaAlbumRepository` once when the list is empty. `UiState.mediaAlbums` is never written. Permission grant while on Albums does nothing. |
| “Pull to refresh” is a lie | Error empty-state says it. There is no pull-to-refresh or retry. |
| Duplicate Brain download cards | Two full download/progress/delete blocks in AI Settings, same test tags. |
| AI Settings Back drops drafts | Pickers and unsaved API keys close with the screen. |
| Four different search products | Home live / Explorer scoped / Gallery “submit” that actually live-filters / Brain graph filter. Different Back contracts. |
| Persistent tabs keep 4 heavy screens composed | `alpha = 0` — paging, Brain graph, Home 4k-line tree still run. TalkBack can land on invisible controls. |
| Home is a 4056-line god composable | ~20 ephemeral `remember` filters reset on rotation. Dead leftover tile-dashboard state. |
| Almost no strings | `strings.xml` has 9 nav labels. Rest is hardcoded English. |
| Dynamic color on API 31+ | Brand palette never applies on modern devices. `Fossify*` color aliases still shipped. |
| Gallery delete has no confirm | Explorer just added “Move to Trash?”; Gallery selection delete is immediate. |
| Explorer header overcrowded | Tabs + search + 5 icons on a 360dp phone. Paste bar overlaps FAB. |
| Brain leftover naming | Snackbar: “Knowledge Graph cleared”. Properties: “AI Knowledge Graph”. File still `KnowledgeGraphScreen.kt`. |

Ask-AI from a file properties sheet sends the **literal** string `Tell me about this file: ${file.name}` — `$` is escaped in Kotlin. Attachment still works; the question text is garbage.

---

## App logic

**Brain v2 is the real achievement.** Bounded extraction, SHA-256 model install, atomic per-file commit with last-known-good, live FS validation at retrieval, GPS redaction for cloud, Ollama-only exact location nodes, targeted Gallery processing on one worker.

**Claims vs code:**

| `task.md` claim | Reality |
|---|---|
| Keyset paging for Brain | File-index candidates: yes. Chunk retrieval: still `LIMIT/OFFSET`, hard cap **20,000** chunks. |
| Avoid whole-library materialization | `syncAll` loads every Brain path + every candidate into HashSets. |
| DB v14 complete | Code is **v15**. No 13→14 data test. `exportSchema = false`. |
| Trash-delete failures reported | Repository path exists. Explorer never calls it. |
| Rename/restore/editor feed Brain | Hooks exist. Clipboard copy/move/delete bypass them. |
| SUBFOLDERS search scoped | Still a live recursive walk. Room `searchFilesUnderPath` is unused. UI still shows “Indexed”. |

Other logic that will show up:

- **Rename a favorited photo** → vanishes from Gallery Favorites (path not rewritten).
- **SUBFOLDERS on a dead path** → silently searches all storage. No symlink visited-set → loop risk.
- **Lexical Brain hits ungated** — “the photos” can match random files. Semantic failures swallow to empty, then lexical fills in.
- **`AiProviderClient` catches `Exception`** including cancellation → cancelled index can still commit fallback analysis.
- **Two ONNX engines** (ViewModel + Worker) — memory risk on-device.
- **Editor `readText()` is unbounded** — large logs can OOM. Brain reads are capped; the editor is not.
- **ZIP** has zip-slip checks, no bomb/size/entry caps.
- **Package identity:** `namespace = "com.example"`, `applicationId = "com.aistudio.emrexplore.nxkqza"`, `versionName = "1.0"`.

**Tests:** 5 files. Chunker, privacy regex, WordPiece, sort, fresh-DB column names. No migration, no file-ops, no retrieval, no UI, no WorkManager. `androidTest` is still `ExampleInstrumentedTest`.

---

## Architecture smell

One `UiState` owns explorer paging, gallery, audio player, trash, Brain graph, RAG chat, zip, editor, AI settings. Four screens are 1.6k–4k lines. That is why the last day of commits is one-line compile patches: there is no feature boundary to change safely.

Do **not** split the ViewModel first. Mutation/trash/permission is broken; moving it just relocates the bugs.

---

## Ship order (if this is meant to merge)

1. Explorer delete → real trash (`FileRepository.deleteFile`), or teach `FileOperationManager` to honor `toTrash`.
2. One `reconcileMutation(old, new)` for file index **and** favorites / recents / bookmarks / media / Brain. Call it from `onFilesMutated`.
3. Do not index until permission is granted; reset the latch on revoke; `refreshGallery()` on grant.
4. Play videos. Fix Gallery Back (search → selection → album → Home).
5. SUBFOLDERS → `searchFilesUnderPath`; visited-set on recursive walks.
6. Fix `askAiAboutFile` interpolation; drop remaining “Knowledge Graph” user copy.
7. Keyset-page `brain_chunks`; gate lexical scores; add a 13→14→15 migration test with dual-embedding rows.
8. Then split `UnifiedViewModel`.

**Do not merge** until 1–4 are done and CI is a required check on `gemini`. Brain v2 itself is in good shape relative to the rest of the app. The product around it is not.
