# emrexplore — Engineering Plan

**Target branch:** `gemini`  
**Current work:** Brain v2 rebuild, integration hardening, and release validation  
**Working branch:** `agent/brain-rebuild`

This file is the engineering source of truth. Completed work is recorded once. Open work is limited to unresolved correctness, testing, security, performance, and release concerns.

---

## 1. Brain v2 — complete

- [x] Remove `BrainEngine` and `KnowledgeGraphRepository`.
- [x] Replace legacy KG/RAG tables with dedicated Brain v2 persistence.
- [x] Move Brain entities, DAOs, repository, indexer, retriever, and models into `data.brain`.
- [x] Keep Gallery AI enrichment outside the Brain subsystem.
- [x] Add the provider-independent `BrainAiGateway` boundary.
- [x] Add bounded text, PDF, and image content extraction.
- [x] Add a real downloadable on-device neural embedding model path.
- [x] Store the model in app-private storage with atomic activation and SHA-256 validation.
- [x] Implement BERT WordPiece tokenization compatible with the selected model.
- [x] Execute on-device embeddings with ONNX Runtime.
- [x] Make the downloaded neural model the single canonical Brain indexing/query embedding space.
- [x] Automatically reindex after successful local model installation.
- [x] Keep lexical search as a separate exact-match fallback.
- [x] Remove obsolete hash/RRF retrieval machinery.
- [x] Reject low-relevance vector matches.
- [x] Validate retrieved sources against the live filesystem.
- [x] Exclude hidden files and `.trash` at Brain indexing and retrieval boundaries.
- [x] Bound graph expansion, source counts, and context size.
- [x] Redact precise location from cloud Brain context.
- [x] Restrict exact-location graph nodes to Ollama indexing.
- [x] Namespace exact-location identities independently from shared city nodes.
- [x] Make per-file indexing atomic with last-known-good preservation.
- [x] Detect file mutation before committing a staged index.
- [x] Bind indexer transactions to the database instance that owns its DAOs.
- [x] Make Brain sync filesystem-authoritative and remove stale Brain sources.
- [x] Replace `OFFSET` Brain candidate paging with keyset pagination.
- [x] Make Brain subtree matching safe for literal filesystem paths.
- [x] Preserve graph-edge provenance per supporting source file.
- [x] Add Brain schema migration through database version 14, including removal of duplicate legacy chunk embeddings.
- [x] Route rename, restore, and text-editor save hooks through Brain.
- [x] Add Brain core unit tests and database-surface assertions.
- [x] Add tokenizer and vector-codec regression tests.

## 2. Logic-hardening fixes — complete

- [x] Fix Favorites-only gallery search SQL bind ordering.
- [x] Preserve gallery sort through search, album paging, and fullscreen navigation.
- [x] Make favorites gallery media-only and globally sorted.
- [x] Reject stale/nonexistent indexed paths when building live search results.
- [x] Reject copy/move into the source directory or any descendant.
- [x] Reject same-source/same-destination streaming copies.
- [x] Validate create/rename child names against traversal and separator abuse.
- [x] Report physical trash-delete failures correctly.
- [x] Reject ZIP output that targets a source file or source subtree.
- [x] Bound direct file/chat reads.
- [x] Preserve RAG history and scope file-chat history to the current attachment.
- [x] Distinguish provider success from local fallback in Brain answers.
- [x] Redact precise location from cloud direct-file and RAG prompts.
- [x] Preserve exact provider identity during normalization.

---

## 3. P0 — release blockers

### CI gate
- [ ] Make Android CI a required branch-protection check for `gemini`.
- [ ] Verify unit tests, lint, and debug APK build on a clean runner.
- [ ] Verify signed release builds whenever release signing secrets are present.
- [ ] Document the required check names and merge policy.

### Filesystem and derived-state consistency
- [ ] Reconcile stale Room indexed-file rows after external delete, move, or rename.
- [ ] Create one authoritative post-mutation synchronization path for filesystem index, media metadata, favorites, bookmarks, recents, trash, and Brain.
- [ ] Cover directory rename/move with nested descendants.
- [ ] Ensure restore operations converge every affected derived store.

### Permission and startup sequencing
- [ ] Prevent automatic indexing from starting before the required storage/media access is available.
- [ ] Make permission callbacks idempotent so multiple callbacks cannot trigger duplicate scans.
- [ ] Make activity resume and first-run initialization converge to one deterministic state.

---

## 4. P1 — Explorer and filesystem correctness

- [ ] Fix `SUBFOLDERS` search scoping so descendant results remain inside the current explorer root.
- [ ] Make empty-query descendant search cover the intended subtree rather than only direct children.
- [ ] Audit all recursive filesystem traversals for symlink loops and canonical-path duplication.
- [ ] Reuse indexed queries where they are cheaper and semantically correct.
- [ ] Revalidate all externally referenced files before opening or sharing.
- [ ] Distinguish permission, missing-source, conflict, I/O, and storage-capacity failures with typed/domain errors where practical.
- [ ] Add regression tests for unusual names, stale paths, inaccessible folders, renamed directories, and symlinked trees.

---

## 5. P1 — AI and Brain quality

### AI client decomposition
- [ ] Separate Gemini transport from OpenAI-compatible transport.
- [ ] Separate model discovery from inference.
- [ ] Separate embedding APIs from chat/vision APIs.
- [ ] Extract response/JSON parsing into small testable components.
- [ ] Centralize endpoint validation, local endpoint exceptions, headers, and credential handling.

### Brain retrieval validation
- [ ] Add explicit no-match regression tests.
- [ ] Add end-to-end device test that downloads the current model and verifies two semantically related queries share the expected local embedding space.
- [ ] Add a model-upgrade regression proving old chunks are invalidated and rebuilt when the local model identity changes.
- [ ] Add tests for lexical fallback, disappearing files, hidden sources, and `.trash`.
- [ ] Benchmark retrieval and scan volume at 1k, 10k, and 100k chunks.
- [ ] Measure indexing latency, chunk counts, vector failures, and retrieval latency.

### Gallery AI cache
- [ ] Version enrichment checkpoints by provider identity, model, prompt/schema version, and privacy policy version.
- [ ] Ensure provider/model changes trigger intentional recomputation.
- [ ] Preserve pause, resume, cancellation, and worker-restart semantics.

---

## 6. P1 — automated test coverage

### File operations
- [ ] Copy: success, overwrite, skip, keep-both.
- [ ] Move: same-filesystem rename and cross-filesystem fallback.
- [ ] Trash, restore, permanent delete.
- [ ] Cancellation, pause/resume, partial-copy cleanup, insufficient storage, nested directories.

### Room migrations
- [ ] Test supported upgrade paths through database version 14.
- [ ] Insert representative legacy KG/RAG data before migration.
- [ ] Verify intentional legacy Brain reset and new Brain tables.
- [ ] Verify primary keys, indices, nullability, `brain_edge_evidence` provenance, and the 13 -> 14 chunk-table migration.

### AI client
- [ ] Endpoint validation and local-endpoint allowances.
- [ ] Authentication and model-not-found behavior.
- [ ] Gemini parsing and OpenAI-compatible parsing.
- [ ] Embedding response ordering/count validation.
- [ ] Malformed JSON fallbacks.
- [ ] Multimodal request construction.
- [ ] Header validation and secret non-logging.

### WorkManager
- [ ] Brain indexing retry/failure/cancellation behavior.
- [ ] Gallery AI checkpoint/resume.
- [ ] Pause/resume and duplicate-work behavior.
- [ ] Missing/unreadable files and missing configuration.

### Android/UI
- [ ] Home, Files, Gallery, and Brain navigation.
- [ ] Back handling for viewers, editors, and dialogs.
- [ ] Permission flows.
- [ ] Gallery filtering, search, and sorting.
- [ ] File sharing.
- [ ] Brain graph interaction and file opening.
- [ ] Text-editor save to metadata/Brain synchronization.

---

## 7. P2 — security and privacy

- [ ] Document exactly what data may leave the device for every AI provider.
- [ ] Make cloud/local privacy implications explicit in AI settings.
- [ ] Audit custom header names and values for security-policy bypasses.
- [ ] Verify secrets never reach logs, crash diagnostics, or committed files.
- [ ] Minimize storage permissions where scoped-storage/MediaStore can cover the use case.
- [ ] Audit every AI prompt for unrelated file context and precise location leakage.

---

## 8. P2 — ZIP and archive safety

- [ ] Add malicious archive-entry tests.
- [ ] Add decompressed-size and entry-count limits.
- [ ] Add compressed-bomb protections.
- [ ] Revalidate extraction targets before writing.
- [ ] Preserve ZIP self-target and source-subtree guards.

---

## 9. P2 — architecture and maintainability

- [ ] Split `UnifiedViewModel` by feature or move orchestration into focused use cases.
- [ ] Rename remaining historical `kg*` UI/state identifiers to Brain terminology.
- [ ] Rename `KnowledgeGraphScreen` to a Brain-oriented screen name.
- [ ] Review package identity before release:
  - `namespace = "com.example"`
  - `applicationId = "com.aistudio.emrexplore.nxkqza"`
- [ ] Verify dependency compatibility across AGP, Gradle, Kotlin, Compose, Room, Paging, Coil, WorkManager, PDFBox, Retrofit, and OkHttp.
- [ ] Remove unused imports/dependencies exposed by the Brain rebuild.
- [ ] Add release artifact/version metadata verification.

---

## 10. Performance and observability

- [x] Keep Brain indexing memory bounded for very large libraries.
- [ ] Keep image preview and metadata work bounded by dimensions/file size.
- [x] Avoid whole-library materialization during Brain synchronization.
- [ ] Avoid unbounded text reads and whole-file strings.
- [ ] Add instrumentation for index duration, chunks created, embedding failures, graph nodes/edges, and retrieval latency.
- [ ] Add migration and database-growth benchmarks.

---

## Definition of done

The Brain v2 rebuild and hardening phase is complete when:

- [ ] CI is a required green gate for `gemini`.
- [ ] Unit tests, lint, and debug APK builds are green on a clean runner.
- [ ] Migration coverage exists through DB v13.
- [ ] File-operation regressions are automated.
- [ ] AI provider and embedding parsing are automated.
- [ ] Brain no-match, privacy, provenance, and filesystem-validation behavior have explicit tests.
- [ ] Brain and Gallery AI remain bounded in memory on large libraries.
- [ ] Filesystem mutations converge all derived state.
- [ ] Startup permission/indexing sequencing is deterministic.
- [ ] Package identity and release signing are production-ready.
- [ ] Historical KG naming is removed from the user-facing Brain implementation.

## Current verification state

PR #3 remains intentionally **draft** until the Android CI workflow finishes successfully and the remaining release-blocking integration coverage is complete.

## 11. Metadata engine — ExifTool integration

- [x] Add an app-private ExifTool process bridge using argv-only execution.
- [x] Route metadata inspection through ExifTool JSON output.
- [x] Remove whole-file JPEG segment scanning from the production inspection path.
- [x] Route AI metadata writes through ExifTool instead of manual JPEG/EXIF byte surgery.
- [x] Support SAF/content URIs through temporary app-private staging and copy-back.
- [x] Package the runtime through ABI-specific `jniLibs` and document the native-artifact contract.
- [ ] Provision and verify generated ExifTool native artifacts in CI before release builds.
