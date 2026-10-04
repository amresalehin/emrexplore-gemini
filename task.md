# emrexplore — Engineering Task List

Target branch: `gemini` (repository default branch)

## P0 — Fix immediately

- [x] **Align GitHub Actions with the default branch**
  - Updated `.github/workflows/build-apk.yml` push and pull-request branch filters to `gemini`.
  - Kept `workflow_dispatch`.
  - CI trigger execution still needs verification on GitHub Actions.


- [ ] **Establish a reliable default-branch build gate**
  - Require the CI workflow to pass before merging changes into `gemini`.
  - Ensure failures in tests or lint fail the workflow.
  - Document the required checks in the repository.

## P1 — Architecture and scalability

- [ ] **Split `UnifiedViewModel` by feature**
  - Extract file explorer state/actions.
  - Extract gallery/media state/actions.
  - Extract file-operation state/actions.
  - Extract AI/RAG state/actions.
  - Extract knowledge-graph/Brain state/actions.
  - Keep cross-feature coordination in small use-case/service classes.
  - Preserve existing UI behavior while reducing ViewModel responsibilities.

- [ ] **Decompose `AiProviderClient`**
  - Separate Gemini transport from OpenAI-compatible transport.
  - Separate model discovery from inference.
  - Separate embedding APIs from chat/vision APIs.
  - Extract response/JSON parsing into testable components.
  - Keep endpoint validation and credential handling centralized.

- [ ] **Make Brain indexing incremental**
  - Replace full materialization from `getAllIndexedFilesForBrain()` with paged/batched reads.
  - Process bounded batches through the indexing pipeline.
  - Avoid holding all indexed files, chunks, embeddings, and graph candidates in memory simultaneously.
  - Add progress reporting at batch boundaries.

- [ ] **Audit recursive filesystem work**
  - Identify all full-tree scans and repeated directory walks.
  - Avoid duplicate traversal for the same operation.
  - Prefer indexed/Room-backed queries for search and category views where possible.
  - Add safeguards for very large directory trees.

## P1 — Testing and data integrity

- [ ] **Add file-operation tests**
  - Copy: success, overwrite, skip, keep-both.
  - Move: same-filesystem rename and cross-filesystem fallback.
  - Delete: trash and permanent delete.
  - Cancellation during large copies.
  - Pause/resume during large copies.
  - Partial-copy cleanup after failure.
  - Disk-space failure.
  - Nested-directory operations.

- [ ] **Add Room migration tests**
  - Test each supported migration path into database version 11.
  - Insert representative data before migration.
  - Assert data preservation after migration.
  - Validate recreated tables, indices, null/default behavior, and constraints.
  - Consider exporting Room schemas for migration review.

- [ ] **Add AI client tests**
  - Endpoint validation.
  - HTTPS enforcement and permitted local endpoints.
  - Authentication failures.
  - Model-not-found handling.
  - Gemini response parsing.
  - OpenAI-compatible response parsing.
  - Embedding response parsing.
  - Malformed JSON fallback behavior.
  - Multimodal request construction.

- [ ] **Add WorkManager tests**
  - Gallery AI checkpoint/resume.
  - Pause behavior.
  - Retry behavior.
  - Missing/invalid configuration.
  - Missing/unreadable files.
  - Cancellation propagation.
  - Completion and failure bookkeeping.

- [ ] **Expand Android/UI tests**
  - Navigation between Home, Files, Gallery, and Brain.
  - Back handling for nested viewers/editors/dialogs.
  - Permission flows.
  - File sharing.
  - Gallery filtering/search/sorting.
  - Knowledge graph interaction and file opening.

## P2 — Reliability and maintainability

- [ ] **Replace broad exception swallowing**
  - Audit `catch (_: Exception) { }` and generic `e.printStackTrace()` usage.
  - Introduce structured/domain-specific error reporting.
  - Preserve user-friendly fallbacks while retaining diagnostics for debugging.

- [ ] **Improve filesystem error semantics**
  - Replace ambiguous boolean-only failures where practical with typed/domain errors.
  - Distinguish permission denied, missing source, destination conflict, I/O failure, and insufficient storage.
  - Surface actionable messages to the UI.

- [ ] **Review file/path handling**
  - Normalize and validate paths at operation boundaries.
  - Confirm behavior for symlinks, unusual filenames, renamed directories, and stale index entries.
  - Ensure all external file references are revalidated before opening.

- [ ] **Review ZIP and archive limits**
  - Keep path traversal protection.
  - Add tests for malicious archive entries.
  - Consider protection against oversized/compressed-bomb archives.
  - Consider output-size and entry-count limits.

## P2 — Security and privacy

- [ ] **Document AI data-privacy boundaries**
  - Clearly identify when file contents, metadata, images, or chat history are sent to external AI providers.
  - Define what content is allowed to leave the device.
  - Make provider selection and AI enablement explicit in the UX.

- [ ] **Audit custom AI headers**
  - Validate header names/values where appropriate.
  - Ensure sensitive custom headers are never logged.
  - Confirm custom headers cannot weaken transport security.

- [ ] **Review storage permissions for distribution**
  - Confirm the use of `MANAGE_EXTERNAL_STORAGE` is necessary for the product's core functionality.
  - Document the justification for distribution/review.
  - Minimize permissions where scoped storage/MediaStore is sufficient.

- [ ] **Validate secret-handling lifecycle**
  - Keep API keys encrypted at rest with Android Keystore.
  - Never commit real keys or signing material.
  - Ensure decrypted keys exist only for the duration needed by requests/workers.
  - Avoid accidental inclusion of secrets in diagnostics, crash reports, or request logging.

## P2 — Build and release hygiene

- [ ] **Normalize application identity**
  - Review `namespace = "com.example"`.
  - Review `applicationId = "com.aistudio.emrexplore.nxkqza"`.
  - Move to a stable product-owned package/application ID before release, if compatible with the app's lifecycle.

- [ ] **Make release-signing failures explicit**
  - Fail early with a clear message when release signing variables/keystore are missing.
  - Keep release signing credentials outside the repository.

- [ ] **Verify dependency compatibility**
  - Validate the current AGP, Gradle, Kotlin, Compose BOM, Room, Paging, Coil, WorkManager, and related versions together.
  - Use CI to detect incompatible upgrades.
  - Remove unused dependencies where possible.

- [ ] **Improve release verification**
  - Build debug and signed release variants in CI when signing secrets are available.
  - Verify generated APK metadata and installability.
  - Record artifact version/build information.

## Definition of done

- [ ] CI runs automatically for every change to the `gemini` default branch.
- [ ] Unit tests, lint, and APK build are green on a clean CI environment.
- [ ] File operations have automated regression coverage.
- [ ] Room migrations have automated upgrade coverage.
- [ ] AI provider/embedding parsing has automated coverage.
- [ ] Brain and Gallery AI processing remain bounded in memory on large libraries.
- [ ] Critical security/privacy paths have explicit tests and documentation.
- [ ] Release signing and package identity are production-ready.


## Logic Audit — Agent Hardening (2026-10-04)

### Completed in `agent/logic-hardening`

- [x] Fix Favorites-only gallery search SQL argument ordering.
- [x] Preserve the selected Gallery sort in search results.
- [x] Preserve the selected Gallery sort in album paging and fullscreen navigation.
- [x] Make Favorites a media-only, globally sorted gallery and ignore stale/non-media favorite rows.
- [x] Reject copy/move destinations that are the source or descendants of a source directory.
- [x] Reject same-file streaming copies before opening the destination.
- [x] Validate create/rename child names against absolute paths and path separators.
- [x] Stop reporting physical trash deletion as successful when recursive deletion fails.
- [x] Reject ZIP output paths that target a source file or source subtree.
- [x] Bound direct text-file chat and document indexing reads without materializing the whole file.
- [x] Carry RAG conversation history into retrieval-backed generation.
- [x] Scope file-chat history to the currently attached file.
- [x] Mark RAG/file-chat service fallbacks as unsuccessful so the UI can distinguish fallback from provider success.
- [x] Redact GPS/location metadata from cloud direct-file prompts.
- [x] Prevent new exact-GPS graph evidence from being created for cloud providers.
- [x] Add query-time redaction for legacy exact-location data before cloud RAG prompts.

### Still open from the audit

- [ ] Reconcile stale Room indexed-file rows after external delete/move/rename.
- [ ] Synchronize KG/RAG/favorites/bookmarks/recents after filesystem mutations and restore.
- [ ] Reindex Brain/KG/RAG after text-editor saves.
- [ ] Exclude `.trash` content from all Brain indexing paths.
- [ ] Fix startup indexing/permission sequencing.
- [ ] Fix explorer SUBFOLDERS scope and empty-query descendant handling.
- [ ] Filter stale/nonexistent Room results before presenting fast search results.
- [ ] Fix Brain file-type filtering before top-k truncation.
- [ ] Resolve provider normalization/embedding-default mismatch.
- [ ] Fix transactional validation so failed remote embeddings cannot leave destructive partial Brain replacements.
- [ ] Version AI enrichment cache by provider/prompt/schema/privacy policy.
- [ ] Add symlink/visited-set protections to all recursive filesystem paths.
- [ ] Expand automated regression coverage for the above invariants.
