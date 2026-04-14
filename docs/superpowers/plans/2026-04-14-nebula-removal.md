# Nebula Package Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the residual `nebula/` Java package and all supporting configuration, i18n, docs, and test scaffolding from Artemis; relocate the still-live TUM Live integration into a new `tumlive/` top-level module; rename the public endpoint from `/api/nebula/video-utils/tum-live-playlist` to `/api/tumlive/playlist`.

**Architecture:** Three surviving files (`TumLiveApi`, `TumLiveService`, `TumLiveResource`) move under `src/main/java/de/tum/cit/aet/artemis/tumlive/` with updated package declarations and without the `@Conditional(NebulaEnabled.class)` guard (the service already has a correct runtime no-op guard on `artemis.tum-live.api-base-url`). Everything else Nebula-related is deleted. Because `ArtemisConfigHelper.isNebulaEnabled()` uses `getPropertyOrExitArtemis()` — which throws on missing property — YAML deletion and helper deletion must be in the same commit. The whole cleanup therefore lands as one atomic commit on a single PR targeting `develop`.

**Tech Stack:** Java 25, Spring Boot 3.5, Angular 21, Gradle 9.3, JUnit 6, Vitest.

---

## Reference Spec

Full design rationale: `docs/superpowers/specs/2026-04-14-nebula-removal-design.md`

## Working Assumptions

- Work happens on a dedicated feature branch off latest `develop`, single PR, single commit.
- Java 25 via sdkman: `JAVA_HOME="/Users/pat/.sdkman/candidates/java/25-tem"` when running Gradle.
- Gradle commands use `-x webapp` to skip the Angular build.
- No intermediate commits. Verification runs at the end against the complete change set.
- **The working tree is expected to be non-compiling between Task 2 and Task 13.** Tasks 2 and 3 move Java files and update `PyrisWebhookService`; Tasks 6, 7, 8 delete production code still referenced by the test base classes; Task 13 finally updates the test base classes to match. Do not attempt a full `gradle test` until Task 18 — intermediate greps in Tasks 2–17 are narrowly scoped (e.g., `src/main/` only) to avoid false positives from unfinished test-side edits.
- **Line numbers drift.** Whenever a task references "at ~line N" inside a file that the same task or an earlier task also modifies, treat the line number as a starting hint only and match by the exact substring or symbol given. The admin-feature-toggle component, both test base classes, and `ArtemisConfigHelper.java` are the highest-risk files for drift — do all edits in each of those files in a single pass, reading the file fully first.

## File Structure After Change

```text
src/main/java/de/tum/cit/aet/artemis/tumlive/          (NEW MODULE)
├── api/TumLiveApi.java                                (moved from nebula/api/)
├── service/TumLiveService.java                        (moved from nebula/service/)
└── web/TumLiveResource.java                           (renamed from NebulaTranscriptionResource)

src/test/java/de/tum/cit/aet/artemis/tumlive/          (NEW TEST MODULE)
├── TumLivePlaylistResourceIntegrationTest.java        (renamed from NebulaTranscriptionResourceIntegrationTest)
└── TumLiveServiceTest.java                            (moved from lecture/)

src/main/java/de/tum/cit/aet/artemis/nebula/           (DELETED IN ENTIRETY)
src/test/java/de/tum/cit/aet/artemis/nebula/           (DELETED IN ENTIRETY)
src/main/webapp/i18n/{en,de}/nebula.json               (DELETED)
LECTURE_PROCESSING_AUTOMATION_PLAN.md                  (DELETED — root-level obsolete plan)
```

---

## Task 1: Prepare the branch

**Files:** none

- [ ] **Step 1: Confirm starting state**

```bash
cd /Users/pat/projects/Artemis
git status
git fetch origin
git checkout develop
git pull --ff-only origin develop
```

Expected: clean tree, on develop, up to date with origin.

- [ ] **Step 2: Create feature branch**

```bash
git checkout -b chore/remove-nebula-package
```

- [ ] **Step 3: Record starting file inventory**

```bash
grep -rEl "[Nn]ebula" src/ documentation/ README.md build.gradle gradle/ 2>/dev/null | sort > /tmp/nebula-files-before.txt
wc -l /tmp/nebula-files-before.txt
```

Keep this list for the end-of-plan grep verification.

---

## Task 2: Create the new `tumlive/` module (package move, no logic change)

**Files:**
- Create: `src/main/java/de/tum/cit/aet/artemis/tumlive/api/TumLiveApi.java`
- Create: `src/main/java/de/tum/cit/aet/artemis/tumlive/service/TumLiveService.java`
- Create: `src/main/java/de/tum/cit/aet/artemis/tumlive/web/TumLiveResource.java`
- Delete (after copy): `src/main/java/de/tum/cit/aet/artemis/nebula/api/TumLiveApi.java`
- Delete (after copy): `src/main/java/de/tum/cit/aet/artemis/nebula/service/TumLiveService.java`
- Delete (after copy): `src/main/java/de/tum/cit/aet/artemis/nebula/web/NebulaTranscriptionResource.java`

- [ ] **Step 1: Move `TumLiveApi` with `git mv`**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/tumlive/api
git mv src/main/java/de/tum/cit/aet/artemis/nebula/api/TumLiveApi.java \
       src/main/java/de/tum/cit/aet/artemis/tumlive/api/TumLiveApi.java
```

- [ ] **Step 2: Rewrite `TumLiveApi.java`**

Edit `src/main/java/de/tum/cit/aet/artemis/tumlive/api/TumLiveApi.java`:
- Change `package de.tum.cit.aet.artemis.nebula.api;` → `package de.tum.cit.aet.artemis.tumlive.api;`
- Remove the `import` for `AbstractNebulaApi`
- Add `import de.tum.cit.aet.artemis.core.api.AbstractApi;`
- Change `extends AbstractNebulaApi` → `extends AbstractApi`
- Remove the `@Conditional(NebulaEnabled.class)` annotation and its import
- Remove the `import de.tum.cit.aet.artemis.nebula.service.TumLiveService;` if present, replace with `import de.tum.cit.aet.artemis.tumlive.service.TumLiveService;`

- [ ] **Step 3: Move `TumLiveService` with `git mv`**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/tumlive/service
git mv src/main/java/de/tum/cit/aet/artemis/nebula/service/TumLiveService.java \
       src/main/java/de/tum/cit/aet/artemis/tumlive/service/TumLiveService.java
```

- [ ] **Step 4: Rewrite `TumLiveService.java`**

Edit `src/main/java/de/tum/cit/aet/artemis/tumlive/service/TumLiveService.java`:
- Change `package de.tum.cit.aet.artemis.nebula.service;` → `package de.tum.cit.aet.artemis.tumlive.service;`
- Remove the `@Conditional(NebulaEnabled.class)` annotation and its import
- Keep the existing blank-check guard on `artemis.tum-live.api-base-url` verbatim
- **Do not touch HTTP-client wiring.** `TumLiveService` uses `RestClient.Builder`, not `@Qualifier("nebulaRestTemplate") RestTemplate`. No bean-qualifier swap is needed here; the `nebulaRestTemplate` bean is confirmed to have no non-test consumers and will be deleted in Task 7.

- [ ] **Step 5: Move + rename `NebulaTranscriptionResource` to `TumLiveResource`**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/tumlive/web
git mv src/main/java/de/tum/cit/aet/artemis/nebula/web/NebulaTranscriptionResource.java \
       src/main/java/de/tum/cit/aet/artemis/tumlive/web/TumLiveResource.java
```

- [ ] **Step 6: Rewrite `TumLiveResource.java`**

Edit `src/main/java/de/tum/cit/aet/artemis/tumlive/web/TumLiveResource.java`:
- Change `package de.tum.cit.aet.artemis.nebula.web;` → `package de.tum.cit.aet.artemis.tumlive.web;`
- Rename the class from `NebulaTranscriptionResource` to `TumLiveResource`
- Change the class-level `@RequestMapping("api/nebula/")` → `@RequestMapping("api/tumlive/")`
- Change the method-level path from `"video-utils/tum-live-playlist"` → `"playlist"`
- Remove the `@Conditional(NebulaEnabled.class)` annotation and its import
- Update the import for `TumLiveService` to `de.tum.cit.aet.artemis.tumlive.service.TumLiveService`
- Keep `@EnforceAtLeastStudent` and all other authorization/validation annotations verbatim
- Update any class-level Javadoc that says "Nebula" to "TUM Live"

- [ ] **Step 7: Verify the three files compile (structural check via IDE or grep)**

```bash
grep -n "package " src/main/java/de/tum/cit/aet/artemis/tumlive/api/TumLiveApi.java \
                   src/main/java/de/tum/cit/aet/artemis/tumlive/service/TumLiveService.java \
                   src/main/java/de/tum/cit/aet/artemis/tumlive/web/TumLiveResource.java
```

Expected output: each file's package declaration matches its directory path and starts with `de.tum.cit.aet.artemis.tumlive.`.

Full compile-check is deferred to Task 18.

---

## Task 3: Update the sole cross-module Java consumer

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisWebhookService.java`

- [ ] **Step 1: Update import**

Change `import de.tum.cit.aet.artemis.nebula.api.TumLiveApi;` → `import de.tum.cit.aet.artemis.tumlive.api.TumLiveApi;`

- [ ] **Step 2: Grep for any other production consumers**

```bash
grep -rn "de.tum.cit.aet.artemis.nebula" src/main/java/
```

Expected: zero matches in `src/main/java/`. Test-side imports under `src/test/java/` are intentionally left stale until Tasks 12–13; the tree-wide gate is Task 18 Step 7.

If any `src/main/java/` matches remain, update their imports to `de.tum.cit.aet.artemis.tumlive.*` accordingly.

---

## Task 4: Rename the REST endpoint on the Angular client

**Files:**
- Modify: `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.ts:107-109`

- [ ] **Step 1: Update the client caller URL**

In `attachment-video-unit.service.ts`, replace the URL literal:

Old (line ~107-109):
```typescript
return this.httpClient.get<...>(`${this.resourceUrl}/nebula/video-utils/tum-live-playlist`, { params }).pipe(catchError(() => of(undefined)));
```

New:
```typescript
return this.httpClient.get<...>(`${this.resourceUrl}/tumlive/playlist`, { params }).pipe(catchError(() => of(undefined)));
```

If the client uses a different `resourceUrl` root (e.g., `api`), adjust the substring only, preserving the rest. The `catchError` fallback stays — it is what keeps rolling deployment graceful.

- [ ] **Step 2: Verify no other client caller exists**

```bash
grep -rn "nebula/video-utils" src/main/webapp/ src/test/
```

Expected: only the test specs listed in Task 5 reference the old URL.

---

## Task 5: Update client test specs referencing the old URL

**Files:**
- Modify: `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.spec.ts:377,393`
- Modify: `src/test/spec/...attachment-video-unit.component.spec.ts:86,282,344,372,558` (see grep in Step 1 for the actual path on disk)

- [ ] **Step 1: Locate specs by content**

```bash
grep -rln "nebula/video-utils/tum-live-playlist" src/
```

- [ ] **Step 2: Replace all occurrences**

In each matched file, replace every occurrence of the substring `nebula/video-utils/tum-live-playlist` with `tumlive/playlist`. Use find/replace in the editor or:

```bash
grep -rln "nebula/video-utils/tum-live-playlist" src/ | xargs sed -i '' 's|nebula/video-utils/tum-live-playlist|tumlive/playlist|g'
```

(On macOS, `sed -i ''` is required for in-place edit.)

- [ ] **Step 3: Re-grep to confirm**

```bash
grep -rn "nebula/video-utils" src/
```

Expected: zero matches.

---

## Task 6: Delete the `nebula/` Java package (production)

**Files:**
- Delete: `src/main/java/de/tum/cit/aet/artemis/nebula/api/AbstractNebulaApi.java`
- Delete: `src/main/java/de/tum/cit/aet/artemis/nebula/config/NebulaEnabled.java`
- Delete: `src/main/java/de/tum/cit/aet/artemis/nebula/` (the now-empty package directory tree)

- [ ] **Step 1: Delete the Java files**

```bash
git rm src/main/java/de/tum/cit/aet/artemis/nebula/api/AbstractNebulaApi.java
git rm src/main/java/de/tum/cit/aet/artemis/nebula/config/NebulaEnabled.java
```

- [ ] **Step 2: Remove empty directories**

```bash
find src/main/java/de/tum/cit/aet/artemis/nebula -type d -empty -delete
```

- [ ] **Step 3: Verify directory gone**

```bash
ls src/main/java/de/tum/cit/aet/artemis/nebula 2>&1
```

Expected: `No such file or directory`.

---

## Task 7: Delete the `nebulaRestTemplate` bean

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/config/RestTemplateConfiguration.java`

- [ ] **Step 1: Locate the bean method**

```bash
grep -n "nebulaRestTemplate" src/main/java/de/tum/cit/aet/artemis/core/config/RestTemplateConfiguration.java
```

- [ ] **Step 2: Delete the `@Bean` method and any now-unused imports**

Remove the entire `@Bean` method that returns `RestTemplate` and is named `nebulaRestTemplate()` (including its annotations and Javadoc). Remove any imports (`NebulaEnabled`, Nebula-specific URL constants) that become unused as a result.

- [ ] **Step 3: Verify no production consumers (test mocks are cleaned up in Task 13)**

```bash
grep -rn "nebulaRestTemplate" src/main/
```

Expected: zero matches. The test-base-class reference in `src/test/java/` remains until Task 13; the full-tree grep gate is deferred to Task 18.

---

## Task 8: Delete Nebula constants and helper methods

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/config/Constants.java:445-448,530-533`
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/config/ArtemisConfigHelper.java:9,168-176,258-260`
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/service/feature/ModuleFeatureService.java:109-116`

- [ ] **Step 1: Delete `MODULE_FEATURE_NEBULA` from `Constants.java`**

Remove the line at ~445-448:
```java
public static final String MODULE_FEATURE_NEBULA = "nebula";
```
(plus any immediately surrounding Javadoc that references it.)

- [ ] **Step 2: Delete `NEBULA_ENABLED_PROPERTY_NAME` from `Constants.java`**

Remove the line at ~530-533:
```java
public static final String NEBULA_ENABLED_PROPERTY_NAME = "artemis.nebula.enabled";
```

- [ ] **Step 3: Delete `isNebulaEnabled()` from `ArtemisConfigHelper.java`**

Remove the method (lines ~168-176), its `@see` / Javadoc, and the import at line ~9 of `NEBULA_ENABLED_PROPERTY_NAME` if it becomes unused.

- [ ] **Step 4: Remove the Nebula registration in `ArtemisConfigHelper.getEnabledFeatures()`**

At lines ~258-260, delete the line that conditionally adds `MODULE_FEATURE_NEBULA` to the enabled-features collection.

- [ ] **Step 5: Delete `isNebulaEnabled()` from `ModuleFeatureService.java`**

Remove the method (lines ~109-116) and any unused imports that result.

- [ ] **Step 6: Grep for stragglers (production code only; tests cleaned up in Tasks 13–14)**

```bash
grep -rn "isNebulaEnabled\|MODULE_FEATURE_NEBULA\|NEBULA_ENABLED_PROPERTY_NAME" src/main/
```

Expected: zero matches. Test-side references in `src/test/java/` remain until Task 14; the full-tree grep gate is in Task 18.

---

## Task 9: Delete YAML `artemis.nebula.*` blocks

**Files (delete the `nebula:` block under `artemis:` in each):**
- Modify: `src/main/resources/config/application.yml:137-141`
- Modify: `src/main/resources/config/application-local.yml:36-39`
- Modify: `src/main/resources/config/application-buildagent.yml:34-35`
- Modify: `src/main/resources/config/application-artemis.yml:121-124`
- Modify: `src/test/resources/config/application.yml:114-118`

- [ ] **Step 1: Delete block in `src/main/resources/config/application.yml`**

Remove lines 137-141 (the `nebula:` block and all nested keys). Preserve surrounding structure and indentation.

- [ ] **Step 2: Delete block in `src/main/resources/config/application-local.yml`**

Remove lines 36-39 (the `nebula:` block). **Keep** the adjacent `tum-live:` block that starts at ~line 40-41.

- [ ] **Step 3: Delete block in `src/main/resources/config/application-buildagent.yml`**

Remove lines 34-35.

- [ ] **Step 4: Delete block in `src/main/resources/config/application-artemis.yml`**

Remove lines 121-124.

- [ ] **Step 5: Delete block in `src/test/resources/config/application.yml`**

Remove lines 114-118.

- [ ] **Step 6: Verify no `nebula:` YAML keys remain**

```bash
grep -rn "^\s*nebula:" src/main/resources/ src/test/resources/
```

Expected: zero matches.

---

## Task 10: Client TypeScript cleanup

**Files:**
- Modify: `src/main/webapp/app/app.constants.ts:60,81`
- Modify: `src/main/webapp/app/core/admin/features/admin-feature-toggle.component.ts:22,113,132,160`

- [ ] **Step 1: Delete `MODULE_FEATURE_NEBULA` constant**

In `app.constants.ts` at ~line 60, remove:
```typescript
export const MODULE_FEATURE_NEBULA = 'nebula';
```

- [ ] **Step 2: Remove `typeof MODULE_FEATURE_NEBULA` from the `ModuleFeature` union**

In `app.constants.ts` at ~line 81, remove the `| typeof MODULE_FEATURE_NEBULA` arm from the union type.

- [ ] **Step 3: Remove the `MODULE_FEATURE_NEBULA` import in `admin-feature-toggle.component.ts`**

At ~line 22, drop the symbol from the import list (the file will still import other constants — keep those).

- [ ] **Step 4: Remove the nebula entry from `displayedModuleFeatures`**

At ~line 113, delete the array element whose key is `MODULE_FEATURE_NEBULA`.

- [ ] **Step 5: Delete the `FeatureToggle.LectureContentProcessing` doc-link map entry**

At ~line 132, remove the entire map entry whose value currently points to `#nebula-setup-guide`. Do not replace it; other toggles in this component do not universally have doc links, and the anchor will no longer exist after Task 15.

- [ ] **Step 6: Remove the nebula entry from the feature-to-doc-link map at line ~160**

(Same map, different entry or the one cross-referenced at 160 — read the file around that line and remove whichever map entry keys off `MODULE_FEATURE_NEBULA`.)

- [ ] **Step 7: Grep for stragglers**

```bash
grep -rn "MODULE_FEATURE_NEBULA\|nebula-setup-guide" src/main/webapp/
```

Expected: zero matches.

---

## Task 11: Delete and edit i18n bundles

**Files:**
- Delete: `src/main/webapp/i18n/en/nebula.json`
- Delete: `src/main/webapp/i18n/de/nebula.json`
- Modify: `src/main/webapp/i18n/en/featureToggles.json:104-107,181-185`
- Modify: `src/main/webapp/i18n/de/featureToggles.json:104-107,181-185`

- [ ] **Step 1: Delete orphan bundles**

```bash
git rm src/main/webapp/i18n/en/nebula.json src/main/webapp/i18n/de/nebula.json
```

- [ ] **Step 2: Edit `en/featureToggles.json`**

- Remove the `"nebula": { ... }` module-feature block at ~lines 104-107.
- At ~lines 181-185, rewrite `LectureContentProcessing.description` and `LectureContentProcessing.disableWarning` to drop Nebula mentions. Suggested replacements:
  - `description`: `"Enables automated lecture content processing: transcription, slide extraction, and Pyris ingestion."`
  - `disableWarning`: `"Disabling lecture content processing stops all ongoing transcription and ingestion jobs for new lectures."`

- [ ] **Step 3: Edit `de/featureToggles.json`**

Apply the same two edits with German translations. Keep parity with English per AGENTS.md i18n rule. Suggested:
- `description`: `"Aktiviert die automatische Verarbeitung von Vorlesungsinhalten: Transkription, Folien-Extraktion und Pyris-Ingestion."`
- `disableWarning`: `"Das Deaktivieren der Vorlesungsinhalt-Verarbeitung stoppt alle laufenden Transkriptions- und Ingestion-Aufträge für neue Vorlesungen."`

- [ ] **Step 4: Verify JSON validity**

```bash
python3 -m json.tool src/main/webapp/i18n/en/featureToggles.json > /dev/null
python3 -m json.tool src/main/webapp/i18n/de/featureToggles.json > /dev/null
```

Expected: both commands exit 0.

- [ ] **Step 5: Grep for stragglers**

```bash
grep -rn -i "nebula" src/main/webapp/i18n/
```

Expected: zero matches.

---

## Task 12: Delete and move tests

**Files:**
- Delete: `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaCodeStyleArchitectureTest.java`
- Delete: `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaRepositoryArchitectureTest.java`
- Delete: `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaResourceArchitectureTest.java`
- Delete: `src/test/java/de/tum/cit/aet/artemis/nebula/NebulaServiceArchitectureTest.java`
- Move + rename: `src/test/java/de/tum/cit/aet/artemis/lecture/NebulaTranscriptionResourceIntegrationTest.java` → `src/test/java/de/tum/cit/aet/artemis/tumlive/TumLivePlaylistResourceIntegrationTest.java`
- Move: `src/test/java/de/tum/cit/aet/artemis/lecture/TumLiveServiceTest.java` → `src/test/java/de/tum/cit/aet/artemis/tumlive/TumLiveServiceTest.java`

- [ ] **Step 1: Delete the four architecture tests**

```bash
git rm src/test/java/de/tum/cit/aet/artemis/nebula/NebulaCodeStyleArchitectureTest.java
git rm src/test/java/de/tum/cit/aet/artemis/nebula/NebulaRepositoryArchitectureTest.java
git rm src/test/java/de/tum/cit/aet/artemis/nebula/NebulaResourceArchitectureTest.java
git rm src/test/java/de/tum/cit/aet/artemis/nebula/NebulaServiceArchitectureTest.java
```

- [ ] **Step 2: Move + rename the integration test**

```bash
mkdir -p src/test/java/de/tum/cit/aet/artemis/tumlive
git mv src/test/java/de/tum/cit/aet/artemis/lecture/NebulaTranscriptionResourceIntegrationTest.java \
       src/test/java/de/tum/cit/aet/artemis/tumlive/TumLivePlaylistResourceIntegrationTest.java
```

Then edit the moved file — **rename every Nebula identifier, not just the class name and package**:
- Change `package de.tum.cit.aet.artemis.lecture;` → `package de.tum.cit.aet.artemis.tumlive;`
- Rename the class from `NebulaTranscriptionResourceIntegrationTest` → `TumLivePlaylistResourceIntegrationTest`
- Update all URL literals in mocks/assertions: `/api/nebula/video-utils/tum-live-playlist` → `/api/tumlive/playlist`
- Update imports referencing `de.tum.cit.aet.artemis.nebula.*` to `de.tum.cit.aet.artemis.tumlive.*`
- Rename fields/locals/constants that contain "Nebula" in their identifier, e.g.:
  - `TEST_PREFIX = "nebulatranscription..."` → `TEST_PREFIX = "tumliveplaylist..."` (or similar; pick a unique short prefix so existing test isolation still works)
  - `restNebulaTranscriptionMockMvc` → `restTumLivePlaylistMockMvc`
- Rewrite any class-level or method-level Javadoc that references "Nebula" to reference "TUM Live" instead.
- Run `grep -n -i nebula src/test/java/de/tum/cit/aet/artemis/tumlive/TumLivePlaylistResourceIntegrationTest.java` — expected: zero matches.

- [ ] **Step 3: Move the service test**

```bash
git mv src/test/java/de/tum/cit/aet/artemis/lecture/TumLiveServiceTest.java \
       src/test/java/de/tum/cit/aet/artemis/tumlive/TumLiveServiceTest.java
```

Edit:
- Change `package de.tum.cit.aet.artemis.lecture;` → `package de.tum.cit.aet.artemis.tumlive;`
- Update imports `de.tum.cit.aet.artemis.nebula.service.TumLiveService` → `de.tum.cit.aet.artemis.tumlive.service.TumLiveService`

- [ ] **Step 4: Remove the empty nebula test directory**

```bash
find src/test/java/de/tum/cit/aet/artemis/nebula -type d -empty -delete
```

Expected: `nebula` subtree under `src/test/java/...` no longer exists.

---

## Task 13: Update test base classes

**Files:**
- Modify: `src/test/java/de/tum/cit/aet/artemis/shared/base/AbstractSpringIntegrationIndependentTest.java`
- Modify: `src/test/java/de/tum/cit/aet/artemis/shared/base/AbstractSpringIntegrationLocalCILocalVCTest.java`

Do all edits in a single pass per file by searching for symbols — do **not** rely on the line numbers below after the first edit, because earlier edits shift them.

- [ ] **Step 1: Locate both base classes**

```bash
grep -rln "artemis.nebula.enabled\|nebulaRestTemplate\|[Nn]ebula" src/test/java/de/tum/cit/aet/artemis/shared/base/
```

- [ ] **Step 2: Symbol-based edits in `AbstractSpringIntegrationIndependentTest.java`**

Apply all of these in one pass:
- Delete the `"artemis.nebula.enabled=true"` string literal from the `@TestPropertySource` array (preserve array delimiters).
- Delete the entire `@MockitoBean(name = "nebulaRestTemplate") RestTemplate nebulaRestTemplate;` field declaration.
- **Keep** the `@MockitoBean` for `TumLiveService` but update its import from `de.tum.cit.aet.artemis.nebula.service.TumLiveService` to `de.tum.cit.aet.artemis.tumlive.service.TumLiveService`.
- Inside `resetSpyBeans()` (or whichever reset method references the mock), delete the line that resets `nebulaRestTemplate`. Keep resets for `tumLiveService`.
- **Remove every remaining comment or Javadoc that mentions "Nebula"** in this file (there is at least one around the `TumLiveService` mock — rewrite it to mention "TUM Live" or just delete it).
- Remove any `import` statements that become unused as a result.

- [ ] **Step 3: `AbstractSpringIntegrationLocalCILocalVCTest.java`**

- Line ~98: remove `"artemis.nebula.enabled=false"` from `@TestPropertySource` array.

- [ ] **Step 4: Grep for stragglers**

```bash
grep -rn "nebulaRestTemplate\|artemis.nebula" src/test/java/
```

Expected: zero matches.

---

## Task 14: Update unit-test methods referencing Nebula

**Files:**
- Modify: `src/test/java/.../ArtemisConfigHelperTest.java:49-50`
- Modify: `src/test/java/.../ModuleFeatureInfoContributorTest.java:34,55`

- [ ] **Step 1: Delete `testNebulaProperty()`**

Remove the entire test method at lines ~49-50 (and its `@Test` annotation + Javadoc) from `ArtemisConfigHelperTest.java`.

- [ ] **Step 2: Update `ModuleFeatureInfoContributorTest.java`**

- Line ~34: remove `NEBULA_ENABLED_PROPERTY_NAME` from any expected/input array.
- Line ~55: remove `MODULE_FEATURE_NEBULA` from any expected/input array.
- Remove the corresponding imports if they become unused.

- [ ] **Step 3: Grep**

```bash
grep -rn -i "testNebula\|NEBULA_ENABLED\|MODULE_FEATURE_NEBULA" src/test/java/
```

Expected: zero matches.

---

## Task 15: Javadoc / comment edits (prose only, 7 edits)

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/domain/LectureTranscription.java:41`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/domain/Lecture.java:35`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/domain/LectureUnitProcessingState.java:20`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/service/LectureService.java:172`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/service/LectureUnitService.java:155`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/api/LectureContentProcessingApi.java:15,19`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/web/LectureUnitResource.java:237`

- [ ] **Step 1: Apply each edit**

| File:Line | Old text (substring) | New text (substring) |
|-----------|----------------------|----------------------|
| `LectureTranscription.java:41` | `external transcription job ID from the transcription service (e.g., Nebula)` | `external transcription job ID from the transcription service` |
| `Lecture.java:35` | `automatic lecture transcription using Nebula` | `automatic lecture transcription` |
| `LectureUnitProcessingState.java:20` | `transcription generation (Nebula) and ingestion into Pyris/Iris` | `transcription generation and ingestion into Pyris/Iris` |
| `LectureService.java:172` | `Clean up external processing resources (cancel Nebula jobs, delete from Pyris)` | `Clean up external processing resources (delete from Pyris)` |
| `LectureUnitService.java:155` | `cancels any ongoing content processing jobs (Nebula transcription, Pyris ingestion)` | `cancels any ongoing content processing jobs (Pyris ingestion)` |
| `LectureContentProcessingApi.java:15` | `nebula, iris` | `iris` |
| `LectureContentProcessingApi.java:19` | (sentence describing cross-module circular dep with Nebula specifically) | Trim the Nebula-specific part; keep Iris-related portion |
| `LectureUnitResource.java:237` | `If processing is not enabled (Iris/Nebula disabled)` | `If processing is not enabled (Iris disabled)` |

- [ ] **Step 2: Grep to confirm only out-of-scope matches remain**

```bash
grep -rn -i "nebula" src/main/java/
```

Expected: zero matches.

---

## Task 16: Documentation and root-level cleanup

**Files:**
- Modify: `documentation/docs/admin/extension-services.mdx:357-438`
- Modify: `documentation/docs/admin/artemis-intelligence.mdx:13,28,61,85`
- Modify: `README.md:144`
- Delete: `LECTURE_PROCESSING_AUTOMATION_PLAN.md`

- [ ] **Step 1: Delete "Nebula Setup Guide" section in `extension-services.mdx`**

Delete lines ~357-438, covering: `### Nebula Setup Guide`, `### Nebula Service Deployment`, `#### Connecting Artemis and Nebula`, and the verification checklist.

- [ ] **Step 2: Edit `artemis-intelligence.mdx`**

- Line ~13: remove the Nebula entry from the service list.
- Line ~28: remove the Nebula row from the table.
- Line ~61: delete the bullet describing Nebula.
- Line ~85: delete the repo link to Nebula.

- [ ] **Step 3: Edit `README.md`**

Delete line ~144: `| Nebula | Patrick Bassner |` (the maintainer-table row). Preserve the surrounding table formatting.

- [ ] **Step 4: Delete the obsolete root plan**

```bash
git rm LECTURE_PROCESSING_AUTOMATION_PLAN.md
```

- [ ] **Step 5: Grep for doc stragglers**

```bash
grep -rn -i "nebula" documentation/ README.md
```

Expected: only `documentation/static/img/artemis-intelligence.svg` remains (out-of-scope per Non-Goals).

---

## Task 17: Gradle / jacoco coverage config

**Files:**
- Modify: `gradle/jacoco.gradle:3,23`

- [ ] **Step 1: Remove the `"nebula"` entry from the module thresholds map**

At line ~23, remove the map entry `"nebula": [INSTRUCTION: 0.836, CLASS: 0]`. Do **not** add a `"tumlive"` entry — the three-file module is below the practical measurement threshold and the spec explicitly says not to.

- [ ] **Step 2: Update the comment at line ~3**

Find the comment that says something like `// merging Nebula PRs …` and rewrite it without the Nebula reference (keep the general guidance about module thresholds).

- [ ] **Step 3: Confirm `nebula.lint` is untouched**

```bash
grep -n "nebula" build.gradle gradle/
```

Expected: the only match is `id "nebula.lint"` in `build.gradle:25` (the Netflix gradle-lint plugin — deliberately kept).

---

## Task 18: Full verification

**Files:** none (read-only verification)

- [ ] **Step 1: Java formatting**

```bash
JAVA_HOME="/Users/pat/.sdkman/candidates/java/25-tem" ./gradlew spotlessCheck -x webapp 2>&1 | tee /tmp/spotless.txt | tail -20
```

Expected: `BUILD SUCCESSFUL`. If Spotless fails with formatting diffs, run `./gradlew spotlessApply -x webapp` and re-run the check.

- [ ] **Step 2: Java linting**

```bash
JAVA_HOME="/Users/pat/.sdkman/candidates/java/25-tem" ./gradlew checkstyleMain modernizer -x webapp 2>&1 | tee /tmp/checkstyle.txt | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Full server test suite**

```bash
JAVA_HOME="/Users/pat/.sdkman/candidates/java/25-tem" ./gradlew test -x webapp 2>&1 | tee /tmp/test.txt | tail -40
```

Expected: `BUILD SUCCESSFUL`. Requires Docker (PostgreSQL Testcontainers). If the moved integration test or the service test fail, they are the most likely culprits — inspect with:
```bash
grep -A 5 -E "Tum[Ll]ive|tumlive" /tmp/test.txt
```

- [ ] **Step 4: Client tests (Vitest for the changed service + component specs)**

```bash
npx vitest run src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.spec.ts 2>&1 | tail -30
```

Then the broader attachment-video-unit component spec(s):
```bash
npx vitest run src/main/webapp/app/lecture/overview/ 2>&1 | tail -30
```

Expected: green.

- [ ] **Step 5: Prettier check on touched client files**

```bash
npm run prettier:check 2>&1 | tail -10
```

(Per user rule: never `npm run lint` — prefer targeted checks.)

Expected: green, or run `npm run prettier:write` to fix.

- [ ] **Step 6: Global Nebula grep (final acceptance criterion)**

```bash
grep -rEn "[Nn]ebula" src/ documentation/ README.md build.gradle gradle/ 2>/dev/null | grep -v "artemis-intelligence.svg"
```

Expected output: **only** these surviving matches:
- `build.gradle:25` — `id "nebula.lint"` (Netflix plugin, deliberately kept)

Anything else — including `MODULE_FEATURE_NEBULA`, `artemis.nebula.enabled`, `@Conditional(NebulaEnabled.class)`, doc strings, i18n keys — indicates incomplete work and must be resolved before commit.

- [ ] **Step 7: Confirm no stray imports**

```bash
grep -rn "de.tum.cit.aet.artemis.nebula" src/
```

Expected: zero matches.

- [ ] **Step 8: Confirm endpoint consistency and client fallback preservation**

The server mapping is split (`@RequestMapping("api/tumlive/")` on the class + `@GetMapping("playlist")` on the method), so the full URL string `/api/tumlive/playlist` will never appear as a single literal in server code. Verify each piece independently:

```bash
# Server: class-level base path
grep -rn 'api/tumlive/' src/main/java/de/tum/cit/aet/artemis/tumlive/web/TumLiveResource.java
# Server: method-level path
grep -n '"playlist"' src/main/java/de/tum/cit/aet/artemis/tumlive/web/TumLiveResource.java
# Client + tests: full URL literal
grep -rn "/api/tumlive/playlist\|tumlive/playlist" src/main/webapp src/test/
# Client catchError fallback still present
grep -n "catchError(() => of(undefined))" src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.ts
# Dead Nebula symbols gone tree-wide
grep -rn "NebulaEnabled\|AbstractNebulaApi\|nebulaRestTemplate" src/
```

Expected:
- Server greps: one match each (`api/tumlive/` in the class mapping; `"playlist"` in the method mapping).
- Client + test-specs grep: at least one match in the client service and several in the updated specs.
- `catchError` grep: one match — the fallback on the client caller (its accidental removal would be a silent regression in rolling-deploy behavior).
- Last grep: zero matches.

---

## Task 19: Commit and push (single atomic commit)

**Files:** all staged in prior tasks.

- [ ] **Step 1: Inspect what will be committed**

```bash
git status
git diff --stat
```

Confirm the scope matches the spec — no stray modifications outside the Nebula cleanup.

- [ ] **Step 2: Stage only the files changed in this plan (no `git add -A`)**

Explicitly add the modified/renamed/deleted files. `git mv` and `git rm` already stage their changes; for `Edit`-based changes, add each file by path. Example:

```bash
git add src/main/java/de/tum/cit/aet/artemis/tumlive/ \
        src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisWebhookService.java \
        src/main/java/de/tum/cit/aet/artemis/core/config/RestTemplateConfiguration.java \
        src/main/java/de/tum/cit/aet/artemis/core/config/Constants.java \
        src/main/java/de/tum/cit/aet/artemis/core/config/ArtemisConfigHelper.java \
        src/main/java/de/tum/cit/aet/artemis/core/service/feature/ModuleFeatureService.java \
        src/main/java/de/tum/cit/aet/artemis/lecture/ \
        src/main/resources/config/ \
        src/test/resources/config/application.yml \
        src/main/webapp/app/app.constants.ts \
        src/main/webapp/app/core/admin/features/admin-feature-toggle.component.ts \
        src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.ts \
        src/main/webapp/i18n/ \
        src/test/java/de/tum/cit/aet/artemis/tumlive/ \
        src/test/java/de/tum/cit/aet/artemis/lecture/ \
        documentation/docs/admin/extension-services.mdx \
        documentation/docs/admin/artemis-intelligence.mdx \
        README.md \
        gradle/jacoco.gradle
```

Adjust the list against `git status` output — do not wildcard.

- [ ] **Step 3: Create the single commit**

```bash
JAVA_HOME="/Users/pat/.sdkman/candidates/java/25-tem" git commit -m "$(cat <<'EOF'
General: Remove Nebula package and relocate TUM Live integration

The Nebula microservice was fully removed from edutelligence; the
Artemis-side Java package is dead weight. PR #12459 migrated the
transcription data flow to Pyris. PR #12463 was closed without the
cleanup. This commit deletes the residual nebula/ package, its
supporting YAML/i18n/docs/tests, and relocates the still-live TUM
Live integration (TumLiveApi, TumLiveService, TumLiveResource) into
a new tumlive/ top-level module. The public endpoint renames from
/api/nebula/video-utils/tum-live-playlist to /api/tumlive/playlist;
the single Angular client caller has an existing catchError fallback
that degrades gracefully during rolling deploys.

Atomic commit required: ArtemisConfigHelper.isNebulaEnabled() uses
getPropertyOrExitArtemis() which throws on missing property, so YAML
removal and helper removal must land together.
EOF
)"
```

Expected: pre-commit hook passes (Java 25 required — hence the `JAVA_HOME` prefix). If the hook fails with `'_' used as an identifier`, the wrong Java version is active; fix `JAVA_HOME` and try a **new** commit (never `--amend`).

- [ ] **Step 4: Push the branch**

```bash
git push -u origin chore/remove-nebula-package
```

- [ ] **Step 5: Before opening the PR, run codex review on the final diff**

Per the user's global instruction: run codex for code review before offering to commit/push a feature branch to GitHub. (Codex review of the *design* is already done; this is review of the *implementation* diff.)

```bash
codex exec -s read-only -o /tmp/nebula-removal-implementation-review.md \
  "Review the diff on the current branch vs origin/develop. The goal is to fully remove the nebula/ package from Artemis and relocate the three surviving TUM Live files to a new tumlive/ module. Check: (1) zero runtime references to 'Nebula' remain except build.gradle nebula.lint and the SVG asset; (2) ArtemisConfigHelper.isNebulaEnabled() and its YAML property both gone (atomicity); (3) the client endpoint URL change is consistent with the server @RequestMapping; (4) i18n EN/DE parity preserved; (5) no dangling imports or @MockitoBean for nebulaRestTemplate; (6) the existing attachment-video-unit.service.ts catchError fallback is still present and unchanged. Report any remaining concerns." 2>&1
```

Address feedback before opening the PR. Resume codex as needed until it explicitly approves.

- [ ] **Step 6: Open the PR**

```bash
gh pr create --title "\`General\`: Remove Nebula package and relocate TUM Live integration" --body "$(cat <<'EOF'
## Summary

- Delete the residual `nebula/` Java package; PR #12459 migrated the data flow to Pyris, PR #12463 was closed without cleanup.
- Relocate `TumLiveApi`, `TumLiveService`, `TumLiveResource` to a new `tumlive/` top-level module (convention: `athena/`, `iris/`, `hyperion/`, `atlas/`).
- Rename the public endpoint: `GET /api/nebula/video-utils/tum-live-playlist` → `GET /api/tumlive/playlist`. The Angular client's existing `catchError` fallback makes rolling deploys graceful (TUM Live video units fall through to the iframe branch if the cached pre-rename bundle hits the new server).
- Delete dead config (`artemis.nebula.*` YAML), constants (`MODULE_FEATURE_NEBULA`, `NEBULA_ENABLED_PROPERTY_NAME`), helpers (`isNebulaEnabled()`), arch tests, i18n bundle, docs, and the obsolete root-level plan file.

## Atomicity

Must ship as a single commit: `isNebulaEnabled()` uses `getPropertyOrExitArtemis()` which throws on missing property, so YAML removal without helper removal would break Spring startup.

## Design doc

`docs/superpowers/specs/2026-04-14-nebula-removal-design.md`

## Test plan

- [ ] `./gradlew spotlessCheck checkstyleMain modernizer -x webapp` — green
- [ ] `./gradlew test -x webapp` — full server suite green (moved integration test hits new endpoint)
- [ ] `npx vitest run` on attachment-video-unit service + component specs — green
- [ ] Manual smoke test on a test server: TUM Live video unit resolves playlist, custom HLS player + transcript sidebar render, admin feature toggle page loads without errors.

## Out of scope

- `documentation/static/img/artemis-intelligence.svg` (embedded "Nebula" text — asset regeneration is a separate effort).
- `build.gradle:25` `id "nebula.lint"` (Netflix gradle-lint plugin, unrelated).
EOF
)"
```

Assign to the next open milestone:
```bash
gh api repos/ls1intum/Artemis/milestones --jq '.[] | select(.state=="open") | "\(.number) \(.title)"'
```

Then edit the PR to set the milestone.

- [ ] **Step 7: Flag private-deployment config bleed in release notes**

Note to add to the PR description or release notes: "Downstream Artemis deployments should remove any `artemis.nebula.*` properties from their local config files; they will be silently ignored otherwise."

---

## Final Self-Review Checklist (run before presenting PR to reviewers)

- [ ] Spec coverage: every section of `2026-04-14-nebula-removal-design.md` maps to at least one task above.
- [ ] No placeholders: every "delete/edit" step has an exact file path and exact substring or line range.
- [ ] Type consistency: the new class is named `TumLiveResource` everywhere; the new endpoint is `/api/tumlive/playlist` everywhere (server + client + 4 specs + integration test).
- [ ] Atomicity: no task commits mid-flight — only Task 19 commits.
- [ ] Grep gate: Task 18 Step 6 is the acceptance criterion for "Nebula is gone."
