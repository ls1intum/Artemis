package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
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

    private ZonedDateTime computeRunAfterDueDate(ZonedDateTime newDueDate, boolean hasAfterDueDatePhase, Duration offset) {
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
    public ZonedDateTime getAutomaticBuildAndTestDate(final AutomaticAfterDueDatePreviewRequestDTO relevantData, final ProgrammingExercise loadedProgrammingExercise,
            final Exam loadedExam) throws IOException {
        final ZonedDateTime dueDate;
        if (relevantData.examId() != null && examDateApi.isPresent()) {
            dueDate = getLatestExamEndDateWithGrace(loadedExam);
        }
        else {
            dueDate = relevantData.dueDate();
        }

        final boolean hasAfterDueDatePhase;
        if (relevantData.hasAfterDueDateBuildPhase() != null) {
            hasAfterDueDatePhase = relevantData.hasAfterDueDateBuildPhase();
        }
        else if (relevantData.programmingExerciseId() != null) {
            final ProgrammingExerciseBuildConfig programmingExerciseBuildConfig = loadedProgrammingExercise.getBuildConfig();
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
            offset = loadedProgrammingExercise.getDueDate() == null || loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(loadedProgrammingExercise.getDueDate(), loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        }
        else {
            // dueDate here must be the derived due date from the exam
            offset = dueDate == null || loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(dueDate, loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        }

        return computeRunAfterDueDate(dueDate, hasAfterDueDatePhase, offset);
    }

    public ZonedDateTime computeBuildAndTestDateForNewExercise(ProgrammingExercise programmingExerciseWithBuildConfig) {
        return computeBuildAndTestDateForExistingExercise(programmingExerciseWithBuildConfig, null);
    }

    public ZonedDateTime computeBuildAndTestDateForExistingExercise(final ProgrammingExercise programmingExerciseWithBuildConfig, final Duration buildAndTestOffset) {
        if (buildAndTestDateIsValid(programmingExerciseWithBuildConfig)) {
            return programmingExerciseWithBuildConfig.getBuildAndTestStudentSubmissionsAfterDueDate();
        }
        final List<BuildPhaseDTO> phases = programmingExerciseWithBuildConfig.getBuildConfig().getBuildPlanPhases().map(BuildPlanPhasesDTO::phases).orElse(null);
        return computeRunAfterDueDate(programmingExerciseWithBuildConfig.getDueDate(), hasAfterDueDatePhase(phases), buildAndTestOffset);
    }

    /**
     * Recomputes the "Run Tests after Due Date" values for all programming exercises in an exam.
     *
     * @param examWithExercises     the exam
     * @param originalLatestEndDate the original latest end date of the exam (used for offset calculation), only needed when timing changed
     * @return a set of the ids of the programming exercises that were updated
     */
    public Set<Long> updateAndSaveBuildAndTestDateInProgrammingExercisesOfExam(final Exam examWithExercises, final ZonedDateTime originalLatestEndDate) {
        final List<ProgrammingExercise> programmingExercises = examWithExercises.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())
                .filter(ProgrammingExercise.class::isInstance).map(e -> (ProgrammingExercise) e).toList();
        for (ProgrammingExercise programmingExercise : programmingExercises) {
            final Duration offset = originalLatestEndDate == null || programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(originalLatestEndDate, programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());

            if (programmingExercise.getBuildConfig() == null) {
                programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow());
            }

            final ZonedDateTime computedBuildAndTestDate = computeBuildAndTestDateForExistingExercise(programmingExercise, offset);
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(computedBuildAndTestDate);
        }

        return programmingExerciseRepository.saveAll(programmingExercises).stream().map(DomainObject::getId).collect(Collectors.toSet());
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

    private boolean buildAndTestDateIsValid(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getDueDate() == null && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null) {
            return false;
        }
        return programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                && !programmingExercise.getDueDate().isAfter(programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
    }
}
