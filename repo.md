# SULAT Repository Report

This document tracks the canonical fix history for the SULAT Android repository.

The canonical artifact identity contract is:

```
LetterData → DocumentLayout → Preview Renderer → PDF Renderer → Print Renderer → Share/Export
```

`PdfArtifactManager` is the canonical artifact funnel: every Save, Share, and Print path
must consume the PDF produced (and possibly cached) by `PdfArtifactManager.ensurePdfArtifact`.
No competing PDF generation path is permitted to be introduced.

Offline constraint: no network, API, cloud DB, analytics, AI service, or runtime
dependency may be introduced at any time.

---

## FIX history (preserved)

### FIX12-B — Canonical PDF artifact identity (COMPLETE, CI GREEN)

**Commit**: `b605cc9` — "fix: sanitize dots in artifact filename and locate source from CWD".

What was fixed:
- Invalid Kotlin string escape `\x00` in `PdfArtifactManagerTest.kt` corrected to `\u0000`
  (Kotlin does not support `\x00` in this context).
- `sanitizeForFilename` in `PdfArtifactManager.kt` now strips `.` so that
  path traversal payloads like `../../../etc/passwd` cannot survive sanitization.
- `productionCodeMustNotInstantiateApplication` test in `PdfArtifactManagerTest.kt`
  now walks up from the JVM working directory to locate the production source
  (AGP launches unit tests with `app/` as the working directory, not the repo root).

Canonical invariants enforced by tests:
- same draft + same paper size → same artifact path.
- different draft → different artifact.
- same draft + different paper size → different artifact.

CI status at completion: GitHub Actions run `33970046626` — both `assembleDebug` and
`testDebugUnitTest` were SUCCESS; overall workflow SUCCESS.

### FIX13 — Independent end-to-end artifact audit (COMPLETE)

**Commit**: `c4d4070` — "audit: add FIX13 audit report" (audits/FIX13/FIX13-audit.md).

**Verdict**: **FAIL** — high-severity staleness defect confirmed.

Defect summary:
- `isValidArtifact` checks file-existence, `%PDF-` header, and canonical-path
  identity. It does NOT compare the artifact to the draft's content or version.
- The canonical artifact filename depends only on `(draftId, sanitizedSubject, paperSize)`,
  which means any edit that preserves `id` and `subject` (which is every edit)
  yields the same canonical filename.
- Therefore: edit draft → save → reopen Preview → tap Save/Share/Print →
  `ensurePdfArtifact` returns the previously cached PDF, which still reflects the
  OLD content.

Severity: HIGH — the user can save, share, or print a PDF that does not match the
visible preview.

See `audits/FIX13/FIX13-audit.md` for the full scenario-by-scenario analysis,
evidence, and exact reproduction.

---

## FIX14 — Stale PDF artifact investigation

### Status

Investigation complete. Reproduction confirmed. Fix NOT yet applied. Production code
intentionally unchanged in this commit.

### Investigation commit

This report and the accompanying reproduction test
(`app/src/test/java/com/sulat/ai/share/Fix14StalenessReproductionTest.kt`) constitute
the investigation commit. The test file is marked TEMPORARY and is intended to be
deleted (or converted into a permanent regression test) in the subsequent FIX14-B
implementation commit. It does NOT modify production code.

### Reproduction result

REPRODUCED. The structural precondition for staleness is verified by three JVM-only
tests in `Fix14StalenessReproductionTest.kt`:

1. `step1_canonicalPathDependsOnlyOnIdSubjectPaperSize` — asserts that two drafts with
   the same `id`, `subject`, and `paperSize` yield the same canonical filename
   regardless of `body`, `modifiedTime`, recipients, dates, sender. This is the
   identity contract that production uses; it is a property of `buildArtifactFilename`
   and is correct per the FIX12-B contract.

2. `step2_cachedOldPdfSurvivesDraftEditAtCanonicalPath` — writes a minimal PDF with an
   `OLD-PDF-MARKER` body to the canonical path, simulates an edit via `draft.copy(...)`
   (the same code path used by `WriteLetterActivity.kt:69-72`), then reads the file
   back. Asserts that (a) the canonical filename is unchanged, (b) the file still
   begins with `%PDF-` (would pass `PdfRenderer.isValidPdfFile`), and (c) the bytes
   still contain `OLD-PDF-MARKER`. This is byte-level evidence that the cached
   artifact at the canonical path is stale and would be served by
   `ensurePdfArtifact`.

3. `step3_contentFingerprintContractIsDeterministic` — shows that a deterministic
   fingerprint over the render-driving fields satisfies the basic hash invariant
   required by the recommended solution.

### Root cause

`PdfArtifactManager.buildArtifactFilename` produces a filename that depends only on
`(draftId, sanitizedSubject, paperSize)`. The `LetterDraft.copy(...)` operation
preserves `id` (verified by `SaveEditDeleteTest.sameDraftIdThroughWorkflow`) and
typically preserves `subject`. Editing a draft therefore never changes the canonical
filename. `PdfArtifactManager.isValidArtifact` checks only file existence, the
`%PDF-` header, and canonical-path match. None of these change as a result of an
edit. `PdfArtifactManager.ensurePdfArtifact` therefore short-circuits and returns
the cached PDF, which reflects the OLD content.

The defect is not in the preview pipeline (preview is recomputed correctly per
`PreviewActivity.kt:69-94`). The defect is not in the Save/Share/Print transport
layers (each correctly consumes `currentArtifact`). The defect is in the artifact
*identity* and *validity* contract of `PdfArtifactManager`: the cached file is
considered valid for any draft that maps to the same canonical filename, regardless
of whether its bytes reflect the current draft.

### Exact affected files / classes

- `app/src/main/kotlin/com/sulat/ai/share/PdfArtifactManager.kt` — `buildArtifactFilename`,
  `ensurePdfArtifact`, `isValidArtifact`. All three participate in the identity and
  validity contract.
- `app/src/main/kotlin/com/sulat/ai/data/persistence/PersistenceManager.kt` — does NOT
  invalidate cached artifacts on `saveDraft` or `deleteDraft`.
- `app/src/main/kotlin/com/sulat/ai/preview/PreviewActivity.kt:115-127` —
  `ensurePdfArtifact` is the funnel that surfaces the stale artifact to Save/Share/Print.
- `app/src/main/kotlin/com/sulat/ai/workflow/WriteLetterActivity.kt`,
  `LetterInfoActivity.kt`, `CreateLetterActivity.kt`, `DateSelectionActivity.kt` —
  all call `PersistenceManager.saveDraft` but none call `PdfArtifactManager.deleteArtifact`.
- `app/src/main/kotlin/com/sulat/ai/workflow/SavedLettersActivity.kt:168-183` —
  `confirmDelete` calls `PersistenceManager.deleteDraft` but does not clean up
  cached artifacts.

### Evidence

Direct source citations:
- `PdfArtifactManager.kt:25-32` — filename formula uses only `(id, subject, paperSize)`.
- `PdfArtifactManager.kt:51-95` — `ensurePdfArtifact` short-circuits on `isValidArtifact`.
- `PdfArtifactManager.kt:101-116` — `isValidArtifact` checks only existence, `%PDF-`, path.
- `LetterModel.kt:39-50` — `LetterDraft.id` is a stable `UUID.randomUUID()` and is
  preserved by `copy()`.
- `WriteLetterActivity.kt:69-77` — Continue button copies draft (preserves id),
  saves, then navigates to PreviewActivity with the same draft id.
- `PreviewActivity.kt:115-127` — `ensurePdfArtifact` is the funnel.

Search evidence:
- `grep "PdfArtifactManager\.deleteArtifact" app/src/main` returns only the declaration
  site (`PdfArtifactManager.kt:147`). Zero callers. There is no other invalidation
  mechanism (no version stamp in filename, no content hash check, no `modifiedTime`
  in filename, no timestamp check on the file).
- `grep "PersistenceManager\.saveDraft" app/src/main` returns 11 call sites across 4
  editor activities + `PersistenceManager.createDraft`. None call artifact invalidation.

Test evidence:
- `PdfArtifactManagerTest` — covers canonical path identity for all four paper sizes
  and for different draft ids. Does NOT cover content-based validity (this is the gap).
- `SaveEditDeleteTest.sameDraftIdThroughWorkflow` — confirms id is preserved across edits.
- `Fix14StalenessReproductionTest` (new, in this investigation commit) — byte-level
  reproduction of the staleness defect.

### Current artifact identity mechanism

`PdfArtifactManager.buildArtifactFilename(draft, paperSize)` returns:

```
Sulat-${safeDraftId}-${safeSubject}-${paperSize.name}.pdf
```

where:
- `safeDraftId = sanitizeForFilename(draft.id)` — strips path-traversal chars,
  control chars, dots; takes first 50 chars; falls back to "Letter" if empty.
- `safeSubject = sanitizeForFilename(draft.subject.ifEmpty { recipients[0].name ?: "Letter" })`.
- `paperSize.name` is one of `A4`, `ShortBond`, `Legal`, `LongBond`.

The full path is `cacheDir/shared/<filename>`. Validity is then checked by
`isValidArtifact` which only verifies file existence, file type, `%PDF-` header,
and canonical-path match.

The mechanism is **content-blind by design**: it produces the same filename for
any draft whose `(id, subject)` tuple is identical, regardless of `body`,
recipients, dates, sender, or `modifiedTime`. This is the structural cause of the
staleness defect.

### Candidate solutions considered

The required invariants:

- I1: same draft + same paper size + same content/version → same artifact path.
- I2: changed draft content → old artifact must NEVER be treated as current.
- I3: different draft → different artifact.
- I4: different paper size → different artifact.

| # | Solution | I1 | I2 | I3 | I4 | Verdict |
|---|---|---|---|---|---|---|
| C1 | Put `modifiedTime` in the filename | FAIL | PASS | PASS | PASS | Reject: breaks I1; every edit renames. |
| C2 | Stamp `modifiedTime` in a sidecar file; check in `isValidArtifact` | PASS | PASS | PASS | PASS | Reject: clock-dependent; loses to silent meta loss. |
| C3 | Stamp a SHA-256 content fingerprint in a sidecar file; check in `isValidArtifact` | PASS | PASS | PASS | PASS | Accept: content-based, deterministic. |
| C4 | Embed the fingerprint inside the PDF (e.g., as an `/Info` entry) | PASS | PASS | PASS | PASS | Reject: requires a PDF parser on the read path; harder to JVM-unit-test. |
| C5 | Explicit `deleteArtifact` from every editor and `confirmDelete` | PASS | PASS | PASS | PASS | Accept: minimal change; fragile to future call sites. |
| C6 | C5 + C3 (defense-in-depth) | PASS | PASS | PASS | PASS | Strong. |
| C7 | Invalidate inside `PersistenceManager.saveDraft` itself | PASS | PASS | PASS | PASS | Strong: single chokepoint; future-proof. |

### Recommended solution

**C3 (content-fingerprint sidecar) + C7 (invalidate inside `PersistenceManager.saveDraft`) +
clean up inside `PersistenceManager.deleteDraft`.**

This is a three-layer defense that costs almost nothing and covers every scenario:

1. **C7 (primary)**: `PersistenceManager.saveDraft` calls
   `PdfArtifactManager.deleteArtifact(context, draft, paperSize)` for every paper size,
   immediately after the JSON write succeeds. This invalidates the cache at the
   single chokepoint where all edits flow through. Cost: one extra method call
   per save. Benefit: the common case is fixed at the source.

2. **C3 (defense-in-depth)**: when `ensurePdfArtifact` writes a fresh PDF, it also
   writes a sidecar file `<filename>.fp` containing a SHA-256 fingerprint over
   the render-driving fields (`id, body, subject, greeting, sender.*, recipients.*,
   dates.*, isGenerated`). On subsequent `ensurePdfArtifact` calls, if the sidecar
   exists but the fingerprint does not match, treat the artifact as invalid and
   regenerate. If the sidecar is missing, treat as invalid (force regeneration).
   Cost: one extra file read + one SHA-256 on the rare path. Benefit: any
   future caller that bypasses `PersistenceManager.saveDraft` cannot leak a stale
   artifact.

3. **delete cleanup**: `PersistenceManager.deleteDraft` calls
   `PdfArtifactManager.deleteArtifact(context, draft, paperSize)` for every paper
   size after the JSON write succeeds. This addresses the orphan-PDF-after-delete
   finding (severity MEDIUM in FIX13) and prevents sensitive content from
   persisting after a user-initiated delete.

### Why the recommended solution is the safest

- **Deterministic and content-aware**: SHA-256 is well-understood, deterministic,
  and depends only on the fields that `LetterTemplateEngine.buildLayout` actually
  consumes. Drift between the fingerprint set and the layout inputs would
  either over-invalidate (regenerate unnecessarily; correct, just slower) or
  under-invalidate (real defect; prevented by the sidecar read step).
- **Single chokepoint for invalidation**: putting the delete call in
  `PersistenceManager.saveDraft` means every existing and future editor activity
  gets invalidation for free. No activity-level edits required for the primary fix.
- **Defense-in-depth via the sidecar**: even if a future caller bypasses
  `PersistenceManager.saveDraft` (or if the delete call is lost due to a
  write-failure race), `isValidArtifact` will detect the staleness and force
  regeneration.
- **No breaking changes to existing tests**: the canonical filename contract is
  preserved; same content → same filename + same sidecar → valid. The
  `differentDraftIdsAllSamePropertiesDifferentPath`,
  `sameDraftSamePaperSizeSamePath`, `sameDraftDifferentPaperSizeDifferentPath`,
  and `allFourPaperSizesProduceUniqueFilenames` tests in `PdfArtifactManagerTest`
  continue to hold because the filename formula is unchanged.
- **No new dependencies**: SHA-256 is provided by `java.security.MessageDigest`
  which is already used elsewhere in the Android stdlib.
- **No network, API, cloud, AI, or analytics dependency** added.
- **No second PDF pipeline** introduced. `PdfArtifactManager` remains the
  canonical artifact funnel. `LetterTemplateEngine`, `PdfContentCalculator`,
  `PdfRenderer`, `SaveHelper`, `ShareHelper`, `PrintHelper` are all unchanged
  in the fix scope. The only edits are: (a) two method calls in
  `PersistenceManager.saveDraft` / `deleteDraft`, (b) one new sidecar file
  write inside `PdfArtifactManager.ensurePdfArtifact` (after the PDF write), and
  (c) one sidecar read inside `PdfArtifactManager.isValidArtifact` (after the
  existing checks).

### Test strategy

A two-tier regression test plan:

1. **JVM unit tests** (pure JVM, no Android Context):
   - `artifactInvalidatedAfterDraftEditAtPersistenceLayer`: write a draft,
     simulate `ensurePdfArtifact`, simulate an edit, simulate `saveDraft`, assert
     the canonical artifact is gone or fingerprint mismatches.
   - `artifactInvalidatedAfterDraftDeleteAtPersistenceLayer`: same pattern for
     `deleteDraft`.
   - `fingerprintIsStableAcrossEquivalentDrafts`: two drafts with identical
     render-driving fields produce identical fingerprints.
   - `fingerprintChangesWhenBodyChanges`: confirms content-sensitivity.
   - `fingerprintChangesWhenRecipientsChange`: same for recipient edits.
   - `fingerprintChangesWhenSenderChanges`: same for sender edits.
   - `missingSidecarForcesRegeneration`: if sidecar is absent but PDF is present,
     artifact is treated invalid.

2. **Existing tests must continue to pass**:
   - `PdfArtifactManagerTest` — all 25+ canonical-identity tests.
   - `SaveHelperTest`, `ShareHelperTest`, `PrintHelperTest` — filename and
     validation tests.
   - `PersistenceManagerTest`, `SaveEditDeleteTest`, `WorkflowTest` — round-trip
     and edit-flow tests.

3. **Optional instrumentation tests** (later, on-device):
   - Save → kill app → reopen → Share must produce current content. Currently
     this scenario is FAIL; after the fix it must be PASS.

### Risks

- **Sidecar drift**: if the fingerprint input set is later changed without
  updating both sides of the contract, fingerprints may stop matching and the
  system would over-invalidate (regenerate on every Preview). This is harmless
  (correctness preserved; just more rendering) but should be guarded by a
  regression test on every change.
- **OS eviction of the sidecar without the PDF**: if the OS evicts only the
  sidecar file, `isValidArtifact` treats the PDF as invalid and regenerates.
  This is correct behavior (defensive), at the cost of one extra render.
- **OS eviction of the PDF without the sidecar**: `isValidArtifact` returns false
  on `!artifact.exists()`, so this is already handled.
- **Future migration of the persistence layer**: if a future change introduces
  a new write path that bypasses `PersistenceManager.saveDraft`, the C7 layer
  is bypassed but the C3 sidecar still detects staleness. The C3 layer is the
  safety net.
- **Clock regression / system clock change**: if any future change relied on
  `modifiedTime` for invalidation, a backwards clock change could create stale
  PDFs. The recommended solution does NOT use `modifiedTime` for invalidation,
  so this risk is avoided entirely.
- **Test flakiness**: SHA-256 over a `List<LetterDate>` requires a stable
  iteration order. `LetterDraft.dates` is `List<LetterDate>` — Kotlin lists are
  ordered. The fingerprint must enumerate in a deterministic order. We can
  enforce this with a regression test.

### Whether production code was intentionally left unchanged

YES. This commit contains ONLY:
1. `app/src/test/java/com/sulat/ai/share/Fix14StalenessReproductionTest.kt` — a
   new JVM-only test file. Does NOT touch production.
2. `repo.md` — this report.

No Gradle, build configuration, dependency, manifest, workflow, Android
framework usage, or production source file was modified. The reproduction test
file is INTENDED TO BE DELETED (or rewritten as a permanent regression test) in
the subsequent FIX14-B implementation commit. It is purely investigative.

### Final decision / recommendation

**Recommend FIX14-B** to apply the C3 + C7 + delete-cleanup solution above,
as a single focused commit:

- Edit `PersistenceManager.saveDraft` to call `PdfArtifactManager.deleteArtifact`
  for every `PaperSize` after the JSON write succeeds.
- Edit `PersistenceManager.deleteDraft` to call `PdfArtifactManager.deleteArtifact`
  for every `PaperSize` after the JSON write succeeds.
- Edit `PdfArtifactManager.ensurePdfArtifact` to write a sidecar fingerprint file
  alongside the PDF on successful generation.
- Edit `PdfArtifactManager.isValidArtifact` to read the sidecar and verify the
  fingerprint matches the supplied draft; if sidecar absent or mismatched, return
  false.
- Add regression tests in `app/src/test/java/com/sulat/ai/share/PdfArtifactManagerTest.kt`
  and/or a new `PdfArtifactFingerprintTest.kt`.
- Promote the temporary `Fix14StalenessReproductionTest.kt` to permanent tests
  (or delete it if equivalent coverage already exists in the new regression tests).

All existing tests must continue to pass. GitHub Actions must remain GREEN.

### FIX14 verdict

Investigation complete. Reproduction confirmed. **Recommended fix documented and
ready for a separate FIX14-B implementation commit.** Production code intentionally
unchanged in this investigation commit.

---
