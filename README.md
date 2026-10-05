# emrexplore

**emrexplore** is an Android file manager and media gallery with an optional AI layer for file understanding, semantic search, connected-file discovery, and local/private knowledge retrieval.

The app remains useful without an AI provider: file browsing, media browsing, search, file operations, metadata inspection, and Brain offline retrieval do not require a cloud API key.

## Features

### File Explorer
- Browse shared storage with tabs and folder navigation.
- Search by filename with type, date, and size filters.
- Sort and switch between supported list/grid views.
- Copy, move, rename, delete, restore, and manage files.
- Use the recycle-bin/trash flow for recoverable deletion.
- Create and inspect ZIP archives.
- Open and edit supported text files.
- Share and open files through Android-compatible URIs.

### Gallery
- Browse photos and videos by timeline or album.
- Filter by media type, date, GPS presence, favorites, and search.
- Sort by date, name, and size.
- Open a paged fullscreen media viewer.
- Inspect EXIF, IPTC, and XMP metadata.
- Optionally enrich images with AI captions, tags, entities, and relations.

### Brain
Brain is the replacement for the legacy Knowledge Graph and RAG subsystem.

Brain v2 is separated into bounded components:

```text
data/brain/
├── BrainContentReader.kt   # bounded text/PDF/image extraction
├── BrainCore.kt            # chunking, privacy, stable identity
├── BrainEntities.kt        # Brain Room entities
├── BrainDaos.kt            # Brain persistence/query surface
├── BrainAiGateway.kt       # provider-independent Brain AI boundary
├── BrainIndexer.kt         # atomic per-file indexing
├── BrainRetriever.kt       # hybrid retrieval + bounded graph expansion
├── BrainRepository.kt      # single public Brain boundary
└── BrainModels.kt          # Brain/UI data models
```

The indexing pipeline is deliberately conservative:
1. Read a bounded file snapshot.
2. Analyze text or images when an AI provider is available.
3. Build deterministic chunks and local embeddings.
4. Optionally generate provider embeddings.
5. Build file/entity nodes and relationship evidence.
6. Verify the file did not change during processing.
7. Commit the staged Brain representation atomically.

A failed index preserves the previous known-good Brain representation.

### Retrieval
Brain always keeps a deterministic on-device embedding representation.

When a provider supports embeddings, Brain stores that representation as a second retrieval signal. It ranks each embedding space independently and combines the rankings with Reciprocal Rank Fusion (RRF). Raw cosine scores from incompatible vector spaces are never compared directly.

Retrieval also provides:
- lexical fallback without mixing lexical scores into RRF scores,
- minimum relevance gating,
- live filesystem validation,
- bounded graph expansion and context construction,
- source-count limits,
- hidden-file and `.trash` exclusion.

## AI providers

The provider layer supports:
- Google Gemini
- OpenAI-compatible endpoints
- OpenRouter
- Ollama
- Groq
- Custom OpenAI-compatible endpoints

Provider identity is preserved instead of collapsing all compatible services into one generic type.

Ollama is the private/local path. Exact GPS graph data is restricted to Ollama indexing. Cloud-oriented Brain context removes or redacts precise location information.

AI is optional. Without a usable provider, Brain can still build its deterministic local index and perform local retrieval/fallback responses.

## Privacy and security

- API keys are handled through the encrypted application-key path.
- Real API keys, tokens, signing keystores, and signing passwords must never be committed.
- Cloud Brain context is redacted for precise GPS/location information.
- Exact location graph nodes are only created for Ollama indexing.
- AI file chat is scoped to the attached file rather than unrelated previous file conversations.
- The repository requests broad storage/media access because file-manager behavior is a core product feature; permission minimization remains a release review item.

## Brain database migration

Brain v2 intentionally replaces the old KG/RAG storage model.

The database is currently **version 13**. The migration from the previous Brain/KG/RAG schema intentionally removes the legacy Brain tables and creates the new Brain v2 schema. After upgrading an existing installation, synchronize Brain again so the new representation is populated.

Current Brain tables:
- `brain_documents`
- `brain_chunks`
- `brain_nodes`
- `brain_edges`
- `brain_edge_evidence`
- `brain_topics`
- `brain_runs`

Edge provenance is stored separately for each supporting source file. Removing one source therefore does not erase a relationship supported by another source.

## Build locally

### Prerequisites
- Android Studio
- JDK 17
- Android SDK compatible with the project
- Android emulator or physical device for device testing

Open the repository in Android Studio, let Gradle sync, and run the `app` configuration.

No `.env` file and no API key are required to build the application.

CI uses the following verification tasks:

```bash
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

Release builds use environment-provided signing credentials. Keep release keystores and passwords outside the repository.

## CI

The Android workflow runs for:
- pushes to `gemini`,
- pull requests targeting `gemini`,
- manual `workflow_dispatch` runs.

The workflow performs environment setup, unit tests, debug lint, debug APK build, optional signed release APK build, APK verification, and artifact upload.

Release signing is optional for the public build workflow and is supplied only through GitHub Actions secrets when available.

## Project structure

```text
app/src/main/java/com/example/
├── data/
│   ├── ai/          # provider transport, model discovery, AI workers
│   ├── brain/       # Brain v2 indexing, retrieval, graph, and models
│   ├── local/       # Room database, DAOs, migrations
│   ├── media/       # gallery/media flows
│   ├── metadata/    # EXIF/IPTC/XMP extraction and writing
│   ├── model/       # domain models
│   ├── operations/  # file-operation execution and progress
│   └── repository/  # filesystem/index coordination
├── ui/
│   ├── components/
│   ├── screens/
│   └── viewmodel/
└── MainActivity.kt
```

The UI still contains some historical `kg*` state and screen naming. Runtime Brain data ownership is now under `com.example.data.brain`; the remaining naming cleanup is tracked in `task.md`.

## Current engineering status

Brain v2 is implemented on `agent/brain-rebuild` in draft PR #3 and is intended to merge into `gemini`.

The current phase is validation and cross-feature hardening, not another Brain rewrite. See [task.md](task.md) for the active backlog and release definition of done.