# Nebula Package Removal — Design Document

**Date:** 2026-04-14
**Status:** Draft (codex-reviewed, approved)
**Scope:** Artemis server + client cleanup (single repository)
**Repository:** `ls1intum/Artemis`
**Blocks:** `2026-04-14-youtube-transcription-design.md`

## Background

The Nebula microservice was a separate transcription service maintained under `ls1intum/edutelligence`. It has been fully removed from edutelligence. On the Artemis side, PR #12459 (`Iris: Nebula to Pyris migration`, merged into 9.0) migrated the transcription **data flow** away from Nebula and onto Pyris, but **did not remove the Java package** under `src/main/java/de/tum/cit/aet/artemis/nebula/`.

A follow-up PR #12463 (`refactor: remove Nebula transcription from lecture processing pipeline`) was opened specifically to remove the leftover code. It was closed on 2026-04-07 based on the author's (incorrect) belief that PR #12459 had already handled the removal. The package therefore still ships in 9.0.1.

The only code in `nebula/` that remains functional post-9.0 is `TumLiveApi` + `TumLiveService` (called by `PyrisWebhookService` to resolve TUM Live HLS playlists) and the single REST endpoint `GET /api/nebula/video-utils/tum-live-playlist` (called by the Angular client to drive playlist-based video unit rendering). Everything else — `AbstractNebulaApi`, `NebulaEnabled`, the module feature system entry, the `nebulaRestTemplate` bean, architecture tests, i18n bundle, YAML config blocks, documentation — is dead weight or actively misleading.

PR #12463's diff is outdated: most files it proposed to remove (`LectureTranscriptionApi`, `LectureTranscriptionService`, `NebulaTranscriptionPollingScheduler`, and related DTOs) no longer exist on develop — PR #12459 removed them directly. Its file list is not a useful starting point for today's cleanup.

## Goals

- Delete all dead Nebula-related code on the Artemis side.
- Relocate the live TUM Live integration into its own top-level `tumlive/` module, consistent with the existing convention (`athena/`, `iris/`, `hyperion/`, `atlas/`).
- Drop the `NebulaEnabled` Spring `Condition` and its backing property — `TumLiveService` already has a correct runtime guard based on `artemis.tum-live.api-base-url` presence.
- Rename the single exposed endpoint to match the new module.
- Leave zero runtime references to "Nebula" in the Artemis codebase (except the unrelated Netflix Nebula gradle-lint plugin).

## Non-Goals

- Changing any user-visible behavior for TUM Live video units.
- Changing how `PyrisWebhookService` resolves video URLs (same contract, just updated imports).
- Regenerating or editing `documentation/static/img/artemis-intelligence.svg` — the embedded "Nebula" text in the SVG is deferred to a separate asset-regeneration effort.
- Recreating architecture tests for the new `tumlive/` module — with three files, convention-by-code-review is sufficient.
- Adding new features or rethinking the TUM Live integration surface.

## Architecture Overview

The cleanup has four orthogonal concerns. Each can be verified independently during implementation:

1. **Relocation** — move `TumLiveApi`, `TumLiveService`, and the REST resource into a new `tumlive/` top-level module; update imports at the sole cross-module consumer (`PyrisWebhookService`).
2. **Deletion** — remove dead code (`NebulaEnabled`, `nebulaRestTemplate` bean, 4 architecture tests, `nebula.json` i18n, `MODULE_FEATURE_NEBULA` + helper methods, YAML `artemis.nebula.*` blocks, setup docs, maintainer table row, jacoco threshold entry, the obsolete root-level plan file).
3. **Endpoint rename** — `/api/nebula/video-utils/tum-live-playlist` → `/api/tumlive/playlist`; update the single Angular client caller and four test-spec URL literals. Safe (no external consumers; session-auth endpoint).
4. **Prose cleanup** — seven Javadoc/comment edits in the `lecture/` module that reference Nebula; update i18n `featureToggles.json` strings that mention Nebula; update the `FeatureToggle.LectureContentProcessing` doc link in the admin toggle component.

## Repository: Artemis (single-repo change)

### New `tumlive/` module structure

```
src/main/java/de/tum/cit/aet/artemis/tumlive/
├── api/
│   └── TumLiveApi.java              (moved from nebula/api/)
├── service/
│   └── TumLiveService.java          (moved from nebula/service/)
└── web/
    └── TumLiveResource.java         (renamed from NebulaTranscriptionResource,
                                      moved from nebula/web/)
```

- Package declaration changes: `package de.tum.cit.aet.artemis.nebula.*` → `package de.tum.cit.aet.artemis.tumlive.*` in all three files.
- `AbstractNebulaApi` is deleted. It is an empty abstract base with only one consumer (`TumLiveApi`). `TumLiveApi` can extend `AbstractApi` directly.
- `@Conditional(NebulaEnabled.class)` is removed from all three files.
- Authorization annotations and existing runtime behavior are preserved: `TumLiveResource` keeps `@EnforceAtLeastStudent`; `TumLiveService` keeps its internal no-op guard (returns empty/null when `artemis.tum-live.api-base-url` is blank).

### REST endpoint rename

| Before | After |
|--------|-------|
| `@RequestMapping("api/nebula/")` on `NebulaTranscriptionResource` | `@RequestMapping("api/tumlive/")` on `TumLiveResource` |
| `GET /api/nebula/video-utils/tum-live-playlist` | `GET /api/tumlive/playlist` |

Same query parameters, same response body. Client caller at `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.ts:107-109` updates to the new URL. Four test specs get the URL string updated:

- `attachment-video-unit.service.spec.ts:377,393`
- `attachment-video-unit.component.spec.ts:86,282,344,372,558`

### Java deletions

- `nebula/api/AbstractNebulaApi.java`
- `nebula/config/NebulaEnabled.java`
- Remaining empty `nebula/` directory removed after all moves.
- `core/config/RestTemplateConfiguration.java` — delete the `nebulaRestTemplate()` bean + its imports (no production consumer; only mocked in the test base, which is cleaned up alongside).

### Constants / feature-toggle removals

- `Constants.java:445-448` — delete `MODULE_FEATURE_NEBULA = "nebula"`
- `Constants.java:530-533` — delete `NEBULA_ENABLED_PROPERTY_NAME = "artemis.nebula.enabled"`
- `ArtemisConfigHelper.java:9,168-176,258-260` — delete `isNebulaEnabled()` + import + registration inside `getEnabledFeatures()`
- `ModuleFeatureService.java:109-116` — delete `isNebulaEnabled()`

### YAML configuration

Remove the `artemis.nebula.*` blocks from:

- `src/main/resources/config/application.yml:137-141`
- `src/main/resources/config/application-local.yml:36-39`
- `src/main/resources/config/application-buildagent.yml:34-35`
- `src/main/resources/config/application-artemis.yml:121-124`
- `src/test/resources/config/application.yml:114-118`

Keep `artemis.tum-live.api-base-url` (`application-local.yml:40-41`) — still used by `TumLiveService`.

### Client TypeScript

- `src/main/webapp/app/app.constants.ts:60` — delete `export const MODULE_FEATURE_NEBULA = 'nebula'`
- `src/main/webapp/app/app.constants.ts:81` — remove `typeof MODULE_FEATURE_NEBULA` from the `ModuleFeature` union
- `src/main/webapp/app/core/admin/features/admin-feature-toggle.component.ts:22` — remove `MODULE_FEATURE_NEBULA` import
- `src/main/webapp/app/core/admin/features/admin-feature-toggle.component.ts:113,160` — remove the nebula entry from `displayedModuleFeatures` and its doc-link map entry
- `src/main/webapp/app/core/admin/features/admin-feature-toggle.component.ts:132` — **delete** the `FeatureToggle.LectureContentProcessing` entry from the doc-link map entirely (currently points to `#nebula-setup-guide` which won't exist after docs cleanup; other feature toggles do not universally have doc links, so dropping is cleaner than inventing a replacement anchor)

### i18n

- Delete: `src/main/webapp/i18n/en/nebula.json`, `src/main/webapp/i18n/de/nebula.json` (orphan bundles — zero consumers; the `internalNebulaError` key has no callsites).
- Edit: `src/main/webapp/i18n/en/featureToggles.json:104-107,181-185` and `de/featureToggles.json:104-107,181-185` — remove the `nebula` module-feature block; rewrite the `LectureContentProcessing.description` and `LectureContentProcessing.disableWarning` strings to drop Nebula mentions (English + German per AGENTS.md i18n-parity rule).

### Tests — deleted

- `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaCodeStyleArchitectureTest.java`
- `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaRepositoryArchitectureTest.java`
- `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaResourceArchitectureTest.java`
- `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaServiceArchitectureTest.java`

### Tests — moved / renamed

- `src/test/java/de/tum/cit/aet/artemis/lecture/NebulaTranscriptionResourceIntegrationTest.java` → `src/test/java/de/tum/cit/aet/artemis/tumlive/TumLivePlaylistResourceIntegrationTest.java` (update endpoint URL in mocks and assertions).
- `src/test/java/de/tum/cit/aet/artemis/lecture/TumLiveServiceTest.java` → `src/test/java/de/tum/cit/aet/artemis/tumlive/TumLiveServiceTest.java` (update imports to new service package).

### Test base classes

- `AbstractSpringIntegrationIndependentTest.java:54,70,123-130,168-170`:
  - Remove `artemis.nebula.enabled=true` from `@TestPropertySource`.
  - Remove `@MockitoBean(name="nebulaRestTemplate") RestTemplate nebulaRestTemplate` (the bean is being deleted).
  - Keep `@MockitoBean TumLiveService tumLiveService` but update the import path to the new `tumlive/service/` location.
  - Update `resetSpyBeans` references accordingly.
- `AbstractSpringIntegrationLocalCILocalVCTest.java:98` — remove `artemis.nebula.enabled=false` from `@TestPropertySource`.

### Tests — methods / entries removed

- `ArtemisConfigHelperTest.java:49-50` — delete `testNebulaProperty()`.
- `ModuleFeatureInfoContributorTest.java:34,55` — remove `NEBULA_ENABLED_PROPERTY_NAME` and `MODULE_FEATURE_NEBULA` from expected arrays.

### Javadoc / comment edits (prose only)

| File:Line | Current | Replacement |
|-----------|---------|-------------|
| `lecture/domain/LectureTranscription.java:41` | `"external transcription job ID from the transcription service (e.g., Nebula)"` | `"external transcription job ID from the transcription service"` |
| `lecture/domain/Lecture.java:35` | `"automatic lecture transcription using Nebula"` | `"automatic lecture transcription"` |
| `lecture/domain/LectureUnitProcessingState.java:20` | `"transcription generation (Nebula) and ingestion into Pyris/Iris"` | `"transcription generation and ingestion into Pyris/Iris"` |
| `lecture/service/LectureService.java:172` | `"Clean up external processing resources (cancel Nebula jobs, delete from Pyris)"` | `"Clean up external processing resources (delete from Pyris)"` |
| `lecture/service/LectureUnitService.java:155` | `"cancels any ongoing content processing jobs (Nebula transcription, Pyris ingestion)"` | `"cancels any ongoing content processing jobs (Pyris ingestion)"` |
| `lecture/api/LectureContentProcessingApi.java:15,19` | Javadoc references `"nebula, iris"` and cross-module circular-dep note mentioning Nebula | Mention only `iris`; trim the Nebula-specific part of the note |
| `lecture/web/LectureUnitResource.java:237` | `"If processing is not enabled (Iris/Nebula disabled)..."` | `"If processing is not enabled (Iris disabled)..."` |

### Documentation

- `documentation/docs/admin/extension-services.mdx:357-438` — delete the entire "Nebula Setup Guide" section (~80 lines: `### Nebula Setup Guide`, `### Nebula Service Deployment`, `#### Connecting Artemis and Nebula`, verification checklist).
- `documentation/docs/admin/artemis-intelligence.mdx:13,28,61,85` — remove the four Nebula references (service list entry, table row, bullet description, repo link).
- `README.md:144` — delete the maintainer table row `| Nebula | Patrick Bassner |`.

### Root-level file deletion

- `LECTURE_PROCESSING_AUTOMATION_PLAN.md` — delete (confirmed obsolete by user).

### Build / coverage config

- `gradle/jacoco.gradle:3,23` — remove the `"nebula": [INSTRUCTION: 0.836, CLASS: 0]` entry from the module coverage thresholds map; update the comment that references "merging Nebula PRs". Do **not** add a `"tumlive"` threshold entry unless the module-threshold policy requires one; the three-file module is below the practical measurement threshold.

### Things deliberately untouched

- `build.gradle:25` `id "nebula.lint"` — the Netflix Nebula gradle-lint plugin, unrelated to the Nebula microservice. Keep.
- `documentation/static/img/artemis-intelligence.svg` — embedded "Nebula" text is out of scope (Non-Goal).
- `artemis.tum-live.api-base-url` property and all references to TUM Live — untouched.

## Implementation ordering

The entire cleanup **must land as a single atomic commit** on a single PR. Do not split into intermediate states where the YAML property blocks (`artemis.nebula.enabled`) or `@TestPropertySource` entries are removed before `ArtemisConfigHelper.isNebulaEnabled()` and its call in `getEnabledFeatures()`. Reason: `isNebulaEnabled()` uses `getPropertyOrExitArtemis()` which throws if the property is missing — an intermediate state with property removed but helper kept would fail to start the Spring context.

A single atomic commit sidesteps the ordering concern entirely: CI only ever sees the final consistent state.

## Deployment and Backward Compatibility

- **Single PR** targeting `develop`. No transitional deployment steps.
- **Endpoint rename is safe due to existing client-side 404-tolerant fallback.** The Angular client at `attachment-video-unit.service.ts:109` wraps the call in `.pipe(catchError(() => of(undefined)))`. When the playlist resolves to `undefined` (either because the URL is not a TUM Live URL, or because the endpoint returns an error), the component falls through to the iframe branch (`attachment-video-unit.component.html:56-62`). During a rolling deployment, any user with a cached pre-rename JS bundle who hits the new server will get a 404 on the old URL and see the iframe fallback — **degraded but not broken**. A browser refresh fetches the new bundle and restores full functionality. Acceptable degradation for the deploy window; no deprecated alias needed.
- **No database migrations.** No entity fields are affected; the `LectureTranscription` entity only had Nebula mentioned in Javadoc.
- **No Spring profile changes.** The `artemis.nebula.enabled` property is deleted outright; no profile named `nebula` exists. Downstream config files in private deployments that still have `artemis.nebula.enabled` set will be ignored silently by Spring — not a breaking change, but recipients should be notified to remove the stale property.
- **No external API consumers.** The renamed endpoint uses session auth and is only called from the Artemis Angular client. No API-key or third-party consumers.
- **`/info` actuator output changes.** The module feature list no longer includes `nebula`. Any deployment monitoring that asserts on that specific entry would need updating — unlikely, but worth flagging.

## Verification

- `./gradlew spotlessCheck checkstyleMain modernizer -x webapp` — passes.
- `./gradlew test -x webapp` (full server suite) — passes; the updated test base class must not leave dangling references to `nebulaRestTemplate` or `artemis.nebula.enabled`.
- `npm run vitest:run` (client tests) — passes with the renamed endpoint URL in specs.
- Grep `[Nn]ebula` across `src/` and `documentation/` after the PR should return only:
  - `build.gradle` — `nebula.lint` (Netflix plugin)
  - `documentation/static/img/artemis-intelligence.svg` — embedded text (deferred by Non-Goals)
  - This spec itself and the YouTube spec
- Manual smoke test on a test server: a TUM Live video unit still resolves its playlist, the custom HLS player + transcript sidebar still render, and the admin feature toggle page loads without errors.

## Risks

- **Rolling-deploy transient degradation.** During a rolling deploy, users with a cached pre-rename JS bundle will see TUM Live video units render via iframe (no transcript sidebar) until they refresh the browser. The failure mode is graceful (no crash) because of the existing `catchError` fallback. Documented and accepted rather than mitigated with a deprecated alias.
- **Private-deployment config bleed.** Artemis deployments at other universities may have `artemis.nebula.enabled` or `artemis.nebula.url` set in their local config. After this PR, those properties are simply ignored. Low-risk; flag in release notes.
- **Documentation link fan-out.** External documentation or Slack threads may link to anchors inside the deleted `#nebula-setup-guide` section. Acceptable loss given the service is gone.
- **Monitoring / dashboards.** Any external monitoring that specifically checks for the `nebula` entry in `/info` actuator output will need updating. Not expected to exist in practice.
- **Unknown consumers of the `nebulaRestTemplate` bean.** We assert it's unused in production. Implementation should grep once more at merge time to confirm no consumer has been added between spec-writing and merge.

## Unblocks

- `2026-04-14-youtube-transcription-design.md` — the YouTube transcription spec currently places `YouTubeService` + `YouTubeApi` in the `nebula/` package (matching PR #12503's location). Once this removal lands, the YouTube spec will be updated to place those files under `tumlive/` or under a new `youtube/` module (to be decided when the YouTube spec is revisited).
