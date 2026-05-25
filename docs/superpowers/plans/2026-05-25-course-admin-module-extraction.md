# `course` and `admin` Module Extraction Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Carve `course` and `admin` out of the oversized `core` module (server + client), following the precedent set by `calendar` (#12681) and the in-flight `account` reorganization.

**Architecture:** Six phases (one shippable PR each):
- Phase 1: Server `course` module (~50 Java files moved, ~250 cross-module imports rewritten — `Course` entity is widely depended on).
- Phase 2: Server `admin` module (~65 Java files moved, fewer cross-module imports).
- Phase 3: Client `course` module (~304 files moved as 4 atomic subtrees, ~600 import paths rewritten).
- Phase 4: Client `admin` module (~213 files, ~400 import paths rewritten).
- Phase 5: ArchUnit + docs + CODEOWNERS + labeler updates (often folded into Phases 1/3 commits, separated here for clarity).
- Phase 6: `core/dto` long-tail cleanup (a few admin-shaped DTOs still living under `core/dto/` after Phase 2).

Each phase mirrors the calendar/account precedent. The server module structure follows the canonical layout: `<module>/{domain,repository,dto,service,web,architecture}` with one `package-info.java` per package and one `Abstract*ArchitectureTest`-derived class per concern (api/code-style/entity-usage/repository/resource/service/test).

**Tech Stack:** Spring Boot 3.5 / Java 25, ArchUnit, JUnit 6, Gradle 9.3 (Spotless + Checkstyle + Modernizer). Angular 21, TypeScript, Vitest, Playwright. Imports are rewritten with `git grep -l … | xargs sed -i ''`. `git mv` preserves history.

**Branch strategy:** One feature branch per phase off `develop`. Do **not** combine phases on one branch — review surface area is already large per phase.

**Skipped (deliberate):** `Course.java` entity itself — moving it touches ~250 files across 21 modules. We do move it (it's the whole point), but the plan calls out the mass-rewrite explicitly so reviewers know what to expect.

---

## Phase 0: Baseline & safety net

### Task 0.1: Confirm clean baseline

**Files:** none

- [ ] **Step 1: Confirm working tree clean** (no uncommitted refactor noise gets mixed in)

```bash
cd /Users/krusche/Projects/Artemis2
git status
```

Expected: only `feature/server-account-package-reorganization` work-in-progress or a clean tree. If anything else, stash it.

- [ ] **Step 2: Baseline build + test passes**

```bash
./gradlew compileJava -x webapp
./gradlew spotlessCheck checkstyleMain -x webapp
./gradlew test --tests "de.tum.cit.aet.artemis.shared.architecture.*" -x webapp
```

Expected: BUILD SUCCESSFUL on all three. If any of these fail before refactoring starts, fix them first.

- [ ] **Step 3: Pin baseline test result** (so we can compare after each phase)

```bash
./gradlew test -x webapp 2>&1 | tee /tmp/baseline-test-output.txt
grep -E "tests completed|FAILED" /tmp/baseline-test-output.txt
```

Expected: write down the "X tests completed, Y skipped" line. Each phase must end with same count (or strictly more, never fewer non-skipped successes).

- [ ] **Step 4: Create branch for Phase 1**

```bash
git switch develop
git pull
git switch -c feature/server-extract-course-module
```

---

## Phase 1: Server `course` module

**Goal:** Move all course-related server code out of `core` into a new `course` module that follows the `calendar` package layout.

**Files to move (50 Java files):**

Domain (8 files) — from `core/domain/` → `course/domain/`:
- `Course.java`
- `CourseInformationSharingConfiguration.java`
- `CourseOperationStatus.java`
- `CourseOperationType.java`
- `CourseExamExportErrorCause.java`
- `CourseExamExportState.java`
- `CourseRequest.java`
- `CourseRequestStatus.java`

Repository (2 files) — from `core/repository/` → `course/repository/`:
- `CourseRepository.java`
- `CourseRequestRepository.java`

DTOs (28 files) — from `core/dto/` → `course/dto/`:
- `ActiveCourseDTO.java`
- `CourseCompetencyProgressDTO.java`
- `CourseContentCountDTO.java`
- `CourseCreateDTO.java`
- `CourseExistingExerciseDetailsDTO.java`
- `CourseForArchiveDTO.java`
- `CourseForDashboardDTO.java`
- `CourseForImportDTO.java`
- `CourseForQuizExerciseDTO.java`
- `CourseGroupsDTO.java`
- `CourseManagementDetailViewDTO.java`
- `CourseMaterialImportOptionsDTO.java`
- `CourseMaterialImportResultDTO.java`
- `CourseOperationProgressDTO.java`
- `CourseRequestCreateDTO.java`
- `CourseRequestDTO.java`
- `CourseRequestDecisionDTO.java`
- `CourseRequestRequesterDTO.java`
- `CourseScoresDTO.java`
- `CourseSummaryDTO.java`
- `CourseUpdateDTO.java`
- `CourseWithIdDTO.java`
- `CoursesForDashboardDTO.java`
- `OnlineCourseDTO.java`
- `OnlineResourceDTO.java`
- (held back for Phase 2 because they're admin-facing: `CourseManagementOverviewExerciseStatisticsDTO`, `CourseManagementOverviewStatisticsDTO`, `CourseManagementStatisticsDTO`, `CourseRequestsAdminOverviewDTO`, `CourseStatisticsAverageScore` — these stay in `core/dto/` for now and move in Phase 2 → admin.)

Service (18 files) — from `core/service/course/` → `course/service/`:
- `CourseAccessService.java`
- `CourseAdminService.java`
- `CourseArchiveService.java`
- `CourseAtlasService.java`
- `CourseDeletionService.java`
- `CourseForUserGroupService.java`
- `CourseLoadService.java`
- `CourseMaterialImportService.java`
- `CourseOperationProgressService.java`
- `CourseOperationWeights.java`
- `CourseOverviewService.java`
- `CourseRequestService.java`
- `CourseResetService.java`
- `CourseSearchService.java`
- `CourseService.java`
- `CourseServiceUtil.java`
- `CourseStatsService.java`
- `CourseVisibleService.java`

Web (8 files) — from `core/web/course/` → `course/web/`:
- `CourseAccessResource.java`
- `CourseArchiveResource.java`
- `CourseManagementResource.java`
- `CourseMaterialImportResource.java`
- `CourseOverviewResource.java`
- `CourseRequestResource.java`
- `CourseStatsResource.java`
- `CourseUpdateResource.java`

### Task 1.1: Create `course` package skeleton

**Files:**
- Create: `src/main/java/de/tum/cit/aet/artemis/course/domain/package-info.java`
- Create: `src/main/java/de/tum/cit/aet/artemis/course/repository/package-info.java`
- Create: `src/main/java/de/tum/cit/aet/artemis/course/dto/package-info.java`
- Create: `src/main/java/de/tum/cit/aet/artemis/course/service/package-info.java`
- Create: `src/main/java/de/tum/cit/aet/artemis/course/web/package-info.java`

- [ ] **Step 1: Verify reference (calendar's package-info)** — calendar doesn't have package-info; check what existing big modules use

```bash
find /Users/krusche/Projects/Artemis2/src/main/java/de/tum/cit/aet/artemis/lecture -name "package-info.java"
```

Expected: list of existing package-info.java files. If lecture has none either, **skip the package-info step** — we don't need to introduce a new convention. Otherwise model new ones after lecture's.

- [ ] **Step 2: Create empty package directories**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/course/{domain,repository,dto,service,web,architecture}
```

- [ ] **Step 3: Commit the skeleton** (using `.gitkeep` for empty dirs so the structure shows up in review)

```bash
for d in domain repository dto service web architecture; do
  touch "src/main/java/de/tum/cit/aet/artemis/course/$d/.gitkeep"
done
git add src/main/java/de/tum/cit/aet/artemis/course/
git status
git commit -m "$(cat <<'EOF'
Course module: create empty package skeleton

Adds empty domain/repository/dto/service/web/architecture packages
under artemis/course/ to receive code that will be moved out of core/
in subsequent commits. No behavior change.
EOF
)"
```

### Task 1.2: Move domain entities + rewrite imports project-wide

**Files moved:** 8 domain files listed above (`Course.java`, etc.).

- [ ] **Step 1: Move all 8 files with git mv** (preserves history)

```bash
cd /Users/krusche/Projects/Artemis2
for f in Course CourseInformationSharingConfiguration CourseOperationStatus CourseOperationType CourseExamExportErrorCause CourseExamExportState CourseRequest CourseRequestStatus; do
  git mv "src/main/java/de/tum/cit/aet/artemis/core/domain/${f}.java" \
         "src/main/java/de/tum/cit/aet/artemis/course/domain/${f}.java"
done
# .gitkeep no longer needed in domain/
rm -f src/main/java/de/tum/cit/aet/artemis/course/domain/.gitkeep
```

- [ ] **Step 2: Rewrite `package` declarations inside the moved files**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.domain;|package de.tum.cit.aet.artemis.course.domain;|' \
  src/main/java/de/tum/cit/aet/artemis/course/domain/*.java
```

- [ ] **Step 3: Rewrite cross-codebase imports** (mass rewrite — `Course` alone has ~250 importers)

```bash
for cls in Course CourseInformationSharingConfiguration CourseOperationStatus CourseOperationType CourseExamExportErrorCause CourseExamExportState CourseRequest CourseRequestStatus; do
  git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.domain\.${cls};" -- '*.java' \
    | xargs sed -i '' "s|import de\.tum\.cit\.aet\.artemis\.core\.domain\.${cls};|import de.tum.cit.aet.artemis.course.domain.${cls};|g"
done
# Also catch wildcard imports if any exist
git grep -l "de\.tum\.cit\.aet\.artemis\.core\.domain\.Course\b" -- '*.java' | head -20
```

Expected after the second `git grep`: empty (all references rewritten) **unless** a class name contains the literal "Course" (e.g. `CourseCompetency` — which lives in `atlas`, *not* in `core/domain`, so should be fine). If anything legitimate is left, inspect manually.

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava -x webapp 2>&1 | tail -40
```

Expected: BUILD SUCCESSFUL. If failing, the most likely cause is a leftover import — re-run the `git grep` from Step 3 with the missing class name.

- [ ] **Step 5: Verify compile of tests** (test sources also import `Course`)

```bash
./gradlew compileTestJava -x webapp 2>&1 | tail -40
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Course module: move 8 domain entities out of core

Moves Course, CourseRequest, CourseInformationSharingConfiguration, and
related enums from core/domain/ to course/domain/. Updates ~250 import
statements across 21 modules. No behavior change.
EOF
)"
```

### Task 1.3: Move repositories + rewrite imports

**Files moved:** `CourseRepository.java`, `CourseRequestRepository.java`.

- [ ] **Step 1: git mv**

```bash
git mv src/main/java/de/tum/cit/aet/artemis/core/repository/CourseRepository.java \
       src/main/java/de/tum/cit/aet/artemis/course/repository/CourseRepository.java
git mv src/main/java/de/tum/cit/aet/artemis/core/repository/CourseRequestRepository.java \
       src/main/java/de/tum/cit/aet/artemis/course/repository/CourseRequestRepository.java
rm -f src/main/java/de/tum/cit/aet/artemis/course/repository/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.repository;|package de.tum.cit.aet.artemis.course.repository;|' \
  src/main/java/de/tum/cit/aet/artemis/course/repository/CourseRepository.java \
  src/main/java/de/tum/cit/aet/artemis/course/repository/CourseRequestRepository.java
```

- [ ] **Step 3: Rewrite imports**

```bash
for cls in CourseRepository CourseRequestRepository; do
  git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.repository\.${cls};" -- '*.java' \
    | xargs sed -i '' "s|import de\.tum\.cit\.aet\.artemis\.core\.repository\.${cls};|import de.tum.cit.aet.artemis.course.repository.${cls};|g"
done
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Course module: move CourseRepository and CourseRequestRepository"
```

### Task 1.4: Move DTOs + rewrite imports

**Files moved:** 25 DTO files (see "Files to move" above; the 5 admin-facing ones are deliberately held back for Phase 2).

- [ ] **Step 1: git mv all DTO files**

```bash
DTOS="ActiveCourseDTO CourseCompetencyProgressDTO CourseContentCountDTO CourseCreateDTO CourseExistingExerciseDetailsDTO CourseForArchiveDTO CourseForDashboardDTO CourseForImportDTO CourseForQuizExerciseDTO CourseGroupsDTO CourseManagementDetailViewDTO CourseMaterialImportOptionsDTO CourseMaterialImportResultDTO CourseOperationProgressDTO CourseRequestCreateDTO CourseRequestDTO CourseRequestDecisionDTO CourseRequestRequesterDTO CourseScoresDTO CourseSummaryDTO CourseUpdateDTO CourseWithIdDTO CoursesForDashboardDTO OnlineCourseDTO OnlineResourceDTO"
for f in $DTOS; do
  git mv "src/main/java/de/tum/cit/aet/artemis/core/dto/${f}.java" \
         "src/main/java/de/tum/cit/aet/artemis/course/dto/${f}.java"
done
rm -f src/main/java/de/tum/cit/aet/artemis/course/dto/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.dto;|package de.tum.cit.aet.artemis.course.dto;|' \
  src/main/java/de/tum/cit/aet/artemis/course/dto/*.java
```

- [ ] **Step 3: Rewrite imports across the codebase**

```bash
for cls in $DTOS; do
  git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.dto\.${cls};" -- '*.java' \
    | xargs sed -i '' "s|import de\.tum\.cit\.aet\.artemis\.core\.dto\.${cls};|import de.tum.cit.aet.artemis.course.dto.${cls};|g" \
    2>/dev/null
done
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Course module: move 25 course DTOs out of core/dto"
```

### Task 1.5: Move services + rewrite imports

**Files moved:** 18 files from `core/service/course/` → `course/service/`.

- [ ] **Step 1: git mv (whole directory)**

```bash
for f in src/main/java/de/tum/cit/aet/artemis/core/service/course/*.java; do
  name=$(basename "$f")
  git mv "$f" "src/main/java/de/tum/cit/aet/artemis/course/service/${name}"
done
rmdir src/main/java/de/tum/cit/aet/artemis/core/service/course
rm -f src/main/java/de/tum/cit/aet/artemis/course/service/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.service\.course;|package de.tum.cit.aet.artemis.course.service;|' \
  src/main/java/de/tum/cit/aet/artemis/course/service/*.java
```

- [ ] **Step 3: Rewrite imports (single regex covers all classes under the old subpackage)**

```bash
git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.service\.course\." -- '*.java' \
  | xargs sed -i '' 's|import de\.tum\.cit\.aet\.artemis\.core\.service\.course\.|import de.tum.cit.aet.artemis.course.service.|g'
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Course module: move 18 course services out of core/service/course"
```

### Task 1.6: Move web resources + rewrite imports

**Files moved:** 8 files from `core/web/course/` → `course/web/`.

- [ ] **Step 1: git mv**

```bash
for f in src/main/java/de/tum/cit/aet/artemis/core/web/course/*.java; do
  name=$(basename "$f")
  git mv "$f" "src/main/java/de/tum/cit/aet/artemis/course/web/${name}"
done
rmdir src/main/java/de/tum/cit/aet/artemis/core/web/course
rm -f src/main/java/de/tum/cit/aet/artemis/course/web/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.web\.course;|package de.tum.cit.aet.artemis.course.web;|' \
  src/main/java/de/tum/cit/aet/artemis/course/web/*.java
```

- [ ] **Step 3: Rewrite imports**

```bash
git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.web\.course\." -- '*.java' \
  | xargs sed -i '' 's|import de\.tum\.cit\.aet\.artemis\.core\.web\.course\.|import de.tum.cit.aet.artemis.course.web.|g'
```

- [ ] **Step 4: Verify compile + run controller tests**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Course module: move 8 course REST resources out of core/web/course"
```

### Task 1.7: Move course-related tests

**Files moved:** every Java file under `src/test/java/de/tum/cit/aet/artemis/core/course/` and `core/service/course/` → corresponding paths under `course/`.

- [ ] **Step 1: Inventory test files**

```bash
find src/test/java/de/tum/cit/aet/artemis/core/course -type f -name "*.java"
find src/test/java/de/tum/cit/aet/artemis/core/service/course -type f -name "*.java"
```

Save the listing — it determines what gets moved.

- [ ] **Step 2: Create test directory skeleton**

```bash
mkdir -p src/test/java/de/tum/cit/aet/artemis/course/{architecture,util}
```

- [ ] **Step 3: Move test files**

```bash
# Move integration/web tests
for f in $(find src/test/java/de/tum/cit/aet/artemis/core/course -type f -name "*.java"); do
  rel="${f#src/test/java/de/tum/cit/aet/artemis/core/course/}"
  dest="src/test/java/de/tum/cit/aet/artemis/course/${rel}"
  mkdir -p "$(dirname "$dest")"
  git mv "$f" "$dest"
done

# Move service tests
for f in $(find src/test/java/de/tum/cit/aet/artemis/core/service/course -type f -name "*.java"); do
  rel="${f#src/test/java/de/tum/cit/aet/artemis/core/service/course/}"
  dest="src/test/java/de/tum/cit/aet/artemis/course/service/${rel}"
  mkdir -p "$(dirname "$dest")"
  git mv "$f" "$dest"
done

# Clean up empty source directories
rmdir src/test/java/de/tum/cit/aet/artemis/core/course 2>/dev/null
rmdir src/test/java/de/tum/cit/aet/artemis/core/service/course 2>/dev/null
```

- [ ] **Step 4: Rewrite package declarations in moved test files**

```bash
# Replace the old package roots with the new module root in every moved test
# (find walks the destination tree — git mv shows as rename, not add, so
#  `git diff --diff-filter=A` would miss these).
for f in $(find src/test/java/de/tum/cit/aet/artemis/course -type f -name "*.java"); do
  sed -i '' -E \
    -e 's|package de\.tum\.cit\.aet\.artemis\.core\.course([.;])|package de.tum.cit.aet.artemis.course\1|g' \
    -e 's|package de\.tum\.cit\.aet\.artemis\.core\.service\.course([.;])|package de.tum.cit.aet.artemis.course.service\1|g' \
    "$f"
done
```

- [ ] **Step 5: Rewrite test-cross-references**

```bash
git grep -l "de\.tum\.cit\.aet\.artemis\.core\.course\." -- 'src/test/**/*.java' \
  | xargs sed -i '' 's|de\.tum\.cit\.aet\.artemis\.core\.course\.|de.tum.cit.aet.artemis.course.|g'
git grep -l "de\.tum\.cit\.aet\.artemis\.core\.service\.course\." -- 'src/test/**/*.java' \
  | xargs sed -i '' 's|de\.tum\.cit\.aet\.artemis\.core\.service\.course\.|de.tum.cit.aet.artemis.course.service.|g'
```

- [ ] **Step 6: Verify test compile**

```bash
./gradlew compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Course module: move course-related server tests under artemis/course/"
```

### Task 1.8: Add module-level ArchUnit tests

**Files:**
- Create: `src/test/java/de/tum/cit/aet/artemis/course/architecture/CourseResourceArchitectureTest.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/course/architecture/CourseServiceArchitectureTest.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/course/architecture/CourseRepositoryArchitectureTest.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/course/architecture/CourseEntityUsageArchitectureTest.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/course/architecture/CourseCodeStyleArchitectureTest.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/course/architecture/CourseTestArchitectureTest.java`

- [ ] **Step 1: Write `CourseResourceArchitectureTest`**

```java
package de.tum.cit.aet.artemis.course.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class CourseResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".course";
    }
}
```

- [ ] **Step 2: Write the remaining five (identical body, only class name + parent differ)**

Repeat the pattern for `Service`, `Repository`, `EntityUsage`, `CodeStyle`, `Test` — mirror exactly what `lecture/architecture/Lecture*ArchitectureTest.java` does. Each is ~10 lines.

- [ ] **Step 3: Run the new tests**

```bash
./gradlew test --tests "de.tum.cit.aet.artemis.course.architecture.*" -x webapp
```

Expected: PASS. If a rule fails, the most likely cause is residual core-package code that wasn't moved — investigate before tweaking the test.

- [ ] **Step 4: Run the broader ArchUnit suite to confirm `core`'s rules still hold**

```bash
./gradlew test --tests "de.tum.cit.aet.artemis.core.architecture.*" -x webapp
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Course module: add ArchUnit rules for new module"
```

### Task 1.9: Full verification + Spotless

- [ ] **Step 1: Spotless + Checkstyle**

```bash
./gradlew spotlessApply -x webapp
./gradlew checkstyleMain checkstyleTest -x webapp
```

Expected: both succeed. Spotless will rewrite import order if needed — commit those tweaks.

- [ ] **Step 2: Full server test suite**

```bash
./gradlew test -x webapp 2>&1 | tee /tmp/phase1-test-output.txt
grep -E "tests completed|FAILED" /tmp/phase1-test-output.txt
```

Expected: same pass/skip counts as `/tmp/baseline-test-output.txt`. Any new failure is a Phase 1 regression — investigate before continuing.

- [ ] **Step 3: Commit any Spotless changes**

```bash
git add -A
git status
# Only commit if there are diffs:
git diff --cached --quiet || git commit -m "Course module: apply Spotless after extraction"
```

### Task 1.10: Open the Phase 1 PR

- [ ] **Step 1: Push**

```bash
git push -u origin feature/server-extract-course-module
```

- [ ] **Step 2: Open PR using the project template**

```bash
gh pr create --base develop --title "Development: Extract course module from core (server)" --body "$(cat <<'EOF'
## Summary
- Extracts course-related code out of the oversized `core` module into a new `course` module on the server side.
- Follows the package layout established by `calendar` (#12681) and the in-flight `account` reorganization.
- Pure refactor — no behavior change.

## Motivation and Context
`core` is the largest module in the project (~53k LOC server) and mixes Spring infrastructure with feature code. Course management is the single largest implicit submodule inside it (~10k LOC server + ~47k LOC client). Extracting it shrinks `core` to its true cross-cutting role and unblocks future module work in `admin`.

## Description
- Moves 50 Java files: 8 domain entities, 2 repositories, 25 DTOs, 18 services, 8 REST resources.
- Rewrites ~250 cross-module imports of `Course`/`CourseRepository` and related classes.
- Adds six module-level ArchUnit tests for the new `course` module.
- Moves all course-related server tests under `artemis/course/`.

## Steps for Testing
- [ ] `./gradlew test -x webapp` passes with no new failures.
- [ ] `./gradlew spotlessCheck checkstyleMain -x webapp` passes.
- [ ] `./gradlew test --tests 'de.tum.cit.aet.artemis.course.architecture.*' -x webapp` passes.
- [ ] Application starts and `/api/courses/...` endpoints continue to respond.

## Testserver States
n/a — refactor only.

## Review Progress

## Test Coverage
n/a — refactor only, no production behavior changed.

## Checklist
- [x] No behavior change
- [x] Spotless + Checkstyle clean
- [x] All existing tests still pass
- [x] New module has its own ArchUnit rules
- [ ] CODEOWNERS / labeler updates → tracked separately in Phase 5

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 2: Server `admin` module

**Goal:** Move admin-only concerns (data export, vulnerability scanning, cleanup, telemetry, statistics, audit, organization, legal documents, course requests admin view) into a new `admin` module.

**Files to move (~65 Java files):**

Domain (10 files) — from `core/domain/` → `admin/domain/`:
- `CleanupJobExecution.java`
- `CleanupJobType.java`
- `DataExport.java`
- `DataExportState.java`
- `LLMRequest.java`
- `LLMServiceType.java`
- `LLMTokenUsageRequest.java`
- `LLMTokenUsageTrace.java`
- `LegalDocumentType.java`
- `MigrationChangelog.java`
- `Organization.java`
- `PersistentAuditEvent.java`
- `StatisticsView.java`
- `GraphType.java`

Repository (12 files) — from `core/repository/` → `admin/repository/`:
- `CustomAuditEventRepository.java`
- `CustomOrganizationRepository.java`
- `CustomOrganizationRepositoryImpl.java`
- `DataExportRepository.java`
- `LLMTokenUsageRequestRepository.java`
- `LLMTokenUsageTraceRepository.java`
- `MigrationChangeRepository.java`
- `OrganizationRepository.java`
- `OrganizationSpecs.java`
- `PersistenceAuditEventRepository.java`
- `StatisticsRepository.java`
- `core/repository/cleanup/CleanupJobExecutionRepository.java`

DTOs (~28 files) — from `core/dto/` → `admin/dto/`:
- `AuditingEntityDTO.java`
- `CleanupServiceExecutionRecordDTO.java`
- `CombinedSbomDTO.java`
- `ComponentVulnerabilitiesDTO.java`
- `ComponentWithVulnerabilitiesDTO.java`
- `CourseManagementOverviewExerciseStatisticsDTO.java` (held back from Phase 1)
- `CourseManagementOverviewStatisticsDTO.java`
- `CourseManagementStatisticsDTO.java`
- `CourseRequestsAdminOverviewDTO.java`
- `CourseStatisticsAverageScore.java`
- `DataExportAdminDTO.java`
- `DataExportDTO.java`
- `ImprintDTO.java`
- `LegalDocument.java`
- `NonLatestNonRatedResultsCleanupCountDTO.java`
- `NonLatestRatedResultsCleanupCountDTO.java`
- `OrganizationCountDTO.java`
- `OrganizationCourseDTO.java`
- `OrganizationDTO.java`
- `OrganizationMemberDTO.java`
- `OrphanCleanupCountDTO.java`
- `PlagiarismComparisonCleanupCountDTO.java`
- `PrivacyStatementDTO.java`
- `RequestDataExportDTO.java`
- `SbomComponentDTO.java`
- `SbomDTO.java`
- `SbomMetadataDTO.java`
- `StatisticsEntry.java`
- `SubmissionVersionsCleanupCountDTO.java`
- `VulnerabilityDTO.java`

Services (~26 files):
- Loose: `AuditEventService.java`, `DataExportScheduleService.java`, `LegalDocumentService.java`, `LLMTokenUsageService.java`, `OrganizationService.java`, `RateLimitConfigurationService.java`, `RateLimitService.java`, `SbomService.java`, `StatisticsService.java`, `VulnerabilityScanScheduleService.java`, `VulnerabilityService.java`
- Subdir `core/service/cleanup/` (1 file): `DataCleanupService.java`
- Subdir `core/service/export/` (14 files): `CourseExamExportService.java`, `CourseStudentDataExportService.java`, `DataExportCommunicationDataService.java`, `DataExportCompetencyProgressService.java`, `DataExportCreationService.java`, `DataExportExamCreationService.java`, `DataExportExerciseCreationService.java`, `DataExportIrisService.java`, `DataExportLearnerProfileService.java`, `DataExportQuizExerciseCreationService.java`, `DataExportScienceEventService.java`, `DataExportService.java`, `DataExportTutorialGroupService.java`, `DataExportUtil.java`
- Subdir `core/service/telemetry/` (2 files): `TelemetrySendingService.java`, `TelemetryService.java`

Web (16 files) — from `core/web/admin/` → `admin/web/`:
- All `Admin*Resource.java` files listed earlier.

Tests:
- `src/test/java/de/tum/cit/aet/artemis/core/dataexport/` → `admin/dataexport/`
- `src/test/java/de/tum/cit/aet/artemis/core/organization/` → `admin/organization/`
- `src/test/java/de/tum/cit/aet/artemis/core/management/` → `admin/management/`

### Task 2.1: Create branch + skeleton

- [ ] **Step 1: Branch off freshly-rebased develop**

```bash
git switch develop
git pull
git switch -c feature/server-extract-admin-module
```

- [ ] **Step 2: Create empty package skeleton**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/admin/{domain,repository,dto,service,web,architecture}
mkdir -p src/test/java/de/tum/cit/aet/artemis/admin/architecture
for d in domain repository dto service web architecture; do
  touch "src/main/java/de/tum/cit/aet/artemis/admin/$d/.gitkeep"
done
git add src/main/java/de/tum/cit/aet/artemis/admin/
git commit -m "Admin module: create empty package skeleton"
```

### Task 2.2: Move domain entities + rewrite imports

- [ ] **Step 1: git mv 14 domain files**

```bash
DOMAIN_ADMIN="CleanupJobExecution CleanupJobType DataExport DataExportState LLMRequest LLMServiceType LLMTokenUsageRequest LLMTokenUsageTrace LegalDocumentType MigrationChangelog Organization PersistentAuditEvent StatisticsView GraphType"
for f in $DOMAIN_ADMIN; do
  git mv "src/main/java/de/tum/cit/aet/artemis/core/domain/${f}.java" \
         "src/main/java/de/tum/cit/aet/artemis/admin/domain/${f}.java"
done
rm -f src/main/java/de/tum/cit/aet/artemis/admin/domain/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.domain;|package de.tum.cit.aet.artemis.admin.domain;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/domain/*.java
```

- [ ] **Step 3: Rewrite imports**

```bash
for cls in $DOMAIN_ADMIN; do
  git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.domain\.${cls};" -- '*.java' \
    | xargs sed -i '' "s|import de\.tum\.cit\.aet\.artemis\.core\.domain\.${cls};|import de.tum.cit.aet.artemis.admin.domain.${cls};|g" \
    2>/dev/null
done
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Admin module: move 14 admin domain entities out of core"
```

### Task 2.3: Move repositories + rewrite imports

- [ ] **Step 1: git mv**

```bash
REPO_ADMIN="CustomAuditEventRepository CustomOrganizationRepository CustomOrganizationRepositoryImpl DataExportRepository LLMTokenUsageRequestRepository LLMTokenUsageTraceRepository MigrationChangeRepository OrganizationRepository OrganizationSpecs PersistenceAuditEventRepository StatisticsRepository"
for f in $REPO_ADMIN; do
  git mv "src/main/java/de/tum/cit/aet/artemis/core/repository/${f}.java" \
         "src/main/java/de/tum/cit/aet/artemis/admin/repository/${f}.java"
done

# Cleanup subdir
git mv src/main/java/de/tum/cit/aet/artemis/core/repository/cleanup/CleanupJobExecutionRepository.java \
       src/main/java/de/tum/cit/aet/artemis/admin/repository/CleanupJobExecutionRepository.java
rmdir src/main/java/de/tum/cit/aet/artemis/core/repository/cleanup
rm -f src/main/java/de/tum/cit/aet/artemis/admin/repository/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.repository\(\.cleanup\)\?;|package de.tum.cit.aet.artemis.admin.repository;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/repository/*.java
```

- [ ] **Step 3: Rewrite imports**

```bash
for cls in $REPO_ADMIN CleanupJobExecutionRepository; do
  git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.repository\(\.cleanup\)\?\.${cls};" -- '*.java' \
    | xargs sed -i '' "s|import de\.tum\.cit\.aet\.artemis\.core\.repository\(\.cleanup\)\?\.${cls};|import de.tum.cit.aet.artemis.admin.repository.${cls};|g" \
    2>/dev/null
done
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Admin module: move 12 admin repositories out of core"
```

### Task 2.4: Move DTOs + rewrite imports

- [ ] **Step 1: git mv 29 DTOs**

```bash
DTOS_ADMIN="AuditingEntityDTO CleanupServiceExecutionRecordDTO CombinedSbomDTO ComponentVulnerabilitiesDTO ComponentWithVulnerabilitiesDTO CourseManagementOverviewExerciseStatisticsDTO CourseManagementOverviewStatisticsDTO CourseManagementStatisticsDTO CourseRequestsAdminOverviewDTO CourseStatisticsAverageScore DataExportAdminDTO DataExportDTO ImprintDTO LegalDocument NonLatestNonRatedResultsCleanupCountDTO NonLatestRatedResultsCleanupCountDTO OrganizationCountDTO OrganizationCourseDTO OrganizationDTO OrganizationMemberDTO OrphanCleanupCountDTO PlagiarismComparisonCleanupCountDTO PrivacyStatementDTO RequestDataExportDTO SbomComponentDTO SbomDTO SbomMetadataDTO StatisticsEntry SubmissionVersionsCleanupCountDTO VulnerabilityDTO"
for f in $DTOS_ADMIN; do
  if [ -f "src/main/java/de/tum/cit/aet/artemis/core/dto/${f}.java" ]; then
    git mv "src/main/java/de/tum/cit/aet/artemis/core/dto/${f}.java" \
           "src/main/java/de/tum/cit/aet/artemis/admin/dto/${f}.java"
  fi
done
rm -f src/main/java/de/tum/cit/aet/artemis/admin/dto/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.dto;|package de.tum.cit.aet.artemis.admin.dto;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/dto/*.java
```

- [ ] **Step 3: Rewrite imports**

```bash
for cls in $DTOS_ADMIN; do
  git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.dto\.${cls};" -- '*.java' \
    | xargs sed -i '' "s|import de\.tum\.cit\.aet\.artemis\.core\.dto\.${cls};|import de.tum.cit.aet.artemis.admin.dto.${cls};|g" \
    2>/dev/null
done
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Admin module: move 29 admin DTOs out of core/dto"
```

### Task 2.5: Move services + rewrite imports

- [ ] **Step 1: git mv loose services (11 files)**

```bash
SVC_LOOSE="AuditEventService DataExportScheduleService LegalDocumentService LLMTokenUsageService OrganizationService RateLimitConfigurationService RateLimitService SbomService StatisticsService VulnerabilityScanScheduleService VulnerabilityService"
for f in $SVC_LOOSE; do
  git mv "src/main/java/de/tum/cit/aet/artemis/core/service/${f}.java" \
         "src/main/java/de/tum/cit/aet/artemis/admin/service/${f}.java"
done
```

- [ ] **Step 2: git mv whole subdirs (cleanup, export, telemetry)**

```bash
# cleanup
git mv src/main/java/de/tum/cit/aet/artemis/core/service/cleanup/DataCleanupService.java \
       src/main/java/de/tum/cit/aet/artemis/admin/service/DataCleanupService.java
rmdir src/main/java/de/tum/cit/aet/artemis/core/service/cleanup

# export (14 files)
mkdir -p src/main/java/de/tum/cit/aet/artemis/admin/service/export
for f in src/main/java/de/tum/cit/aet/artemis/core/service/export/*.java; do
  name=$(basename "$f")
  git mv "$f" "src/main/java/de/tum/cit/aet/artemis/admin/service/export/${name}"
done
rmdir src/main/java/de/tum/cit/aet/artemis/core/service/export

# telemetry (2 files)
mkdir -p src/main/java/de/tum/cit/aet/artemis/admin/service/telemetry
for f in src/main/java/de/tum/cit/aet/artemis/core/service/telemetry/*.java; do
  name=$(basename "$f")
  git mv "$f" "src/main/java/de/tum/cit/aet/artemis/admin/service/telemetry/${name}"
done
rmdir src/main/java/de/tum/cit/aet/artemis/core/service/telemetry

rm -f src/main/java/de/tum/cit/aet/artemis/admin/service/.gitkeep
```

- [ ] **Step 3: Rewrite package declarations**

```bash
# Loose services
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.service;|package de.tum.cit.aet.artemis.admin.service;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/service/*.java
# cleanup → flat into admin/service
# (DataCleanupService was in core.service.cleanup; was moved to admin.service)
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.service\.cleanup;|package de.tum.cit.aet.artemis.admin.service;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/service/DataCleanupService.java
# export
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.service\.export;|package de.tum.cit.aet.artemis.admin.service.export;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/service/export/*.java
# telemetry
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.service\.telemetry;|package de.tum.cit.aet.artemis.admin.service.telemetry;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/service/telemetry/*.java
```

- [ ] **Step 4: Rewrite imports**

```bash
# Loose: rewrite each named class to avoid accidental matches on prefixes
for cls in $SVC_LOOSE; do
  git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.service\.${cls};" -- '*.java' \
    | xargs sed -i '' "s|import de\.tum\.cit\.aet\.artemis\.core\.service\.${cls};|import de.tum.cit.aet.artemis.admin.service.${cls};|g" \
    2>/dev/null
done

# DataCleanupService — was in core.service.cleanup
git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.service\.cleanup\.DataCleanupService;" -- '*.java' \
  | xargs sed -i '' 's|import de\.tum\.cit\.aet\.artemis\.core\.service\.cleanup\.DataCleanupService;|import de.tum.cit.aet.artemis.admin.service.DataCleanupService;|g' \
  2>/dev/null

# Subpackages keep their leaf name
git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.service\.export\." -- '*.java' \
  | xargs sed -i '' 's|import de\.tum\.cit\.aet\.artemis\.core\.service\.export\.|import de.tum.cit.aet.artemis.admin.service.export.|g'
git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.service\.telemetry\." -- '*.java' \
  | xargs sed -i '' 's|import de\.tum\.cit\.aet\.artemis\.core\.service\.telemetry\.|import de.tum.cit.aet.artemis.admin.service.telemetry.|g'
```

- [ ] **Step 5: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Admin module: move admin services out of core/service

Moves 28 services: 11 loose top-level, DataCleanupService from
core/service/cleanup, 14 services from core/service/export, and 2
from core/service/telemetry. Updates imports across the codebase.
EOF
)"
```

### Task 2.6: Move web resources + rewrite imports

- [ ] **Step 1: git mv all 16 admin resources**

```bash
for f in src/main/java/de/tum/cit/aet/artemis/core/web/admin/*.java; do
  name=$(basename "$f")
  git mv "$f" "src/main/java/de/tum/cit/aet/artemis/admin/web/${name}"
done
rmdir src/main/java/de/tum/cit/aet/artemis/core/web/admin
rm -f src/main/java/de/tum/cit/aet/artemis/admin/web/.gitkeep
```

- [ ] **Step 2: Rewrite package declarations**

```bash
sed -i '' 's|package de\.tum\.cit\.aet\.artemis\.core\.web\.admin;|package de.tum.cit.aet.artemis.admin.web;|' \
  src/main/java/de/tum/cit/aet/artemis/admin/web/*.java
```

- [ ] **Step 3: Rewrite imports**

```bash
git grep -l "import de\.tum\.cit\.aet\.artemis\.core\.web\.admin\." -- '*.java' \
  | xargs sed -i '' 's|import de\.tum\.cit\.aet\.artemis\.core\.web\.admin\.|import de.tum.cit.aet.artemis.admin.web.|g'
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Admin module: move 16 admin REST resources out of core/web/admin"
```

### Task 2.7: Move admin tests

- [ ] **Step 1: Move whole test subtrees**

```bash
# dataexport tests
mkdir -p src/test/java/de/tum/cit/aet/artemis/admin/dataexport
for f in $(find src/test/java/de/tum/cit/aet/artemis/core/dataexport -type f -name "*.java"); do
  rel="${f#src/test/java/de/tum/cit/aet/artemis/core/dataexport/}"
  dest="src/test/java/de/tum/cit/aet/artemis/admin/dataexport/${rel}"
  mkdir -p "$(dirname "$dest")"
  git mv "$f" "$dest"
done
rmdir src/test/java/de/tum/cit/aet/artemis/core/dataexport 2>/dev/null

# organization tests
mkdir -p src/test/java/de/tum/cit/aet/artemis/admin/organization
for f in $(find src/test/java/de/tum/cit/aet/artemis/core/organization -type f -name "*.java"); do
  rel="${f#src/test/java/de/tum/cit/aet/artemis/core/organization/}"
  dest="src/test/java/de/tum/cit/aet/artemis/admin/organization/${rel}"
  mkdir -p "$(dirname "$dest")"
  git mv "$f" "$dest"
done
rmdir src/test/java/de/tum/cit/aet/artemis/core/organization 2>/dev/null

# management tests
mkdir -p src/test/java/de/tum/cit/aet/artemis/admin/management
for f in $(find src/test/java/de/tum/cit/aet/artemis/core/management -type f -name "*.java"); do
  rel="${f#src/test/java/de/tum/cit/aet/artemis/core/management/}"
  dest="src/test/java/de/tum/cit/aet/artemis/admin/management/${rel}"
  mkdir -p "$(dirname "$dest")"
  git mv "$f" "$dest"
done
rmdir src/test/java/de/tum/cit/aet/artemis/core/management 2>/dev/null
```

- [ ] **Step 2: Rewrite package declarations in moved tests**

```bash
for f in $(find src/test/java/de/tum/cit/aet/artemis/admin -type f -name "*.java"); do
  sed -i '' -E \
    -e 's|package de\.tum\.cit\.aet\.artemis\.core\.dataexport([.;])|package de.tum.cit.aet.artemis.admin.dataexport\1|g' \
    -e 's|package de\.tum\.cit\.aet\.artemis\.core\.organization([.;])|package de.tum.cit.aet.artemis.admin.organization\1|g' \
    -e 's|package de\.tum\.cit\.aet\.artemis\.core\.management([.;])|package de.tum.cit.aet.artemis.admin.management\1|g' \
    "$f"
done
```

- [ ] **Step 3: Rewrite test cross-references**

```bash
git grep -l "de\.tum\.cit\.aet\.artemis\.core\.dataexport\." -- 'src/test/**/*.java' \
  | xargs sed -i '' 's|de\.tum\.cit\.aet\.artemis\.core\.dataexport\.|de.tum.cit.aet.artemis.admin.dataexport.|g'
git grep -l "de\.tum\.cit\.aet\.artemis\.core\.organization\." -- 'src/test/**/*.java' \
  | xargs sed -i '' 's|de\.tum\.cit\.aet\.artemis\.core\.organization\.|de.tum.cit.aet.artemis.admin.organization.|g'
git grep -l "de\.tum\.cit\.aet\.artemis\.core\.management\." -- 'src/test/**/*.java' \
  | xargs sed -i '' 's|de\.tum\.cit\.aet\.artemis\.core\.management\.|de.tum.cit.aet.artemis.admin.management.|g'
```

- [ ] **Step 4: Verify test compile**

```bash
./gradlew compileTestJava -x webapp 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Admin module: move admin-related server tests under artemis/admin/"
```

### Task 2.8: Add module-level ArchUnit tests for admin

Same pattern as Task 1.8 — create 6 architecture tests under `src/test/java/de/tum/cit/aet/artemis/admin/architecture/`. Each is ~10 lines, all returning `ARTEMIS_PACKAGE + ".admin"`.

- [ ] **Step 1: Write all six tests** (model after `lecture/architecture/Lecture*ArchitectureTest.java`)

```java
// src/test/java/de/tum/cit/aet/artemis/admin/architecture/AdminResourceArchitectureTest.java
package de.tum.cit.aet.artemis.admin.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class AdminResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".admin";
    }
}
```

Plus identical class bodies for `AdminServiceArchitectureTest`, `AdminRepositoryArchitectureTest`, `AdminEntityUsageArchitectureTest`, `AdminCodeStyleArchitectureTest`, `AdminTestArchitectureTest`.

- [ ] **Step 2: Run them**

```bash
./gradlew test --tests "de.tum.cit.aet.artemis.admin.architecture.*" -x webapp
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "Admin module: add ArchUnit rules for new module"
```

### Task 2.9: Full verification + open PR

- [ ] **Step 1: Spotless + Checkstyle**

```bash
./gradlew spotlessApply -x webapp
./gradlew checkstyleMain checkstyleTest -x webapp
git diff --quiet || git commit -am "Admin module: apply Spotless after extraction"
```

- [ ] **Step 2: Full test suite**

```bash
./gradlew test -x webapp 2>&1 | tee /tmp/phase2-test-output.txt
grep -E "tests completed|FAILED" /tmp/phase2-test-output.txt
```

Expected: same pass count as baseline.

- [ ] **Step 3: Push and open PR**

```bash
git push -u origin feature/server-extract-admin-module
gh pr create --base develop --title "Development: Extract admin module from core (server)" --body "$(cat <<'EOF'
## Summary
- Extracts admin-only concerns (data export, vulnerability scan, cleanup, telemetry, statistics, audit, organization, legal docs, course-requests admin views) into a new `admin` module.
- Continues the `core`-decomposition started by `calendar` (#12681), `account`, and the parallel `course` extraction.

## Motivation and Context
After Phase 1 (course), `core` still bundles ~25k LOC of admin-only operations that have no business-domain coupling to the rest of `core`. Splitting these out makes ROLE_ADMIN code paths visible at the package level and lets admin features (SBOM, vulnerability scan, cleanup) evolve independently of the framework layer.

## Description
- Moves ~65 Java files: 14 domain entities, 12 repositories, 29 DTOs, 28 services, 16 REST resources.
- Moves admin-related server tests under `artemis/admin/`.
- Adds six module-level ArchUnit tests.

## Steps for Testing
- [ ] `./gradlew test -x webapp` passes with no new failures.
- [ ] `./gradlew spotlessCheck checkstyleMain -x webapp` passes.
- [ ] Admin UI endpoints (`/api/admin/...`) still respond.

## Testserver States
n/a — refactor only.

## Review Progress

## Test Coverage
n/a — refactor only.

## Checklist
- [x] No behavior change
- [x] All existing tests still pass
- [x] New module has its own ArchUnit rules

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 3: Client `course` module

**Goal:** Move `src/main/webapp/app/core/course/` (304 files, ~47k LOC) to `src/main/webapp/app/course/`. Update all import paths and route declarations.

**Directories moved (4 subtrees):**
- `app/core/course/manage/` (control-center, course-exercise-card, course-group-membership, course-lti-configuration, course-management, course-management-container, course-material-import, course-operation-progress, course-scores, detail, exercises, exercises-search, header-course, image-cropper-modal, onboarding, overview, quick-actions, services, statistics, update, user-management-dropdown, plus `course-management.route.ts`)
- `app/core/course/overview/` (course-archive, course-card, course-card-header, course-dashboard, course-exercises, course-overview, course-registration, course-settings, course-statistics, course-unenrollment-modal, courses, exercise-details, services, setup-passkey-modal, submission-result-status, visualizations, plus `courses.route.ts`)
- `app/core/course/request/`
- `app/core/course/shared/`

### Task 3.1: Branch + move all four subtrees

- [ ] **Step 1: Branch**

```bash
git switch develop
git pull
git switch -c feature/client-extract-course-module
```

- [ ] **Step 2: Move directories** (single atomic move per subtree)

```bash
mkdir -p src/main/webapp/app/course
for sub in manage overview request shared; do
  git mv "src/main/webapp/app/core/course/${sub}" "src/main/webapp/app/course/${sub}"
done
# core/course should now be empty
rmdir src/main/webapp/app/core/course
```

- [ ] **Step 3: Verify `app/core/course` is gone and `app/course` is populated**

```bash
ls src/main/webapp/app/core/course 2>&1   # should fail "no such file"
ls src/main/webapp/app/course/             # should list manage overview request shared
find src/main/webapp/app/course -type f | wc -l   # should be ~304
```

### Task 3.2: Rewrite import paths

The codebase uses two kinds of imports for these files:
- `from 'app/core/course/...'`
- `from './core/course/...'` (relative, mostly from `app.routes.ts`)
- `from '../core/course/...'` and other relative variants from sibling modules

- [ ] **Step 1: Rewrite absolute `app/` imports**

```bash
grep -rln "from 'app/core/course/" src/main/webapp src/test/playwright 2>/dev/null \
  | xargs sed -i '' "s|from 'app/core/course/|from 'app/course/|g"

# Also catch double-quoted variants
grep -rln 'from "app/core/course/' src/main/webapp src/test/playwright 2>/dev/null \
  | xargs sed -i '' 's|from "app/core/course/|from "app/course/|g'
```

- [ ] **Step 2: Rewrite relative imports** (mostly `app.routes.ts` and adjacent files)

```bash
grep -rln "from './core/course/" src/main/webapp 2>/dev/null \
  | xargs sed -i '' "s|from './core/course/|from './course/|g"
grep -rln "from '\.\./core/course/" src/main/webapp 2>/dev/null \
  | xargs sed -i '' "s|from '\.\./core/course/|from '\.\./course/|g"
```

- [ ] **Step 3: Update any TypeScript path aliases / `tsconfig.json`** if `app/core/course` is mapped explicitly

```bash
grep -n "core/course" src/main/webapp/tsconfig*.json tsconfig*.json 2>/dev/null
```

If any path mapping mentions `core/course`, update it to `course`. If no output, skip.

- [ ] **Step 4: Sanity check — no remaining references**

```bash
grep -rn "core/course/" src/main/webapp src/test/playwright 2>/dev/null | grep -v ".d.ts" | head
```

Expected: empty (or only false positives in generated openapi files — those use the URL "core/course/..." in API path strings, which are runtime URLs not imports; **do not** rewrite those).

- [ ] **Step 5: Update `app.routes.ts` explicit reference**

```bash
sed -n '210,212p' src/main/webapp/app/app.routes.ts
```

The line should now read `loadChildren: () => import('./course/manage/course-management.route')...` after Step 2 rewrote it. Verify visually.

### Task 3.3: Verify client build + tests

- [ ] **Step 1: Type-check + build**

```bash
pnpm run webapp:build 2>&1 | tail -30
```

Expected: build succeeds. If TS errors point at missing imports, repeat the regex with the specific subpath shown in the error.

- [ ] **Step 2: Lint**

```bash
pnpm run lint 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 3: Vitest run**

```bash
pnpm run vitest:run 2>&1 | tail -30
```

Expected: pass count matches baseline. If a test fails because of a path import, the fix is the same as Step 1.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Client: extract course module from core

Moves src/main/webapp/app/core/course/ (manage, overview, request,
shared subtrees) to src/main/webapp/app/course/ and updates ~600
import paths plus the lazy-loaded route declaration in app.routes.ts.
No behavior change.
EOF
)"
```

### Task 3.4: Open PR

- [ ] **Step 1: Push + PR**

```bash
git push -u origin feature/client-extract-course-module
gh pr create --base develop --title "Development: Extract course module from core (client)" --body "$(cat <<'EOF'
## Summary
- Moves all course-related Angular code from `app/core/course/` to `app/course/`.
- Mirrors the server-side `course` module extraction.

## Motivation and Context
`core/course/` is the single largest subtree under `core/` on the client (~47k LOC, 304 files). Splitting it out makes the module structure symmetric with the server.

## Description
- Moves four directories: `manage`, `overview`, `request`, `shared`.
- Rewrites ~600 import paths across the webapp and Playwright tests.
- Updates the lazy-loaded route in `app.routes.ts`.

## Steps for Testing
- [ ] `pnpm run webapp:build` succeeds.
- [ ] `pnpm run lint` passes.
- [ ] `pnpm run vitest:run` passes.
- [ ] Course list, course detail, and course management routes load in the dev server.

## Testserver States
n/a — refactor only.

## Review Progress

## Test Coverage
n/a — refactor only.

## Checklist
- [x] No behavior change
- [x] Build + lint + tests pass

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 4: Client `admin` module

**Goal:** Move `src/main/webapp/app/core/admin/` (213 files, ~27k LOC) to `src/main/webapp/app/admin/`.

**Directories moved (23 subdirs):** admin-container, admin-data-exports, admin-sbom, admin-sidebar, audits, cleanup-service, configuration, course-requests, features, health, legal, logs, lti-configuration, metrics, organization-management, passkey-management, shared, standardized-competencies, statistics, system-notification-management, upcoming-exams-and-exercises, user-management, websocket. Plus `admin.routes.ts`.

### Task 4.1: Branch + move

- [ ] **Step 1: Branch**

```bash
git switch develop
git pull
git switch -c feature/client-extract-admin-module
```

- [ ] **Step 2: Move whole subtree**

```bash
mkdir -p src/main/webapp/app/admin
for sub in admin-container admin-data-exports admin-sbom admin-sidebar audits cleanup-service configuration course-requests features health legal logs lti-configuration metrics organization-management passkey-management shared standardized-competencies statistics system-notification-management upcoming-exams-and-exercises user-management websocket; do
  git mv "src/main/webapp/app/core/admin/${sub}" "src/main/webapp/app/admin/${sub}"
done
git mv src/main/webapp/app/core/admin/admin.routes.ts src/main/webapp/app/admin/admin.routes.ts
rmdir src/main/webapp/app/core/admin
```

### Task 4.2: Rewrite imports

- [ ] **Step 1: Absolute imports**

```bash
grep -rln "from 'app/core/admin/" src/main/webapp src/test/playwright 2>/dev/null \
  | xargs sed -i '' "s|from 'app/core/admin/|from 'app/admin/|g"
grep -rln 'from "app/core/admin/' src/main/webapp src/test/playwright 2>/dev/null \
  | xargs sed -i '' 's|from "app/core/admin/|from "app/admin/|g'
```

- [ ] **Step 2: Relative imports (catches `app.routes.ts`'s `loadChildren: () => import('app/core/admin/admin.routes')`)**

```bash
grep -rln "from './core/admin/" src/main/webapp 2>/dev/null \
  | xargs sed -i '' "s|from './core/admin/|from './admin/|g"
grep -rln "from '\.\./core/admin/" src/main/webapp 2>/dev/null \
  | xargs sed -i '' "s|from '\.\./core/admin/|from '\.\./admin/|g"

# app.routes.ts uses import('app/core/admin/admin.routes') (string literal in dynamic import)
sed -i '' "s|import('app/core/admin/|import('app/admin/|g" \
  src/main/webapp/app/app.routes.ts
```

- [ ] **Step 3: Sanity check**

```bash
grep -rn "core/admin/" src/main/webapp 2>/dev/null | grep -v ".d.ts" | grep -v "openapi" | head
```

Expected: empty.

### Task 4.3: Verify + commit + PR

- [ ] **Step 1: Build / lint / vitest**

```bash
pnpm run webapp:build 2>&1 | tail -20
pnpm run lint 2>&1 | tail -10
pnpm run vitest:run 2>&1 | tail -20
```

Expected: all pass.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Client: extract admin module from core

Moves src/main/webapp/app/core/admin/ (23 subdirs + admin.routes.ts)
to src/main/webapp/app/admin/ and updates ~400 import paths plus the
lazy-loaded route declaration in app.routes.ts.
EOF
)"
```

- [ ] **Step 3: Push + PR** (analogous to Phase 3 Task 3.4 — adapt the body)

```bash
git push -u origin feature/client-extract-admin-module
gh pr create --base develop --title "Development: Extract admin module from core (client)" --body "$(cat <<'EOF'
## Summary
- Moves all admin-only Angular code from `app/core/admin/` to `app/admin/`.
- Mirrors the server-side `admin` module extraction.

## Motivation and Context
After course is split out, `core/admin/` is the second-largest subtree under `core/` on the client (~27k LOC, 213 files). Splitting it out completes the symmetry with the server and isolates ROLE_ADMIN UI from the rest of core.

## Description
- Moves 23 directories plus `admin.routes.ts`.
- Rewrites ~400 import paths across the webapp and Playwright tests.

## Steps for Testing
- [ ] `pnpm run webapp:build` succeeds.
- [ ] `pnpm run lint` passes.
- [ ] `pnpm run vitest:run` passes.
- [ ] `/admin/*` routes load in the dev server.

## Testserver States
n/a — refactor only.

## Review Progress

## Test Coverage
n/a — refactor only.

## Checklist
- [x] No behavior change
- [x] Build + lint + tests pass

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 5: Repo metadata (CODEOWNERS, labeler, docs, README)

**Goal:** Mirror the metadata pattern from commit `b6aec2d6` (which added entries for account/calendar/globalsearch/videosource) for the new `course` and `admin` modules. Can be a single PR opened after Phases 1–4 land.

**Files to update:**
- `README.md` — maintainers table.
- `.github/CODEOWNERS` — server + client + server-test paths for each new module.
- `.github/labeler.yml` — `module:course`, `module:admin` labels.
- `.github/issue-labeler.yml` — keyword matchers; move admin keywords (sbom, vulnerability, cleanup, data export, telemetry, organization, legal, imprint, privacy, audit) out of the `core` block and into a new `admin` block; move course keywords (course, enrollment, course request, course archive, course material import) out of `core` and into a new `course` block.
- `CLAUDE.md` project-structure list — add lines for `course` and `admin`.

### Task 5.1: Branch + edits

- [ ] **Step 1: Branch**

```bash
git switch develop
git pull
git switch -c feature/extract-course-admin-metadata
```

- [ ] **Step 2: Update `README.md`** — find the maintainers table (search for `@krusche` or "Account") and add two new rows: `Course → @krusche`, `Admin → @krusche` (assignee to confirm).

```bash
grep -n "@krusche\|Account\|Calendar" README.md
```

Edit using the `Edit` tool around the located lines.

- [ ] **Step 3: Update `.github/CODEOWNERS`**

Look at the entries `b6aec2d6` added for `account` and `calendar`, then add analogous lines for `course` and `admin`. Each module gets three entries:

```text
src/main/java/de/tum/cit/aet/artemis/course/      @krusche
src/test/java/de/tum/cit/aet/artemis/course/      @krusche
src/main/webapp/app/course/                       @krusche

src/main/java/de/tum/cit/aet/artemis/admin/       @krusche
src/test/java/de/tum/cit/aet/artemis/admin/       @krusche
src/main/webapp/app/admin/                        @krusche
```

(Owner is `@krusche` based on b6aec2d6; confirm before submitting.)

- [ ] **Step 4: Update `.github/labeler.yml`**

Add label definitions for `module:course` and `module:admin`:

```yaml
module:course:
  - changed-files:
      - any-glob-to-any-file:
          - 'src/main/java/de/tum/cit/aet/artemis/course/**'
          - 'src/test/java/de/tum/cit/aet/artemis/course/**'
          - 'src/main/webapp/app/course/**'

module:admin:
  - changed-files:
      - any-glob-to-any-file:
          - 'src/main/java/de/tum/cit/aet/artemis/admin/**'
          - 'src/test/java/de/tum/cit/aet/artemis/admin/**'
          - 'src/main/webapp/app/admin/**'
```

(Use the existing labeler syntax — check `b6aec2d6` for the exact structure.)

- [ ] **Step 5: Update `.github/issue-labeler.yml`**

Move admin-related keywords (sbom, vulnerability, cleanup, "data export", telemetry, organization, imprint, "privacy statement", audit) out of the `core` block, into a new `admin` block. Move course-related keywords (course, enrollment, "course request", "course archive", "course material import") into a new `course` block.

- [ ] **Step 6: Update `CLAUDE.md`** — add the two new modules to the Project Structure → Server section:

```text
- `course/` - Course management, registration, archive, dashboard, statistics
- `admin/` - Admin operations: data export, vulnerability scan, cleanup, telemetry, organization mgmt, legal docs
```

Place alphabetically. Also add the matching client-side entries if the file lists those.

### Task 5.2: Commit + PR

- [ ] **Step 1: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Documentation: List course and admin modules and maintainers

Adds the two newly extracted modules to the places that enumerate
modules or maintainers, so PRs in those areas get the right labels
and reviewers.

- README maintainers table: Course + Admin -> @krusche.
- .github/CODEOWNERS: server, client, and server-test paths.
- .github/labeler.yml: module:course and module:admin labels.
- .github/issue-labeler.yml: keyword matchers; move admin/course
  keywords out of the core block.
- CLAUDE.md project structure: lists the two new modules.
EOF
)"
```

- [ ] **Step 2: Push + PR** (uses the project template; standard refactor description).

---

## Phase 6: Long-tail cleanup

**Goal:** Audit what's left in `core/dto/` after Phase 2 and route remaining course/admin-shaped DTOs to the right home. This phase is intentionally small.

### Task 6.1: Audit residue

- [ ] **Step 1: List remaining `core/dto/`**

```bash
ls src/main/java/de/tum/cit/aet/artemis/core/dto/ | sort
```

- [ ] **Step 2: For each remaining DTO, identify the right home** (course, admin, or stays in core because it's genuinely cross-cutting). Use a table to track decisions:

```bash
for f in src/main/java/de/tum/cit/aet/artemis/core/dto/*.java; do
  base=$(basename "$f")
  echo "=== $base ==="
  head -1 "$f"
  grep -c "import de\.tum\.cit\.aet\.artemis" "$f" || true
done
```

Then move any that clearly belong to course or admin using the same `git mv` + import-rewrite pattern as previous tasks. DTOs that are truly cross-cutting (`AuditingEntityDTO`, `DomainObjectDTO`, `DueDateStat`, generic image/legal types) stay in `core`.

- [ ] **Step 3: Per-DTO commit** — one commit per DTO move keeps reviews trivial.

- [ ] **Step 4: Open PR** when batch is done.

---

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| `git mv` followed by sed disagrees in case-sensitive corner (e.g. `OnlineCourseDTO` vs `OnlineCourseDto`). | Each task ends with `./gradlew compileJava compileTestJava`. Compile failure is immediate and points at the file. |
| `Course` rename breaks a generated SQL/Hibernate name that's referenced in a Liquibase changelog. | Liquibase changelogs reference column/table names, not Java packages. The `@Table(name = "course")` annotation stays inside `Course.java` and moves with it. Still: run the application against a staging DB after Phase 1 lands. |
| Phase 1 PR is too big to review (~50 files moved + 250 import rewrites). | Tasks 1.2–1.6 each end with a commit. The PR has 5 incremental commits, reviewable independently. If still too large, split the PR into two: domain+repo (Tasks 1.2–1.3) vs DTO+service+web (Tasks 1.4–1.6). |
| Client `core/admin/admin.routes.ts` is referenced as `import('app/core/admin/admin.routes')` (string-literal dynamic import) — Phase 4 must catch this. | Task 4.2 Step 2 includes an explicit `sed` on `app.routes.ts` for the dynamic-import string literal. |
| ArchUnit `core` rules forbid `core.*` → `admin.*` references but `core` may still need to *call* admin APIs (e.g. for scheduling). | After Phase 2, audit `core.*` references to admin: `git grep "de.tum.cit.aet.artemis.admin" -- 'src/main/java/de/tum/cit/aet/artemis/core/**/*.java'`. If any exist, either (a) admin code shouldn't be in admin (re-evaluate), or (b) introduce an `admin/api/` facade like atlas/api/. |
| Multi-node E2E tests catch issues single-node misses (HazelcastPathSerializer registers explicit classes). | After each phase, run `./run-e2e-tests-local-multinode-fast.sh --filter "Course\|Admin"` to verify Hazelcast registration didn't break. |
| Merge conflicts between phases since they touch `core/*`. | Phases 1 and 3 can run in parallel (server vs client). Phases 2 and 4 likewise. But Phase 2 should land *after* Phase 1 because admin DTOs include 5 `CourseManagement*` DTOs originally held back. |

---

## Verification checklist (end of each phase)

Run before opening PR:

```bash
# Server
./gradlew compileJava compileTestJava -x webapp
./gradlew spotlessCheck checkstyleMain checkstyleTest -x webapp
./gradlew test -x webapp

# Module-specific ArchUnit
./gradlew test --tests "de.tum.cit.aet.artemis.<module>.architecture.*" -x webapp
./gradlew test --tests "de.tum.cit.aet.artemis.core.architecture.*" -x webapp

# Client
pnpm run webapp:build
pnpm run lint
pnpm run vitest:run

# Targeted E2E (skip if no UI change)
./run-e2e-tests-local-fast.sh --filter "<ModuleName>"
```

All must pass. Test counts must match baseline (`/tmp/baseline-test-output.txt`).

---

## Out of scope

- Splitting `programming` (separately analyzed; intra-module reorg only).
- Splitting `core/config/` (Hazelcast/Eureka/Redis stuff stays put).
- Moving the `Course` entity into a `core/api/` interface (would require redesigning ~250 callers; not justified for a pure refactor).
- Migrating ng-bootstrap modals in moved client files to PrimeNG (separate ongoing migration).
- Renaming the new `admin` module to `operations` (naming bikeshed; keep "admin" because it matches the URL prefix `/api/admin`, the role `ROLE_ADMIN`, and the existing client `admin.routes.ts`).
