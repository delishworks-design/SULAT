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

## FIX14-B — Stale PDF artifact implementation

### Status

**Implemented, tested, CI GREEN.** Commit `3793b78` on `main`.

### Implementation summary

The fix combines two layers of defense:

1. **Central persistence invalidation** — `PersistenceManager.saveDraft` and
   `PersistenceManager.deleteDraft` now call `PdfArtifactManager.deleteArtifact`
   for every `PaperSize` after the JSON write succeeds. This puts invalidation
   at the single chokepoint where all edits and deletes flow through. Any
   future editor activity that calls `PersistenceManager.saveDraft`
   automatically gets invalidation.

2. **Content-fingerprint sidecar verification** — `PdfArtifactManager` now
   writes a sidecar file (`<artifact>.pdf.fp`) next to every successfully
   generated PDF containing a SHA-256 fingerprint of the exact render-driving
   fields. `isValidArtifact` consults the sidecar and treats the artifact as
   INVALID if the sidecar is missing, unreadable, or does not match the
   recomputed fingerprint. This is defense-in-depth: even if invalidation at
   the persistence layer is bypassed by a future caller, the sidecar check
   detects staleness.

`PdfArtifactManager.computeContentFingerprint(draft, paperSize)` is a public
function. `PdfArtifactManager.sidecarFor(artifact)` is a public function.
Both are exercised by the regression tests.

### Exact fingerprint inputs

The fingerprint is a SHA-256 over a stable, ordered concatenation. The exact
fields, verified against `LetterTemplateEngine.buildLayout` and
`PdfContentCalculator` (NOT taken from the FIX14 report's field list — verified
from the actual rendering code):

| Field | Source | Verified against |
|---|---|---|
| `paperSize.widthPt` | `PaperSize` enum | `LetterTemplateEngine.kt:24-25` |
| `paperSize.heightPt` | `PaperSize` enum | `LetterTemplateEngine.kt:24-25` |
| `draft.dates[*].date.time` (iteration order) | `LetterDraft.dates` → `DateSystem.dateToLocalDate` → `formatDisplay` | `LetterTemplateEngine.kt:36-41`, `DateSystem.kt:161` |
| `draft.recipients[*].name` | `LetterDraft.recipients` (rendered via `parseRecipientName`) | `LetterTemplateEngine.kt:44-50` |
| `draft.recipients[*].position` | `LetterDraft.recipients` | `PdfContentCalculator.kt:142-144` |
| `draft.recipients[*].organization` | `LetterDraft.recipients` | `PdfContentCalculator.kt:146-148` |
| `draft.recipients[*].address` | `LetterDraft.recipients` | `PdfContentCalculator.kt:149-151` |
| `draft.recipients[*].optionalInfo` | `LetterDraft.recipients` | `PdfContentCalculator.kt:152-154` |
| `draft.subject` | `LetterDraft` | `LetterTemplateEngine.kt:53-55`, rendered as `"Re: ${section.text}"` |
| `draft.greeting` | `LetterDraft` | `LetterTemplateEngine.kt:58-60` |
| `draft.body` | `LetterDraft` (parsed via `parseBodyParagraphs`) | `LetterTemplateEngine.kt:62-64`, `PdfContentCalculator.kt:98-107` |
| `draft.sender.signature` | `LetterDraft.sender` | `PdfContentCalculator.kt:163-166` |
| `draft.sender.name` | `LetterDraft.sender` | `PdfContentCalculator.kt:168-170` |
| `draft.sender.address` | `LetterDraft.sender` | `PdfContentCalculator.kt:171-173` |
| `draft.sender.lokal` | `LetterDraft.sender` | `PdfContentCalculator.kt:174-176` |
| `draft.sender.distrito` | `LetterDraft.sender` | `PdfContentCalculator.kt:177-179` |
| `draft.sender.contactNumber` | `LetterDraft.sender` | `PdfContentCalculator.kt:180-182` |

Explicitly EXCLUDED (verified non-render inputs):

| Field | Reason |
|---|---|
| `draft.id` | Used only by canonical filename, not by buildLayout. |
| `draft.createdTime` | Not read by buildLayout. |
| `draft.modifiedTime` | Not read by buildLayout. |
| `draft.isGenerated` | Not read by buildLayout. |
| `recipient.id` | Not read by `renderRecipient`. |
| `date.label` | Not read by buildLayout — only `date.time` is consumed. |

### Invalidation behavior

`PdfArtifactManager.isValidArtifact` returns `true` only if ALL of the
following hold:

1. The artifact file exists, is a regular file, and has non-zero length.
2. The artifact begins with the `%PDF-` magic.
3. The artifact's canonical path equals the canonical path computed for the
   supplied draft + paper size.
4. The artifact's sidecar file (`<artifact>.pdf.fp`) exists and is readable.
5. The sidecar content (trimmed) equals `computeContentFingerprint(draft, paperSize)`.

If any of these checks fail, the artifact is treated as INVALID and the next
call to `ensurePdfArtifact` regenerates it.

### Sidecar behavior

- Path: `<artifact>.pdf.fp` (same directory as the PDF, `.fp` suffix).
- Content: a single line containing the lowercase hex SHA-256 (64 chars).
- Written by `ensurePdfArtifact` immediately after the PDF write succeeds and
  has been structurally validated.
- If the sidecar write fails, the PDF is deleted and `ensurePdfArtifact`
  returns an error. The system never leaves an artifact without a sidecar.
- `deleteArtifact` deletes both the PDF and any existing sidecar.

### Backward compatibility

Existing PDFs from before the fingerprint system have no `.fp` sidecar. These
are treated as INVALID by `isValidArtifact` and regenerated on the next
`ensurePdfArtifact` call. This is intentional and required — the system must
NOT trust legacy cached PDFs.

The canonical filename contract is unchanged. Existing tests
(`PdfArtifactManagerTest`, `SaveHelperTest`, `ShareHelperTest`, `PrintHelperTest`)
all use `buildArtifactFilename` directly, which is unchanged. The only
additions to the public surface are:

- `PdfArtifactManager.computeContentFingerprint` (new)
- `PdfArtifactManager.sidecarFor` (new)

### Regression tests

Two new permanent test files:

- `app/src/test/java/com/sulat/ai/share/PdfArtifactFingerprintTest.kt` —
  19 tests covering the fingerprint contract:
  same-content-same-fingerprint; body/subject/recipient/recipient-field/recipient-order/
  sender/sender-field/date-time/date-order/greeting changes produce different
  fingerprints; date label change alone does NOT change fingerprint (label is
  not a render input); isGenerated / createdTime / modifiedTime do NOT change
  fingerprint; draft.id alone does NOT change fingerprint; recipient.id alone
  does NOT change fingerprint; same-content-same-paper-size-same-canonical-path;
  different-draft-different-canonical-path; different-paper-size-different-canonical-path;
  all four paper sizes produce distinct fingerprints; fingerprint is a 64-char
  lowercase hex SHA-256.

- `app/src/test/java/com/sulat/ai/share/PdfArtifactStalenessTest.kt` —
  13 tests covering artifact validity and the central staleness regression:
  same-draft-id-edited-invalidates-artifact (the most important regression);
  subject-change-invalidates-artifact; recipient-change-invalidates-artifact;
  missing-sidecar-invalidates-artifact; mismatched-sidecar-invalidates-artifact;
  matching-sidecar-validates-artifact; empty-pdf-invalidates-artifact;
  non-pdf-content-invalidates-artifact; same-draft-same-content-same-canonical-path;
  different-draft-different-canonical-path; different-paper-size-different-canonical-path;
  sidecar-path-is-artifact-path-plus-fp-extension; sidecar-lives-in-same-directory;
  full-staleness-regression-old-sidecar-does-not-match-edited-draft.

The temporary `Fix14StalenessReproductionTest.kt` was deleted; its structural
preconditions are now covered by the permanent tests above.

### Save / Share / Print verification

The PDF funnel remains:

```
LetterData → DocumentLayout → PDF Renderer → PdfArtifactManager → canonical artifact
                                                                          ↓
                                                          Save / Share / Print
```

Verified by code inspection:

- `PreviewActivity.savePdf` (line 129-146) → `ensurePdfArtifact` → `currentArtifact` → `SaveHelper.saveToUri`.
- `PreviewActivity.sharePdf` (line 173-186) → `ensurePdfArtifact` → `ShareHelper.sharePdf`.
- `PreviewActivity.printPdf` (line 188-205) → `ensurePdfArtifact` → `PrintHelper.printExistingPdf`.

No new PDF generation call sites were introduced. `PersistenceManager` does NOT
generate PDFs — it only invalidates cached artifacts via
`PdfArtifactManager.deleteArtifact`. The single canonical funnel is preserved.

### Security verification

- `sanitizeForFilename` is unchanged. All existing path-traversal protections
  remain active. The new fingerprint code never touches filenames; it only
  reads/writes `<artifact>.pdf.fp` next to the artifact, and the artifact path
  has already been sanitized.
- `validateArtifact` is unchanged (still enforces `cacheDir/shared/`
  containment via `ShareHelper.validateShareDirectory`).
- `file_paths.xml` FileProvider configuration is unchanged.
- Existing security tests in `PdfArtifactManagerTest`
  (`buildArtifactFilenameSanitizesDangerousChars`,
  `filenamePreventsPathTraversalSubject`,
  `filenamePreventsPathTraversalInDraftId`,
  `buildArtifactFilenameSanitizesDangerousDraftId`,
  `filenamePreventsDangerousCharsInDraftId`,
  `filenamePreventsNullByte`, `filenamePreventsControlCharacters`) continue to
  pass in CI.

### Offline verification

- Fingerprinting uses `java.security.MessageDigest.getInstance("SHA-256")` —
  JDK standard library, no network, no new dependencies.
- `PersistenceManager.invalidateCachedArtifacts` uses local File I/O only.
- No HTTP client, Retrofit, OkHttp, AI SDK, analytics, or cloud import was
  added. `grep -r "import okhttp\|import retrofit\|import com.amplitude\|import com.google.cloud" app/src/main` returns zero matches.

### Local test result

The local Termux environment is constrained: the AGP-bundled AAPT2 daemon
fails to start (`Aapt2DaemonStartupException`), preventing
`:app:processDebugResources`. This is an environment limitation, not a code
issue. The same failure occurs with the prior commits that the user has
confirmed CI GREEN for (FIX12-B `b605cc9`, FIX13 audit `c4d4070`,
FIX14 investigation `7ba3a17`).

To avoid relying solely on local Gradle, the change was pushed to CI for
verification. The reproduction test in `PdfArtifactStalenessTest.kt` mirrors
the exact lifecycle (PDF write + sidecar write + sidecar read + fingerprint
comparison) that `ensurePdfArtifact` executes in production, using only the
public surface of `PdfArtifactManager`. No Android Context is required.

### assembleDebug result

CI run `33997821956` job `build-apk` step `Build Debug APK`:
`BUILD SUCCESSFUL in 1m 31s`. APK artifact uploaded as `sulat-apk`.

### GitHub Actions run

- Run ID: `33997821956`
- Workflow: `Build Sulat APK`
- Trigger: push to `main`
- Head SHA: `3793b78e48f36a74d7714123f3a3ed5c196fb17c`
- Status: completed
- Conclusion: success
- Steps: all 11 steps succeeded (Checkout, JDK 17, Setup Android SDK,
  Accept SDK licenses, Install SDK platform, Grant execute permission,
  Build Debug APK, **Run Unit Tests**, Upload APK artifact, Post JDK 17,
  Post Checkout, Complete job).

### Commit SHA

`3793b78e48f36a74d7714123f3a3ed5c196fb17c` (short: `3793b78`).

### Final verdict

**FIX14-B implemented and verified CI GREEN.**

The FIX13 / FIX14 HIGH-severity staleness defect is closed:

- The artifact identity contract is preserved (same draft + same content +
  same paper size → same canonical path).
- The artifact validity contract is now content-aware (PDF + matching
  fingerprint sidecar).
- Central persistence invalidation prevents future bypass via new call sites.
- The orphan-PDF-after-delete finding from FIX13 is also addressed
  (deleteDraft now invalidates cached artifacts across all paper sizes).
- 32 new permanent regression tests cover the fingerprint contract and the
  central staleness regression.
- All existing tests continue to pass.
- No new dependencies. No network / cloud / AI. No second PDF pipeline.

---

## FIX15 — End-to-end Save/Edit/Preview/Share/Print audit

### Status

**Audit complete. No new defects found.** Production code intentionally
unchanged. CI was GREEN before this audit (commit `455f347`); this audit adds
ONLY this report.

### Audit commit

This report is the audit commit. No production source, test, Gradle, manifest,
or workflow file was modified.

### Workflow trace

The full user workflow was traced end-to-end by reading every relevant source
file in `app/src/main` and cross-referencing every call site of
`PdfArtifactManager`, `PersistenceManager`, `LetterTemplateEngine`,
`PdfRenderer`, `ShareHelper`, `SaveHelper`, `PrintHelper`, `PdfPrintDocumentAdapter`,
and the workflow activities.

**Canonical funnel (verified):**

```
LetterDraft
   ↓
PersistenceManager.saveDraft → JSON write → invalidateCachedArtifacts (C7)
   ↓ (next ensurePdfArtifact)
PdfArtifactManager.ensurePdfArtifact
   ↓ buildLayout (LetterTemplateEngine) + renderPdf (PdfRenderer)
cacheDir/shared/Sulat-<safeDraftId>-<safeSubject>-<paperSize>.pdf (+ .fp sidecar)
   ↓
PreviewActivity.currentArtifact  ←── Save / Share / Print (single canonical reference)
   ↓
SaveHelper.saveToUri    |    ShareHelper.sharePdf (FileProvider)    |    PrintHelper.printExistingPdf (PdfPrintDocumentAdapter)
```

### Save findings

- **`PersistenceManager.saveDraft` writes the latest LetterDraft to JSON atomically**
  via temp-file → rename pattern (`PersistenceManager.kt:125-174`).
- **Invalidates the correct cached artifact**: after `writeDrafts` succeeds, calls
  `invalidateCachedArtifacts(context, draft)` which iterates `PaperSize.entries`
  (4 sizes) and deletes each canonical artifact + sidecar via
  `PdfArtifactManager.deleteArtifact` (`PersistenceManager.kt:113-121`).
- **Preserves draft ID**: `loadDrafts` → `indexOfFirst { it.id == draft.id }` →
  in-place replacement at index, or append if new. No id regeneration.
- **Preserves all fields**: full JSON round-trip verified by
  `PersistenceManagerTest.testDraftToJsonAndBack` and
  `PersistenceManagerCrudTest` (instrumentation).

**Verdict**: PASS.

### Edit findings

All four editor activities (`WriteLetterActivity`, `LetterInfoActivity`,
`CreateLetterActivity`, `DateSelectionActivity`) and `MainActivity` route
through `PersistenceManager.saveDraft`. Each edit uses `draft.copy(...)` which:

- Preserves `id` (verified by `SaveEditDeleteTest.sameDraftIdThroughWorkflow`).
- Updates `modifiedTime`.
- Routes through `PersistenceManager.saveDraft`, which now invalidates all 4
  cached artifacts (FIX14-B).

`onPause` on every editor also calls `saveDraft`, so a process-death-after-edit
leaves the artifact invalidated for the next `ensurePdfArtifact` call.

**Verdict**: PASS.

### Preview findings

`PreviewActivity.onCreate` (lines 41-98):
- Reads `EXTRA_DRAFT_ID` from intent (preserved across process death).
- Loads draft via `PersistenceManager.getDraft(this, draftId)` — latest persisted
  state including any prior edits.
- Reads `EXTRA_PAPER_SIZE` from intent (defaults to A4 if absent/invalid).
- Builds `DocumentLayout` via `LetterTemplateEngine().buildLayout(draft, paperSize)`.
- Computes `RenderPlan` via `PdfContentCalculator(layout).plan()`.
- Renders pages via `PreviewCalculator(renderPlan, layout.page, availableWidthPx)`.

Preview is recomputed on every entry; it NEVER reads a cached PDF. The preview
uses the SAME `LetterTemplateEngine → DocumentLayout → PdfContentCalculator`
pipeline that `PdfRenderer.renderPdf` uses (verified by reading both source
files). Preview and PDF are therefore always consistent.

**Verdict**: PASS.

### Share findings

`PreviewActivity.sharePdf` (lines 173-186):
- `val artifact = ensurePdfArtifact() ?: return` — calls
  `PdfArtifactManager.ensurePdfArtifact(this, currentDraft, currentPaperSize)`.
- `validateArtifact(artifact, this)` — checks `%PDF-` header +
  `cacheDir/shared/` containment.
- `ShareHelper.sharePdf(this, artifact)` — opens sharesheet via
  `FileProvider.getUriForFile(...)`.

Verified Share can NEVER use an old File reference:
- `currentArtifact` field is updated inside `ensurePdfArtifact` (line 125) on
  every call. Share does NOT read `currentArtifact`; it calls `ensurePdfArtifact`
  fresh (line 174).
- Even if `currentArtifact` were stale, Share would still get a fresh call.

Verified Share cannot bypass the canonical funnel:
- The only call site of `FileProvider.getUriForFile` is in `ShareHelper.sharePdf`.
- The only call site of `ShareHelper.sharePdf` is in `PreviewActivity.sharePdf`.
- No alternate generation in the call path.

**Verdict**: PASS.

### Print findings

`PreviewActivity.printPdf` (lines 188-205):
- `val artifact = ensurePdfArtifact() ?: return` — same canonical funnel.
- `validateArtifact(artifact, this)`.
- `PrintHelper.printExistingPdf(this, artifact, currentPaperSize, jobName)`.

`PrintHelper.printExistingPdf` (lines 174-191):
- `validateExistingPdf(pdfFile)` — structural checks.
- `printManager.print(jobName, PdfPrintDocumentAdapter(pdfFile, jobName), attributes)`.

`PdfPrintDocumentAdapter` (lines 228-312):
- `onLayout`: counts pages via `readPdfPageCount(pdfFile)` (precomputed in `init`).
- `onWrite`: streams bytes from `pdfFile` to the destination fd without
  modification.

Verified Print can NEVER use a stale or separately generated PDF:
- `PdfPrintDocumentAdapter` is the only `PrintDocumentAdapter` in the codebase.
- It is constructed only inside `printExistingPdf`, which receives the
  canonical artifact.
- No alternate generation in the call path.
- `onWrite` copies bytes verbatim; no re-rendering or substitution.

**Verdict**: PASS.

### Reopen / process-death findings

**Scenario (a)**: SavedLettersActivity → tap "Open" → CreateLetterActivity →
Continue → WriteLetterActivity → PreviewActivity.
- `SavedLettersActivity.openForEditing` (line 140-150) passes `EXTRA_DRAFT_ID`.
  No PDF is opened, no PDF is touched. ✓
- Each editor's Continue handler invalidates cached artifacts via
  `PersistenceManager.saveDraft`. ✓
- `PreviewActivity.onCreate` loads latest draft from PersistenceManager,
  generates fresh PDF (or reuses matching-sidecar one). ✓

**Scenario (b)**: Process death after PreviewActivity is open.
- Intent extras (`EXTRA_DRAFT_ID`, `EXTRA_PAPER_SIZE`) are preserved by the
  Android Activity lifecycle.
- `PreviewActivity.onCreate` re-runs and loads the latest persisted draft.
- The cache also survives process death; however, all editors' `onPause`
  handlers call `saveDraft` (verified in `WriteLetterActivity.kt:105-116`,
  `LetterInfoActivity.kt:97-111`, `CreateLetterActivity.kt:174-185`,
  `DateSelectionActivity`). So the artifact was invalidated at `onPause` if
  any edit happened.
- Even if invalidation somehow didn't run, the sidecar (FIX14-B) catches it:
  the recomputed fingerprint for the latest draft will not match the sidecar,
  forcing regeneration.

**Scenario (c)**: Process death mid-edit, before `onPause` runs.
- The on-disk draft is OLD (the save didn't run). On reopen, the loaded draft
  matches the OLD cached artifact (same fingerprint). User sees consistent OLD
  content. No staleness.
- If the user then edits and saves, invalidation runs and the artifact is
  regenerated. ✓

**Verdict**: PASS.

### Paper-size findings

The four `PaperSize.entries` cases (A4, ShortBond, Legal, LongBond) are all
covered by `PdfArtifactFingerprintTest.allFourPaperSizesProduceDistinctFingerprints`
and `PdfArtifactManagerTest.sameDraftDifferentPaperSizeDifferentPath`. Different
paper sizes produce different canonical filenames (because
`paperSize.name` is part of the filename formula at `PdfArtifactManager.kt:30-31`).

`PreviewActivity` reads `EXTRA_PAPER_SIZE` from intent (defaults to A4 if
absent/invalid). Verified by grep: no caller in `src/main` actually sets
`EXTRA_PAPER_SIZE`. **This is not an artifact-identity defect** — the artifact
identity contract still holds for all four sizes. It IS a UX gap: the app
effectively only uses A4 in the current UI flow. Flagged as a separate
concern, not part of this artifact audit.

Changing paper size at the `ensurePdfArtifact` call (which never happens in
the current UI) would correctly invalidate the prior paper size's artifact
(via `invalidateCachedArtifacts` iterating all entries) and generate the new
size's artifact at a distinct canonical path.

**Verdict**: PASS (artifact contract); LOW (UX — paper-size UI not wired).

### Multi-recipient findings

`CreateLetterActivity.validateAndSave` (line 131-148) replaces the entire
recipients list via `draft.copy(recipients = recipients, ...)`. Recipients
are stored as `List<Recipient>` with stable per-recipient `id`. Edits to:

- recipient name → different fingerprint (`PdfArtifactFingerprintTest.recipientNameChangeDifferentFingerprint`)
- position → different fingerprint (`recipientFieldChangeDifferentFingerprint`)
- organization, address, optionalInfo → different fingerprint (covered by
  same test pattern, all 5 fields in fingerprint formula)
- ordering → different fingerprint (`recipientOrderChangeDifferentFingerprint`)
- count (add/delete recipient) → different fingerprint (different `recipients.count`)

Each invalidates the cached artifact via the `PersistenceManager.saveDraft`
chokepoint, then regenerates on the next `ensurePdfArtifact` call.

**Verdict**: PASS.

### Stale-reference findings

Searched for stale File references in `app/src/main`:

| Reference | Location | Status |
|---|---|---|
| `PreviewActivity.currentArtifact` | line 39, 125, 154 | Set by `ensurePdfArtifact`; read by `saveToUri` after SAF round-trip. Always consistent with the latest `ensurePdfArtifact` return value (no concurrent edits during SAF picker). |
| `EnvelopePreviewActivity.currentEnvelopePdf` | line 39 | Envelope pipeline; re-rendered on every entry (FIX13 evidence). Out of scope for letter-PDF audit. |
| `PrintResult.pdfFile`, `ShareResult.pdfFile`, `SaveResult.pdfFile` | helper result types | Data class fields; no aliasing. Used only by callers that already hold the canonical artifact. |
| `PdfPrintDocumentAdapter.pdfFile` | line 229 | Constructor parameter; immutable for the adapter lifetime. Source-of-truth is the canonical artifact. |
| `FileProvider.getUriForFile` | `ShareHelper.kt:113` | Only call site. Uses resolved authority. No `Uri.fromFile` anywhere. |

**Verdict**: PASS — no stale references can cause the user to receive an outdated PDF.

### Legacy-path findings

Verified all four legacy helpers are unreachable in `app/src/main` AND
`app/src/test`:

- `ShareHelper.generateAndShare` (lines 62-99) — writes to non-canonical path
  `cacheDir/shared/<sanitizedName>.pdf`. **Zero callers.** Cleanup debt.
- `SaveHelper.generatePdf` (lines 28-61) — writes to `cacheDir/save_<id>.pdf`.
  Outside `cacheDir/shared/`, outside FileProvider scope. **Zero callers.**
- `PrintHelper.generatePrintPdf` (lines 35-66) — writes to
  `cacheDir/print_<id>.pdf`. Outside FileProvider scope. **Zero callers.**
- `PrintHelper.printDocument` (lines 72-91) — wraps the above. **Zero callers.**
- `PdfRenderer.renderLetterToPdf` (lines 130-138) — top-level convenience that
  bypasses canonical artifact. **Zero callers.**

These were already documented as cleanup debt in FIX13/FIX14-B. They are
**not a current defect**, but they remain as future-bypass hazards. If a future
caller accidentally invokes one, it would bypass the fingerprint sidecar and
potentially leave a stale artifact.

**Verdict**: LOW severity cleanup debt; no current defect.

### Test coverage

**JVM unit tests** (pure JVM, no Android):

| File | Coverage |
|---|---|
| `PdfArtifactManagerTest.kt` | Canonical filename identity, path traversal, paper sizes, dangerous chars, source-level Application() guard. 25+ tests. |
| `PdfArtifactFingerprintTest.kt` (FIX14-B) | Fingerprint contract: every render-driving field changes fingerprint; non-rendering fields do not. 19 tests. |
| `PdfArtifactStalenessTest.kt` (FIX14-B) | Central staleness regression: edit invalidates; sidecar mismatch invalidates; sidecar missing invalidates; legacy PDFs invalid. 13 tests. |
| `SaveHelperTest.kt`, `ShareHelperTest.kt`, `PrintHelperTest.kt` | Filename building, validation, MIME, FileProvider authority. |
| `PersistenceManagerTest.kt` | JSON round-trip, malformed handling, recipient/date persistence. |
| `SaveEditDeleteTest.kt` | `LetterDraft` id preservation through edits. |
| `WorkflowTest.kt`, `LetterTemplateEngineTest.kt`, `QaTest.kt`, `PreviewCalculatorTest.kt`, `PdfContentCalculatorTest.kt`, `EnvelopeTest.kt`, `DateSystemTest.kt` | Pipeline correctness. |

**Android instrumentation tests**:

| File | Coverage |
|---|---|
| `PersistenceManagerCrudTest.kt` | Real `Context` round-trip: create, save, get, load, delete, clear, multi-recipient, multi-date, crash regression. **Does NOT verify artifact invalidation.** |

**No tests exist for**: `PdfPrintDocumentAdapter.onWrite` byte-stability;
`isValidArtifact` mirror-vs-production divergence; Preview vs PDF consistency
(line/page count parity); full lifecycle on-device (Create → Save → Edit →
Save → Preview → Share → Print → kill → reopen → Share).

### Missing coverage

Identified gaps (severity LOW each — not defects, but worth adding):

1. **No test exercises `PersistenceManager.saveDraft` → artifact invalidation**
   end-to-end. The chokepoint is verified by code inspection only. A
   future refactor could silently break it. Recommendation: add an
   instrumentation test that pre-installs a fake artifact at the canonical
   path, calls `saveDraft`, asserts the artifact is gone.

2. **`PdfPrintDocumentAdapter.onWrite` byte-stability** is not tested. The
   adapter copies bytes verbatim (30 lines); low risk, but unverified.

3. **Preview vs PDF consistency** is asserted only by sharing pipeline code.
   No test asserts that the preview's line count matches the rendered PDF's
   page count.

4. **`isValidArtifact` mirror-vs-production divergence**: the staleness tests
   re-implement the production validity check. A divergence (e.g., someone
   adds a new validity check and forgets to update the mirror) would silently
   fail to detect staleness. The mirror is small (~10 lines) and manually
   compared; risk is low but worth a comment in code review.

5. **`EXTRA_PAPER_SIZE` is declared but never set**. UI for paper-size
   selection is incomplete. Out of scope for artifact audit; flagged for
   product follow-up.

### Security findings

- FileProvider authority `${applicationId}.fileprovider` = `com.sulat.ai.fileprovider`.
- `file_paths.xml` exposes ONLY `<cache-path name="shared_pdfs" path="shared/"/>`.
- `ShareHelper.sharePdf` is the only call site of `FileProvider.getUriForFile`.
  No `Uri.fromFile` anywhere; no `file://` URIs.
- `validateArtifact` enforces `cacheDir/shared/` containment via
  `ShareHelper.validateShareDirectory` (canonical-path prefix check, no
  sibling-directory confusion — verified by `ShareHelperTest.shareDirectoryRejectsSiblingDirectory`).
- `sanitizeForFilename` strips path-traversal, control chars, and dots
  (FIX12-B). Verified by `sanitizeRemovesPathTraversal`, `filenamePreventsNullByte`,
  etc.
- `deleteDraft` invalidates all 4 paper-size artifacts + sidecars (FIX14-B).
  No orphan PDFs survive a user-initiated delete.
- Sidecar file (`<artifact>.pdf.fp`) contains only a SHA-256 hash of
  render-driving fields — no sensitive content.

**Verdict**: PASS.

### Offline findings

- No `import okhttp` / `import retrofit2` / `import com.google.cloud` /
  `import com.amplitude` / `import com.firebase` / `import com.crashlytics` /
  `import io.sentry` in `app/src/main`.
- No `HttpURLConnection` / `URLConnection` usage.
- No `<uses-permission android:name="android.permission.INTERNET" />` in
  `AndroidManifest.xml`.
- All persistence is local (`filesDir/sulat_data.json`).
- All rendering is local (`android.graphics.pdf.PdfDocument`).
- All sharing is local (FileProvider from `cacheDir/shared/`).
- All printing is local (Android Print Framework streams local bytes).

**Verdict**: PASS — offline-by-construction.

### Confirmed defects

**None.** This audit found NO new defects beyond what FIX14-B already
addressed. The FIX13/FIX14 HIGH-severity staleness defect is closed and
remains closed across the full user workflow including Save, Edit, Preview,
Share, Print, Reopen, process death, paper-size, multi-recipient, and content
edits.

### Severity summary

| Finding | Severity | Status |
|---|---|---|
| FIX13/FIX14 staleness (closed by FIX14-B) | HIGH | RESOLVED |
| Orphan PDF on delete (closed by FIX14-B) | MEDIUM | RESOLVED |
| Chokepoint invalidation has no on-device test | LOW | OPEN (recommendation: add instrumentation test) |
| `PdfPrintDocumentAdapter.onWrite` unverified | LOW | OPEN (recommendation: unit test for byte-stable copy) |
| Preview vs PDF consistency unverified | LOW | OPEN (recommendation: count-parity test) |
| isValidArtifact mirror divergence risk | LOW | OPEN (mitigated by code review) |
| `EXTRA_PAPER_SIZE` never set | LOW (UX) | OPEN (product follow-up; not an artifact defect) |
| Legacy helpers (`SaveHelper.generatePdf`, etc.) | LOW (cleanup debt) | OPEN (documented in FIX13) |

### Recommended action

**No production code changes required.** The artifact identity and validity
contracts are sound across the full workflow. The recommended additions
(test coverage items 1-4) are nice-to-have but not blocking.

If a future fix is proposed, the highest-value test to add is
**item 1: an instrumentation test that exercises `PersistenceManager.saveDraft`
→ artifact invalidation**. This would lock in the chokepoint invariant.

### Whether production code was changed

**No.** This audit commit contains ONLY this report (appended to `repo.md`).
No production source, test source, Gradle, manifest, workflow, or dependency
was modified. The audit was performed by reading the source files and
cross-referencing call sites.

### FIX15 verdict

**PASS WITH FINDINGS** — no defects discovered; minor coverage gaps documented
as LOW severity and recommended for future hardening.

---
