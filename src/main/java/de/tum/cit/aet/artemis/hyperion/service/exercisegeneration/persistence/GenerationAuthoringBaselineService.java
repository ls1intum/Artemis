package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/** Freezes the authoring inputs on core; no mutable entity or secret is sent to the worker. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationAuthoringBaselineService {

    private final ProgrammingExerciseRepository exercises;

    private final ProgrammingExerciseBuildConfigRepository buildConfigurations;

    private final ProgrammingExerciseTestCaseRepository tests;

    private final ProgrammingExerciseTaskRepository tasks;

    public GenerationAuthoringBaselineService(ProgrammingExerciseRepository exercises, ProgrammingExerciseBuildConfigRepository buildConfigurations,
            ProgrammingExerciseTestCaseRepository tests, ProgrammingExerciseTaskRepository tasks) {
        this.exercises = exercises;
        this.buildConfigurations = buildConfigurations;
        this.tests = tests;
        this.tasks = tasks;
    }

    /** Detached values used only for final-save comparison. Nullable metadata values remain distinct from empty values. */
    public record Snapshot(Map<String, String> metadata, Map<String, String> buildConfiguration, List<TestCase> tests, List<Task> tasks) {

        public Snapshot {
            metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            buildConfiguration = Collections.unmodifiableMap(new LinkedHashMap<>(buildConfiguration));
            tests = List.copyOf(tests);
            tasks = List.copyOf(tasks);
        }
    }

    public record TestCase(Long id, @Nullable String name, @Nullable String weight, @Nullable String bonusMultiplier, @Nullable String bonusPoints, @Nullable String visibility,
            @Nullable Boolean active, @Nullable String type) {
    }

    public record Task(Long id, @Nullable String name, List<Long> testIds) {

        public Task {
            testIds = List.copyOf(testIds);
        }
    }

    /**
     * Capture while the caller owns the exercise DB write guard.
     *
     * @param exercise fresh exercise loaded under the reservation
     * @return detached authoring inputs
     */
    public Snapshot capture(ProgrammingExercise exercise) {
        ProgrammingExerciseBuildConfig configuration = buildConfigurations.getProgrammingExerciseBuildConfigElseThrow(exercise.getId());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("getTitle", value(exercise.getTitle()));
        metadata.put("getShortName", value(exercise.getShortName()));
        metadata.put("getProblemStatement", value(exercise.getProblemStatement()));
        metadata.put("getGradingInstructions", value(exercise.getGradingInstructions()));
        metadata.put("getMaxPoints", value(exercise.getMaxPoints()));
        metadata.put("getBonusPoints", value(exercise.getBonusPoints()));
        metadata.put("getAssessmentType", value(exercise.getAssessmentType()));
        metadata.put("getReleaseDate", value(exercise.getReleaseDate()));
        metadata.put("getStartDate", value(exercise.getStartDate()));
        metadata.put("getDueDate", value(exercise.getDueDate()));
        metadata.put("getAssessmentDueDate", value(exercise.getAssessmentDueDate()));
        metadata.put("getExampleSolutionPublicationDate", value(exercise.getExampleSolutionPublicationDate()));
        metadata.put("getDifficulty", value(exercise.getDifficulty()));
        metadata.put("getMode", value(exercise.getMode()));
        metadata.put("getAllowComplaintsForAutomaticAssessments", value(exercise.getAllowComplaintsForAutomaticAssessments()));
        metadata.put("getIncludedInOverallScore", value(exercise.getIncludedInOverallScore()));
        metadata.put("getPresentationScoreEnabled", value(exercise.getPresentationScoreEnabled()));
        metadata.put("getSecondCorrectionEnabled", value(exercise.getSecondCorrectionEnabled()));
        metadata.put("getAllowOnlineEditor", value(exercise.isAllowOnlineEditor()));
        metadata.put("getAllowOfflineIde", value(exercise.isAllowOfflineIde()));
        metadata.put("getAllowOnlineIde", value(exercise.isAllowOnlineIde()));
        metadata.put("getStaticCodeAnalysisEnabled", value(exercise.isStaticCodeAnalysisEnabled()));
        metadata.put("getMaxStaticCodeAnalysisPenalty", value(exercise.getMaxStaticCodeAnalysisPenalty()));
        metadata.put("getProgrammingLanguage", value(exercise.getProgrammingLanguage()));
        metadata.put("getPackageName", value(exercise.getPackageName()));
        metadata.put("getShowTestNamesToStudents", value(exercise.getShowTestNamesToStudents()));
        metadata.put("getBuildAndTestStudentSubmissionsAfterDueDate", value(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()));
        metadata.put("getProjectType", value(exercise.getProjectType()));
        metadata.put("getTestRepositoryUri", value(exercise.getTestRepositoryUri()));
        metadata.put("getProjectKey", value(exercise.getProjectKey()));
        metadata.put("isReleaseTestsWithExampleSolution", value(exercise.isReleaseTestsWithExampleSolution()));
        metadata.put("templateParticipationId", exercise.getTemplateParticipation() == null ? null : value(exercise.getTemplateParticipation().getId()));
        metadata.put("templateRepositoryUri", exercise.getTemplateParticipation() == null ? null : exercise.getTemplateParticipation().getRepositoryUri());
        metadata.put("templateBuildPlanId", exercise.getTemplateParticipation() == null ? null : exercise.getTemplateParticipation().getBuildPlanId());
        metadata.put("solutionParticipationId", exercise.getSolutionParticipation() == null ? null : value(exercise.getSolutionParticipation().getId()));
        metadata.put("solutionRepositoryUri", exercise.getSolutionParticipation() == null ? null : exercise.getSolutionParticipation().getRepositoryUri());
        metadata.put("solutionBuildPlanId", exercise.getSolutionParticipation() == null ? null : exercise.getSolutionParticipation().getBuildPlanId());
        Map<String, String> build = new LinkedHashMap<>();
        build.put("id", value(configuration.getId()));
        build.put("hasSequentialTestRuns", value(configuration.hasSequentialTestRuns()));
        build.put("getBranch", value(configuration.getBranch()));
        build.put("getBuildPlanConfiguration", value(configuration.getBuildPlanConfiguration()));
        build.put("getCheckoutSolutionRepository", value(configuration.getCheckoutSolutionRepository()));
        build.put("getTestCheckoutPath", value(configuration.getTestCheckoutPath()));
        build.put("getAssignmentCheckoutPath", value(configuration.getAssignmentCheckoutPath()));
        build.put("getSolutionCheckoutPath", value(configuration.getSolutionCheckoutPath()));
        build.put("getTimeoutSeconds", value(configuration.getTimeoutSeconds()));
        build.put("getDockerFlags", value(configuration.getDockerFlags()));
        build.put("getTheiaImage", value(configuration.getTheiaImage()));
        build.put("isAllowBranching", value(configuration.isAllowBranching()));
        build.put("getBranchRegex", value(configuration.getBranchRegex()));
        build.put("getBuildPlanAccessSecret", value(configuration.getBuildPlanAccessSecret()));
        List<TestCase> capturedTests = tests.findByExerciseId(exercise.getId()).stream().map(test -> new TestCase(test.getId(), test.getTestName(), value(test.getWeight()),
                value(test.getBonusMultiplier()), value(test.getBonusPoints()), value(test.getVisibility()), test.isActive(), value(test.getType())))
                .sorted(Comparator.comparing(TestCase::id)).toList();
        List<Task> capturedTasks = tasks.findByExerciseIdWithTestCases(exercise.getId()).stream()
                .map(task -> new Task(task.getId(), task.getTaskName(), task.getTestCases().stream().map(test -> test.getId()).sorted().toList()))
                .sorted(Comparator.comparing(Task::id)).toList();
        return new Snapshot(metadata, build, capturedTests, capturedTasks);
    }

    /**
     * Load fresh persisted authoring values while the caller holds its seed or save reservation.
     *
     * @param exerciseId exercise to reload
     * @return current exercise with repository participations
     */
    public ProgrammingExercise requireCurrentExercise(long exerciseId) {
        return exercises.findWithAllParticipationsById(exerciseId)
                .orElseThrow(() -> new IllegalStateException("The exercise no longer exists; the verified candidate was not saved"));
    }

    /**
     * Compare after acquiring the final-save DB guard and before any canonical repository or metadata write.
     *
     * @param exerciseId exercise being saved
     * @param expected   frozen seed values; missing evidence fails closed
     */
    public void assertMatches(long exerciseId, @Nullable Snapshot expected) {
        ProgrammingExercise current = requireCurrentExercise(exerciseId);
        if (expected == null || !expected.equals(capture(current))) {
            throw new AuthoringConflictException();
        }
    }

    @Nullable
    private static String value(@Nullable Object value) {
        return Objects.toString(value, null);
    }

    /** Signals a retained verified candidate without any canonical writes. */
    public static class AuthoringConflictException extends IllegalStateException {

        public AuthoringConflictException() {
            this("The exercise changed during authoring. The verified candidate was retained; nothing was saved.");
        }

        public AuthoringConflictException(String message) {
            super(message);
        }
    }
}
