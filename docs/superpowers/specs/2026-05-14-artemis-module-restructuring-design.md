# Artemis Module Restructuring — Design

**Date:** 2026-05-14
**Author:** Stephan Krusche (drafted with Claude Code)
**Status:** Proposal, awaiting review

## 1. Problem

A handful of Artemis modules have grown to dwarf the rest of the codebase. They mix multiple unrelated subdomains, expose internals via `service/` and `repository/` imports that bypass the established `api/` boundary, and are increasingly hard to navigate or test in isolation.

Measured size (production code, excluding tests; client TS only):

| Module        | Server LOC | Client LOC | Total LOC |
|---------------|-----------:|-----------:|----------:|
| core          | 61 201     | 96 944     | 158 145   |
| programming   | 48 498     | 57 666     | 106 164   |
| exercise      | 23 123     | 43 996     | 67 119    |
| communication | 21 241     | 37 418     | 58 659    |
| shared        | —          | 45 605     | 45 605    |

These five modules account for roughly 73 % of the codebase. Past attempts to add features to `core` or `shared` have produced silent coupling: cross-module imports increasingly reach into `core.service`, `core.repository`, or `app/shared/<feature-specific>` instead of going through the public `api/` surface that the existing ArchUnit rules expect.

## 2. Goals

1. Reduce the five over-sized modules to manageable sizes (target: no production module above ~25 k LOC).
2. Carve out feature-aligned modules along seams that already exist in the code (sub-folders, Spring profiles, distinct subdomains).
3. **Avoid creating new modules smaller than ~5 k LOC.** Small carve-outs incur a fixed cost (their own `api/`, ArchUnit suite, `*Configuration`, integration-test base, mocking glue) and add navigational fragmentation without proportional benefit. Tiny subdomains stay folded into a larger parent and get a clean `*Api` surface there instead.
4. Reinforce the existing module-boundary conventions (`api/`, `domain/`, `dto/` as the only public surfaces; ArchUnit enforcement; per-module `*Configuration`) by applying them consistently to the bloated modules.
5. Generalize the yml feature-flag mechanism that already exists for ~14 modules into a single, declarative model (`artemis.modules.<name>.enabled`).
6. Keep the client folder layout mirroring the server **at the module level**, accepting some asymmetry where the client UI for a server module is so small it lives inside a parent client folder.

Non-goals:

- Rebuilding the dependency-injection model.
- Splitting modules into separate Gradle subprojects (this stays a single-Gradle-module monorepo; we are restructuring packages, not artifacts).
- Touching well-shaped modules (`atlas`, `exam`, `quiz`, `assessment`, `lecture`, `iris`, `tutorialgroup`, `text`, `modeling`, `fileupload`, `lti`, `plagiarism`, `hyperion`, `athena`, `buildagent`, `videosource`, `globalsearch`) — these stay as they are.

## 3. Existing scaffolding to extend

We do not need to invent a new module system. Artemis already has:

- **`api/` packages** as the only allowed public surface (e.g. `ExamApi`, `LectureApi`, `CompetencyApi`, …; ~50 such classes). All extend the marker interface `de.tum.cit.aet.artemis.core.api.AbstractApi`.
- **ArchUnit module-boundary tests** (`AbstractModuleAccessArchitectureTest`, `AbstractModuleServiceArchitectureTest`, etc.) enforcing that from outside a module, only `module.api.*`, `module.domain.*`, `module.dto.*` are accessible. Every module ships its own `X*ArchitectureTest` suite.
- **yml-driven feature gates** via `ArtemisConfigHelper` → `ModuleFeatureService` for ~14 existing modules (`artemis.atlas.enabled`, `artemis.iris.enabled`, `artemis.exam.enabled`, `artemis.plagiarism.enabled`, …).
- **Spring profiles** splitting deployment topology (`core`, `buildagent`, `localci`, `localvc`, `jenkins`, …).

All four mechanisms are reused; this design extends them, it does not replace them.

## 4. Target module map (server)

Seven new modules carved out of the four bloated ones. Every new module clears the ~5 k LOC floor. The five tiny subdomains we measured (calendar, file, legal, aitracking, faq, linkpreview, theia, submissionversion, participation, programming-sharing) are **kept folded** into their natural parent and surfaced through a dedicated `*Api` class instead of becoming modules of their own.

### 4.1 Carved out of `core` — three new modules; the rest folds back into the kernel

| Module          | Server pkg          | LOC est. | Feature flag                              | Always-on |
|-----------------|---------------------|---------:|-------------------------------------------|-----------|
| `core` (kernel) | `artemis.core`      | ~20 k    | —                                         | yes       |
| `account`       | `artemis.account`   | ~10 k    | `artemis.modules.account.enabled` (default true) plus sub-gates `passkey`, `ldap`, `saml2` | yes |
| `course`        | `artemis.course`    | ~9 k     | —                                         | yes       |
| `admin`         | `artemis.admin`     | ~6 k     | —                                         | yes       |

**`core` (kernel) keeps and absorbs:**
- Cross-cutting primitives: `DomainObject`, `AbstractAuditingEntity`, `security/{Role, SecurityUtils, SpringSecurityAuditorAware, annotations, allowedTools, filter, jwt}`, `AuthorizationCheckService`, `ScheduleService`, `ProfileService`, `ArtemisVersionService`, `Constants`, base config (`SpringConfig`, `HazelcastConfiguration`, `LiquibaseConfiguration`, `DateTimeFormatConfiguration`, `WebConfigurer`, `TaskSchedulingConfiguration`, `LoggingAspectConfiguration`, …), `service/{messaging, telemetry, connectors}`, base exceptions, `util`, `service/feature` (the `@FeatureToggle` aspect and `Feature` enum), `RoleUtils`, `ResourceLoaderService`, `RateLimitConfigurationService`/`RateLimitService`, `TitleCacheEvictionService`, `ArtemisCompatibleVersionsConfiguration`.
- **File infrastructure** (~2.6 k LOC, below 5 k floor): `FileService`, `FileUpload` entity, `FileUploadEntityType`, `FilePathInformation`, `FileResource`, `ZipFileService`, `ZipStreamHelper`, `TempFileUtilService`, `service/file`, `FileUploadRepository`. Exposed via `core.api.FileApi`, `core.api.FileUploadApi`.
- **Legal documents** (~0.2 k LOC): `LegalDocument*`, legal endpoints in `web/open`. Exposed via `core.api.LegalApi`.
- **LLM-token / AI-call tracking** (~0.6 k LOC): `LLMTokenUsage*`, `LLMTokenUsageRequest`, `LLMTokenUsageTrace`, `AiSelectionDecision`, `LLMServiceType`, `LLMRequest`, `LLMModelCostConfiguration`, `LLMTokenUsageService`. Exposed via `core.api.LlmTokenUsageApi`.

These three subdomains are too small to be modules on their own but each gets a clean `*Api` surface inside `core.api/`, so their usage is still controllable and discoverable.

**`account` carries:** `User`, `Authority`, `UserGroup`, `PasskeyCredential`, `PasskeyType`, `AccountService`, `service/user`, `service/ldap`, `AccountResource`, `UserResource`, `PasskeyResource`, `TokenResource`, `ArtemisAuthenticationProvider`, `ArtemisInternalAuthenticationProvider`, `ArtemisSuccessfulLoginService`, `PasskeyAuthenticationService`, `security/passkey`, `AndroidFingerprintService`, `RandomUtil`, `UserNotActivatedException`, `UserSpecs`, `UserRepository`, `AuthorityRepository`, `PasskeyCredentialsRepository`, `DataExportScheduleService` (account-related parts), `UserScheduleService`. `ConductAgreement*` moves here from `communication`.

New `account.api/`: `AccountApi`, `UserApi`, `UserGroupApi`, `PasskeyApi`, `UserRepositoryApi`.

**`course` carries:** `Course`, `CourseRequest*`, `CourseExamExport*`, `CourseOperation*`, `CourseInformationSharingConfiguration`, `CourseRepository`, `CourseRequestRepository`, `Organization`, `OrganizationRepository`, `CustomOrganizationRepository(Impl)`, `OrganizationSpecs`, `EnrollmentService`, `OrganizationService`, `web/course`. **Calendar subdomain** (~0.7 k LOC) folds in: `CalendarSubscription*`, `CalendarSubscriptionTokenStore`, `CalendarSubscriptionTokenStoreRepository`, `CalendarSubscriptionService`, `web/calendar`, `core/dto/calendar`.

New `course.api/`: `CourseApi`, `CourseRepositoryApi`, `EnrollmentApi`, `OrganizationApi`, `CourseRequestApi`, `CalendarApi`.

**`admin` carries:** `DataExport*`, `DataExportRepository`, `DataExportState`, `MigrationChangelog`, `MigrationChangeRepository`, `CleanupJob*`, `service/cleanup`, `AuditEvent*`, `PersistentAuditEvent`, `PersistenceAuditEventRepository`, `CustomAuditEventRepository`, `web/admin`, `CustomMetricsExtension`, `StatisticsService`, `StatisticsView`, `GraphType`, `SbomService`, `VulnerabilityScan*`, `ArchivalReportEntry`, `ModuleFeatureService` (the central registry), `AuditEventService`, `web/SharingSupportResource`.

New `admin.api/`: `AdminApi`, `StatisticsApi`, `AuditApi`, `DataExportApi`, `ModuleFeatureApi`.

### 4.2 Carved out of `programming` — conservative 3-way split

| Module        | Server pkg            | LOC est. | Feature flag                       |
|---------------|-----------------------|---------:|------------------------------------|
| `programming` | `artemis.programming` | ~27 k    | `artemis.modules.programming.enabled` (default true) |
| `vcs`         | `artemis.vcs`         | ~12 k    | —                                  |
| `buildci`     | `artemis.buildci`     | ~8 k     | —                                  |

`localvc`/`localci`/`sharing`/`theia` stay as profile-gated sub-packages inside their parent module rather than separate modules:

- `vcs/service/localvc/` + `vcs/web/localvc/` activate under the existing `localvc` Spring profile.
- `buildci/service/localci/` + `buildci/web/localci/` activate under the existing `localci` Spring profile.
- `programming/service/sharing/` + `programming/web/ExerciseSharingResource` gate on `artemis.sharing.enabled` (existing).
- `programming/theia/` + `programming/domain/ide/` + `programming/web/theia/` gate on `artemis.theia.enabled` (existing). Theia is only ~0.3 k LOC of server code.

**`programming` keeps:** `ProgrammingExercise`, `ProgrammingExerciseBuildConfig`, `ProgrammingExerciseBuildStatistics`, `ProgrammingExerciseTask`, `ProgrammingExerciseTestCase*`, `ProgrammingExerciseStudentParticipation`, `SolutionProgrammingExerciseParticipation`, `TemplateProgrammingExerciseParticipation`, `ProgrammingSubmission`, `ProgrammingLanguage`, `ProjectType`, `StaticCodeAnalysisCategory*`, `StaticCodeAnalysisTool`, `domain/submissionpolicy`, lifecycle services (`ProgrammingExerciseService`, `*CreationUpdateService`, `*DeletionService`, `*ImportService` (all three variants), `*ExportService`, `*GradingService`, `*FeedbackCreationService`, `*TaskService`, `*TestCaseService`, `*ValidationService`, `ProgrammingAssessmentService`, `ProgrammingMessagingService`, `ProgrammingSubmissionMessagingService`, `ProgrammingSubmissionService`, `ProgrammingTriggerService`, `SubmissionPolicyService`, `StaticCodeAnalysisService`, `LicenseService`, `ConsistencyCheckService`, `DefaultTemplateUpgradeService`, `JavaTemplateUpgradeService`, `TemplateUpgrade*`), `PlantUmlService`, `service/sharing`, `theia/`, `domain/ide/`, `web/Programming*Resource` + `web/Static*Resource` + `web/SubmissionPolicyResource` + `web/PlantUmlResource` + `web/ExerciseSharingResource` + `web/theia/` + `web/IdeSettingsResource`. Repositories for the above.

New `programming.api/`: `ProgrammingExerciseApi`, `ProgrammingExerciseRepositoryApi`, `ProgrammingTestCaseApi`, `StaticCodeAnalysisApi`, `SubmissionPolicyApi`, `ProgrammingMessagingApi`, `TheiaApi`, `ProgrammingSharingApi`.

**`vcs` carries:** `GitService`, `AbstractGitService`, `RepositoryService`, `RepositoryAccessService`, `RepositoryCheckoutService`, `GitRepositoryExportService`, `RepositoryExportGitService`, `UriService`, `VcsRepositoryUri`, `AuxiliaryRepository`, `AuxiliaryRepositoryService`, `Repository` (entity), `RepositoryType`, `UserSshPublicKey*`, `ParticipationVCSAccessToken*`, `ParticipationVcsAccessTokenService`, `service/git`, `service/vcs`, `service/sshuserkeys`, `service/tokens`, `service/localvc` (profile-gated), `service/structureoraclegenerator`, `web/repository`, `web/sshuserkeys`, `web/localvc`, `VcsAccessLog`, `VcsAccessLogRepository`, `RepositoryParticipationService`, `InternalUrlService`, `AuthenticationMechanism`, `Commit`, `File`, `FileType` (the programming-domain ones).

New `vcs.api/`: `VcsApi`, `RepositoryApi`, `GitApi`, `VcsAccessLogApi`, `SshKeyApi`.

**`buildci` carries:** `BuildScriptGeneratorService`, `BuildScriptProviderService`, `BuildLogEntryService`, `BuildLogEntryRepository`, `BuildPlanRepository`, `BuildJobRepository`, `ProgrammingExerciseBuildPlanService`, `AutomaticBuildJobCleanupService`, `domain/build`, `service/ci`, `service/jenkins`, `service/localci` (profile-gated, including `distributed/`), `web/localci`, `ParticipationLifecycle` (programming-specific bits).

New `buildci.api/`: `BuildScriptApi`, `BuildPlanApi`, `BuildLogApi`, `CiTriggerApi`.

### 4.3 Carved out of `communication` — one new module; faq + linkpreview stay folded

| Module          | Server pkg              | LOC est. | Feature flag                              |
|-----------------|-------------------------|---------:|-------------------------------------------|
| `communication` | `artemis.communication` | ~13 k    | `artemis.modules.communication.enabled` (default true) |
| `notification`  | `artemis.notification`  | ~9 k     | —                                         |

The two tiny subdomains stay inside `communication`:
- `Faq*`, `FaqService`, `FaqImportService`, `FaqResource` (~0.6 k LOC). Surfaced via `communication.api.FaqApi`. Gated by `artemis.modules.communication.faq.enabled` (sub-flag of communication; default true).
- `service/linkpreview/`, `LinkPreviewResource`, `LinkPreviewDTO` (~0.7 k LOC). Surfaced via `communication.api.LinkPreviewApi`.

**`communication` keeps:** `Post`, `AnswerPost`, `Conversation`, `ConversationParticipant`, `Reaction`, `SavedPost`, `ForwardedMessage`, `Posting*`, `DisplayPriority`, `PostSortCriterion`, `domain/conversation`, `service/conversation`, `service/linkpreview`, `PostingService`, `AnswerMessageService`, `ConversationMessagingService`, `SavedPostService`, `SavedPostScheduleService`, `ReactionService`, `WebsocketMessagingService`, `web/AnswerMessageResource`, `web/ConversationMessageResource`, `LinkPreviewResource`, all `Faq*` classes, related conversation web endpoints. `ConductAgreement*` moves out to `account`.

New `communication.api/`: `CommunicationApi`, `ConversationApi`, `PostingApi`, `FaqApi`, `LinkPreviewApi`.

**`notification` carries:** `CourseNotification*`, `CourseNotificationParameter`, `GlobalNotificationSetting*`, `SystemNotification*`, `SystemNotificationType`, `UserCourseNotificationStatus*`, `UserCourseNotificationSettingPreset`, `UserCourseNotificationSettingSpecification`, `NotificationChannelOption`, `domain/course_notifications`, `domain/notification`, `domain/push_notification`, `domain/setting_presets`, `service/notifications`, `CourseNotificationBroadcastService`, `CourseNotificationCacheService`, `CourseNotificationCleanupService`, `CourseNotificationEmailService`, `CourseNotificationPushService`, `CourseNotificationRegistryService`, `CourseNotificationService`, `CourseNotificationSettingPresetRegistryService`, `CourseNotificationSettingService`, `CourseNotificationWebappService`, `NotificationScheduleService`, `PushNotificationDeviceConfigurationCleanupService`, `SystemNotificationService`, `UserCourseNotificationStatusService`, `web/AndroidAppSiteAssociationResource`, notification-related endpoints.

Notification is referenced from ~12 modules (every feature that emits a course-level notification); extracting it gives those modules a clean `NotificationApi` to depend on.

New `notification.api/`: `NotificationApi`, `CourseNotificationApi`, `SystemNotificationApi`, `PushNotificationApi`.

### 4.4 `exercise` — no extractions; introduce `ParticipationApi` and `SubmissionVersionApi` instead

`exercise` stays at ~23 k LOC, right at the target ceiling. The two natural extraction candidates measured below the 5 k floor:

- `participation` subdomain: 4 785 LOC.
- `submissionversion`/`exerciseversion`: 964 LOC.

Both stay inside `exercise` but get dedicated `*Api` classes:

New `exercise.api/`: `ExerciseApi`, `ParticipationApi`, `ParticipationLifecycleApi`, `ParticipationRepositoryApi`, `SubmissionApi`, `SubmissionVersionApi`, `ExerciseLifecycleApi`, `ExerciseImportApi`.

This gives cross-module callers the same clean public surface they would have got from separate modules, at a fraction of the structural cost.

### 4.5 Final server module map

| Module        | LOC (est.) | New? | Carved from         |
|---------------|-----------:|------|---------------------|
| core          | ~20 k      | reshaped | (kernel) |
| account       | ~10 k      | ✅   | core                |
| course        | ~9 k       | ✅   | core                |
| admin         | ~6 k       | ✅   | core                |
| programming   | ~27 k      | reshaped | (was 48 k)      |
| vcs           | ~12 k      | ✅   | programming        |
| buildci       | ~8 k       | ✅   | programming        |
| communication | ~13 k      | reshaped | (was 21 k)      |
| notification  | ~9 k       | ✅   | communication      |
| exercise      | ~23 k      | unchanged | —              |
| (atlas, exam, quiz, assessment, lecture, iris, tutorialgroup, text, modeling, fileupload, lti, plagiarism, hyperion, athena, buildagent, videosource, globalsearch) | as today | unchanged | — |

**Six new modules** (`account`, `course`, `admin`, `vcs`, `buildci`, `notification`), every one above 5 k LOC.

## 5. Target module map (client)

### 5.1 Promote sub-folders out of `app/core/` to top-level

Measured client LOC (TS only) for the candidates:

| Sub-folder                                    | LOC      | Verdict |
|-----------------------------------------------|---------:|---------|
| `app/core/course`                             | 38 654   | ✅ promote |
| `app/core/admin`                              | 21 609   | ✅ promote |
| `app/core/user` + `app/core/account` (merge)  | 14 546   | ✅ promote as `app/account` |
| `app/core/calendar`                           | 2 995    | ❌ folds into `app/course` |
| `app/core/notification` + `loading-notification` | 2 245 | ❌ folds into `app/communication` |
| `app/core/legal`                              | 1 425    | ❌ stays in `app/core` |

Net: three new top-level client folders (`account`, `course`, `admin`), mirroring the three new server modules.

### 5.2 Programming client split

Client `programming` is 57 k LOC. The natural VCS slice (code editor + repository views + commit history + git diff) totals ~17 k LOC:

| New client folder | Carries (from `app/programming/`) |
|-------------------|-----------------------------------|
| `app/vcs`         | `shared/code-editor`, `shared/repository-view`, `shared/commit-history`, `shared/commit-details-view`, `shared/commits-info`, `shared/git-diff-report`, `manage/vcs-repository-access-log-view`, related services |

`buildci` client UI (`shared/build-details`, `manage/build-plan-editor` + a slice of services) measures only ~2 k LOC — below the floor. It stays inside `app/programming/`, but a `vcs` top-level is justified.

After extraction, `app/programming` shrinks to ~40 k LOC.

### 5.3 Top-level mirroring for new server modules without a sibling client module

Where a server module has UI smaller than 5 k LOC, that UI stays inside the parent client module rather than getting its own top-level. Documented explicitly:

| Server module | Client home               | Reason                                        |
|---------------|---------------------------|-----------------------------------------------|
| notification  | `app/communication/notification/` | Notification UI is 2.2 k LOC (settings + status badges); lives where messaging UI lives |
| (file infra)  | `app/core/`               | Tiny file-related UI components are generic; stay in core |
| (legal)       | `app/core/legal/`         | Privacy/imprint pages stay in core            |
| (calendar)    | `app/course/calendar/`    | Calendar UI is course-scoped                  |
| (faq)         | `app/communication/faq/`  | FAQ UI is part of course communication        |
| (linkpreview) | `app/communication/`      | Link-preview component is part of posts       |

### 5.4 Cleanup of `app/shared` (45 k LOC)

Only one extraction clears the 5 k floor:

| New folder        | LOC    | Carries                            |
|-------------------|-------:|------------------------------------|
| `app/shared/editor` | ~9 k | `monaco-editor` (7.4 k) + `markdown-editor` (1.8 k) — cross-cutting code/markdown editing widgets |

For the rest of the bloat in `shared`, we **do not create new shared sub-modules**. Instead we move feature-specific widgets into their owning feature module:

| Currently in `shared` | Moves to | LOC |
|-----------------------|----------|----:|
| `participant-scores`  | `app/assessment` | ~0.6 k |
| `statistics-graph`, `dashboards`, `chart`, `score-display` | nearest owner (`app/assessment` or `app/atlas`) | ~3.4 k |
| `structured-grading-criterion`, `grading-instruction-link-icon` | `app/assessment` | ~0.6 k |
| `exercise-filter`     | `app/exercise` | ~0.9 k |
| `user-import`         | `app/account` | ~1.3 k |
| `organization-selector` | `app/admin` | small |
| `category-selector` + `category-selector-primeng` | `app/exercise` | ~0.8 k |
| `feature-toggle`, `feature-activation` | `app/admin` (toggle UI lives in admin) | ~0.5 k |
| `science`             | `app/atlas` (it's atlas-specific telemetry) | ~0.1 k |

After both extraction (`shared/editor`) and dispersal, `app/shared` shrinks from 45 k → ~25-28 k of truly generic widgets (pipes, util, service, language, components, table, form, date-time-picker, sidebar, navigation widgets, etc.). Still a single module — but a coherent one.

### 5.5 Final client module map

| Client module     | LOC (est.) | New? | Notes |
|-------------------|-----------:|------|-------|
| `app/core`        | ~25 k      | shrunk    | app shell, alert, auth, config, environments, home, interceptor, landing, language, layouts, navbar, sentry, theme, login, feature-overview, legal |
| `app/account`     | ~16 k      | ✅        | from core/account + core/user + shared/user-import |
| `app/course`      | ~42 k      | ✅        | from core/course + core/calendar |
| `app/admin`       | ~22 k      | ✅        | from core/admin + shared/organization-selector + shared/feature-toggle |
| `app/programming` | ~40 k      | shrunk    | minus the VCS slice |
| `app/vcs`         | ~17 k      | ✅        | from programming sub-folders |
| `app/communication` | ~40 k    | grew      | + core/notification + core/loading-notification |
| `app/exercise`    | ~46 k      | grew      | + shared/exercise-filter + shared/category-selector* |
| `app/assessment`  | ~24 k      | grew      | + shared/structured-grading-criterion + shared/grading-instruction-link-icon + shared/participant-scores + shared/statistics-graph + shared/dashboards + shared/score-display |
| `app/shared`      | ~26 k      | shrunk    | only truly generic widgets |
| `app/shared/editor` | ~9 k     | ✅        | monaco + markdown editor |
| `app/atlas`       | ~24 k      | grew      | + shared/science |
| (exam, quiz, lecture, iris, hyperion, modeling, text, tutorialgroup, fileupload, plagiarism, lti, buildagent, openapi) | as today | unchanged | — |

**Four new client folders** (`account`, `course`, `admin`, `vcs`) plus one sub-folder split (`shared/editor`). Six dispersals of feature-specific widgets out of `shared` into their owning feature module.

## 6. Feature-flag generalization

Today the gates are hand-coded per module: one constant in `Constants.java` + one method on `ArtemisConfigHelper` + one delegate on `ModuleFeatureService` + scattered `@ConditionalOnProperty` annotations. This works but doesn't scale and isn't discoverable.

### 6.1 Declarative module registry

```java
public enum ArtemisModule {
    CORE          ("artemis.modules.core",          true,  EnumSet.noneOf(ArtemisModule.class)),
    ACCOUNT       ("artemis.modules.account",       true,  EnumSet.of(CORE)),
    COURSE        ("artemis.modules.course",        true,  EnumSet.of(CORE, ACCOUNT)),
    ADMIN         ("artemis.modules.admin",         true,  EnumSet.of(CORE, ACCOUNT, COURSE)),
    COMMUNICATION ("artemis.modules.communication", true,  EnumSet.of(CORE, ACCOUNT, COURSE)),
    NOTIFICATION  ("artemis.modules.notification",  true,  EnumSet.of(CORE, ACCOUNT, COURSE)),
    EXERCISE      ("artemis.modules.exercise",      true,  EnumSet.of(CORE, COURSE)),
    EXAM          ("artemis.modules.exam",          true,  EnumSet.of(CORE, COURSE, EXERCISE)),
    QUIZ          ("artemis.modules.quiz",          true,  EnumSet.of(EXERCISE)),
    TEXT          ("artemis.modules.text",          true,  EnumSet.of(EXERCISE)),
    MODELING      ("artemis.modules.modeling",      true,  EnumSet.of(EXERCISE)),
    FILEUPLOAD    ("artemis.modules.fileupload",    true,  EnumSet.of(EXERCISE)),
    PROGRAMMING   ("artemis.modules.programming",   true,  EnumSet.of(EXERCISE, VCS, BUILDCI)),
    VCS           ("artemis.modules.vcs",           true,  EnumSet.of(CORE)),
    BUILDCI       ("artemis.modules.buildci",       true,  EnumSet.of(CORE, VCS)),
    ASSESSMENT    ("artemis.modules.assessment",    true,  EnumSet.of(EXERCISE)),
    PLAGIARISM    ("artemis.modules.plagiarism",    false, EnumSet.of(EXERCISE)),
    LECTURE       ("artemis.modules.lecture",       true,  EnumSet.of(COURSE)),
    TUTORIALGROUP ("artemis.modules.tutorialgroup", true,  EnumSet.of(COURSE)),
    LTI           ("artemis.modules.lti",           false, EnumSet.of(COURSE)),
    ATLAS         ("artemis.modules.atlas",         true,  EnumSet.of(COURSE, EXERCISE)),
    IRIS          ("artemis.modules.iris",          false, EnumSet.of(COURSE, EXERCISE)),
    ATHENA        ("artemis.modules.athena",        false, EnumSet.of(EXERCISE)),
    HYPERION      ("artemis.modules.hyperion",      false, EnumSet.of(EXERCISE));

    public final String propertyKey;
    public final boolean defaultEnabled;
    public final Set<ArtemisModule> requires;
    // ...
}
```

This is the single source of truth. Adding a module = adding an enum value. Sub-feature gates (e.g. account's `passkey`, `ldap`, `saml2`; programming's `theia`, `sharing`; communication's `faq`) live as ordinary nested properties (`artemis.modules.account.passkey.enabled`) and are read directly by the relevant `@Configuration` — they are not first-class modules.

### 6.2 `@ConditionalOnModule` meta-annotation

```java
@Retention(RUNTIME)
@Target(TYPE)
@Conditional(OnModuleCondition.class)
public @interface ConditionalOnModule {
    ArtemisModule value();
}
```

`OnModuleCondition` checks: (a) the module's property is enabled, AND (b) all `requires` modules are enabled. Used on exactly **one** `@Configuration` class per module:

```java
@Configuration
@ConditionalOnModule(ArtemisModule.NOTIFICATION)
@ComponentScan(basePackages = "de.tum.cit.aet.artemis.notification")
public class NotificationConfiguration { }
```

Everything else in the module rides on Spring's component scan, no decoration required.

### 6.3 yml shape

```yaml
artemis:
  modules:
    account:       { enabled: true,  passkey: false, ldap: false, saml2: false }
    course:        { enabled: true }
    admin:         { enabled: true }
    communication: { enabled: true,  faq: true }
    notification:  { enabled: true }
    exam:          { enabled: true }
    quiz:          { enabled: true }
    text:          { enabled: true }
    modeling:      { enabled: true }
    fileupload:    { enabled: true }
    programming:   { enabled: true,  theia: false, sharing: false }
    vcs:           { enabled: true }
    buildci:       { enabled: true }
    tutorialgroup: { enabled: true }
    lecture:       { enabled: true }
    plagiarism:    { enabled: true }
    atlas:         { enabled: true,  atlasml: false }
    iris:          { enabled: false }
    athena:        { enabled: false }
    hyperion:      { enabled: false }
    lti:           { enabled: false }
```

Modules omitted from this yml (`core`, `exercise`, `assessment`) are always-on and have no operator toggle. They still appear in the `ArtemisModule` enum and participate in `requires` chains, but their `propertyKey` is informational only.

The existing keys (`artemis.iris.enabled`, `artemis.exam.enabled`, …) are aliased for one release cycle via `@DeprecatedConfigurationProperty`; deployments get a startup warning and a migration window.

### 6.4 Client awareness

- `EnabledFeaturesService` (extending today's `ProfileService`) fetches `/api/core/modules` and exposes a typed `enabled(ArtemisModule.NOTIFICATION): Signal<boolean>`.
- The Angular router config wraps each top-level route with a `ModuleGuard` that redirects if the module is disabled.
- Navigation/menu components consume the signals to hide entries.

### 6.5 ArchUnit additions

- `ModuleDependencyArchitectureTest`: every module that imports `X.api.*` must declare `X` in its `requires` set in `ArtemisModule`. Catches accidental dependencies.
- `DisabledModuleSafetyArchitectureTest`: `*Api` classes used outside their owning module must throw a clear "module disabled" exception when their backing module is off, not silently return null/false.

## 7. Architecture (boundaries, data flow, errors, testing)

### 7.1 Boundaries

- Module-private packages: `service/`, `repository/`, `web/`, `config/`, `domain/<X>/internal/`, anything not under `api/`, `domain/`, `dto/`.
- Public surfaces: `module.api.*` (delegates + abstract classes), `module.domain.*` (entities), `module.dto.*` (request/response shapes).
- ArchUnit enforces this per module.

### 7.2 Cross-module data flow

- Service-to-service communication goes through `*Api` classes only. Example: `ProgrammingExerciseService` (in `programming`) calls `vcsApi.cloneRepository(...)`, never `GitService` directly.
- Entities defined in module A and referenced from module B remain in A's `domain/` (this is the existing pattern; e.g. `Course` will live in `course.domain` and be referenced from `exercise`, `exam`, `communication`, etc.).
- Cross-module DTOs live in the *producing* module's `dto/` (consumer can read them, but cannot construct mutable variants from outside).
- Subdomains kept folded into a parent module (calendar→course, file/legal/aitracking→core, faq/linkpreview→communication, theia/sharing→programming, participation/submissionversion→exercise) are surfaced via `*Api` classes in the parent's `api/` package, identical to how true modules expose themselves. The contract for outside callers is the same; only the housing differs.

### 7.3 Error handling

- Each module owns its exception types under `module.exception` (already a convention). The base exceptions (`AccessDeniedException`, `BadRequestException`, `EntityNotFoundException`) stay in `core.exception`.
- When a feature flag disables a module, its `*Api` throws a `ModuleDisabledException` (new, in `core.exception`) with a descriptive message. Consumers catch this for graceful degradation; in most cases the consumer should be guarded by a dependent `@ConditionalOnModule` and never see this exception at runtime.

### 7.4 Testing

- Each module has a `*IntegrationTest` base class (existing pattern for atlas, exam, lecture, communication, …). New modules each get one.
- The shared `AbstractArtemisIntegrationTest` continues to be the composition point that wires all module bases together; we just expand the list.
- ArchUnit suites per module (`*ApiArchitectureTest`, `*ServiceArchitectureTest`, `*RepositoryArchitectureTest`, `*ResourceArchitectureTest`, `*EntityUsageArchitectureTest`, `*CodeStyleArchitectureTest`, `*TestArchitectureTest`) — each new module ships these.
- A "module off" smoke test per feature-flagged module: spin up the app with `artemis.modules.<name>.enabled=false` and verify no bean references the disabled module's internals.

## 8. Coordination with in-flight work

Two open PRs by the same author overlap with parts of this plan. The rollout is sequenced to avoid hard conflicts.

### 8.1 PR [#12667](https://github.com/ls1intum/Artemis/pull/12667) — *Migrate range-slider, dashboards, and image-cropper to Angular signals*

Touches `app/shared/dashboards/`, `app/shared/image-cropper/`, `app/shared/range-slider/`, plus `jest.config.js`, `vitest.config.ts`, `vitest-test-setup.ts`. Status: open, "ready for review", last updated 2026-05-12.

| This plan's step | Overlaps #12667? | Action |
|---|---|---|
| Phase 1 (server `core` split + client `core/*` promotion) | No | Start now |
| Phase 2 (server `programming` split + `app/vcs`) | No | Start now |
| Phase 3a: server `notification` carve-out, `app/shared/editor` extraction, and 11 of 12 widget dispersals out of `shared` | No | Start now |
| Phase 3b: `app/shared/dashboards/*` → `app/assessment/dashboards/*` move | **Yes — hard conflict** on 8 files | **Wait for #12667 to merge**, then do this as its own small PR |
| Phase 4 (yml feature flags) | No | See §8.2 |

`shared/image-cropper` and `shared/range-slider` stay in `app/shared/` per the plan (they are truly generic widgets), so no rename ever competes with #12667 on those.

### 8.2 PR [#12711](https://github.com/ls1intum/Artemis/pull/12711) — *Migrate Athena, Apollon, LDAP, and SAML2 from Spring profiles to module-feature toggles*

Touches `core/config/{ArtemisConfigHelper, Constants, LdapEnabled, SAML2Configuration, SAML2Properties, Saml2Enabled, RestTemplateConfiguration}.java`, `core/service/ProfileService.java`, `core/exception/ApiProfileNotPresentException.java`, plus the affected modules' config/service files. Status: open, "ready for review", last updated 2026-05-14.

This PR establishes the very pattern Phase 4 generalizes: per-module `*Enabled.java` classes that check `artemis.<module>.enabled` and a `ApiProfileNotPresentException` for safe disabled-module callers.

| This plan's step | Overlaps #12711? | Action |
|---|---|---|
| Phase 1, 2, 3 | No | Start now |
| Phase 4 (`ArtemisModule` enum + `@ConditionalOnModule` + migrate the 14 ad-hoc flags) | **Yes — same files, same pattern** | **Wait for #12711 to merge.** Phase 4 then reshapes the per-module pattern #12711 just established into the `ArtemisModule` enum, keeps the deprecated keys aliased for one release, and adds `ApiProfileNotPresentException` (which #12711 introduces) as the standard "module disabled" exception. |

Doing Phase 4 first would force a heavy rebase on #12711; doing #12711 first means Phase 4 only has to consolidate existing in-tree code into the new shape.

### 8.3 Other open PRs by the author

Quickly screened against the plan; none overlap:

- #12710 (client SBOM fix) — touches build scripts only.
- #12709 (Iris Memiris permission fix) — touches `iris/` (untouched module).
- #12640 (Quiz `@OrderColumn` → `mappedBy`) — touches `quiz/` (untouched module).
- #12598 (course overview styling) — touches templates inside `core/course/`. Phase 1 *moves* this folder; merging this PR before Phase 1 starts is easiest. If it's still open when Phase 1 PR is ready, defer the Phase 1 cut over until #12598 lands or coordinate as a single combined PR.
- #12541 (landing-page web vitals) — touches `core/landing/`. Stays in `app/core/` per the plan — no conflict.

## 9. Rollout — four phases, each landable independently

### Phase 1 — Server `core` decomposition + client `core/*` promotion
- Extract `account`, `course`, `admin` from server `core`. File / legal / aitracking subdomains stay in core kernel but get `core.api.{FileApi, FileUploadApi, LegalApi, LlmTokenUsageApi}`.
- Calendar subdomain folds into `course`; gets `course.api.CalendarApi`.
- `ConductAgreement*` moves from `communication` to `account`.
- Promote client `app/core/{account,user,course,admin}` to top-level (`app/{account, course, admin}`); calendar folds into `app/course/calendar`.
- Add `*Api`, `*ArchitectureTest`, `*Configuration` for each new module.
- Update ArchUnit's `AbstractModuleAccessArchitectureTest` to also enforce core's `api/` boundary.
- Estimated touch: ~900-1 000 files moved/renamed; 4-5 PRs grouped by extracted module.

### Phase 2 — Server `programming` decomposition (3-way) + client `vcs` split
- Extract `vcs` and `buildci` from `programming`.
- `localvc`/`localci`/`sharing`/`theia` stay as profile-gated sub-packages inside their parent module; they each get a dedicated `*Api` in the parent's `api/` package.
- Update `buildagent` to depend on `buildci`+`vcs` instead of `programming.service`.
- Client: extract `app/vcs` (code-editor + repository views + commit history + git diff). `buildci` client UI stays inside `app/programming/` (too small to extract).
- Estimated touch: ~500-600 files; 3 PRs.

### Phase 3 — Server `communication` decomposition + client `shared` cleanup

Split into 3a (independent of #12667) and 3b (blocked by #12667):

**Phase 3a — start any time:**
- Extract `notification` from `communication`. Faq + linkpreview stay folded; both get `communication.api.{FaqApi, LinkPreviewApi}`.
- Client: notification UI moves to `app/communication/notification/`.
- Client `shared`: extract `app/shared/editor` (monaco + markdown).
- Client `shared`: disperse 11 of 12 feature-specific widget groups out to their owning feature modules (all except `dashboards`).
- Estimated touch: ~450 files; 3 PRs.

**Phase 3b — only after PR #12667 has merged:**
- Move `app/shared/dashboards/{tutor-leaderboard, tutor-participation-graph, progress-bar}` → `app/assessment/dashboards/...`. Update the 17 importers in `plagiarism`, `course`, `assessment`, and `exam`. Update `jest.config.js` and `vitest.config.ts` to reflect the new path.
- Estimated touch: ~25 files; 1 PR.

### Phase 4 — yml feature-flag generalization (after PR #12711 has merged)
- Introduce `ArtemisModule` enum, `@ConditionalOnModule`, `EnabledFeaturesService` (or extend `ProfileService`).
- Reshape the per-module `*Enabled.java` classes that #12711 introduces (`AthenaEnabled`, `LdapEnabled`, `Saml2Enabled`, `ApollonEnabled`, `ModelingWithApollonEnabled`) to delegate to the enum.
- Migrate the 14 remaining ad-hoc flags onto the new structure (with deprecation aliases for one release).
- Reuse `ApiProfileNotPresentException` (introduced by #12711) as the standard "module disabled" exception, renaming it to `ModuleDisabledException` if naming is agreed.
- Add `ModuleDependencyArchitectureTest`, `DisabledModuleSafetyArchitectureTest`.
- Estimated touch: ~200-300 files; 2-3 PRs.

## 10. Validation criteria

- All ArchUnit tests green on every module.
- Existing server `*IntegrationTest` suite green at every phase boundary.
- Existing Playwright E2E (`./run-e2e-tests-local-fast.sh` and multi-node variants) green at every phase boundary.
- No new module under ~5 k production LOC; no module above 25 k production LOC (excluding generated `openapi/`).
- Each newly extracted module has a non-empty `api/` package; the `AbstractApi`-only state in current `core/api/` is eliminated.
- `artemis.modules.<name>.enabled=false` for a feature-flagged module produces a clean startup (no DI errors) and the corresponding routes return 404.
- `documentation/docs/developer/system-design.mdx` updated to reflect the new module map.

## 11. Risks and trade-offs

1. **Code churn affects in-flight branches.** Phase 1 alone touches ~900 files. Mitigation: communicate a freeze window per phase; merge each extracted module as its own PR within the phase to keep diffs reviewable.
2. **`Course` moves out of `core.domain`** — 910 import sites updated. This is one mechanical commit, but every open branch will conflict on it. Mitigation: schedule on a quiet day; provide an `auto-fix` IDE refactor script.
3. **`Notification` extraction** must update ~12 modules that emit course-level notifications. They currently import from `communication.service.*`; after the split they go through `notification.api.CourseNotificationApi`. This is mechanical but wide.
4. **Folded subdomains still need owners.** A subdomain inside a parent module is not a free pass for poor hygiene: each folded subdomain ships its own `*Api` and its own sub-tests so it remains discoverable and testable. The rule is "no separate module" not "no boundaries."
5. **`@ConditionalOnModule` interacts with Spring's bean ordering.** Existing `@DependsOn`/`@Order` declarations may need touch-ups; verify with the existing multi-node E2E (Hazelcast cluster) which has historically surfaced bean-init regressions.
6. **Backwards-compatible yml.** Deployments rely on the existing key shape. Phase 4 ships with aliases (`artemis.iris.enabled` continues to work, with a deprecation warning) for one minor release before removal.

## 12. Open questions for review

- **`participation`**: it measures 4.8 k LOC, right at the floor. Folding it into `exercise` with a `ParticipationApi` is the conservative choice. Argument for extracting anyway: it is referenced from 9+ modules and a clean module boundary would simplify their dependency declarations. Decision in this draft: fold; revisit only if Phase 4 reveals that `exercise` is still too unwieldy.
- **`atlas` as a sibling vs. carving `science` out of it**: not in scope here; called out only because `shared/science` lives in atlas-adjacent code today.
- **Naming**: `buildci` vs. `ci` vs. `programming-build`. Going with `buildci` since `ci` is too generic.
- **Documentation site** (`documentation/`): we need a new diagram in `system-design.mdx` and updates to `guidelines/server-development.mdx` describing how to create a new feature module. Bundle with Phase 1.

## 13. References

- `documentation/docs/developer/guidelines/caching.mdx` — module-boundary rationale around L2 cache removal.
- `src/test/java/de/tum/cit/aet/artemis/shared/architecture/module/` — current module ArchUnit base classes.
- `src/main/java/de/tum/cit/aet/artemis/core/config/Constants.java` — current `*_ENABLED_PROPERTY_NAME` constants.
- `src/main/java/de/tum/cit/aet/artemis/core/service/ModuleFeatureService.java` — current feature-toggle aggregator.
- `src/main/java/de/tum/cit/aet/artemis/core/config/ArtemisConfigHelper.java` — current property-read helper.
