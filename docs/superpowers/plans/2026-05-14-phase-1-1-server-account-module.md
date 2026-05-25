# Phase 1.1 — Server + Client `account` Package Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all User / authentication / account-related Java classes from `de.tum.cit.aet.artemis.core.*` into a new dedicated `de.tum.cit.aet.artemis.account.*` package, AND in the same PR move the corresponding TypeScript folders `src/main/webapp/app/core/{account,user}` to `src/main/webapp/app/account/{,user}`. Plus add an empty `AccountConfiguration` marker. No behavior change. No new public-API surface, no consumer refactor, no module-boundary ArchUnit suite. Those follow in **Phase 1.1b**.

**Architecture:** Pure repackaging refactor. The Java package names and TypeScript folder paths change; everything else (annotations, signatures, behavior, Spring bean wiring, Angular routing) is identical. Cross-module callers continue to import the moved classes/symbols directly — only the import paths shift.

**Execution approach:** Scripted, not IDE-driven. The original plan called for IntelliJ "Move class" refactors, but the agent driving this PR cannot operate IntelliJ. Instead, all moves are done via `git mv` plus targeted `sed` rewrites of (a) `package` declarations in moved Java files, (b) `import de.tum.cit.aet.artemis.core.<old>.X;` statements across the entire repo, and (c) `from 'app/core/<old>/...'` statements in the TypeScript codebase. The codebase forbids wildcard Java imports (Spotless) and the TypeScript code uses the absolute-path alias `app/*` mapped to `src/main/webapp/app/*`, so every cross-module reference is an exact textual match that find-and-replace can update.

**Combined client + server scope:** Server and client moves ship in the same PR because doing them as separate PRs would leave the codebase in an asymmetric intermediate state where the client `app/core/account/` still imports server endpoints that no longer match its own package. Keeping them together preserves naming parity.

**Tech Stack:** Java 25, Spring Boot 3.5, Gradle 9.3, Spotless, Checkstyle, JUnit 6, pnpm 11 / Node 24, Angular 21, TypeScript, Vitest, ESLint.

**Scope note:** Part of the broader module-restructuring effort documented in `docs/superpowers/specs/2026-05-14-artemis-module-restructuring-design.md`. This is the first of 6 sub-plans that make up Phase 1. Boundary enforcement (`account.api.*` delegates, ArchUnit `Account*ArchitectureTest` suite, consumer refactor away from direct `account.service.*` imports) is deferred to Phase 1.1b after this lands.

---

## File map

### Production code moves

`F6` in IntelliJ on each file/folder, target as listed. Multi-select to batch within a destination.

| From | To |
|---|---|
| `core/domain/User.java` | `account/domain/User.java` |
| `core/domain/Authority.java` | `account/domain/Authority.java` |
| `core/domain/UserGroup.java` | `account/domain/UserGroup.java` |
| `core/domain/PasskeyCredential.java` | `account/domain/PasskeyCredential.java` |
| `core/domain/PasskeyType.java` | `account/domain/PasskeyType.java` |
| `core/repository/UserRepository.java` | `account/repository/UserRepository.java` |
| `core/repository/UserSpecs.java` | `account/repository/UserSpecs.java` |
| `core/repository/AuthorityRepository.java` | `account/repository/AuthorityRepository.java` |
| `core/repository/PasskeyCredentialsRepository.java` | `account/repository/PasskeyCredentialsRepository.java` |
| `core/service/AccountService.java` | `account/service/AccountService.java` |
| `core/service/ArtemisSuccessfulLoginService.java` | `account/service/ArtemisSuccessfulLoginService.java` |
| `core/service/PasskeyAuthenticationService.java` | `account/service/PasskeyAuthenticationService.java` |
| `core/service/AndroidFingerprintService.java` | `account/service/AndroidFingerprintService.java` |
| `core/service/UserScheduleService.java` | `account/service/UserScheduleService.java` |
| `core/service/user/` (whole subpackage, 5 files) | `account/service/user/` |
| `core/service/ldap/` (whole subpackage, 3 files) | `account/service/ldap/` |
| `core/security/ArtemisAuthenticationProvider.java` | `account/security/ArtemisAuthenticationProvider.java` |
| `core/security/ArtemisInternalAuthenticationProvider.java` | `account/security/ArtemisInternalAuthenticationProvider.java` |
| `core/security/RandomUtil.java` | `account/security/RandomUtil.java` |
| `core/security/UserNotActivatedException.java` | `account/exception/UserNotActivatedException.java` |
| `core/security/passkey/` (whole subpackage, 8 files) | `account/security/passkey/` |
| `core/web/AccountResource.java` | `account/web/AccountResource.java` |
| `core/web/UserResource.java` | `account/web/UserResource.java` |
| `core/web/PasskeyResource.java` | `account/web/PasskeyResource.java` |
| `core/web/TokenResource.java` | `account/web/TokenResource.java` |
| `core/web/admin/AdminUserResource.java` | `account/web/admin/AdminUserResource.java` |
| `communication/domain/ConductAgreement.java` | `account/domain/ConductAgreement.java` |
| `communication/domain/ConductAgreementId.java` | `account/domain/ConductAgreementId.java` |
| `communication/repository/ConductAgreementRepository.java` | `account/repository/ConductAgreementRepository.java` |
| `communication/service/ConductAgreementService.java` | `account/service/ConductAgreementService.java` |

### Test code moves

| From | To |
|---|---|
| `test/.../core/user/util/UserUtilService.java` | `test/.../account/util/UserUtilService.java` |
| `test/.../core/user/util/UserFactory.java` | `test/.../account/util/UserFactory.java` |
| `test/.../core/test_repository/UserTestRepository.java` | `test/.../account/test_repository/UserTestRepository.java` |

### Client moves (TypeScript)

| From | To |
|---|---|
| `src/main/webapp/app/core/account/` (36 files) | `src/main/webapp/app/account/` |
| `src/main/webapp/app/core/user/` (138 files) | `src/main/webapp/app/account/user/` |

External importers to update:
- `app/core/account/...` → `app/account/...` (7 external files)
- `app/core/user/...` → `app/account/user/...` (215 external files)

### Routing updates

`src/main/webapp/app/app.routes.ts` references both `app/core/account/...` and `app/core/user/...` for lazy-loaded routes. These string paths are inside `import(...)` calls and need updating identically to the static imports.

### New files created (just one)

- `src/main/java/de/tum/cit/aet/artemis/account/config/AccountConfiguration.java`

### Files that stay in `core` (clarifying)

- `core/security/{Role, SecurityUtils, SpringSecurityAuditorAware, annotations/, allowedTools/, filter/, jwt/}` — cross-cutting Spring Security primitives used by every module.
- `core/service/{AuthorizationCheckService, ScheduleService, ProfileService, ArtemisVersionService, …}` — true kernel, untouched here.
- `core/service/DataExportScheduleService.java` — referenced by both account *and* admin; revisit when `admin` extraction happens (Phase 1.3).

---

## Tasks

### Task 1: Set up the worktree and feature branch

- [ ] **Step 1.1: Verify clean working tree**

```bash
git status
```
Expected: `nothing to commit, working tree clean`.

- [ ] **Step 1.2: Pull latest develop**

```bash
git fetch origin
git switch develop
git pull --rebase origin develop
```
Expected: fast-forward or already up to date.

- [ ] **Step 1.3: Create the feature branch**

```bash
git switch -c feature/server-account-package-reorganization
```

- [ ] **Step 1.4: Baseline sanity build**

```bash
./gradlew compileJava compileTestJava -x webapp
```
Expected: `BUILD SUCCESSFUL`. Establishes the green baseline so any later failure is a regression introduced by this PR.

---

### Task 2: Create the `AccountConfiguration` marker

**File:** `src/main/java/de/tum/cit/aet/artemis/account/config/AccountConfiguration.java`.

This is a single empty `@Configuration` class that gives the new module a conventional home for future module-level bean definitions. Spring Boot's component scan from `de.tum.cit.aet.artemis` already picks up every `@Service`/`@Repository`/`@Controller` under the new `account` package — `AccountConfiguration` itself adds no bean today.

- [ ] **Step 2.1: Create the package directory**

```bash
mkdir -p src/main/java/de/tum/cit/aet/artemis/account/config
```

- [ ] **Step 2.2: Write the configuration**

Create `src/main/java/de/tum/cit/aet/artemis/account/config/AccountConfiguration.java`:

```java
package de.tum.cit.aet.artemis.account.config;

import org.springframework.context.annotation.Configuration;

/**
 * Marker configuration for the Account module.
 *
 * Spring Boot's default component scan from {@code de.tum.cit.aet.artemis} already discovers every
 * {@code @Service}, {@code @Repository}, and {@code @Controller} under {@code de.tum.cit.aet.artemis.account},
 * so this class is intentionally empty. It exists as the conventional home for future module-level
 * bean definitions and as a single point of attachment for module-wide conditions (see Phase 4 of the
 * module-restructuring plan).
 */
@Configuration
public class AccountConfiguration {
}
```

- [ ] **Step 2.3: Compile**

```bash
./gradlew compileJava -x webapp
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2.4: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/account/config/AccountConfiguration.java
git commit -m "Account module: add empty AccountConfiguration marker"
```

---

### Task 3: Move the five account entities

Touches ~700 import sites. Done as one IntelliJ refactor.

- [ ] **Step 3.1: Open IntelliJ on the feature branch**

(Manual.)

- [ ] **Step 3.2: Multi-select the five entities**

In `src/main/java/de/tum/cit/aet/artemis/core/domain/`, hold ⌘/Ctrl and click: `User.java`, `Authority.java`, `UserGroup.java`, `PasskeyCredential.java`, `PasskeyType.java`.

- [ ] **Step 3.3: `F6` to move**

`Refactor → Move Classes…`. Target package: `de.tum.cit.aet.artemis.account.domain`. Tick **Search in comments and strings** and **Search for text occurrences**. Click **Refactor**.

- [ ] **Step 3.4: Compile**

```bash
./gradlew compileJava compileTestJava -x webapp
```
Expected: `BUILD SUCCESSFUL`. If a stale import error appears, find leftover references:

```bash
grep -rln "de.tum.cit.aet.artemis.core.domain.\(User\|Authority\|UserGroup\|PasskeyCredential\|PasskeyType\);" src/
```

and fix the lines manually.

- [ ] **Step 3.5: Commit**

```bash
git add -A
git commit -m "Account module: move User, Authority, UserGroup, PasskeyCredential, PasskeyType to account.domain"
```

---

### Task 4: Move the four account repositories

- [ ] **Step 4.1: In IntelliJ, multi-select in `core/repository/`**

`UserRepository.java`, `UserSpecs.java`, `AuthorityRepository.java`, `PasskeyCredentialsRepository.java`.

- [ ] **Step 4.2: `F6` to `de.tum.cit.aet.artemis.account.repository`**

- [ ] **Step 4.3: Compile**

```bash
./gradlew compileJava compileTestJava -x webapp
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4.4: Commit**

```bash
git add -A
git commit -m "Account module: move UserRepository, UserSpecs, AuthorityRepository, PasskeyCredentialsRepository to account.repository"
```

---

### Task 5: Move the top-level account services

- [ ] **Step 5.1: In IntelliJ, multi-select in `core/service/`**

`AccountService.java`, `ArtemisSuccessfulLoginService.java`, `PasskeyAuthenticationService.java`, `AndroidFingerprintService.java`, `UserScheduleService.java`.

(Do NOT include `DataExportScheduleService.java` — it stays in core for now.)

- [ ] **Step 5.2: `F6` to `de.tum.cit.aet.artemis.account.service`**

- [ ] **Step 5.3: Compile + spot-check**

```bash
./gradlew compileJava compileTestJava -x webapp
./gradlew test --tests AccountServiceTest -x webapp
```
Expected: `BUILD SUCCESSFUL` and the test passes.

- [ ] **Step 5.4: Commit**

```bash
git add -A
git commit -m "Account module: move AccountService, login + passkey + fingerprint + schedule services to account.service"
```

---

### Task 6: Move the `user/` and `ldap/` service sub-packages

- [ ] **Step 6.1: Move `core/service/user/` to `account/service/user/`**

In IntelliJ's Project view, right-click the `user` directory under `core/service/`. `Refactor → Move`. Target package: `de.tum.cit.aet.artemis.account.service` (this creates `account.service.user` as a child).

- [ ] **Step 6.2: Move `core/service/ldap/` to `account/service/ldap/`**

Same approach for the `ldap` directory.

- [ ] **Step 6.3: Compile**

```bash
./gradlew compileJava compileTestJava -x webapp
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6.4: Commit**

```bash
git add -A
git commit -m "Account module: move service/user/ and service/ldap/ subpackages to account.service"
```

---

### Task 7: Move authentication providers + RandomUtil + UserNotActivatedException

Watch for a potential `core → account` import cycle if anything in `core` (especially `SecurityUtils`) references `RandomUtil`. Step 7.5 checks this.

- [ ] **Step 7.1: Move `ArtemisAuthenticationProvider.java` and `ArtemisInternalAuthenticationProvider.java`**

Multi-select both in `core/security/`. `F6`. Target: `de.tum.cit.aet.artemis.account.security`.

- [ ] **Step 7.2: Move `RandomUtil.java`**

`F6`. Target: `de.tum.cit.aet.artemis.account.security`.

- [ ] **Step 7.3: Move `UserNotActivatedException.java`**

`F6`. Target: `de.tum.cit.aet.artemis.account.exception` (new sub-package; IntelliJ will create it).

- [ ] **Step 7.4: Compile**

```bash
./gradlew compileJava compileTestJava -x webapp
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7.5: Verify no `core → account` cycle was introduced**

```bash
grep -rln "import de.tum.cit.aet.artemis.account\." src/main/java/de/tum/cit/aet/artemis/core
```
Expected: empty output.

If any file in `core` now imports from `account`, that's a cycle and the build will later fail with ArchUnit issues (Phase 1.1b). Resolve now by either:
- Moving `RandomUtil.java` back to `core/security/` (i.e. revert step 7.2 only), or
- Moving the offending `core/` class to `account/` (if it's actually account-bound and was missed earlier).

- [ ] **Step 7.6: Commit**

```bash
git add -A
git commit -m "Account module: move authentication providers, RandomUtil, UserNotActivatedException to account"
```

---

### Task 8: Move the `security/passkey/` subpackage

- [ ] **Step 8.1: Move `core/security/passkey/` to `account/security/passkey/`**

In IntelliJ's Project view, right-click `passkey` under `core/security/`. `Refactor → Move`. Target package: `de.tum.cit.aet.artemis.account.security`.

- [ ] **Step 8.2: Compile**

```bash
./gradlew compileJava compileTestJava -x webapp
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8.3: Commit**

```bash
git add -A
git commit -m "Account module: move security/passkey/ subpackage to account.security.passkey"
```

---

### Task 9: Move the five account web resources

- [ ] **Step 9.1: Move `AccountResource`, `UserResource`, `PasskeyResource`, `TokenResource`**

Multi-select in `core/web/`. `F6`. Target: `de.tum.cit.aet.artemis.account.web`.

- [ ] **Step 9.2: Move `AdminUserResource`**

`F6` on `core/web/admin/AdminUserResource.java`. Target: `de.tum.cit.aet.artemis.account.web.admin`.

- [ ] **Step 9.3: Compile + run resource integration tests**

```bash
./gradlew compileJava compileTestJava -x webapp
./gradlew test --tests "*AccountResource*" --tests "*UserResource*" --tests "*PasskeyResource*" --tests "*TokenResource*" --tests "*AdminUserResource*" -x webapp
```
Expected: all pass.

- [ ] **Step 9.4: Commit**

```bash
git add -A
git commit -m "Account module: move AccountResource, UserResource, PasskeyResource, TokenResource, AdminUserResource to account.web"
```

---

### Task 10: Migrate `ConductAgreement*` from communication to account

`ConductAgreement` represents a user's acceptance of a code-of-conduct document, keyed on (userId, courseId). It is a user attribute checked at login — its home in `communication` was historical.

- [ ] **Step 10.1: Move `ConductAgreement.java` and `ConductAgreementId.java`**

Multi-select in `communication/domain/`. `F6`. Target: `de.tum.cit.aet.artemis.account.domain`.

- [ ] **Step 10.2: Move `ConductAgreementRepository.java`**

`F6` from `communication/repository/`. Target: `de.tum.cit.aet.artemis.account.repository`.

- [ ] **Step 10.3: Move `ConductAgreementService.java`**

`F6` from `communication/service/`. Target: `de.tum.cit.aet.artemis.account.service`.

- [ ] **Step 10.4: Compile + run conduct-agreement tests**

```bash
./gradlew compileJava compileTestJava -x webapp
./gradlew test --tests "*ConductAgreement*" -x webapp
```
Expected: all pass.

- [ ] **Step 10.5: Run communication ArchUnit tests to confirm the move didn't leave dangling references**

```bash
./gradlew test --tests "Communication*ArchitectureTest" -x webapp
```
Expected: all pass.

- [ ] **Step 10.6: Commit**

```bash
git add -A
git commit -m "Account module: migrate ConductAgreement from communication to account"
```

---

### Task 11: Move account test utilities

- [ ] **Step 11.1: Move `UserUtilService.java` and `UserFactory.java`**

In IntelliJ, multi-select both in `test/.../core/user/util/`. `F6`. Target: `de.tum.cit.aet.artemis.account.util`.

- [ ] **Step 11.2: Move `UserTestRepository.java`**

`F6` from `test/.../core/test_repository/`. Target: `de.tum.cit.aet.artemis.account.test_repository`.

- [ ] **Step 11.3: Compile test sources**

```bash
./gradlew compileTestJava -x webapp
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11.4: Commit**

```bash
git add -A
git commit -m "Account module: move UserUtilService, UserFactory, UserTestRepository test utilities"
```

---

### Task 12: Full verification gate

- [ ] **Step 12.1: Java formatting**

```bash
./gradlew spotlessApply -x webapp
```
Expected: succeeds; some files may be reformatted.

- [ ] **Step 12.2: Lint checks**

```bash
./gradlew spotlessCheck checkstyleMain modernizer -x webapp
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12.3: Full server test suite**

```bash
./gradlew test -x webapp
```
Expected: `BUILD SUCCESSFUL`. ~30–60 min. Any failure is a regression introduced by the moves; investigate the failing test's imports first.

- [ ] **Step 12.4: Multi-node E2E smoke (login / account / passkey paths)**

```bash
./run-e2e-tests-local-multinode-fast.sh --filter "(Login|Account|User|Passkey)"
```
Expected: pass. The multi-node runner catches Hazelcast / bean-init ordering regressions that the single-node `test` task can miss.

- [ ] **Step 12.5: Commit formatting changes if any**

```bash
git status
```
If `git status` shows unstaged changes (from `spotlessApply`):

```bash
git add -A
git commit -m "Account module: apply Spotless formatting after package moves"
```

---

### Task 13: Update documentation

**File:** `documentation/docs/developer/system-design.mdx`.

- [ ] **Step 13.1: Add an entry for the `account` package**

Find the section that lists feature modules; insert a brief paragraph describing the `account` package: user / authority / passkey / authentication / LDAP-integration / account REST endpoints. Reference the spec at `docs/superpowers/specs/2026-05-14-artemis-module-restructuring-design.md` for the broader context.

- [ ] **Step 13.2: Mention that boundary enforcement is forthcoming**

Add one sentence noting that `account` does not yet enforce a module API boundary (no `account.api.*`, no ArchUnit suite); that work is tracked separately as Phase 1.1b.

- [ ] **Step 13.3: Commit**

```bash
git add documentation/docs/developer/system-design.mdx
git commit -m "Account module: document the new package in system-design.mdx"
```

---

### Task 14: Push and open the PR

- [ ] **Step 14.1: Push**

```bash
git push -u origin feature/server-account-package-reorganization
```

- [ ] **Step 14.2: Open the PR**

```bash
gh pr create --title "Development: Reorganize User and account code into a dedicated \`account\` package" --body "$(cat <<'EOF'
## Summary
- Moves all User / Authority / UserGroup / PasskeyCredential / authentication / passkey / LDAP / account-REST classes out of `de.tum.cit.aet.artemis.core.*` into a new `de.tum.cit.aet.artemis.account.*` package.
- Migrates `ConductAgreement*` from `communication` to `account`.
- Adds an empty `AccountConfiguration` marker as the conventional module-config home.
- **Zero behavior change.** Pure repackaging refactor: no new public-API surface, no consumer refactor, no module-boundary ArchUnit suite (those follow in Phase 1.1b).

## Checklist
- [x] I followed the language guidelines.
- [x] PR title conforms to naming conventions.

### Server
- [x] No new code; pure refactor.
- [x] No new performance characteristics.
- [x] Existing test suite covers the moved code; no new tests required.
- [x] No new beans introduced.

## Motivation and Context
First sub-PR of Phase 1 of the broader module-restructuring effort. Server `core` is 61 k LOC and a dumping ground; this PR carves out the User/account subdomain into its own package, paving the way for proper module-boundary enforcement (Phase 1.1b) without doing the disruptive consumer refactor in the same PR.

Spec: `docs/superpowers/specs/2026-05-14-artemis-module-restructuring-design.md`

## Description
Eight commits, each a single coherent move:
1. Add empty `AccountConfiguration` marker
2. Move 5 entities to `account.domain`
3. Move 4 repositories to `account.repository`
4. Move top-level services to `account.service`
5. Move `service/user/` and `service/ldap/` subpackages
6. Move authentication providers + `RandomUtil` + `UserNotActivatedException`
7. Move `security/passkey/` subpackage
8. Move 5 web resources to `account.web` / `account.web.admin`
9. Migrate `ConductAgreement*` from `communication` to `account`
10. Move test utilities (`UserUtilService`, `UserFactory`, `UserTestRepository`)
11. Spotless formatting fixup
12. Documentation update in `system-design.mdx`

## Steps for Testing
Prerequisites:
- Clean develop branch + Docker running.

1. Pull this branch, run `./gradlew test -x webapp`. All tests must pass.
2. Run `./run-e2e-tests-local-multinode-fast.sh --filter "(Login|Account|User|Passkey)"`. All login / account / passkey E2Es must pass.
3. Open the app, log in as a regular user, register a new user, change password, set up a passkey, log out, log in via passkey. Verify identical behavior to develop.

## Testserver States
You can manage test servers using [Helios](https://helios.aet.cit.tum.de/).

## Review Progress
### Performance Review
- [ ] Confirmed no performance regression (pure refactor).
### Code Review
- [ ] Code Review 1
- [ ] Code Review 2
### Manual Tests
- [ ] Login + logout
- [ ] Registration + email activation
- [ ] Password reset
- [ ] Passkey registration + login
- [ ] Conduct agreement acceptance

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Risks specific to this plan

1. **`RandomUtil` may create a `core → account` cycle** if `core.security.SecurityUtils` references it. Step 7.5 catches this; if it triggers, revert step 7.2 and leave `RandomUtil` in `core/security/`.
2. **`UserRepository` is imported from every module** (74 in core, 63 in communication, 50 in programming, 41 in exercise, etc.). IntelliJ updates these automatically during the move refactor — if the refactor times out or partially completes, the codebase is in a broken intermediate state. Mitigation: keep `git status` open in a separate terminal; if anything looks half-done, `git restore .` and retry on a faster machine. Do **not** push partial-state commits.
3. **Liquibase changelogs reference `User`, `Authority`, etc. by table name (`jhi_user`, `jhi_authority`)** — not by Java class. The move does not touch these. Verify by `grep -rn "core.domain" src/main/resources/config/liquibase` — expected: empty.
4. **PR #12598** (course overview styling) touches `app/core/course/` on the client, not server `core` Java — no conflict with this PR.
5. **PR #12711** (Athena/Apollon/LDAP/SAML2 profile-to-toggle migration) edits `core/config/{ArtemisConfigHelper, Constants}.java`. This PR does *not* edit those files (scope is "move only"), so no conflict. If #12711 merges after this one, the `ldap/` subpackage move (task 6) shifts the LDAP-related class paths #12711 references, so a small rebase will be needed.

## Out-of-scope (deferred)

- `account.api.{AccountApi, UserApi, UserGroupApi, PasskeyApi, UserRepositoryApi}` delegates → **Phase 1.1b**.
- Refactor of ~700 cross-module consumers to use the new `*Api` instead of direct service/repository imports → **Phase 1.1b**.
- 7-suite ArchUnit pack (`Account*ArchitectureTest`) enforcing the module boundary → **Phase 1.1b**.
- `AbstractAccountIntegrationTest` test base → **Phase 1.1b** (existing tests continue to extend `AbstractSpringIntegrationIndependentTest` directly).
- `AccountEnabled` Spring condition + `artemis.modules.account.enabled` yml gate → **Phase 4**.
- Adding `core.api.{FileApi, FileUploadApi, LegalApi, LlmTokenUsageApi}` for the file/legal/aitracking subdomains that stay in core → **Phase 1.7**.
- Extracting `course` from core → **Phase 1.2**.
- Extracting `admin` from core → **Phase 1.3**.
- Client `app/account` promotion → **Phase 1.4**.
- `DataExportScheduleService` placement decision → revisited in **Phase 1.3** (admin extraction).
