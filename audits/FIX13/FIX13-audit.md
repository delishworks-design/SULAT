# FIX13 — Independent End-to-End Artifact Audit

**Scope**: Evidence-based end-to-end audit of whether `PdfArtifactManager` can serve a stale PDF after a same-id draft is edited.

**Method**: Read every production source file referenced by the question (`PdfArtifactManager.kt`, `LetterDraft`, `PersistenceManager`, `DocumentLayout`, `PdfRenderer`, `LetterTemplateEngine`, `PreviewActivity`, `ShareHelper`, `SaveHelper`, `PrintHelper`, `EnvelopePreviewActivity`, all workflow edit activities, `AndroidManifest.xml`, `file_paths.xml`). Read every relevant test (`PdfArtifactManagerTest`, `SaveHelperTest`, `ShareHelperTest`, `PrintHelperTest`, `PersistenceManagerTest`, `SaveEditDeleteTest`, `WorkflowTest`, `QaTest`). Cross-reference call sites for every public PDF-generation entry point.

---

## Architectural facts (verified by reading source)

| Fact | Source |
|---|---|
| Canonical artifact path is `cacheDir/shared/Sulat-<safeDraftId>-<safeSubject>-<paperSize>.pdf` | `PdfArtifactManager.kt:31` |
| Filename uses `draft.id`, `draft.subject` (or first recipient name), and `paperSize.name` only — **does not use `modifiedTime` or any content hash** | `PdfArtifactManager.kt:25-32` |
| `LetterDraft.id` is a stable `UUID.randomUUID()` set at creation and never regenerated; `copy()` preserves it | `LetterModel.kt:40`; `SaveEditDeleteTest.newDraftIdStableThroughCopy` |
| All edits use `draft.copy(...)` which preserves `id` | `WriteLetterActivity.kt:69-72`, `LetterInfoActivity.kt:59-63`, `CreateLetterActivity.kt:142-146`, `DateSelectionActivity.kt` (saveDraft calls) |
| After every edit, `PersistenceManager.saveDraft` is called, **but `PdfArtifactManager.deleteArtifact` is never called** | grep over `app/src/main` shows zero callers of `deleteArtifact` outside its declaration |
| `isValidArtifact` checks ONLY: (1) file exists, (2) is file, (3) length > 0, (4) starts with `%PDF-`, (5) canonical path matches expected | `PdfArtifactManager.kt:101-116` |
| `ensurePdfArtifact` reuses the cached file if `isValidArtifact` returns true | `PdfArtifactManager.kt:58-60` |
| `PreviewActivity.savePdf/sharePdf/printPdf` all funnel through the single `ensurePdfArtifact` entry point and reuse `currentArtifact` | `PreviewActivity.kt:115-205` |
| Preview screen uses the same `LetterTemplateEngine` → `DocumentLayout` → `PdfContentCalculator` → `RenderPlan` as the PDF pipeline | `PreviewActivity.kt:69-94` and `PdfRenderer.kt:47-49` |
| `cacheDir` survives process death; only OS-eviction or app-data-clear removes it | Android contract |
| `PreviewActivity` does NOT persist `currentArtifact` or `currentDraft` across process death; only the launching `Intent`'s `EXTRA_DRAFT_ID` is preserved | `PreviewActivity.kt` (no `onSaveInstanceState`) |
| `EnvelopePreviewActivity` always re-renders the envelope PDF on entry (overwrites file unconditionally); envelope filename includes today's date → new file each visit | `EnvelopePreviewActivity.kt:209-239`; `EnvelopeFilename.kt:25-43` |

---

## Scenario-by-scenario analysis

### A. Create draft → generate PDF → generate again (same session)
- **Expected**: second call returns cached artifact without re-rendering.
- **Actual**: `ensurePdfArtifact` returns the existing file via `isValidArtifact` returning `true`. Correct.
- **Verdict**: **PASS**

### B. Create draft → generate PDF → edit content → generate again
- **Expected behavior**: PDF must reflect the new content.
- **Actual implementation behavior**:
  1. User edits via `WriteLetterActivity.btnContinue` (or `LetterInfoActivity` / `CreateLetterActivity` / `DateSelectionActivity`).
  2. `draft.copy(body = newBody, modifiedTime = now)` preserves `id`. `PersistenceManager.saveDraft` persists.
  3. Activity navigates to `PreviewActivity` with same `EXTRA_DRAFT_ID`.
  4. `PreviewActivity.onCreate` loads the updated draft.
  5. `PreviewActivity` is rendered from the new draft (preview shows new content).
  6. User taps Save / Share / Print → `ensurePdfArtifact` is called → `isValidArtifact` checks file-existence + `%PDF-` header + canonical path match → all pass (file still on disk, header intact, path matches because `id`+`paperSize` unchanged) → returns the **stale PDF containing the OLD content**.
- **Can the artifact become stale?** **YES, deterministically.** Every edit to a draft whose `id` is preserved (which is every edit, by `LetterDraft` invariants) leaves a stale PDF at the canonical path.
- **Can Save/Share/Print use the wrong PDF?** **YES** — all three consume the same `currentArtifact` returned by `ensurePdfArtifact`. Save writes the stale bytes via `SaveHelper.saveToUri`; Share shares it via `ShareHelper.sharePdf`; Print streams it via `PdfPrintDocumentAdapter.onWrite`.
- **Does Preview and PDF use the same DocumentLayout?** Yes — preview is recomputed from the new draft (`PreviewActivity.kt:69-94`), but PDF is NOT recomputed; it serves the cache.
- **Evidence**:
  - `WriteLetterActivity.kt:56-78` — Continue button edits body, saves, navigates to PreviewActivity with same draftId.
  - `PdfArtifactManager.kt:51-95` — `ensurePdfArtifact` consults `isValidArtifact` and short-circuits on success.
  - `PdfArtifactManager.kt:101-116` — `isValidArtifact` does no content comparison.
  - grep `app/src/main` for `PdfArtifactManager.deleteArtifact` returns only the declaration in `PdfArtifactManager.kt:147`; zero callers.
- **Verdict**: **FAIL** (stale PDF served after same-id edit)

### C. Create draft → generate A4 → generate Legal
- **Expected**: different paper sizes must produce different artifacts.
- **Actual**: `buildArtifactFilename` includes `paperSize.name` → distinct filenames → distinct files. `isValidArtifact` confirms path match against the requested paper size, so the A4 file is not returned for a Legal request. Correct.
- **Evidence**:
  - `PdfArtifactManager.kt:31` — `paperSizeSuffix = paperSize.name`.
  - `PdfArtifactManagerTest.sameDraftDifferentPaperSizeDifferentPath` — asserts 6 distinct pairs.
- **Verdict**: **PASS**

### D. Two different drafts with identical content → same paper size
- **Expected**: distinct artifacts (different ids).
- **Actual**: `buildArtifactFilename` includes `safeDraftId` (sanitized id). Two different ids → two different filenames. `differentDraftIdsAllSamePropertiesDifferentPath` test enforces this. Correct.
- **Evidence**:
  - `PdfArtifactManager.kt:29-31` — `safeDraftId = sanitizeForFilename(draft.id)`.
  - `PdfArtifactManagerTest.differentDraftIdsAllSamePropertiesDifferentPath` — asserts.
- **Verdict**: **PASS**

### E. Save → kill app → reopen → Share
- **Expected behavior**: when the user opens the same draft and shares, the PDF reflects the latest persisted content.
- **Actual implementation behavior**:
  - On relaunch, `cacheDir/shared/<canonical>.pdf` is still on disk (cacheDir survives process death unless evicted by OS).
  - `PreviewActivity.onCreate` reads `EXTRA_DRAFT_ID` from intent → loads draft → calls `ensurePdfArtifact` → `isValidArtifact` returns true (file exists, valid header, path matches) → **stale PDF returned**.
  - Even if the user re-edited the draft between sessions, the cached PDF still contains the OLD content from the last session that generated it.
- **Stale?** **YES** if the draft was edited between sessions. (If the draft was never edited after the cache was created, the cache is correct.)
- **Evidence**:
  - `PreviewActivity.kt:41-98` — `onCreate` re-reads draft ID from intent extras (preserved across process death), loads draft from `PersistenceManager`, but does not invalidate cache.
  - `PdfArtifactManager.kt:101-116` — `isValidArtifact` does not consult draft content.
- **Verdict**: **FAIL** (stale PDF reused on cold restart after any prior edit)

### F. Save → kill app → reopen → Print
- **Same defect as E**: print uses `printExistingPdf` with `currentArtifact`, which comes from `ensurePdfArtifact`. Stale PDF printed.
- **Evidence**:
  - `PreviewActivity.kt:188-205` — `printPdf` → `ensurePdfArtifact` → `PrintHelper.printExistingPdf`.
  - `PrintHelper.kt:174-191` — `printExistingPdf` does not regenerate; consumes whatever PDF file is passed.
  - `PdfPrintDocumentAdapter.kt:264-306` — `onWrite` copies bytes without re-rendering.
- **Verdict**: **FAIL**

### G. Malicious draft ID / subject
- `sanitizeForFilename` strips `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`, `.`, control chars, and `..` is therefore impossible after sanitization (verified by tests `filenamePreventsPathTraversalInDraftId`, `filenamePreventsPathTraversalSubject`, `buildArtifactFilenameSanitizesDangerousDraftId`).
- `validateArtifact` rejects files outside `cacheDir/shared/`.
- FileProvider exposes only `cache-path name="shared_pdfs" path="shared/"` (`file_paths.xml`).
- **No security regression** in the artifact path. (Confirmed by FIX12-B tests passing on CI run `33970046626`.)
- **Evidence**:
  - `PdfArtifactManager.kt:152-161` — sanitization rules.
  - `ShareHelper.kt:47-56` — `validateShareDirectory` containment check.
  - `AndroidManifest.xml:11-19` and `file_paths.xml` — FileProvider configuration.
- **Verdict**: **PASS**

### H. Offline / no network
- The pipeline is fully local: `PersistenceManager` reads/writes JSON in `filesDir`, `PdfRenderer` uses `android.graphics.pdf.PdfDocument`, FileProvider serves from local cache, Android Print Framework prints a local file.
- **No network, API, AI, or cloud dependency** — verified by reading all imports and declarations. Audit confirms the original "no network/API/AI/cloud" constraint is intact.
- **Evidence**:
  - `PdfRenderer.kt:5` — `import android.graphics.pdf.PdfDocument`.
  - `PersistenceManager.kt` — uses only `Context.filesDir`, `org.json`, and `java.io`.
  - No HTTP clients, no Retrofit, no OkHttp, no AI/network imports anywhere in `app/src/main`.
- **Verdict**: **PASS**

---

## Specific attention items

1. **Artifact validation** — `isValidArtifact` does NOT compare the artifact against the current draft's content or version. This is the root cause of the staleness defect.

2. **Draft mutation** — `LetterDraft.copy(...)` preserves `id` by design. Combined with (1), any edit leaves the cached PDF addressable and "valid".

3. **Artifact invalidation** — There is NO call site of `PdfArtifactManager.deleteArtifact` in `app/src/main`. `deleteArtifact` exists but is unused. There is no other invalidation mechanism (no version stamp in filename, no content hash check, no modifiedTime in filename, no timestamp check on the file).

4. **`currentArtifact` lifecycle** — set inside `PreviewActivity.ensurePdfArtifact` (line 125) and used by Save/Share/Print. Stale across (a) same-session edit, (b) cross-session reuse after process death, (c) any subsequent navigation back to the same draft.

5. **Activity recreation** — `PreviewActivity` does not save/restore `currentDraft`, `currentArtifact`, or `currentPaperSize` in `onSaveInstanceState`. On config change Android preserves the Activity instance by default, so `currentArtifact` survives a rotation. On process death the Intent extras survive but `currentArtifact` does not; `onCreate` reconstructs draft from PersistenceManager but re-uses whatever PDF is on disk at the canonical path.

6. **Process death** — Same as (5): Intent extras preserved, `cacheDir` preserved, stale PDF at canonical path is served.

7. **Paper-size changes** — Different paper sizes produce different canonical filenames; switching paper size correctly invalidates by filename collision. No defect here.

8. **FileProvider** — Only `cacheDir/shared/` is exposed. The artifact lives there. No leak to other paths. `validateArtifact` enforces `cacheDir/shared/` containment.

9. **PrintDocumentAdapter** — `PdfPrintDocumentAdapter.onWrite` reads the supplied `pdfFile` and copies bytes to the print fd without re-rendering. So whatever `currentArtifact` it receives is what gets printed. Stale artifact → stale printout.

10. **Legacy PDF helpers** — `SaveHelper.generatePdf`, `PrintHelper.generatePrintPdf`, `PrintHelper.printDocument`, `ShareHelper.generateAndShare` all exist but **no callers in `src/main` or `src/test`**. They write to non-canonical paths (`cacheDir/save_<id>.pdf`, `cacheDir/print_<id>.pdf`, `cacheDir/shared/<sanitizedName>.pdf`) which would silently leak outside the canonical-artifact invariant if any future caller uses them. Not currently a defect, but a future hazard. Not refactored in this audit phase.

---

## Other findings (not the primary question but evidence-based)

- **Orphan PDF on delete** — **Severity: MEDIUM**. `SavedLettersActivity.confirmDelete` calls `PersistenceManager.deleteDraft` but never `PdfArtifactManager.deleteArtifact`. For each of the four paper sizes, a stale `Sulat-<id>-<safeSubject>-<size>.pdf` may remain in `cacheDir/shared/`. Sensitive content retention. Out of scope for the primary question.
- **`cacheDir/shared/` is never cleaned** — **Severity: LOW**. Files accumulate. Android may eventually evict under storage pressure but there is no application-level hygiene.

---

## Canonical-artifact invariant verification

- **same draft + same paper size = same artifact identity**: **PASS** (`PdfArtifactManagerTest.sameDraftSamePaperSizeSamePath`, `sameDraftIdSameSubjectSameDateSamePaperSizeSamePath`)
- **different draft + same paper size = different artifact identity**: **PASS** (`PdfArtifactManagerTest.differentDraftIdsSameSubjectSameDateSamePaperDifferentPath`, `differentDraftIdsDifferentSubjectDifferentPath`, `differentDraftIdsAllSamePropertiesDifferentPath`)
- **same draft + different paper size = different artifact identity**: **PASS** (`PdfArtifactManagerTest.sameDraftDifferentPaperSizeDifferentPath`, `allFourPaperSizesProduceUniqueFilenames`)

## Save / Share / Print funnel verification

- **Save → canonical artifact**: **PASS** — `PreviewActivity.savePdf` → `ensurePdfArtifact` → `currentArtifact` → `SaveHelper.saveToUri` (no regeneration).
- **Share → canonical artifact**: **PASS** — `PreviewActivity.sharePdf` → `ensurePdfArtifact` → `currentArtifact` → `ShareHelper.sharePdf` (no regeneration).
- **Print → canonical artifact**: **PASS** — `PreviewActivity.printPdf` → `ensurePdfArtifact` → `currentArtifact` → `PrintHelper.printExistingPdf` (no regeneration).

The funnel itself is correct (no competing generation paths are invoked from the Preview flow). The defect is that the cached artifact at the funnel's destination can be stale relative to the current draft.

---

## Defect

**Stale PDF served after editing a draft whose `id` is preserved.**

**Severity**: **HIGH** — the user can save, share, or print a PDF that does not match the visible preview. The preview is recomputed; the PDF is not. This is a data-integrity defect for the user-facing Save/Share/Print features.

**Exact code path**:
1. User edits draft → `WriteLetterActivity.kt:69-73` (or any sibling editor): `currentDraft.copy(body = body, modifiedTime = now)` → `PersistenceManager.saveDraft(this, updated)`.
2. Navigation to `PreviewActivity` with the same `draftId` (`WriteLetterActivity.kt:75-77`).
3. `PreviewActivity.ensurePdfArtifact` (`PreviewActivity.kt:115-127`) calls `PdfArtifactManager.ensurePdfArtifact(this, draft, paperSize)`.
4. `PdfArtifactManager.ensurePdfArtifact` (`PdfArtifactManager.kt:51-95`) calls `isValidArtifact(context, artifactFile, draft, paperSize)`.
5. `isValidArtifact` (`PdfArtifactManager.kt:101-116`) returns `true` because the stale file still exists, starts with `%PDF-`, and its path matches the canonical path (canonical path depends only on `draft.id` + `subject` + `paperSize.name`, none of which changed).
6. The stale PDF is returned and subsequently served by Save (`PreviewActivity.kt:148-171` → `SaveHelper.saveToUri`), Share (`PreviewActivity.kt:173-186` → `ShareHelper.sharePdf` via FileProvider), and Print (`PreviewActivity.kt:188-205` → `PrintHelper.printExistingPdf` → `PdfPrintDocumentAdapter.onWrite`).

**Minimal reproduction**:
- Draft with `id = "draft-stale"`, `body = "OLD"`, `subject = "Subject"`.
- Call `PdfArtifactManager.ensurePdfArtifact(context, draft, A4)` → writes `Sulat-draft-stale-Subject-A4.pdf` to `cacheDir/shared/`.
- Edit draft: `draft.copy(body = "NEW", modifiedTime = oldModifiedTime + 60_000L)`. (`id` preserved by `LetterDraft.copy`.)
- Call `PdfArtifactManager.ensurePdfArtifact(context, editedDraft, A4)` → returns the cached file containing the OLD body.
- Expected: a regenerated PDF containing "NEW".
- Actual: the OLD PDF is served.

**Evidence**:
- `SaveEditDeleteTest.sameDraftIdThroughWorkflow` confirms `LetterDraft.copy(...)` preserves `id`.
- `PdfArtifactManagerTest` proves canonical-path identity depends only on `(id, subject, paperSize)`.
- `isValidArtifact` source proves there is no content/version comparison.
- Zero callers of `PdfArtifactManager.deleteArtifact` in `app/src/main`.

---

## FIX13 Verdict

**FAIL**
