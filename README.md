### Critical weaknesses

1. **Filesystem ↔ Brain consistency**
   - Rename/move/delete can update the filesystem without every derived store being updated consistently.
   - This can leave stale Brain paths, favorites, bookmarks, metadata, etc.
   - A file can effectively exist in Brain after it has disappeared from storage.

2. **Delete/Trash safety**
   - There is risk that a UI operation presented as “move to trash” can reach physical deletion paths.
   - For a file manager, this is a **data-loss-class defect**.

3. **Permission lifecycle**
   - Initial indexing can race with storage permission initialization.
   - Granting/revoking permission can leave stale indexing state.
   - The app needs permission changes to be treated as lifecycle events, not one-time startup conditions.

4. **Database migration confidence**
   - The migration chain is extensive, but there isn't enough automated testing of upgrades from every historical DB version.
   - A fresh install working does **not** prove existing users can upgrade safely.

---

### Major Brain weaknesses

5. **Local vector search doesn't scale well**
   - Room retrieval uses paged scanning and has a hard scan ceiling.
   - At sufficiently large Brain indexes, relevant documents can simply never be examined.
   - This becomes a **recall correctness problem**, not merely a performance problem.

6. **Lexical fallback can produce weak matches**
   - Semantic retrieval has meaningful relevance controls.
   - Lexical fallback is comparatively permissive.
   - Generic words can therefore pull irrelevant files into the answer context.

7. **Room ↔ Qdrant consistency**
   - Room can successfully commit an index while Qdrant fails afterward.
   - There is no strong durable synchronization/outbox mechanism guaranteeing eventual convergence.
   - The two stores can temporarily disagree.

8. **Secret ingestion**
   - Brain can index ordinary filesystem text, including potentially `.env`/credential-bearing files.
   - If cloud enrichment is enabled, sensitive content could become eligible for provider processing.
   - A warning to the user isn't as strong as automatic secret detection/exclusion.

9. **Model-switch transition**
   - Changing the embedding model triggers reindexing.
   - During rebuild, the application can temporarily operate against an index produced by a different model.
   - The UX needs an explicit “rebuilding / old index still active” state.

---

### Architecture weaknesses

10. **`UnifiedViewModel` is too large**
   - Roughly 3,000 lines.
   - It owns Explorer, Gallery, Brain, AI configuration, operations, permissions, media, topics, etc.
   - This dramatically increases regression risk.

11. **Heavy screens remain alive**
   - Multiple major screens can remain composed rather than being fully disposed.
   - This increases memory/recomposition pressure, especially alongside ONNX inference.

12. **ONNX memory/lifecycle isn't sufficiently proven**
   - The implementation is legitimate, but real-device memory and model-loading behavior haven't been sufficiently demonstrated.
   - Multiple inference/model lifecycle paths could become expensive on low-RAM Android devices.

---

### Testing weaknesses

13. **Too much unit testing, not enough integration testing**
   Missing high-value tests include:
   - rename → Brain update
   - move → Brain update
   - delete → Brain cleanup
   - trash → restore → Brain recovery
   - permission revoke/regrant
   - migration upgrades
   - Room/Qdrant divergence
   - model change → complete reindex
   - large-index retrieval
   - cancellation during indexing

14. **No convincing real-device semantic validation**
   - Code inspection shows the ONNX pipeline exists.
   - It doesn't prove semantic quality, latency, memory consumption, or stability across actual Android hardware.

15. **Current CI evidence is insufficient**
   - The audited PR head doesn't currently have corresponding GitHub status/workflow evidence proving a green build.
   - Therefore the branch should not be described as verified-green merely from source inspection.

---

### Secondary weaknesses

16. **UI/configuration can permit theoretically valid but operationally unusable combinations.**
   - Provider/model selection should validate actual compatibility and readiness.

17. **Some legacy terminology remains.**
   - “Knowledge Graph” naming persists around a Brain system that has been substantially redesigned.

18. **Large files remain difficult to maintain.**
   - `UnifiedViewModel`, settings UI, and other large classes make future changes risky.

19. **ZIP extraction needs stronger resource limits.**
   - Zip bombs / enormous extraction workloads need stronger protection.

20. **Large-file text handling needs stricter bounds.**
   - Direct whole-file reads can create memory pressure.

---
