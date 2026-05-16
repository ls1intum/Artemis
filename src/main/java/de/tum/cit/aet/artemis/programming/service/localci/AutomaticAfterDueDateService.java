package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.AutomaticAfterDueDatePreviewRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

@Service
@Lazy
@Profile(PROFILE_LOCALCI)
public class AutomaticAfterDueDateService {

    private static final int BUILD_AND_TEST_OFFSET_MINUTES = 15;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final Optional<ExamDateApi> examDateApi;

    private final BuildPhasesTemplateService buildPhasesTemplateService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public AutomaticAfterDueDateService(ProgrammingExerciseRepository programmingExerciseRepository, Optional<ExamDateApi> examDateApi,
            BuildPhasesTemplateService buildPhasesTemplateService, final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.examDateApi = examDateApi;
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    public ZonedDateTime computeRunAfterDueDate(ZonedDateTime newDueDate, boolean hasAfterDueDatePhase, Duration offset) {
        if (!hasAfterDueDatePhase || newDueDate == null) {
            return null;
        }
        return toOffsetDate(newDueDate, offset);
    }

    /**
     * Computes the "Run Tests after Due Date" value for a programming exercise.
     * This method is used when the client needs to see what the date will be set to
     * if the update/create/import is applied.
     *
     * @param relevantData the relevant data needed for knowing what the due date will be set to
     * @return the computed date or null if the value would not be set
     */
    public ZonedDateTime getAutomaticBuildAndTestDate(final AutomaticAfterDueDatePreviewRequestDTO relevantData) throws IOException {
        final ZonedDateTime dueDate;
        if (relevantData.examId() != null && examDateApi.isPresent()) {
            dueDate = examDateApi.orElseThrow().getLatestIndividualExamEndDate(relevantData.examId());
        }
        else {
            dueDate = relevantData.dueDate();
        }

        final boolean hasAfterDueDatePhase;
        if (relevantData.hasAfterDueDateBuildPhase() != null) {
            hasAfterDueDatePhase = relevantData.hasAfterDueDateBuildPhase();
        }
        else if (relevantData.programmingExerciseId() != null) {
            final ProgrammingExerciseBuildConfig programmingExerciseBuildConfig = programmingExerciseBuildConfigRepository
                    .findByProgrammingExerciseId(relevantData.programmingExerciseId()).orElseThrow();
            final Optional<BuildPlanPhasesDTO> buildPlanPhases = programmingExerciseBuildConfig.getBuildPlanPhases();
            final List<BuildPhaseDTO> phases = buildPlanPhases.map(BuildPlanPhasesDTO::phases).orElse(null);
            hasAfterDueDatePhase = hasAfterDueDatePhase(phases);
        }
        else {
            List<BuildPhaseDTO> phases = buildPhasesTemplateService.getBuildPlanPhasesFor(Objects.requireNonNull(relevantData.programmingLanguage()),
                    Optional.ofNullable(relevantData.projectType()), relevantData.staticCodeAnalysisEnabled(), relevantData.sequentialTestRuns());
            if (relevantData.examId() != null) {
                phases = buildPhasesTemplateService.applyExamDefaults(phases);
            }
            hasAfterDueDatePhase = hasAfterDueDatePhase(phases);
        }

        final Duration offset;
        if (relevantData.programmingExerciseId() == null) {
            offset = null;
        }
        else if (relevantData.examId() == null) {
            final ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(relevantData.programmingExerciseId());
            offset = programmingExercise.getDueDate() == null || programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(programmingExercise.getDueDate(), programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        }
        else {
            final ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(relevantData.programmingExerciseId());
            offset = dueDate == null || programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(dueDate, programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        }

        return computeRunAfterDueDate(dueDate, hasAfterDueDatePhase, offset);
    }

    /**
     * Computes the "Run Tests after Due Date" value for a programming exercise.
     * This method is used when the client needs to see what the date will be set to
     * if the update/create/import is applied.
     *
     * @param programmingExerciseId       the id of the programming exercise
     * @param newDueDate                  the due date of the exercise
     * @param changedBuildHasAfterDueDate whether a build phase that runs after the due date is set, if null its left uncustomized
     * @param programmingLanguage         the programming language of the exercise
     * @param projectType                 the project type of the exercise
     * @param staticCodeAnalysisEnabled   whether static code analysis is enabled
     * @param sequentialTestRuns          whether sequential test runs are enabled
     * @param exam                        the exam if the exercise is an exam exercise
     * @return the computed date or null if the value would not be set
     */
    public ZonedDateTime getAutomaticBuildAndTestDate(Long programmingExerciseId, ZonedDateTime newDueDate, Boolean changedBuildHasAfterDueDate,
            ProgrammingLanguage programmingLanguage, ProjectType projectType, Boolean staticCodeAnalysisEnabled, Boolean sequentialTestRuns, Exam exam) {
        boolean isExistingProgrammingExercise = programmingExerciseId != null;
        boolean isInExam = exam != null;

        boolean hasNewlySetNoAfterDueDate = Boolean.FALSE.equals(changedBuildHasAfterDueDate);
        boolean hasNoNewDueDateAsStandalone = newDueDate == null && !isInExam;
        if (hasNewlySetNoAfterDueDate || hasNoNewDueDateAsStandalone) {
            return null;
        }

        if (isExistingProgrammingExercise) {
            ProgrammingExercise exercise = programmingExerciseRepository.findForUpdateByIdElseThrow(programmingExerciseId);
            return deriveBuildAndTestDate(exercise, changedBuildHasAfterDueDate, newDueDate, null);
        }

        boolean hasAfterDueDatePhaseAfterSave = Boolean.TRUE.equals(changedBuildHasAfterDueDate)
                || hasAfterDueDateBuildPhaseInDefaultConfig(exam != null, programmingLanguage, projectType, staticCodeAnalysisEnabled, sequentialTestRuns);
        if (!hasAfterDueDatePhaseAfterSave) {
            return null;
        }

        if (isInExam) {
            ZonedDateTime examEndWithGrace = getLatestExamEndDateWithGrace(exam);
            return toOffsetDate(examEndWithGrace, null);
        }

        return toOffsetDate(newDueDate, null);
    }

    /**
     * Recomputes the "Run Tests after Due Date" value for the given programming exercise and updates the exercise object.
     *
     * @param exercise the programming exercise to recompute the date for
     */
    public void recomputeBuildAndTestDate(ProgrammingExercise exercise, ZonedDateTime originalDueDate) {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(deriveBuildAndTestDate(exercise, null, null, originalDueDate));
    }

    /**
     * Recomputes the "Run Tests after Due Date" values for all programming exercises in an exam.
     *
     * @param examId                    the id of the exam
     * @param originalExamLatestEndDate the original latest end date of the exam (used for offset calculation), only needed when timing changed
     * @return a set of the ids of the programming exercises that were updated
     */
    public Set<Long> recomputeBuildAndTestDatesForExam(long examId, ZonedDateTime originalExamLatestEndDate) {
        Set<Long> programmingExerciseIds = programmingExerciseRepository.findProgrammingExerciseIdsByExamId(examId);
        for (Long programmingExerciseId : programmingExerciseIds) {
            ProgrammingExercise exercise = programmingExerciseRepository
                    .findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesAndBuildConfigElseThrow(programmingExerciseId);
            ZonedDateTime previousDate = exercise.getBuildAndTestStudentSubmissionsAfterDueDate();
            ZonedDateTime derivedDate = deriveBuildAndTestDate(exercise, null, null, originalExamLatestEndDate);
            if (!Objects.equals(previousDate, derivedDate)) {
                exercise.setBuildAndTestStudentSubmissionsAfterDueDate(derivedDate);
                programmingExerciseRepository.save(exercise);
            }
        }
        return programmingExerciseIds;
    }

    private ZonedDateTime deriveBuildAndTestDate(ProgrammingExercise exercise, Boolean hasSetAfterDueDatePhase, ZonedDateTime newDueDate, ZonedDateTime originalDueDate) {
        boolean hasAfterDueDatePhase = Objects.requireNonNullElse(hasSetAfterDueDatePhase, hasAfterDueDateBuildPhase(exercise));
        if (!hasAfterDueDatePhase) {
            return null;
        }

        final ZonedDateTime previousDueDate = exercise.isExamExercise() || originalDueDate != null ? originalDueDate : exercise.getDueDate();
        final ZonedDateTime previousRunAfterDate = exercise.getBuildAndTestStudentSubmissionsAfterDueDate();
        Duration offset = (previousDueDate == null || previousRunAfterDate == null) ? null : Duration.between(previousDueDate, previousRunAfterDate);

        if (newDueDate != null) {
            return toOffsetDate(newDueDate, offset);
        }

        ZonedDateTime referenceDate = exercise.isExamExercise() ? getLatestExamEndDateWithGrace(exercise.getExerciseGroup().getExam()) : exercise.getDueDate();
        if (referenceDate == null) {
            return null;
        }

        // if already explicitly set and valid, just use that
        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && !referenceDate.isAfter(exercise.getBuildAndTestStudentSubmissionsAfterDueDate())) {
            return exercise.getBuildAndTestStudentSubmissionsAfterDueDate();
        }

        return toOffsetDate(referenceDate, offset);
    }

    private boolean hasAfterDueDateBuildPhase(ProgrammingExercise exercise) {
        if (exercise.getBuildConfig() == null) {
            return false;
        }
        final List<BuildPhaseDTO> phases = exercise.getBuildConfig().getBuildPlanPhases().map(BuildPlanPhasesDTO::phases).orElse(null);
        return hasAfterDueDatePhase(phases);
    }

    private boolean hasAfterDueDateBuildPhaseInDefaultConfig(boolean examMode, ProgrammingLanguage programmingLanguage, ProjectType projectType, Boolean staticCodeAnalysisEnabled,
            Boolean sequentialTestRuns) {
        if (programmingLanguage == null) {
            return false;
        }
        try {
            List<BuildPhaseDTO> phases = buildPhasesTemplateService.getBuildPlanPhasesFor(programmingLanguage, Optional.ofNullable(projectType),
                    Boolean.TRUE.equals(staticCodeAnalysisEnabled), Boolean.TRUE.equals(sequentialTestRuns));
            if (examMode) {
                phases = buildPhasesTemplateService.applyExamDefaults(phases);
            }
            return hasAfterDueDatePhase(phases);
        }
        catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Calculates the latest individual exam end date including the grace period.
     *
     * @param exam the exam to calculate the date for
     * @return the latest end date with grace period or null if the exam is null
     */
    public ZonedDateTime getLatestExamEndDateWithGrace(Exam exam) {
        if (exam == null) {
            return null;
        }
        ZonedDateTime latestExamEndDate = examDateApi.map(api -> api.getLatestIndividualExamEndDate(exam)).orElse(exam.getEndDate());
        int gracePeriodInSeconds = Objects.requireNonNullElse(exam.getGracePeriod(), 0);
        return latestExamEndDate.plusSeconds(gracePeriodInSeconds);
    }

    private boolean hasAfterDueDatePhase(final List<BuildPhaseDTO> phases) {
        return phases != null && phases.stream().anyMatch(phase -> phase.condition() == BuildPhaseCondition.AFTER_DUE_DATE);
    }

    private ZonedDateTime toOffsetDate(ZonedDateTime referenceDate, Duration offset) {
        if (referenceDate == null) {
            return null;
        }
        if (offset != null) {
            return referenceDate.plus(offset);
        }
        return referenceDate.plusMinutes(BUILD_AND_TEST_OFFSET_MINUTES);
    }
}
