# Notification and Programming Module Extractions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Plan covers four independent sub-projects executed in PR order; each sub-project is its own merge unit and must leave `develop` green on its own.

**Goal:** Reduce the two largest server modules (`programming` 87 k LOC, `communication` 34 k LOC) and the largest client modules (`programming` 67 k LOC, `communication` 44 k LOC) by extracting the four cleanly-separable internal sub-modules already present in their substructure: `notification` (from `communication`), and `jenkins` + `localvc` + `localci` (from `programming`). Maintain a 1:1 server-client module structure.

**Architecture:** Each extraction follows the precedent set by `course`/`admin` (PR #12784), `account` (PR #12712), and `calendar` (PR #12681) extractions:

1. Create new top-level package `de.tum.cit.aet.artemis.<module>` (server) and `app/<module>/` (client).
2. Move all files belonging to the module into the new package, preserving the internal sub-structure (`domain/`, `dto/`, `repository/`, `service/`, `web/`, `architecture/` on the server; `manage/`, `overview/`, `shared/`, `services/` on the client).
3. Update package declarations, imports, `@ComponentScan`-style configuration, and Spring Data `@EnableJpaRepositories.basePackages`.
4. Add module-level ArchUnit tests (`*CodeStyleArchitectureTest`, `*RepositoryArchitectureTest`, `*ResourceArchitectureTest`, `*ServiceArchitectureTest`, `*EntityUsageArchitectureTest`, `*TestArchitectureTest`) modelled on the existing ones for `course`, `admin`, `account`, `calendar`.
5. Add per-module Jacoco line/branch thresholds in `gradle/jacoco.gradle` and per-module client coverage thresholds in `supporting_scripts/code-coverage/module-coverage-client/check-client-module-coverage.mjs`.
6. Add per-module include/exclude patterns to `jest.config.js` and `vitest.config.ts`.
7. Ratchet the parent module's coverage threshold down where the extraction removed covered code.
8. Run the full server test suite + the full client Vitest + the Playwright fast suite locally before pushing.

**Tech Stack:** Spring Boot 4.0.6 (Java 25, Hibernate 7), Spring Data JPA, ArchUnit, Jacoco; Angular 21 (signal-based APIs), TypeScript, Vitest/Jest; Gradle 9.3, pnpm 11.

**Scope discipline — what this plan is NOT:** No application-layer refactoring. **Cross-module dependencies are preserved as direct method calls and direct imports.** We do not introduce `ApplicationEvent` / `@TransactionalEventListener` indirection, port interfaces, or anti-corruption layers. The extraction is mechanical: move files, fix package declarations, fix imports, register packages with Spring config, add ArchUnit module tests, add coverage thresholds. If `localci` calls into `localvc`, that call stays a direct call; the only change is the import path.

**Risk profile:** Without any dependency-inversion work, all four extractions are similar low-to-medium complexity. The relative ordering is `localci` (most files) > `localvc` (interlocked with `localci`) > `jenkins` ≈ `notification`. Recommended PR order: `notification → jenkins → localvc → localci`, so the simplest extractions validate the move-package tooling before the larger ones.

---

## Target end-state structure

### Server packages (after all four extractions)

```
src/main/java/de/tum/cit/aet/artemis/
├── communication/             # messaging only: posts, conversations, faqs, saved-posts, reactions, forwarded messages
├── notification/              # NEW — course/global/system/push notifications + mail service
├── programming/               # programming exercises domain + CRUD + grading + import/export + scheduling
├── jenkins/                   # NEW — Jenkins CI backend
├── localvc/                   # NEW — embedded git server (HTTP + SSH) + repo URI handling + vcs access log
├── localci/                   # NEW — local CI orchestration: queue, dispatch, result processing, websockets
└── (...all other existing modules unchanged...)
```

**Naming:** the new server packages are `de.tum.cit.aet.artemis.notification`, `de.tum.cit.aet.artemis.jenkins`, `de.tum.cit.aet.artemis.localvc`, `de.tum.cit.aet.artemis.localci`. Single-word lowercase Java package segments matching the convention used by `buildagent`, `tutorialgroup`, `globalsearch`, `videosource`. The directory names on disk match the package segments. Client directories use the same single-word names: `app/notification/`, `app/jenkins/`, `app/localvc/`, `app/localci/`.

### Client modules (after all four extractions)

```
src/main/webapp/app/
├── communication/      # messaging only
├── notification/       # NEW — mirrors server
├── programming/        # programming exercises UI (manage, overview, shared)
├── localvc/            # NEW — repository view, commit history, SSH key management UI
├── localci/            # NEW — build queue, build job, build agent management UI (absorbs the entire current app/buildagent/)
└── (...all other existing modules unchanged...)
```

**Removed in this refactor:** `app/buildagent/` (current 57 files, ~8,550 lines) — its entire content moves to `app/localci/`. Rationale: the build-agent management UI (build queue, build job detail, agent details, agent summary, statistics, distributed-data clearing) is served by **core nodes**, not by build-agent nodes (which are headless workers), and it talks to LocalCI REST endpoints (`BuildJobQueueResource`, `BuildLogResource`). Keeping it under `app/buildagent/` is misleading. The server-side `de.tum.cit.aet.artemis.buildagent` module (the worker-side runtime) stays as-is — that one is genuinely the agent-side code.

**No `app/jenkins/` module is created.** Jenkins has its own admin UI; Artemis has no Jenkins-specific client surface. **No `app/notification/` module is created if the only client move is the `course-notification/` subtree** — wait, there IS a substantial notification UI subtree (~5 k lines, 40 files), so `app/notification/` IS created. Skip-rule applies only to `jenkins`.

If a sub-project's client surface is genuinely empty (only `jenkins` in this plan), document that in the sub-project's "Client scope" section and skip the client skeleton. **Do not create empty client modules.**

### Cross-module dependencies (preserved as direct calls)

After the extraction, these direct call paths intentionally remain:

| From | To | Why kept |
|---|---|---|
| `communication` | `notification` | Conversation messaging triggers notifications — direct call |
| `notification` | `communication` | Notifications reference `Post`/`Conversation` domain — direct import |
| `programming` | `localci` | Exercise lifecycle (create/delete) calls `LocalCITriggerService` / `LocalCIService` directly |
| `programming` | `localvc` | Exercise CRUD uses `GitService` / `LocalVCService` directly |
| `localci` | `localvc` | Build trigger reads from VC; `LocalVCPostPushHook` schedules a build |
| `localci` | `programming` | Build result processing writes to `ProgrammingSubmission` / `Result` domain |
| `jenkins` | `programming` | Jenkins backend reads/writes programming exercise domain |

This is a deliberate choice. ArchUnit module tests do **not** forbid these cross-module dependencies; they only enforce that classes live in their declared package and follow shape rules (Resources thin, Repositories non-public, etc.).

---

## Pre-flight (do once, before any sub-project)

### Task P1: Verify a clean baseline

- [ ] **Step 1: Confirm the branch is up to date with `develop`.**

```bash
git fetch origin
git rev-parse HEAD
git rev-parse origin/develop
git merge-base --is-ancestor origin/develop HEAD && echo "develop is ancestor of HEAD"
```

Expected: the `is-ancestor` check prints the message.

- [ ] **Step 2: Run the full server build + tests against the current state to confirm baseline green.**

```bash
./gradlew compileJava compileTestJava -x webapp
./gradlew test -x webapp --tests "de.tum.cit.aet.artemis.shared.architecture.*"
```

Expected: BUILD SUCCESSFUL. Save the architecture-test baseline output to `/tmp/arch-baseline.txt` so later ratchets can be compared.

- [ ] **Step 3: Record the current Jacoco baseline per module.**

```bash
./gradlew test jacocoTestReport -x webapp 2>&1 | tee /tmp/baseline-jacoco.log
```

Capture the per-module line/branch percentages from `build/reports/jacoco/test/jacocoTestReport.xml`. These become the floor for any ratchets.

- [ ] **Step 4: Confirm Playwright fast suite is green.**

```bash
./run-e2e-tests-local-fast.sh --filter "CourseManagement"
```

Expected: all tests pass. (This is the cheapest UI smoke test that touches the modules being extracted.)

### Task P2: Reusable extraction utilities

These scripts get used four times. Write them once at the start of the master branch.

- [ ] **Step 1: Create `scripts/extract-module/move-package.sh`.**

```bash
# scripts/extract-module/move-package.sh
#!/usr/bin/env bash
# Move a list of files into a new package, rewriting the `package` declaration
# in each Java file and updating all imports across the codebase.
#
# Usage: move-package.sh <old_pkg> <new_pkg> <file1> [file2 ...]
set -euo pipefail
OLD_PKG="$1"; shift
NEW_PKG="$1"; shift
NEW_DIR="src/main/java/$(echo "$NEW_PKG" | tr . /)"
mkdir -p "$NEW_DIR"
for f in "$@"; do
    base=$(basename "$f")
    # Rewrite the package declaration in-place
    sed -i '' "s|^package $OLD_PKG\b.*$|package $NEW_PKG;|" "$f"
    git mv "$f" "$NEW_DIR/$base"
done
# Update imports across the whole repo
grep -rlE "import +$OLD_PKG\." src/ --include='*.java' \
    | xargs -I{} sed -i '' "s|import $OLD_PKG\.|import $NEW_PKG.|g" {}
```

- [ ] **Step 2: Create `scripts/extract-module/ratchet-jacoco.mjs`.** Reads `build/reports/jacoco/test/jacocoTestReport.xml`, prints the current coverage per package, and emits a Gradle snippet to paste into `gradle/jacoco.gradle`.

```javascript
// scripts/extract-module/ratchet-jacoco.mjs
import { readFileSync } from 'node:fs';
import { XMLParser } from 'fast-xml-parser';
const xml = readFileSync(process.argv[2], 'utf-8');
const report = new XMLParser({ ignoreAttributes: false }).parse(xml).report;
const packages = Array.isArray(report.package) ? report.package : [report.package];
for (const p of packages) {
    const counters = Array.isArray(p.counter) ? p.counter : [p.counter];
    const line = counters.find((c) => c['@_type'] === 'LINE');
    const branch = counters.find((c) => c['@_type'] === 'BRANCH');
    const covered = +line['@_covered'];
    const missed = +line['@_missed'];
    const total = covered + missed;
    const pct = total ? (covered / total).toFixed(3) : 'n/a';
    console.log(`${p['@_name'].padEnd(60)} lines=${pct} (${covered}/${total}) branch-missed=${branch ? branch['@_missed'] : 'n/a'}`);
}
```

- [ ] **Step 3: Commit the utilities.**

```bash
git add scripts/extract-module/
git commit -m "build: utility scripts for module extraction (move-package, ratchet-jacoco)"
```

These utilities live on the branch that ships PR 1 (notification). PR 2/3/4 just reuse them.

---

## Sub-project A — Extract `notification` from `communication`

**PR title:** `Development: Move notification handling into separate module`

**PR scope, server side:** 19 services, 9 repositories, 14 domain classes, 6 REST resources, 1 sub-package for push notifications, the entire `notifications/` sub-package (mail, push, markdown renderers, single/group notification services). Estimated 10–12 k server lines plus tests. The remaining `communication` module keeps messaging only (posts, answer posts, conversations, faqs, saved posts, reactions, forwarded messages).

**PR scope, client side:** the `course-notification/` sub-tree (6 components, ~2 k lines) plus shared notification entities (~120 lines). Push notification UI is minimal; mail templates are server-rendered.

### A.0: Manifest of files to move

**Server — domain (move to `notification/domain/`):**

- `communication/domain/CourseNotification.java`
- `communication/domain/CourseNotificationParameter.java`
- `communication/domain/GlobalNotificationSetting.java`
- `communication/domain/GlobalNotificationType.java`
- `communication/domain/NotificationChannelOption.java`
- `communication/domain/SystemNotificationType.java`
- `communication/domain/UserCourseNotificationSettingPreset.java`
- `communication/domain/UserCourseNotificationSettingSpecification.java`
- `communication/domain/UserCourseNotificationStatus.java`
- `communication/domain/UserCourseNotificationStatusType.java`
- `communication/domain/course_notifications/` (entire sub-package)
- `communication/domain/notification/` (entire sub-package — contains `Notification.java`, `GroupNotification.java`, `SingleUserNotification.java`, `SystemNotification.java`, `NotificationType.java`, etc.)
- `communication/domain/push_notification/` (entire sub-package)
- `communication/domain/setting_presets/` (entire sub-package)

**Stay in `communication/domain/`:** `Post`, `AnswerPost`, `Reaction`, `ConversationParticipant`, `Conversation` sub-package, `Faq`, `SavedPost`, `ForwardedMessage`, `PostingType`, `PostSortCriterion`, `DisplayPriority`, `PostConstraints`, `Posting`, `ReactionConstraints`, validators, `UserRole`, `DefaultChannelType`, `ConversationType`, `ConversationNotificationRecipientSummary` (this last one is a borderline call — see Task A.7).

**Server — repository (move to `notification/repository/`):**

- `communication/repository/CourseNotificationParameterRepository.java`
- `communication/repository/CourseNotificationRepository.java`
- `communication/repository/GlobalNotificationSettingRepository.java`
- `communication/repository/MaintenanceEmailRecipientRepository.java`
- `communication/repository/PushNotificationDeviceConfigurationRepository.java`
- `communication/repository/SystemNotificationRepository.java`
- `communication/repository/UserCourseNotificationSettingPresetRepository.java`
- `communication/repository/UserCourseNotificationSettingSpecificationRepository.java`
- `communication/repository/UserCourseNotificationStatusRepository.java`

Plus any notification repos already under `communication/repository/notification/` (if present).

**Server — service (move to `notification/service/`):**

- All 15 `Course*Notification*Service.java` + `GlobalNotificationSettingService.java` + `SystemNotificationService.java` + `NotificationScheduleService.java` + `PushNotificationDeviceConfigurationCleanupService.java` + `UserCourseNotificationStatusService.java` from `communication/service/` (top-level).
- The entire `communication/service/notifications/` sub-package (mail, group/single notifications, markdown renderers, push notifications).

**Stay in `communication/service/`:** `AnswerMessageService`, `ConversationDataCleanupService`, `ConversationMessagingService`, `FaqImportService`, `FaqService`, `PostingService`, `ReactionService`, `SavedPostService`, `SavedPostScheduleService`, `WebsocketMessagingService`, `conversation/` sub-package, `linkpreview/` sub-package.

**Server — web (move to `notification/web/`):**

- `communication/web/CourseNotificationResource.java`
- `communication/web/GlobalNotificationSettingResource.java`
- `communication/web/PushNotificationResource.java`
- `communication/web/SystemNotificationResource.java`
- `communication/web/UserCourseNotificationSettingResource.java`
- `communication/web/UserCourseNotificationStatusResource.java`

**Server — dto:** identify any DTO that is only used by the notification resources/services above and move it. The `communication/dto/` directory must be scanned with grep at execution time.

**Server — config:** move any notification-only `@Configuration` classes; create `notification/config/NotificationConfiguration.java` if needed for `@EnableJpaRepositories` scoping.

**Client — components (move from `app/communication/course-notification/` to `app/notification/`):**

- The entire `course-notification/` sub-tree under `app/communication/`.
- `app/communication/shared/entities/course-notification/` (move under `app/notification/shared/entities/course-notification/`).
- Any standalone notification service file under `app/communication/conversations/service/` whose name contains `notification` (audit during execution).

### A.1: Create the new module skeleton

- [ ] **Step 1: Create directory structure.**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/notification/{domain,dto,repository,service,web,config}
mkdir -p src/test/java/de/tum/cit/aet/artemis/notification/{architecture,service,web}
mkdir -p src/main/webapp/app/notification/{course-notification,shared,services}
```

- [ ] **Step 2: Commit the empty skeleton with a `.gitkeep` file in each new directory so subsequent commits diff cleanly.**

```bash
for d in src/main/java/de/tum/cit/aet/artemis/notification/{domain,dto,repository,service,web,config} \
         src/test/java/de/tum/cit/aet/artemis/notification/{architecture,service,web} \
         src/main/webapp/app/notification/{course-notification,shared,services}; do
    touch "$d/.gitkeep"
done
git add src/main/java/de/tum/cit/aet/artemis/notification \
        src/test/java/de/tum/cit/aet/artemis/notification \
        src/main/webapp/app/notification
git commit -m "notification module: create skeleton directories"
```

### A.2: Move server domain classes

- [ ] **Step 1: Run the move-package script per file.** Use the manifest in A.0. Example for `CourseNotification`:

```bash
bash scripts/extract-module/move-package.sh \
    de.tum.cit.aet.artemis.communication.domain \
    de.tum.cit.aet.artemis.notification.domain \
    src/main/java/de/tum/cit/aet/artemis/communication/domain/CourseNotification.java
```

Repeat for each top-level domain file in the manifest.

- [ ] **Step 2: Move entire sub-packages by moving the directory then rewriting `package` lines and imports.**

```bash
git mv src/main/java/de/tum/cit/aet/artemis/communication/domain/course_notifications \
       src/main/java/de/tum/cit/aet/artemis/notification/domain/course_notifications
# Rewrite package declarations in the moved files
find src/main/java/de/tum/cit/aet/artemis/notification/domain/course_notifications -name '*.java' \
    -exec sed -i '' 's|^package de\.tum\.cit\.aet\.artemis\.communication\.domain\.course_notifications|package de.tum.cit.aet.artemis.notification.domain.course_notifications|' {} +
# Update imports across the codebase
grep -rlE "import +de\.tum\.cit\.aet\.artemis\.communication\.domain\.course_notifications\." src/ --include='*.java' \
    | xargs -I{} sed -i '' 's|de\.tum\.cit\.aet\.artemis\.communication\.domain\.course_notifications|de.tum.cit.aet.artemis.notification.domain.course_notifications|g' {}
```

Repeat verbatim for `notification/`, `push_notification/`, `setting_presets/` sub-packages.

- [ ] **Step 3: Verify with `./gradlew compileJava -x webapp` after each sub-package move.** Expected: clean compile. If a file in the staying-behind half of `communication` imports a moved class, the compile error pinpoints the missing import update.

- [ ] **Step 4: Commit.**

```bash
git add -A
git commit -m "notification module: move domain classes from communication.domain"
```

### A.3: Move server repositories

- [ ] **Step 1: For each repository in the manifest, run move-package.sh.**
- [ ] **Step 2: Verify compile.**
- [ ] **Step 3: Configure Spring Data to scan the new package.** Edit `src/main/java/de/tum/cit/aet/artemis/core/config/DatabaseConfiguration.java`:

```java
@EnableJpaRepositories(basePackages = {
    // ... existing packages ...
    "de.tum.cit.aet.artemis.notification.repository",
})
```

- [ ] **Step 4: Run `./gradlew test --tests "*RepositoryArchitectureTest" -x webapp`.** Expected: pass. (This is the most common failure mode — `@EnableJpaRepositories` not picking up the new package surfaces here.)

- [ ] **Step 5: Commit.**

```bash
git add -A
git commit -m "notification module: move repositories and register with @EnableJpaRepositories"
```

### A.4: Move server services

The order matters: services that depend on others should be moved last, OR the bulk-move + import-rewrite catches everything in one shot. Use the bulk approach:

- [ ] **Step 1: Move every notification-related service in one batch.** For each file in the manifest's service list, run move-package.sh. Use a loop:

```bash
for f in \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationBroadcastService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationCacheService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationCleanupService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationEmailService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationPushService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationRegistryService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationSettingPresetRegistryService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationSettingService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/CourseNotificationWebappService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/GlobalNotificationSettingService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/NotificationScheduleService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/PushNotificationDeviceConfigurationCleanupService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/SystemNotificationService.java \
    src/main/java/de/tum/cit/aet/artemis/communication/service/UserCourseNotificationStatusService.java; do
    bash scripts/extract-module/move-package.sh \
        de.tum.cit.aet.artemis.communication.service \
        de.tum.cit.aet.artemis.notification.service \
        "$f"
done
```

- [ ] **Step 2: Move the entire `notifications/` sub-package directory.**

```bash
git mv src/main/java/de/tum/cit/aet/artemis/communication/service/notifications \
       src/main/java/de/tum/cit/aet/artemis/notification/service/notifications
# Rewrite package declarations for everything that just moved
find src/main/java/de/tum/cit/aet/artemis/notification/service/notifications -name '*.java' \
    -exec sed -i '' 's|^package de\.tum\.cit\.aet\.artemis\.communication\.service\.notifications|package de.tum.cit.aet.artemis.notification.service.notifications|' {} +
# Rewrite imports across the codebase
grep -rlE "import +de\.tum\.cit\.aet\.artemis\.communication\.service\.notifications" src/ --include='*.java' \
    | xargs -I{} sed -i '' 's|de\.tum\.cit\.aet\.artemis\.communication\.service\.notifications|de.tum.cit.aet.artemis.notification.service.notifications|g' {}
```

- [ ] **Step 3: Run `./gradlew compileJava -x webapp`.** Expected: clean compile.

- [ ] **Step 4: Commit.**

```bash
git add -A
git commit -m "notification module: move services from communication.service"
```

### A.5: Move server REST resources

- [ ] **Step 1: For each REST resource in the manifest, run move-package.sh.**

```bash
for f in \
    src/main/java/de/tum/cit/aet/artemis/communication/web/CourseNotificationResource.java \
    src/main/java/de/tum/cit/aet/artemis/communication/web/GlobalNotificationSettingResource.java \
    src/main/java/de/tum/cit/aet/artemis/communication/web/PushNotificationResource.java \
    src/main/java/de/tum/cit/aet/artemis/communication/web/SystemNotificationResource.java \
    src/main/java/de/tum/cit/aet/artemis/communication/web/UserCourseNotificationSettingResource.java \
    src/main/java/de/tum/cit/aet/artemis/communication/web/UserCourseNotificationStatusResource.java; do
    bash scripts/extract-module/move-package.sh \
        de.tum.cit.aet.artemis.communication.web \
        de.tum.cit.aet.artemis.notification.web \
        "$f"
done
```

- [ ] **Step 2: Verify the REST routes are unchanged.** The `@RequestMapping` paths must not change — this is a refactor, not an API break. Run:

```bash
./gradlew test -x webapp --tests "*NotificationResource*Test" 2>&1 | tail -20
```

Expected: existing tests still pass. If any test fails because of MockMvc context issues, it almost always means the move dropped a `@Profile` or `@ConditionalOnProperty` — diff against the pre-move file.

- [ ] **Step 3: Commit.**

```bash
git add -A
git commit -m "notification module: move REST resources from communication.web"
```

### A.6: Move and audit DTOs

DTOs are tricky: many DTOs are shared between modules. The rule is: **a DTO moves to the new module only if every caller is now in that module**.

- [ ] **Step 1: List every DTO in `communication/dto/`.**

```bash
ls src/main/java/de/tum/cit/aet/artemis/communication/dto/
```

- [ ] **Step 2: For each DTO, grep for its usages.** Move it if and only if every reference is now under `notification/`. Otherwise leave it in `communication/dto/`.

```bash
for f in src/main/java/de/tum/cit/aet/artemis/communication/dto/*.java; do
    name=$(basename "$f" .java)
    echo "=== $name ==="
    grep -rl "import .*\.$name\b\|\b$name\b" src/ --include='*.java' \
        | xargs -I{} echo "  {}" \
        | grep -v "/communication/dto/$name.java" \
        | sed 's|src/main/java/de/tum/cit/aet/artemis/||; s|src/test/java/de/tum/cit/aet/artemis/||' \
        | cut -d/ -f1 | sort -u
done
```

If the only consuming module is `notification`, move the DTO. Otherwise leave it.

- [ ] **Step 3: Commit.**

```bash
git add -A
git commit -m "notification module: move notification-only DTOs"
```

### A.7: Sanity-check cross-module references (no refactoring)

After all moves, both directions of cross-module imports between `communication` and `notification` are expected and acceptable. **Do not refactor them.** The goal of this sub-project is mechanical extraction only.

- [ ] **Step 1: Print the residual cross-references just so the PR description can document them accurately.**

```bash
echo "=== communication imports from notification ==="
grep -rn "import de\.tum\.cit\.aet\.artemis\.notification\." \
    src/main/java/de/tum/cit/aet/artemis/communication/ | wc -l
echo "=== notification imports from communication ==="
grep -rn "import de\.tum\.cit\.aet\.artemis\.communication\." \
    src/main/java/de/tum/cit/aet/artemis/notification/ | wc -l
```

Record both counts in the PR description's "Description" section. No code change at this step.

- [ ] **Step 2: No commit at this step** — it's diagnostic only. Proceed to A.8.

### A.8: Move client `course-notification/` sub-tree

- [ ] **Step 1: Move the directory.**

```bash
git mv src/main/webapp/app/communication/course-notification \
       src/main/webapp/app/notification/course-notification
git mv src/main/webapp/app/communication/shared/entities/course-notification \
       src/main/webapp/app/notification/shared/entities/course-notification
```

- [ ] **Step 2: Rewrite imports.** TypeScript imports use the `app/` prefix:

```bash
grep -rlE "from 'app/communication/course-notification|from 'app/communication/shared/entities/course-notification" \
    src/main/webapp/app/ src/test/playwright/ --include='*.ts' --include='*.html' \
    | xargs -I{} sed -i '' \
        -e "s|app/communication/course-notification|app/notification/course-notification|g" \
        -e "s|app/communication/shared/entities/course-notification|app/notification/shared/entities/course-notification|g" {}
```

- [ ] **Step 3: Run `pnpm run webapp:build`.** Expected: clean build. If the Angular compiler complains about missing modules, the import rewrite missed a `*.html` or `*.scss` reference — investigate.

- [ ] **Step 4: Run client tests for affected modules.**

```bash
pnpm run vitest:run -- app/notification
```

Expected: all pass.

- [ ] **Step 5: Commit.**

```bash
git add -A
git commit -m "notification module: move course-notification UI from app/communication"
```

### A.9: Create module ArchUnit tests

Mirror `src/test/java/de/tum/cit/aet/artemis/course/architecture/` exactly. Each test class has a single-line `getModulePackage()` method.

- [ ] **Step 1: Create `NotificationCodeStyleArchitectureTest.java`.**

```java
package de.tum.cit.aet.artemis.notification.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleArchitectureTest;

class NotificationCodeStyleArchitectureTest extends AbstractModuleCodeStyleArchitectureTest {
    @Override public String getModulePackage() { return "de.tum.cit.aet.artemis.notification"; }
}
```

- [ ] **Step 2: Create `NotificationRepositoryArchitectureTest.java`, `NotificationResourceArchitectureTest.java`, `NotificationServiceArchitectureTest.java`, `NotificationEntityUsageArchitectureTest.java`, `NotificationTestArchitectureTest.java`** with identical shapes (only the package name in `getModulePackage()` differs from the existing `Course*` counterparts).

- [ ] **Step 3: Run them and ratchet baselines.**

```bash
./gradlew test -x webapp --tests "de.tum.cit.aet.artemis.notification.architecture.*" 2>&1 | tail -40
```

Each ArchUnit test base class typically has a per-subclass list of expected violation counts (legacy code). The new module starts with the count it actually has; record those numbers in the test class via `@Override protected int getExpectedNumberOfViolations() { return N; }` or whatever pattern the abstract test uses (consult `CourseRepositoryArchitectureTest.java` for the exact pattern).

- [ ] **Step 4: Commit.**

```bash
git add -A
git commit -m "notification module: add ArchUnit module tests"
```

### A.10: Coverage thresholds (server + client)

- [ ] **Step 1: Compute current notification coverage.** Run after all moves:

```bash
./gradlew test jacocoTestReport -x webapp
node scripts/extract-module/ratchet-jacoco.mjs build/reports/jacoco/test/jacocoTestReport.xml \
    | grep notification
```

Note the line coverage percentage and the branch-missed count.

- [ ] **Step 2: Add the threshold to `gradle/jacoco.gradle`.** Pattern (verbatim from the course/admin extraction):

```groovy
'de/tum/cit/aet/artemis/notification/**': [lineCoverage: <0.0X less than measured>, branchMissed: <ceil(measured) + small headroom>],
```

Pick `lineCoverage` slightly below the measured value (e.g. measured 0.78 → use 0.77) and `branchMissed` slightly above. This is the **floor**; coverage must stay above it.

- [ ] **Step 3: Ratchet `communication` down to its new (lower) post-extraction value.** Same procedure: measure, set floor just below measured.

- [ ] **Step 4: Add the client threshold.** Edit `supporting_scripts/code-coverage/module-coverage-client/check-client-module-coverage.mjs`:

```javascript
notification: { statements: <measured-1>, branches: <measured-1>, functions: <measured-1>, lines: <measured-1> },
```

Measure first via `pnpm run vitest:coverage -- app/notification`.

- [ ] **Step 5: Add include/exclude patterns to client test runners.** `vitest.config.ts`: add `'src/main/webapp/app/notification/**/*'` to the include list. `jest.config.js`: add it to `roots` if applicable.

- [ ] **Step 6: Commit.**

```bash
git add -A
git commit -m "notification module: add server + client coverage thresholds, ratchet communication"
```

### A.11: Full verification before PR

- [ ] **Step 1: Server compile + spotless + checkstyle.**

```bash
./gradlew compileJava spotlessCheck checkstyleMain -x webapp
```

- [ ] **Step 2: Full server test suite.**

```bash
./gradlew test -x webapp
```

Expected: BUILD SUCCESSFUL. Any failure here points to a missed import update or a missed `@Profile` annotation.

- [ ] **Step 3: Client lint + build + Vitest.**

```bash
pnpm run lint
pnpm run webapp:build
pnpm run vitest:run
```

- [ ] **Step 4: Playwright fast suite.**

```bash
./run-e2e-tests-local-fast.sh
```

- [ ] **Step 5: Open the PR.**

```bash
gh pr create --base develop \
    --title "Development: Move notification handling into separate module" \
    --body-file docs/superpowers/plans/_pr-bodies/notification.md
```

Use the PR template in `.github/PULL_REQUEST_TEMPLATE.md` — Summary / Motivation / Description / Checklist / Steps for Testing / Testserver States / Review Progress.

---

## Sub-project B — Extract `jenkins` from `programming`

**PR title:** `Development: Move Jenkins CI backend into separate module`

**PR scope, server side:** 16 files under `programming/service/jenkins/` + sub-package `programming/service/jenkins/build_plan/` (5 files) + `programming/service/jenkins/jobs/JenkinsJobService.java`. Estimated 1,700 main lines plus ~600 test lines.

**PR scope, client side:** Verify whether any client code is Jenkins-specific. From the inspection, the only Jenkins-touching client code is in `app/programming/manage/update/update-components/custom-build-plans/` (the build-plan editor). That UI is shared between Jenkins and LocalCI in present-day Artemis. **Decision: keep the client build-plan editor in `app/programming/` for this PR.** Document this in the PR's "Client scope" section.

### B.1: Skeleton

- [ ] **Step 1: Create directories.**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/jenkins/{service,service/build_plan,service/jobs,config}
mkdir -p src/test/java/de/tum/cit/aet/artemis/jenkins/{architecture,service}
```

- [ ] **Step 2: Commit the skeleton.**

```bash
for d in src/main/java/de/tum/cit/aet/artemis/jenkins/{service,service/build_plan,service/jobs,config} \
         src/test/java/de/tum/cit/aet/artemis/jenkins/{architecture,service}; do
    touch "$d/.gitkeep"
done
git add src/main/java/de/tum/cit/aet/artemis/jenkins \
        src/test/java/de/tum/cit/aet/artemis/jenkins
git commit -m "jenkins module: create skeleton directories"
```

### B.2: Move the `service/jenkins/` sub-tree

- [ ] **Step 1: Move each file via move-package.sh.**

```bash
for f in $(find src/main/java/de/tum/cit/aet/artemis/programming/service/jenkins -maxdepth 1 -name '*.java'); do
    bash scripts/extract-module/move-package.sh \
        de.tum.cit.aet.artemis.programming.service.jenkins \
        de.tum.cit.aet.artemis.jenkins.service \
        "$f"
done
```

- [ ] **Step 2: Move the `build_plan/` sub-package.**

```bash
git mv src/main/java/de/tum/cit/aet/artemis/programming/service/jenkins/build_plan \
       src/main/java/de/tum/cit/aet/artemis/jenkins/service/build_plan
find src/main/java/de/tum/cit/aet/artemis/jenkins/service/build_plan -name '*.java' \
    -exec sed -i '' 's|^package de\.tum\.cit\.aet\.artemis\.programming\.service\.jenkins\.build_plan|package de.tum.cit.aet.artemis.jenkins.service.build_plan|' {} +
grep -rlE "de\.tum\.cit\.aet\.artemis\.programming\.service\.jenkins\.build_plan" src/ --include='*.java' \
    | xargs -I{} sed -i '' 's|de\.tum\.cit\.aet\.artemis\.programming\.service\.jenkins\.build_plan|de.tum.cit.aet.artemis.jenkins.service.build_plan|g' {}
```

- [ ] **Step 3: Move the `jobs/` sub-package** (identical pattern).

- [ ] **Step 4: Verify compile.**

```bash
./gradlew compileJava -x webapp
```

Expected: BUILD SUCCESSFUL. If a file in `programming/` imports a Jenkins class via the old package path, the compile error surfaces it.

- [ ] **Step 5: Commit.**

```bash
git add -A
git commit -m "jenkins module: move service classes"
```

### B.3: ArchUnit + coverage + verification

- [ ] **Step 1: Create the six standard ArchUnit tests** under `src/test/java/de/tum/cit/aet/artemis/jenkins/architecture/`. Pattern is identical to Sub-project A.9 — only the package and class name prefix change.

- [ ] **Step 2: Ratchet baselines.** Run the tests and record the actual violation counts:

```bash
./gradlew test -x webapp --tests "de.tum.cit.aet.artemis.jenkins.architecture.*" 2>&1 | tail -30
```

- [ ] **Step 3: Add server Jacoco threshold + ratchet `programming` downwards.** Same procedure as A.10.

- [ ] **Step 4: Run full server tests.**

```bash
./gradlew test -x webapp
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit and PR.**

```bash
git add -A
git commit -m "jenkins module: ArchUnit tests + coverage threshold"
gh pr create --base develop \
    --title "Development: Move Jenkins CI backend into separate module" \
    --body-file docs/superpowers/plans/_pr-bodies/jenkins.md
```

---

## Sub-project C — Extract `localvc` from `programming`

**PR title:** `Development: Move local VC (embedded git server) into separate module`

**PR scope, server side:** the entire `programming/service/localvc/` sub-tree (25 files in the top-level + ssh/ sub-package) + `programming/web/localvc/` (SshFingerprintsProviderResource) + a handful of top-level `programming/service/` files specifically devoted to git/VCS:
- `AbstractGitService.java`
- `GitService.java`
- `GitRepositoryExportService.java`
- `InternalUrlService.java`
- `ParticipationVcsAccessTokenService.java`

Plus the `programming/service/git/`, `programming/service/vcs/`, and `programming/service/sshuserkeys/` sub-packages.

Estimated 4 k server main lines + ~3 k test lines.

**PR scope, client side:** the `app/programming/shared/repository-view/` and `app/programming/shared/commit-history/` directories — both are the UI for browsing repositories served by LocalVC. They are wholly LocalVC-specific (no Jenkins/external VCS equivalent UI uses them). Estimated ~3 k client lines.

### C.1: Skeleton

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/localvc/{service,service/ssh,service/git,service/vcs,service/sshuserkeys,web,web/ssh,config}
mkdir -p src/test/java/de/tum/cit/aet/artemis/localvc/{architecture,service,web}
mkdir -p src/main/webapp/app/localvc/{repository-view,commit-history,shared}
```

Commit as in A.1.

### C.2: Move server LocalVC service + sub-packages

- [ ] **Step 1: Move the entire `localvc/` sub-tree.** Use `git mv` directly for whole directories:

```bash
git mv src/main/java/de/tum/cit/aet/artemis/programming/service/localvc \
       src/main/java/de/tum/cit/aet/artemis/localvc/service/localvc-tmp
# Rename to flatten — the sub-tree IS the module's service layer
git mv src/main/java/de/tum/cit/aet/artemis/localvc/service/localvc-tmp/ssh \
       src/main/java/de/tum/cit/aet/artemis/localvc/service/ssh
# Move the rest of the files to localvc/service/ (flat, since we don't need a nested "localvc" inside "localvc")
mv src/main/java/de/tum/cit/aet/artemis/localvc/service/localvc-tmp/*.java \
   src/main/java/de/tum/cit/aet/artemis/localvc/service/
rmdir src/main/java/de/tum/cit/aet/artemis/localvc/service/localvc-tmp
# Rewrite package declarations
find src/main/java/de/tum/cit/aet/artemis/localvc/service -name '*.java' \
    -exec sed -i '' \
        -e 's|^package de\.tum\.cit\.aet\.artemis\.programming\.service\.localvc\.ssh|package de.tum.cit.aet.artemis.localvc.service.ssh|' \
        -e 's|^package de\.tum\.cit\.aet\.artemis\.programming\.service\.localvc|package de.tum.cit.aet.artemis.localvc.service|' \
        {} +
# Rewrite imports across the whole repo
grep -rlE "de\.tum\.cit\.aet\.artemis\.programming\.service\.localvc(\.ssh)?\b" src/ --include='*.java' \
    | xargs -I{} sed -i '' \
        -e 's|de\.tum\.cit\.aet\.artemis\.programming\.service\.localvc\.ssh|de.tum.cit.aet.artemis.localvc.service.ssh|g' \
        -e 's|de\.tum\.cit\.aet\.artemis\.programming\.service\.localvc\b|de.tum.cit.aet.artemis.localvc.service|g' {}
```

- [ ] **Step 2: Move `programming/service/git/`, `programming/service/vcs/`, `programming/service/sshuserkeys/`.** Same pattern.

- [ ] **Step 3: Move the five top-level service files** (`AbstractGitService`, `GitService`, `GitRepositoryExportService`, `InternalUrlService`, `ParticipationVcsAccessTokenService`):

```bash
for f in \
    src/main/java/de/tum/cit/aet/artemis/programming/service/AbstractGitService.java \
    src/main/java/de/tum/cit/aet/artemis/programming/service/GitService.java \
    src/main/java/de/tum/cit/aet/artemis/programming/service/GitRepositoryExportService.java \
    src/main/java/de/tum/cit/aet/artemis/programming/service/InternalUrlService.java \
    src/main/java/de/tum/cit/aet/artemis/programming/service/ParticipationVcsAccessTokenService.java; do
    bash scripts/extract-module/move-package.sh \
        de.tum.cit.aet.artemis.programming.service \
        de.tum.cit.aet.artemis.localvc.service \
        "$f"
done
```

- [ ] **Step 4: Verify compile.** This is the first place where cross-module references inside `programming` will surface — `LocalCIService` etc. import `GitService` heavily. The import-rewrite handles them, but verify:

```bash
./gradlew compileJava -x webapp 2>&1 | grep -E "error:|cannot find symbol" | head -20
```

Expected: no errors. Any "cannot find symbol" indicates a missed reference (most likely in a `package-info.java` or a Javadoc `{@link}` tag).

- [ ] **Step 5: Move the LocalVC web resource.**

```bash
git mv src/main/java/de/tum/cit/aet/artemis/programming/web/localvc \
       src/main/java/de/tum/cit/aet/artemis/localvc/web/localvc-tmp
mv src/main/java/de/tum/cit/aet/artemis/localvc/web/localvc-tmp/* \
   src/main/java/de/tum/cit/aet/artemis/localvc/web/
rmdir src/main/java/de/tum/cit/aet/artemis/localvc/web/localvc-tmp
# package + import rewrites as above
find src/main/java/de/tum/cit/aet/artemis/localvc/web -name '*.java' \
    -exec sed -i '' 's|^package de\.tum\.cit\.aet\.artemis\.programming\.web\.localvc|package de.tum.cit.aet.artemis.localvc.web|' {} +
grep -rlE "de\.tum\.cit\.aet\.artemis\.programming\.web\.localvc" src/ --include='*.java' \
    | xargs -I{} sed -i '' 's|de\.tum\.cit\.aet\.artemis\.programming\.web\.localvc|de.tum.cit.aet.artemis.localvc.web|g' {}
```

- [ ] **Step 6: Commit.**

```bash
git add -A
git commit -m "localvc module: move service + web from programming.service.localvc"
```

### C.3: Move client repository-view and commit-history

- [ ] **Step 1: Move directories.**

```bash
git mv src/main/webapp/app/programming/shared/repository-view \
       src/main/webapp/app/localvc/repository-view
git mv src/main/webapp/app/programming/shared/commit-history \
       src/main/webapp/app/localvc/commit-history
```

- [ ] **Step 2: Rewrite imports.**

```bash
grep -rlE "from 'app/programming/shared/(repository-view|commit-history)" \
    src/main/webapp/app/ src/test/playwright/ --include='*.ts' --include='*.html' \
    | xargs -I{} sed -i '' \
        -e "s|app/programming/shared/repository-view|app/localvc/repository-view|g" \
        -e "s|app/programming/shared/commit-history|app/localvc/commit-history|g" {}
```

- [ ] **Step 3: Build + test client.**

```bash
pnpm run webapp:build
pnpm run vitest:run -- app/localvc
```

- [ ] **Step 4: Commit.**

```bash
git add -A
git commit -m "localvc module: move repository-view + commit-history UI"
```

### C.4: ArchUnit + coverage + verification

Mirror A.9 and A.10. Six ArchUnit tests, Jacoco threshold, client coverage threshold, ratchet `programming` downwards. Run full server tests + full Playwright fast suite before PR.

### C.5: PR

```bash
gh pr create --base develop \
    --title "Development: Move local VC (embedded git server) into separate module" \
    --body-file docs/superpowers/plans/_pr-bodies/localvc.md
```

---

## Sub-project D — Extract `localci` from `programming`

**PR title:** `Development: Move local CI orchestration into separate module`

**PR scope, server side:** the entire `programming/service/localci/` sub-tree (82 files) + `programming/service/ci/` (12 files, the abstract CI port) + selected top-level services:
- `BuildScriptGenerationService.java`
- `BuildScriptProviderService.java`
- `ProgrammingExerciseFeedbackCreationService.java` — direct call from `programming/service/ProgrammingExerciseGradingService.java` stays as-is (cross-module direct call after move).
- `LegacyBuildPlanConverterService.java` (already in localci/)
- `programming/web/localci/` (4 web resources: `BuildJobQueueResource`, `BuildLogResource`, `BuildPhasesTemplateResource`, `BuildPlanResource`)
- `programming/web/open/PublicBuildPlanResource.java`
- `programming/repository/BuildJobRepository.java`
- `programming/domain/build/BuildJob.java`
- `programming/dto/BuildJobInterface.java`
- `programming/dto/BuildJobStatisticsDTO.java`

Estimated 9 k+ server main lines + 5–6 k test lines. Largest of the four extractions purely by file count; mechanically the same as the others.

**Dependency on Sub-project C:** `localci` references `localvc` directly (push triggers build via `LocalVCPostPushHook`). After Sub-project C lands, the import path of `LocalVCPostPushHook` and related classes changes from `programming.service.localvc.*` to `localvc.service.*`. Sub-project D therefore **must be rebased on top of merged Sub-project C** so the new import paths are correct in the localci files at move time.

**PR scope, client side:** absorb the **entire `app/buildagent/` tree** (57 files, ~8,550 lines: build-agent-details, build-agent-summary + sub-modals, build-job-statistics, build-queue + sub-components, build-agents.service, build-overview, shared entities). This UI runs on **core nodes** and talks to LocalCI REST endpoints — naming it `buildagent` is misleading. After the move, `app/buildagent/` is removed entirely.

The server-side `de.tum.cit.aet.artemis.buildagent` module (35 files, ~6,300 lines: `BuildAgentConfiguration` + the agent-side build executor / DTO) is **not touched** — it is the genuine worker-side runtime that runs on agent nodes.

### D.1 – D.6: Same shape as Sub-project C, with the buildagent client move added

- [ ] **D.1: Skeleton.** Create `localci/{service,service/ci,service/distributed,web,web/open,repository,domain,dto,config}` and `app/localci/`.

- [ ] **D.2: Move `service/localci/` sub-tree** (82 files, including the nested `distributed/` sub-package — pay attention: it has its own internal package depth via `distributed/api/{map,queue,topic}/listener/`. Move the entire `distributed/` directory atomically).

- [ ] **D.3: Move `service/ci/` interfaces + selected top-level service files + web resources + repository + domain + DTOs.** Each move uses move-package.sh.

- [ ] **D.4: Sanity-check `programming ↔ localci` cross-references (no refactoring).** Both directions are expected:
  - `programming` calls `LocalCITriggerService` / `LocalCIService` in the exercise lifecycle.
  - `localci` writes to `ProgrammingSubmission` / `Result` after a build finishes.

  Print the counts for the PR description; do not change call sites.

  ```bash
  echo "programming → localci imports:"
  grep -rn "import de\.tum\.cit\.aet\.artemis\.localci\." \
      src/main/java/de/tum/cit/aet/artemis/programming/ | wc -l
  echo "localci → programming imports:"
  grep -rn "import de\.tum\.cit\.aet\.artemis\.programming\." \
      src/main/java/de/tum/cit/aet/artemis/localci/ | wc -l
  ```

- [ ] **D.5: Move the entire `app/buildagent/` tree into `app/localci/`.**

  ```bash
  # Move every top-level subdirectory of app/buildagent/ into app/localci/
  for d in src/main/webapp/app/buildagent/*/; do
      name=$(basename "$d")
      git mv "$d" "src/main/webapp/app/localci/$name"
  done
  # Move any loose top-level files (e.g. build-agents.service.ts, build-agents.service.spec.ts)
  for f in src/main/webapp/app/buildagent/*.ts src/main/webapp/app/buildagent/*.html src/main/webapp/app/buildagent/*.scss; do
      [ -f "$f" ] && git mv "$f" "src/main/webapp/app/localci/$(basename "$f")"
  done
  # Remove the now-empty parent
  rmdir src/main/webapp/app/buildagent 2>/dev/null || true
  # Rewrite imports across the whole webapp + playwright tests
  grep -rlE "from 'app/buildagent/" src/main/webapp/app/ src/test/playwright/ --include='*.ts' --include='*.html' \
      | xargs -I{} sed -i '' "s|from 'app/buildagent/|from 'app/localci/|g" {}
  # Some templates may have routerLink references — search broadly
  grep -rlE "app/buildagent" src/main/webapp/ --include='*.html' \
      | xargs -I{} sed -i '' "s|app/buildagent|app/localci|g" {}
  ```

  Expected: `src/main/webapp/app/buildagent/` no longer exists. `pnpm run webapp:build` succeeds. Vitest specs co-located with the moved components run from their new path.

- [ ] **D.6: ArchUnit + coverage + verification + PR.** Same shape as the other sub-projects, **plus** update `jest.config.js` and `vitest.config.ts` to remove `app/buildagent/**` patterns and add `app/localci/**` patterns. Update the client coverage threshold script: the existing `buildagent` entry moves to `localci` with the same numbers (the underlying code didn't change).

### D.7: Cluster-coherence test

Because LocalCI uses Hazelcast distributed structures (`SharedQueueProcessingService`, `DistributedDataAccessService`, etc.), the multi-node fast e2e is mandatory before merging:

- [ ] **Step 1:**

```bash
./run-e2e-tests-local-multinode-fast.sh
```

- [ ] **Step 2: Verify no `ConstraintViolationException` in `programming_exercise_test_case` or any new cluster-coherence failure.** Inspect `.e2e-local-multinode-fast/server-*.log`:

```bash
grep -n "ConstraintViolationException\|Hazelcast.*Error\|build job.*failed" \
    .e2e-local-multinode-fast/server-*.log | head
```

Expected: zero or only-known issues.

---

## Cross-cutting concerns (apply to every sub-project)

### CONFIG — Spring Data repositories

Every new module that contains a `@Repository` needs its package registered in `src/main/java/de/tum/cit/aet/artemis/core/config/DatabaseConfiguration.java`:

```java
@EnableJpaRepositories(basePackages = {
    // ... existing ...
    "de.tum.cit.aet.artemis.notification.repository",
    "de.tum.cit.aet.artemis.jenkins.repository",   // if any
    "de.tum.cit.aet.artemis.localvc.repository",   // if any
    "de.tum.cit.aet.artemis.localci.repository",
})
```

Verification: run `*RepositoryArchitectureTest`. Failure = forgotten registration.

### CONFIG — `@EntityScan`

For every new module that owns `@Entity` classes, register its `domain` package in any `@EntityScan` that needs to find them. The course/admin precedent shows the right file to edit; consult `HibernateConfiguration` and the per-test base classes.

### TEST — `JacksonDeserializerInitializationConfig`

The currently-staged file `src/test/java/de/tum/cit/aet/artemis/shared/JacksonDeserializerInitializationConfig.java` shows there is already a centralised hook for deserialiser registration in tests. If a moved domain class has a custom Jackson deserializer, verify the registration still works after the package move. Run any failing `JacksonDeserializerTest` early.

### TEST — ArchUnit module test naming

Every module gets exactly **six** ArchUnit tests under `<module>/architecture/`:

1. `<Module>CodeStyleArchitectureTest` extends `AbstractModuleCodeStyleArchitectureTest`
2. `<Module>RepositoryArchitectureTest` extends `AbstractModuleRepositoryArchitectureTest`
3. `<Module>ResourceArchitectureTest` extends `AbstractModuleResourceArchitectureTest`
4. `<Module>ServiceArchitectureTest` extends `AbstractModuleServiceArchitectureTest`
5. `<Module>EntityUsageArchitectureTest` extends `AbstractModuleEntityUsageArchitectureTest`
6. `<Module>TestArchitectureTest` extends `AbstractModuleTestArchitectureTest`

The first invocation of each surfaces the baseline violation count for the moved code. Encode that baseline in the subclass (the exact mechanism is whatever the abstract base uses — read `course/architecture/CourseRepositoryArchitectureTest` for the canonical pattern).

### COVERAGE — Jacoco

`gradle/jacoco.gradle` has a `coverageThresholds` map keyed by package glob. For every new module:

1. Run `./gradlew test jacocoTestReport -x webapp` after all moves.
2. Read off the per-package line coverage from `build/reports/jacoco/test/jacocoTestReport.xml`.
3. Add an entry to `coverageThresholds`. Set the floor 1–2 percentage points below the measured value to allow for noise but reject regressions.
4. **Ratchet the donor module downwards.** If we extracted 12 k well-covered lines out of `programming`, the remaining `programming` percentage will shift; set the new floor accordingly.

### COVERAGE — client

`supporting_scripts/code-coverage/module-coverage-client/check-client-module-coverage.mjs` has a similar map keyed by `app/<module>` prefix. Same procedure: measure, set floor below.

Also add `app/<new-module>/**/*` to the include patterns in `vitest.config.ts` and to the `roots` array in `jest.config.js` (if the module has tests).

### ARTEMIS-CODESTATS

The `Artemis-CodeStats` external repo tracks DTO and signal migration progress per module. After each extraction:

1. Open the `Artemis-CodeStats` repo.
2. Add the new module entry mirroring the structure of existing entries (`account`, `course`, `admin`, `calendar`).
3. Re-run `scripts/merge-violations.sh` (already extended to dynamic module discovery in PR #5 of CodeStats) and verify the new module shows up with sensible numbers.

### DOC — Module list in CLAUDE.md

CLAUDE.md has a server module list and a client module list under `## Project Structure`. After each extraction, add the new module with a one-line description.

### DOC — README + module ownership

Add an entry to whatever README / maintainers file lists module maintainers (the existing one is referenced by commit `b6aec2d649`: "Documentation: List account, calendar, globalsearch, videosource modules and maintainers"). Pattern: short description + maintainer GitHub handle.

---

## Roll-out order and merge checklist

```
develop
  │
  ├──── PR-A: notification   (independent)
  │       │
  │       └── merge → next PR rebases
  │
  ├──── PR-B: jenkins        (independent, but rebase on PR-A for fewer conflicts)
  │       │
  │       └── merge
  │
  ├──── PR-C: localvc        (rebase on PR-B; PR-D depends on this for import paths)
  │       │
  │       └── merge
  │
  └──── PR-D: localci        (rebase on PR-C — references LocalVC classes directly)
          │
          └── merge
```

For each PR before opening:

- [ ] `./gradlew compileJava spotlessCheck checkstyleMain modernizer -x webapp` clean
- [ ] `./gradlew test -x webapp` clean (full server suite)
- [ ] `pnpm run lint` clean
- [ ] `pnpm run webapp:build` clean
- [ ] `pnpm run vitest:run` clean (or the equivalent Jest run for not-yet-migrated specs)
- [ ] `./run-e2e-tests-local-fast.sh` clean
- [ ] `./run-e2e-tests-local-multinode-fast.sh` clean (recommended for PR-C and PR-D because they touch the build path; optional for PR-A and PR-B)
- [ ] PR template fully filled in
- [ ] CodeStats updated
- [ ] CLAUDE.md updated

---

## Self-review checklist (applied to this plan, 2026-05-26)

1. **Spec coverage:**
   - "Extract notification from communication, server + client" → Sub-project A ✓
   - "Extract jenkins from programming, server + client" → Sub-project B ✓ (client scope explicitly documented as no-op)
   - "Extract localvc from programming, server + client" → Sub-project C ✓
   - "Extract localci from programming, server + client" → Sub-project D ✓
   - "Keep server and client structure similar" → addressed in "Target end-state structure" and per-sub-project skeletons; client directories use the same single-word names as the server packages ✓
   - "No `ApplicationEvent` / `@TransactionalEventListener` indirection; keep cross-module dependencies as-is" → no inversion steps in any sub-project; A.7 and D.4 are diagnostic-only ✓
2. **Placeholder scan:** searched the plan for "TODO", "TBD", "implement later", "add appropriate error handling" — none present. Some sections deliberately reference the existing pattern in `course/architecture/` rather than re-writing the ArchUnit boilerplate verbatim; this is by design because the abstract test base classes are the source of truth and re-writing the subclass shape inline would duplicate code we don't yet know the exact final form of in this codebase version.
3. **Type / naming consistency:** server package names are `notification` / `jenkins` / `localvc` / `localci` (single-word lowercase Java segments); client directories use the same names. No `programming-` prefix on either side. Naming used consistently in every sub-project section.
4. **Order of operations:** four PRs are mechanically similar; recommended order A → B → C → D so that PR-D can directly import the moved LocalVC classes by their final paths. No hard ordering between A and B/C, just rebase discipline.
5. **Cross-references:** the move-package.sh script defined in Pre-flight is referenced in every sub-project; the ArchUnit pattern in A.9 is referenced from B.3/C.4/D.5 rather than duplicated.

---

## Execution handoff

This plan has four independent merge units. **Recommended execution: one PR at a time, in the order A → B → C → D**, each merged to `develop` before starting the next. Each sub-project is self-contained and can be executed via `superpowers:subagent-driven-development` or `superpowers:executing-plans` independently.

Estimated effort (purely mechanical extraction — no application-layer refactoring):
- Sub-project A (notification): 0.5–1 working day
- Sub-project B (jenkins): 0.25 working day
- Sub-project C (localvc): 0.5–1 working day
- Sub-project D (localci): 1–2 working days (largest file count, but mechanically identical to the others)

Total: ~2–3 working days of focused work if executed serially with reviews between PRs. Each sub-project is a typical Artemis refactor PR in scope.
