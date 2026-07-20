package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultOutcome;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.PendingBuild;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Capability adapters for programming-exercise variants. Thin wrappers around existing, battle-tested
 * services — very little new logic.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ProgrammingVariantAdapters implements VariantTypeAdapters {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingVariantAdapters.class);

    /** Suffix-retry budget for short-name/project-key collisions. */
    private static final int MAX_NAME_ATTEMPTS = 10;

    private final HyperionProgrammingExerciseContextRendererService contextRendererService;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final UserRepository userRepository;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final VariantBuildVerificationService buildVerificationService;

    private final HyperionConsistencyCheckService consistencyCheckService;

    private final VariantPlacementService variantPlacementService;

    private final ExerciseVariantJobService jobService;

    private final String defaultBranch;

    public ProgrammingVariantAdapters(HyperionProgrammingExerciseContextRendererService contextRendererService, ProgrammingExerciseImportService programmingExerciseImportService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseTaskService programmingExerciseTaskService, UserRepository userRepository,
            GitService gitService, RepositoryService repositoryService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingSubmissionService programmingSubmissionService,
            VariantBuildVerificationService buildVerificationService, HyperionConsistencyCheckService consistencyCheckService, VariantPlacementService variantPlacementService,
            ExerciseVariantJobService jobService, @Value("${artemis.version-control.default-branch:main}") String defaultBranch) {
        this.contextRendererService = contextRendererService;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.userRepository = userRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.buildVerificationService = buildVerificationService;
        this.consistencyCheckService = consistencyCheckService;
        this.variantPlacementService = variantPlacementService;
        this.jobService = jobService;
        this.defaultBranch = defaultBranch;
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
        return contextRendererService.renderContext(exercise);
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
        try {
            ProgrammingExercise imported = programmingExerciseImportService.importProgrammingExercise(original, newExercise, false, false, false);
            // The import service unconditionally resets the problem statement to the ORIGINAL's (its REST flow
            // forbids editing the statement while importing) — re-apply the plan's statement afterwards, or the
            // skeleton's value from buildVariantSkeleton is silently lost and the variant keeps the source text.
            imported.setProblemStatement(stripPlantUmlCodeFences(plan.problemStatement()));
            // The planner writes its statement against the SOURCE context, so any <testid> markers it copied
            // reference the source's test case ids — remap them to the variant's test cases (matched by test
            // name, both sides straight from the import) like the import flow itself does.
            Map<String, Long> variantTestIdByName = imported.getTestCases().stream()
                    .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, ProgrammingExerciseTestCase::getId, (first, second) -> first));
            Map<Long, Long> newTestCaseIdByOldId = original.getTestCases().stream().filter(testCase -> variantTestIdByName.containsKey(testCase.getTestName()))
                    .collect(Collectors.toMap(ProgrammingExerciseTestCase::getId, testCase -> variantTestIdByName.get(testCase.getTestName()), (first, second) -> first));
            programmingExerciseTaskService.updateTestIds(imported, newTestCaseIdByOldId);
            imported = programmingExerciseRepository.save(imported);
            programmingExerciseTaskService.updateTasksFromProblemStatement(imported);
            // Return a copy WITHOUT an initialized task collection: task rows change again after provisioning
            // (agent problem-statement updates, the final FINALIZING re-sync), and saving this instance later
            // (group placement) with a stale initialized orphanRemoval collection fails with EntityNotFound.
            return programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(imported.getId());
        }
        catch (Exception e) {
            throw new RuntimeException("Importing the variant clone failed: " + e.getMessage(), e);
        }
    }

    @Override
    public VariantToolset createTools(Exercise variant, VariantJob job) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(variant.getId());
        User user = userRepository.getUserWithGroupsAndAuthorities(job.getInitiatorLogin());
        return new ProgrammingVariantTools(exercise, user, job.getJobId(), jobService, gitService, repositoryService, buildVerificationService,
                continuousIntegrationTriggerService::triggerBuild, programmingExerciseParticipationService, programmingSubmissionService, programmingExerciseRepository,
                programmingExerciseTaskService, defaultBranch);
    }

    @Override
    public VerificationReport verify(Exercise variant, ChangePlan plan, VariantJob job) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(variant.getId());
        List<VerificationReport.VerificationFinding> findings = new ArrayList<>();

        // Gate 1: fresh builds for BOTH repositories — solution must pass 100%, template must fail with tests
        // present. Builds are always re-triggered with a freshness bound so a test-repo change can never smuggle
        // a stale green result past the gate (build-dependency constraint). Both are triggered together and
        // awaited jointly: they run concurrently in CI, so the gate costs about the slower build, not the sum.
        verifyBuilds(exercise, findings);

        // Gate 3: semantic consistency between problem statement and artifacts — only worth
        // its LLM cost once the deterministic gates are green.
        if (findings.isEmpty()) {
            checkConsistency(exercise, findings);
        }
        return new VerificationReport(findings.isEmpty(), List.copyOf(findings));
    }

    @Override
    public void finalizeVariant(Exercise variant, VariantJob job) {
        // Final task→test wiring (deterministic, no LLM): tests ADDED during transformation only exist as
        // test-case rows once their first build result was processed, so the provision-time sync could not
        // resolve them yet — re-run the sync on the final statement. Also covers rounds that changed tests but
        // never called updateProblemStatement.
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(variant.getId());
        programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
        // What remains is the shared placement logic.
        variantPlacementService.place(variant, job.getSourceExerciseId(), job.getRequest());
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
    private void verifyBuilds(ProgrammingExercise exercise, List<VerificationReport.VerificationFinding> findings) {
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
            String commitHash = gitService.getLastCommitHash(repositoryUri);
            ProgrammingExerciseParticipation participation = switch (repositoryType) {
                case TEMPLATE -> programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exercise.getId());
                default -> programmingExerciseParticipationService.retrieveSolutionParticipation(exercise);
            };
            Instant triggeredAt = Instant.now();
            try {
                continuousIntegrationTriggerService.triggerBuild(participation, commitHash, repositoryType);
                pending.put(repositoryType, new PendingBuild(commitHash, triggeredAt));
            }
            catch (ContinuousIntegrationException e) {
                findings.add(new VerificationReport.VerificationFinding(buildGate.gate(), "Could not trigger the " + repositoryType + " build: " + e.getMessage()));
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        try {
            Map<RepositoryType, BuildResultOutcome> outcomes = buildVerificationService.waitForBuildResults(exercise, pending);
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
            case FAILED -> findings.add(new VerificationReport.VerificationFinding(buildGate.gate(),
                    buildGate.target() + " Current result: " + buildVerificationService.describeBuildResult(outcome.result())));
            // Distinct detail for CI timeouts.
            case TIMED_OUT -> findings.add(new VerificationReport.VerificationFinding(buildGate.gate(),
                    "The " + repositoryType + " build result did not arrive within the timeout (BuildResultState.TIMED_OUT). " + buildGate.target()));
            case PARTICIPATION_NOT_FOUND, CI_TRIGGER_FAILED ->
                findings.add(new VerificationReport.VerificationFinding(buildGate.gate(), "The " + repositoryType + " build could not be verified: " + outcome.state()));
        }
    }

    /**
     * Gate 3: semantic consistency between the problem statement and the repositories (incl. "test names referenced
     * in the problem statement exist in the test repo" — the check the ChangePlan invariants call out).
     * Best-effort: an unavailable checker must not fail an otherwise green variant.
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
