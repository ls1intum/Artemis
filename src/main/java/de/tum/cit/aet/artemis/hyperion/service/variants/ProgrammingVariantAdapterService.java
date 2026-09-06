package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultOutcome;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.PendingBuild;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;

/**
 * Capability adapters for programming-exercise variants. Thin wrappers around existing, battle-tested
 * services — very little new logic.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ProgrammingVariantAdapterService implements VariantTypeAdapters {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingVariantAdapterService.class);

    /** A resolved test reference as Artemis stores it: the tag plus its numeric id. */
    private static final Pattern TESTID_REFERENCE = Pattern.compile("<testid>(\\d+)</testid>");

    /**
     * A task marker with its reference list, e.g. {@code [task][Implement BubbleSort](<testid>15</testid>)}.
     * Mirrors {@code ProgrammingExerciseTaskService.TASK_PATTERN}, but only far enough to delimit the region a
     * dropped reference can leave a dangling separator in — see {@link #dropUnresolvableTestIds}.
     */
    private static final Pattern TASK_MARKER = Pattern.compile("\\[task]\\[[^\\[\\]]*]\\([^()]*\\)");

    /** Suffix-retry budget for short-name/project-key collisions. */
    private static final int MAX_NAME_ATTEMPTS = 300;

    /**
     * Bounds Gate 3's (LLM consistency check) concurrent wait — comfortably shorter than Gate 1's own build
     * timeout ({@code VariantBuildVerificationService.TIMEOUT}, 3 minutes) so a hung LLM endpoint never becomes
     * the long pole, and short enough that a genuinely slow response still gets treated as best-effort-skipped
     * (see {@link #checkConsistency}) instead of blocking the whole VERIFYING call indefinitely.
     */
    private static final Duration CONSISTENCY_CHECK_TIMEOUT = Duration.ofMinutes(2);

    private final HyperionProgrammingExerciseContextRendererService contextRendererService;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final UserRepository userRepository;

    private final ProgrammingVariantToolsetService toolsetService;

    private final VariantBuildVerificationService buildVerificationService;

    private final HyperionConsistencyCheckService consistencyCheckService;

    private final VariantPlacementService variantPlacementService;

    private final ExerciseVariantJobService jobService;

    private final ExerciseDeletionService exerciseDeletionService;

    public ProgrammingVariantAdapterService(HyperionProgrammingExerciseContextRendererService contextRendererService,
            ProgrammingExerciseImportService programmingExerciseImportService, ProgrammingExerciseValidationService programmingExerciseValidationService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            UserRepository userRepository, ProgrammingVariantToolsetService toolsetService, VariantBuildVerificationService buildVerificationService,
            HyperionConsistencyCheckService consistencyCheckService, VariantPlacementService variantPlacementService, ExerciseVariantJobService jobService,
            ExerciseDeletionService exerciseDeletionService) {
        this.contextRendererService = contextRendererService;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.userRepository = userRepository;
        this.toolsetService = toolsetService;
        this.buildVerificationService = buildVerificationService;
        this.consistencyCheckService = consistencyCheckService;
        this.variantPlacementService = variantPlacementService;
        this.jobService = jobService;
        this.exerciseDeletionService = exerciseDeletionService;
    }

    @Override
    public ExerciseType supportedExerciseType() {
        return ExerciseType.PROGRAMMING;
    }

    @Override
    public String renderContext(Exercise source) {
        // Reload with participations so the renderer can resolve the repository URIs (the pipeline only holds a
        // plain findById copy). No new rendering logic.
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(source.getId());
        // Without line-number gutters: this toolset edits by unique text match and never cites a line number,
        // so the gutters only cost tokens and get echoed back into the generated problem statement.
        return contextRendererService.renderContext(exercise, false);
    }

    @Override
    public Exercise provision(Exercise source, VariantGenerationRequestDTO request, VariantJob job) {
        ChangePlan plan = job.getChangePlan();
        if (plan == null) {
            throw new IllegalStateException("Cannot provision a variant without a change plan");
        }
        // Same eager graph as the import REST endpoint — the import service copies test cases, tasks, hints,
        // static code analysis categories and grading criteria from this instance.
        ProgrammingExercise original = programmingExerciseRepository
                .findByIdWithEagerBuildConfigTestCasesStaticCodeAnalysisCategoriesAndTemplateAndSolutionParticipationsAndAuxReposAndBuildConfigAndGradingCriteria(source.getId())
                .orElseThrow(() -> new EntityNotFoundException("ProgrammingExercise", source.getId()));
        // Fetching the tasks separately, as putting them in the query above leads to Hibernate duplicating the tasks.
        var templateTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(original.getId());
        original.setTasks(new ArrayList<>(templateTasks));
        // Exercise.categories is a lazy @ElementCollection NOT covered by the import fetch graph above (the REST
        // import path receives categories in the request payload instead) — reading it on this detached instance
        // in buildVariantSkeleton threw a LazyInitializationException in the first real-CI run. Hydrate separately.
        programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(source.getId())
                .ifPresent(withCategories -> original.setCategories(withCategories.getCategories()));

        ProgrammingExercise newExercise = buildVariantSkeleton(original, plan, request);
        applyUniqueShortNameAndTitle(newExercise, original, plan.variantTitle());
        ProgrammingExercise imported;
        try {
            imported = programmingExerciseImportService.importProgrammingExercise(original, newExercise, false, false);
        }
        catch (Exception e) {
            // The import persists the exercise row before it copies the repositories, sets up the build plans and
            // schedules its operations — a throw in one of those later steps leaves that row (and whatever
            // infrastructure was already created) behind. The first save is a persist, so the id is on the
            // skeleton instance this method passed in.
            throw deleteProvisionedCloneAndWrap(newExercise.getId(), original.getId(), e);
        }
        try {
            // The import service unconditionally resets the problem statement to the ORIGINAL's (its REST
            // flow forbids editing the statement while importing) — re-apply the plan's statement afterwards,
            // or the skeleton's value from buildVariantSkeleton is silently lost and the variant keeps the
            // source text.
            imported.setProblemStatement(stripPlantUmlCodeFences(plan.problemStatement()));
            // The planner writes its statement against the SOURCE context, so any <testid> markers it copied
            // reference the source's test case ids — remap them to the variant's test cases (matched by test
            // name, both sides straight from the import) like the import flow itself does.
            // `imported` comes back detached from findForCreationById, whose graph excludes testCases.
            Map<String, Long> variantTestIdByName = programmingExerciseTestCaseRepository.findByExerciseId(imported.getId()).stream()
                    .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, ProgrammingExerciseTestCase::getId, (first, second) -> first));
            Map<Long, Long> newTestCaseIdByOldId = original.getTestCases().stream().filter(testCase -> variantTestIdByName.containsKey(testCase.getTestName()))
                    .collect(Collectors.toMap(ProgrammingExerciseTestCase::getId, testCase -> variantTestIdByName.get(testCase.getTestName()), (first, second) -> first));
            programmingExerciseTaskService.updateTestIds(imported, newTestCaseIdByOldId);
            // Whatever <testid> survives the remap above cannot be valid: the remap covers every id the source
            // actually had, so a leftover id was never a real test case — the planner invented it. Observed on
            // a run whose statement carried six ids belonging to neither exercise, which silently unlinked
            // those tasks from grading while every gate reported green. Ids are unguessable by construction
            // (they are assigned when the variant is created, after the plan is written), so dropping them is
            // provably right, and dropping the reference rather than the task keeps the visible text intact.
            imported.setProblemStatement(dropUnresolvableTestIds(imported.getProblemStatement(), variantTestIdByName.values()));
            imported = programmingExerciseRepository.save(imported);
            programmingExerciseTaskService.updateTasksFromProblemStatement(imported);
            // Return a copy WITHOUT an initialized task collection: task rows change again after provisioning
            // (agent problem-statement updates, the final FINALIZING re-sync), and saving this instance later
            // (group placement) with a stale initialized orphanRemoval collection fails with EntityNotFound.
            return programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(imported.getId());
        }
        catch (Exception e) {
            // importProgrammingExercise above already persisted the DB row + VCS repos + CI build plans. This
            // method never returns on that path, so the pipeline's own null-variant cleanup
            // (ExerciseVariantGenerationPipelineService.cleanupProvisionedVariant) can never find this exercise to
            // remove it — delete it here instead of leaking it forever.
            throw deleteProvisionedCloneAndWrap(imported.getId(), original.getId(), e);
        }
    }

    /**
     * Deletes a clone this method provisioned but will never return, and wraps the failure that made it
     * unreachable. Both failure paths — the import throwing after its first save, and the post-import work
     * throwing — leave an exercise that only this method knows about.
     *
     * @param provisionedId the id assigned to the clone, or null when nothing was persisted yet
     * @param sourceId      the source exercise, never deleted here
     * @param failure       the failure to wrap
     * @return the exception to throw: a {@link LeftoverVariantExerciseException} carrying the id when the clone
     *         survived the deletion, so the job keeps its only pointer to it; a plain wrapper otherwise
     */
    private RuntimeException deleteProvisionedCloneAndWrap(@Nullable Long provisionedId, Long sourceId, Exception failure) {
        String message = "Importing the variant clone failed: " + failure.getMessage();
        if (provisionedId == null || provisionedId.equals(sourceId)) {
            return new RuntimeException(message, failure);
        }
        try {
            exerciseDeletionService.delete(provisionedId, true);
        }
        catch (Exception cleanupException) {
            log.error("Failed to clean up partially provisioned variant exercise {} after a provisioning failure", provisionedId, cleanupException);
            // The clone survived: hand its id to the pipeline so the FAILED job keeps the deep link.
            return new LeftoverVariantExerciseException(provisionedId, message, failure);
        }
        return new RuntimeException(message, failure);
    }

    @Override
    public VariantToolset createTools(Exercise variant, VariantJob job) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(variant.getId());
        ProgrammingExercise sourceExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(job.getSourceExerciseId());
        User user = userRepository.getUserWithCourseRolesAndAuthorities(job.getInitiatorLogin());
        return toolsetService.create(exercise, sourceExercise, user, job.getJobId(), this::runSolutionBuildForTestDiscovery);
    }

    /**
     * Runs the SOLUTION build and waits for it, so that tests written this round become registered test cases.
     * <p>
     * A {@link ProgrammingExerciseTestCase} row only exists once a build has compiled and executed the test, so an
     * agent that has just written tests cannot learn their real names from {@code listTestCases} — it is forced to
     * guess, and a guessed name in a task marker silently unlinks the task from grading. Only the solution build
     * is needed: it is the one that executes the suite.
     *
     * @param exercise the variant exercise whose tests should be discovered
     * @param jobId    the job id, for build telemetry
     */
    void runSolutionBuildForTestDiscovery(ProgrammingExercise exercise, String jobId) {
        LocalVCRepositoryUri repositoryUri = exercise.getRepositoryURI(RepositoryType.SOLUTION);
        if (repositoryUri == null) {
            return;
        }
        try {
            PendingBuild pending = buildVerificationService.triggerBuild(exercise, repositoryUri, RepositoryType.SOLUTION);
            buildVerificationService.waitForBuildResults(exercise, Map.of(RepositoryType.SOLUTION, pending));
            jobService.recordBuildStat(jobId, "TEST_DISCOVERY:SOLUTION", Duration.between(pending.triggeredAt(), Instant.now()).toMillis());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the test-discovery build", e);
        }
        catch (ContinuousIntegrationException | EntityNotFoundException e) {
            // Best effort: the agent falls back to the test cases already registered, exactly as before.
            log.warn("Could not run the test-discovery build for variant exercise {}: {}", exercise.getId(), e.getMessage());
        }
    }

    @Override
    public VerificationReport verify(Exercise variant, ChangePlan plan, VariantJob job, VariantToolset toolset) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(variant.getId());
        List<VerificationReport.VerificationFinding> findings = new ArrayList<>();
        // The agent is expected to hand back a complete, working exercise each round, so verification reports as
        // many TRUE findings together in one pass as it can, rather than always waiting for the previous gate to
        // be clean before running the next. Gate 3 has no such dependency and always runs; Gate 2 is skipped (not
        // just deferred) when Gate 1 isn't clean yet, because its result would otherwise not be true (see below).
        // Synchronized: a cancelled consistency task can still be writing here after the bounded wait gave up.
        List<VerificationReport.VerificationFinding> consistencyFindings = Collections.synchronizedList(new ArrayList<>());
        ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        try {
            // Gate 3 (LLM consistency check) only reads the problem statement and repository content — it never
            // depends on a build result — so it runs CONCURRENTLY with Gate 1's build wait instead of after it,
            // on its own virtual thread. Never submit blocking work like this to the bounded
            // hyperionVariantTaskExecutor pool the job itself is already occupying a thread of: that pool is
            // sized for one thread per running job, so a saturated pool would deadlock waiting on itself.
            Future<?> consistencyTask = virtualThreads.submit(() -> checkConsistency(exercise, consistencyFindings));
            try {
                // Gate 1: fresh builds for BOTH repositories — solution must pass 100%, template must fail with
                // tests present. Triggered together and awaited jointly, since the builds run concurrently in CI
                // and the gate then costs about the slower build, not the sum.
                verifyBuilds(exercise, findings, job.getJobId());

                // Gate 2: every test referenced in the problem statement's task markers must resolve to a real
                // test case. This one CANNOT run concurrently with Gate 1, and it must not run at all when Gate 1
                // found a problem: a test added this round only becomes a real ProgrammingExerciseTestCase row
                // once the SOLUTION build has compiled and executed it, so a failed/still-pending Gate 1 means the
                // current test-case data is not yet settled — reporting a brand-new reference as "unresolved" in
                // that state would be a FALSE finding, not an incomplete one (it resolves itself once Gate 1 is
                // green, without the agent touching the problem statement at all). Only trust it once Gate 1 is
                // fully clean.
                if (findings.isEmpty()) {
                    checkTestReferences(exercise, findings);
                }
            }
            finally {
                // Bounds the wait on Gate 3 NO MATTER how Gate 1/2 above exit — including via an exception, which
                // would otherwise skip straight past a plain "consistencyTask.get()" call and leave the whole
                // VERIFYING call (and the job's one thread from the bounded hyperionVariantTaskExecutor pool)
                // waiting on a still-running, still-unbounded consistency check indefinitely.
                awaitConsistencyTask(consistencyTask, exercise);
            }
        }
        finally {
            // Not try-with-resources: close() waits for termination without a timeout, which is the very block
            // awaitConsistencyTask exists to prevent. shutdownNow() interrupts and returns.
            virtualThreads.shutdownNow();
        }
        synchronized (consistencyFindings) {
            findings.addAll(consistencyFindings);
        }
        return new VerificationReport(findings.isEmpty(), List.copyOf(findings));
    }

    /**
     * Waits for Gate 3 (LLM consistency check) with a bounded timeout, called from a {@code finally} block so it
     * must never itself throw — that would suppress whatever Gate 1/2 exception may already be propagating.
     * Best-effort, same philosophy as an unavailable checker (see {@link #checkConsistency}): a timeout or
     * failure here contributes zero consistency findings for this round rather than failing verification.
     */
    static void awaitConsistencyTask(Future<?> consistencyTask, ProgrammingExercise exercise) {
        try {
            consistencyTask.get(CONSISTENCY_CHECK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            consistencyTask.cancel(true);
            log.warn("Consistency check for variant exercise {} did not complete within {}. Skipping the semantic gate for this round.", exercise.getId(),
                    CONSISTENCY_CHECK_TIMEOUT);
        }
        catch (ExecutionException e) {
            log.warn("Consistency check for variant exercise {} failed: {}. Skipping the semantic gate for this round.", exercise.getId(),
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<String> finalizeVariant(Exercise variant, VariantJob job) {
        // Final task→test wiring (deterministic, no LLM): tests ADDED during transformation only exist as
        // test-case rows once their first build result was processed, so the provision-time sync could not
        // resolve them yet — re-run the sync on the final statement. Also covers rounds that changed tests but
        // never called updateProblemStatement.
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(variant.getId());
        programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
        // What remains is the shared placement logic.
        return variantPlacementService.place(variant, job.getSourceExerciseId(), job.getRequest());
    }

    /**
     * Builds the unsaved variant skeleton the import service fills in: generic fields + programming-specific fields
     * copied from the original (same field set as the exam-import precedent in {@code ExamImportService}), title and
     * problem statement from the {@link ChangePlan}, difficulty from the request when a difficulty change was asked
     * for. Exam variants go into the source's exam exercise group (SAME_EXAM_GROUP).
     */
    private ProgrammingExercise buildVariantSkeleton(ProgrammingExercise original, ChangePlan plan, VariantGenerationRequestDTO request) {
        ProgrammingExercise newExercise = new ProgrammingExercise();
        if (original.isExamExercise()) {
            newExercise.setExerciseGroup(original.getExerciseGroup());
        }
        else {
            newExercise.setCourse(original.getCourseViaExerciseGroupOrCourseMember());
            newExercise.setReleaseDate(original.getReleaseDate());
            newExercise.setStartDate(original.getStartDate());
            newExercise.setDueDate(original.getDueDate());
            newExercise.setAssessmentDueDate(original.getAssessmentDueDate());
            newExercise.setExampleSolutionPublicationDate(original.getExampleSolutionPublicationDate());
        }
        newExercise.setMaxPoints(original.getMaxPoints());
        newExercise.setBonusPoints(original.getBonusPoints());
        newExercise.setIncludedInOverallScore(original.getIncludedInOverallScore());
        newExercise.setMode(original.getMode());
        newExercise.setDifficulty(request.targetDifficulty() != null ? request.targetDifficulty() : original.getDifficulty());
        newExercise.setCategories(new HashSet<>(original.getCategories()));
        newExercise.setProblemStatement(plan.problemStatement());
        newExercise.setGradingInstructions(original.getGradingInstructions());
        newExercise.setProgrammingLanguage(original.getProgrammingLanguage());
        newExercise.setProjectType(original.getProjectType());
        newExercise.setPackageName(original.getPackageName());
        newExercise.setAllowOnlineEditor(original.isAllowOnlineEditor());
        newExercise.setAllowOfflineIde(original.isAllowOfflineIde());
        newExercise.setAllowOnlineIde(original.isAllowOnlineIde());
        newExercise.setStaticCodeAnalysisEnabled(original.isStaticCodeAnalysisEnabled());
        newExercise.setMaxStaticCodeAnalysisPenalty(original.getMaxStaticCodeAnalysisPenalty());
        newExercise.setShowTestNamesToStudents(original.getShowTestNamesToStudents());
        newExercise.setReleaseTestsWithExampleSolution(original.isReleaseTestsWithExampleSolution());
        newExercise.setAssessmentType(original.getAssessmentType());
        newExercise.setAllowComplaintsForAutomaticAssessments(original.getAllowComplaintsForAutomaticAssessments());
        newExercise.setAllowFeedbackRequests(original.getAllowFeedbackRequests());
        newExercise.setBuildAndTestStudentSubmissionsAfterDueDate(original.getBuildAndTestStudentSubmissionsAfterDueDate());
        return newExercise;
    }

    /**
     * Derives the short name deterministically from the planner-generated title and resolves VCS/CI project
     * collisions with a suffix retry (-V2, -V3, ...), re-running the existence pre-check each time — never via the
     * LLM.
     */
    private void applyUniqueShortNameAndTitle(ProgrammingExercise newExercise, ProgrammingExercise original, String variantTitle) {
        Course course = original.getCourseViaExerciseGroupOrCourseMember();
        String courseShortName = course != null ? course.getShortName() : "";
        String baseShortName = deriveShortName(variantTitle);
        for (int attempt = 1; attempt <= MAX_NAME_ATTEMPTS; attempt++) {
            String suffix = attempt == 1 ? "" : "V" + attempt;
            newExercise.setTitle(attempt == 1 ? variantTitle : variantTitle + " V" + attempt);
            newExercise.setShortName(baseShortName + suffix);
            if (!programmingExerciseValidationService.preCheckProjectExistsOnVCSOrCI(newExercise, courseShortName)) {
                log.debug("Provisioning variant with short name {} (attempt {})", newExercise.getShortName(), attempt);
                return;
            }
        }
        throw new IllegalStateException(
                "Could not find a free short name for the variant after " + MAX_NAME_ATTEMPTS + " attempts (base: " + baseShortName + "). Please clean up stale projects.");
    }

    /**
     * Unwraps PlantUML blocks the LLM wrapped in Markdown code fences (```plantuml ... ```): Artemis replaces
     * bare {@code @startuml ... @enduml} blocks with a diagram placeholder BEFORE Markdown parsing, so a fenced
     * block renders the raw placeholder div as literal code text instead of the diagram.
     */
    static String stripPlantUmlCodeFences(String problemStatement) {
        if (problemStatement == null) {
            return null;
        }
        return problemStatement.replaceAll("(?m)^[ \\t]*```[\\w-]*[ \\t]*\\n(\\s*@startuml)", "$1").replaceAll("(?m)(@enduml)\\s*\\n[ \\t]*```[ \\t]*$", "$1");
    }

    /**
     * Removes {@code <testid>} references that name no test case of the variant, leaving the task's visible text.
     * <p>
     * Applied once, right after the source-to-variant id remap, where the set of valid ids is known exactly.
     * Anything still unresolvable at that point was never a real id in either exercise: the planner writes its
     * statement before the variant exists, so it cannot know an id, and every id the source did have has just
     * been remapped. Such a reference is silently unlinked from grading rather than rejected anywhere, so it
     * survives to the finished exercise while the pipeline reports success — which is why this drops them instead
     * of leaving them for a later gate. A task whose references are all dropped keeps its heading and prose; only
     * the broken link disappears.
     *
     * @param problemStatement the statement after id remapping
     * @param validTestIds     ids of the variant's own test cases
     * @return the statement with unresolvable {@code <testid>} references removed
     */
    static String dropUnresolvableTestIds(String problemStatement, Collection<Long> validTestIds) {
        if (problemStatement == null || problemStatement.isBlank()) {
            return problemStatement;
        }
        Set<String> valid = validTestIds.stream().map(String::valueOf).collect(Collectors.toSet());
        Matcher matcher = TESTID_REFERENCE.matcher(problemStatement);
        StringBuilder result = new StringBuilder();
        boolean dropped = false;
        while (matcher.find()) {
            boolean keep = valid.contains(matcher.group(1));
            dropped |= !keep;
            // Drop the reference AND a comma that would otherwise be left dangling beside it.
            matcher.appendReplacement(result, keep ? Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(result);
        if (!dropped) {
            return problemStatement;
        }
        // Tidy the separators a removal leaves behind, but ONLY inside the task markers themselves. A problem
        // statement also carries code samples, where "foo(a, )" and "[1,,3]" are content the instructor wrote,
        // not artefacts of this method — rewriting those would corrupt the exercise text.
        return TASK_MARKER.matcher(result.toString()).replaceAll(match -> Matcher.quoteReplacement(tidySeparators(match.group())));
    }

    /** Collapses ",,", "( ," and ", )" inside a single task marker's reference list. */
    private static String tidySeparators(String taskMarker) {
        return taskMarker.replaceAll(",{2,}", ",").replaceAll("\\(\\s*,", "(").replaceAll(",\\s*\\)", ")");
    }

    /** PascalCases the alphanumeric words of the title into a valid exercise short name (starts with a letter, ≥3 chars). */
    private static String deriveShortName(String title) {
        StringBuilder builder = new StringBuilder();
        boolean upperCaseNext = true;
        for (char character : title.toCharArray()) {
            if (Character.isLetterOrDigit(character)) {
                builder.append(upperCaseNext ? Character.toUpperCase(character) : character);
                upperCaseNext = false;
            }
            else {
                upperCaseNext = true;
            }
        }
        String shortName = builder.toString();
        if (shortName.isEmpty() || !Character.isLetter(shortName.charAt(0))) {
            shortName = "Variant" + shortName;
        }
        if (shortName.length() < 3) {
            shortName = shortName + "Var";
        }
        return shortName.length() > 25 ? shortName.substring(0, 25) : shortName;
    }

    /** Per-repository-type verification spec: which gate a build finding belongs to and the target it must reach. */
    private record BuildGate(VerificationReport.VerificationGate gate, String target) {
    }

    /**
     * Gate 1 for both build repositories: trigger the SOLUTION and TEMPLATE builds together, then wait for both
     * under a single shared timeout. The builds run concurrently in CI, so joint waiting costs about the slower
     * build instead of the sum of the two.
     */
    private void verifyBuilds(ProgrammingExercise exercise, List<VerificationReport.VerificationFinding> findings, String jobId) {
        Map<RepositoryType, BuildGate> gates = new EnumMap<>(RepositoryType.class);
        gates.put(RepositoryType.SOLUTION, new BuildGate(VerificationReport.VerificationGate.SOLUTION_BUILD, "The solution repository build must compile and pass 100% of tests."));
        gates.put(RepositoryType.TEMPLATE,
                new BuildGate(VerificationReport.VerificationGate.TEMPLATE_BUILD, "The template repository build must execute at least one test and score 0%."));

        Map<RepositoryType, PendingBuild> pending = new EnumMap<>(RepositoryType.class);
        for (Map.Entry<RepositoryType, BuildGate> entry : gates.entrySet()) {
            RepositoryType repositoryType = entry.getKey();
            BuildGate buildGate = entry.getValue();
            LocalVCRepositoryUri repositoryUri = exercise.getRepositoryURI(repositoryType);
            if (repositoryUri == null) {
                findings.add(new VerificationReport.VerificationFinding(buildGate.gate(), "No " + repositoryType + " repository URI found for the variant exercise."));
                continue;
            }
            try {
                pending.put(repositoryType, buildVerificationService.triggerBuild(exercise, repositoryUri, repositoryType));
            }
            catch (ContinuousIntegrationException | EntityNotFoundException e) {
                // A missing participation is a defect of this variant clone, not of the job — report it as a
                // finding for its own gate instead of aborting the whole generation.
                findings.add(new VerificationReport.VerificationFinding(buildGate.gate(), "Could not trigger the " + repositoryType + " build: " + e.getMessage()));
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        Instant jointTriggeredAt = Instant.now();
        try {
            Map<RepositoryType, BuildResultOutcome> outcomes = buildVerificationService.waitForBuildResults(exercise, pending);
            String triggeredLabel = pending.keySet().stream().map(RepositoryType::name).collect(Collectors.joining("+"));
            jobService.recordBuildStat(jobId, "VERIFYING:" + triggeredLabel + (pending.size() > 1 ? " (joint)" : ""), Duration.between(jointTriggeredAt, Instant.now()).toMillis());
            for (Map.Entry<RepositoryType, BuildResultOutcome> entry : outcomes.entrySet()) {
                addBuildFinding(exercise, entry.getKey(), gates.get(entry.getKey()), entry.getValue(), findings);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the verification builds", e);
        }
    }

    private void addBuildFinding(ProgrammingExercise exercise, RepositoryType repositoryType, BuildGate buildGate, BuildResultOutcome outcome,
            List<VerificationReport.VerificationFinding> findings) {
        switch (outcome.state()) {
            case SUCCESS -> log.debug("Verification build for {} of exercise {} reached its target", repositoryType, exercise.getId());
            case FAILED -> findings.add(new VerificationReport.VerificationFinding(buildGate.gate(), buildFailureMessage(repositoryType, buildGate, outcome)));
            // Distinct detail for CI timeouts.
            case TIMED_OUT -> findings.add(new VerificationReport.VerificationFinding(buildGate.gate(),
                    "The " + repositoryType + " build result did not arrive within the timeout (BuildResultState.TIMED_OUT). " + buildGate.target()));
            case PARTICIPATION_NOT_FOUND, CI_TRIGGER_FAILED ->
                findings.add(new VerificationReport.VerificationFinding(buildGate.gate(), "The " + repositoryType + " build could not be verified: " + outcome.state()));
        }
    }

    /**
     * Builds the FAILED-outcome finding message. For SOLUTION (and TEMPLATE with no test executed at all, or a
     * compile failure with nothing to distinguish) this is the plain generic description. For a TEMPLATE build
     * that DID execute tests, the generic description is a long list of "FAILED" entries that are actually the
     * CORRECT, desired state (an unimplemented template must fail every test) — a repair round reading that list
     * without this framing has, in practice, misread ordinary expected failures as things to fix and gone on to
     * implement the missing scaffolding instead of the one real defect. Naming the actual problem — which
     * test(s) unexpectedly PASSED against the stub — up front avoids that misreading.
     */
    private String buildFailureMessage(RepositoryType repositoryType, BuildGate buildGate, BuildResultOutcome outcome) {
        String fullResult = buildVerificationService.describeBuildResult(outcome.result());
        if (repositoryType != RepositoryType.TEMPLATE) {
            return buildGate.target() + " Current result: " + fullResult;
        }
        // Score 0 with ZERO executed tests is not "almost there": the gate wants 0% WITH tests running, and a
        // template that runs no test at all is nearly always broken (compile error, or a tests/build change that
        // stopped discovering them). Without this branch the finding reads "Score: 0.0% (0/0 tests passed)"
        // against a target demanding 0%, which invites the repair round to conclude nothing is wrong.
        Integer testCaseCount = outcome.result() != null ? outcome.result().getTestCaseCount() : null;
        if (testCaseCount != null && testCaseCount == 0) {
            return "The template build executed NO tests at all (0 of 0). Scoring 0% this way does NOT satisfy the gate: the template must COMPILE and RUN the test suite, and "
                    + "fail it on the student's missing work. Zero executed tests means the build broke — a compile error in the template or the test repository, or a build-file "
                    + "or test-discovery change. Read the build logs below, fix the compile/build error, and leave the tests themselves alone. Current result: " + fullResult;
        }
        List<String> unexpectedPasses = buildVerificationService.unexpectedlyPassingTestNames(outcome.result());
        if (unexpectedPasses.isEmpty()) {
            return buildGate.target() + " Current result: " + fullResult;
        }
        return buildGate.target() + " Every OTHER test failing in the template below is CORRECT and expected — the template intentionally has nothing implemented yet, so do "
                + "NOT implement anything to make a failing test pass. The actual problem is the opposite: " + unexpectedPasses.size()
                + " test(s) unexpectedly PASSED against the unimplemented template: " + String.join(", ", unexpectedPasses)
                + ".\n\nFirst, what is NOT the fix: the classes the failing structural tests look for are SUPPOSED to be missing from the template — that is the student's work. Never "
                + "create them, and never add stub classes so tests \"can run and fail\". Adding scaffolding to the template always makes this gate worse, never better.\n\n"
                + "Both plausible causes are things the transformation ADDED — fix the cause, never the tests' subject matter:\n"
                + "1. A student-owned class/file was created in TEMPLATE that the source template does not ship. Compare the TEMPLATE tree with the source template tree and "
                + "delete what was added (deleteFiles).\n"
                + "2. A class the template legitimately ships (a new given domain type) was added to the structure oracle or gained a structural test. Remove that oracle entry "
                + "or test — the template implements the class, so such a test can only ever pass.\n"
                + "Do NOT manufacture failure to force the score down: a throwing static initializer or constructor, an intentionally broken block, a stub that throws where the "
                + "source's stub returned silently, or a name mismatch between template and solution are all rejected. The template must compile cleanly and fail only because "
                + "the student's work is genuinely missing.\n\nFull result for reference:\n" + fullResult;
    }

    /**
     * Gate 2: every test referenced in a problem-statement task marker must resolve to a real
     * {@link de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase} — deterministic and cheap
     * (reuses {@link ProgrammingExerciseTaskService#findUnresolvedTaskTestReferences}, the same matching
     * {@link ProgrammingExerciseTaskService#updateTasksFromProblemStatement} uses when linking tasks, except this
     * reports unresolved references instead of silently dropping them).
     */
    private void checkTestReferences(ProgrammingExercise exercise, List<VerificationReport.VerificationFinding> findings) {
        // Reload before checking. findUnresolvedTaskTestReferences parses exercise.getProblemStatement() from the
        // object it is handed, so checking the in-memory copy validates whatever statement that object happens to
        // hold rather than the one that will ship. Observed reporting "all gates green" on a run whose persisted
        // statement carried 20 unresolvable references — a false green that hands the instructor an exercise whose
        // tasks are silently unlinked from grading, which is strictly worse than failing loudly.
        ProgrammingExercise persisted = programmingExerciseRepository.findByIdElseThrow(exercise.getId());
        List<String> unresolved = programmingExerciseTaskService.findUnresolvedTaskTestReferences(persisted);
        if (!unresolved.isEmpty()) {
            // A reference that is <testid>-wrapped but unresolved is a different defect from a wrong name, and the
            // generic "use the exact current test name" advice does not describe its fix at all — spell the format
            // contract out instead, or the repair round keeps re-emitting the same malformed tag.
            boolean malformedTestId = unresolved.stream().anyMatch(reference -> reference.startsWith("<testid>"));
            String hint = malformedTestId
                    ? " A <testid> tag may ONLY contain a numeric test-case id (<testid>27</testid>); the references above wrap something else, so they can never resolve. "
                            + "Do not write <testid> tags at all — put the plain test name in the marker instead, e.g. \"[task][Implement Bubble Sort](testBubbleSort)\", "
                            + "and Artemis converts it to an id itself. The name carries no parentheses and no parameter list: \"testBubbleSort()\" resolves to nothing."
                    : " Update the task marker(s) to use the exact current test name(s).";
            // Naming the problem without naming the remedy left this gate unable to repair itself: observed
            // burning four consecutive attempts while the agent kept re-emitting invented names
            // (testSortStrategyInterface for testClass[SortStrategy]). Structural test names are generated per
            // member, so they cannot be guessed from the class name — listing them inline removes both the tool
            // round-trip and the chance the agent simply does not look.
            String available = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId()).stream().map(ProgrammingExerciseTestCase::getTestName).sorted()
                    .collect(Collectors.joining(", "));
            findings.add(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.TEST_REFERENCES,
                    "The problem statement references test(s) that do not exist in the test repository: " + String.join(", ", unresolved) + "." + hint
                            + " These are the only test names that exist — copy one of them character for character, and do not adapt it to read better: [" + available
                            + "]. A name not in that list cannot be made to work by rewording the statement; either use the listed name that covers the same requirement, or drop the reference."));
        }
    }

    /**
     * Gate 3: semantic consistency between the problem statement and the repositories. Best-effort: an unavailable
     * checker must not fail an otherwise green variant.
     */
    private void checkConsistency(ProgrammingExercise exercise, List<VerificationReport.VerificationFinding> findings) {
        try {
            ConsistencyCheckResponseDTO response = consistencyCheckService.checkConsistency(exercise.getId());
            if (response == null || response.issues() == null) {
                return;
            }
            for (ConsistencyIssueDTO issue : response.issues()) {
                if (issue == null) {
                    continue;
                }
                String message = "[" + issue.severity() + "] " + issue.category() + ": " + issue.description()
                        + (issue.suggestedFix() != null && !issue.suggestedFix().isBlank() ? " Suggested fix: " + issue.suggestedFix() : "");
                findings.add(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.CONSISTENCY, message));
            }
        }
        catch (RuntimeException e) {
            log.warn("Consistency check failed for variant exercise {}: {}. Skipping the semantic gate.", exercise.getId(), e.getMessage());
        }
    }
}
