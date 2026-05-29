package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exam.api.ExamApi;
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

    private final Optional<ExamApi> examApi;

    public AutomaticAfterDueDateService(ProgrammingExerciseRepository programmingExerciseRepository, Optional<ExamDateApi> examDateApi,
            BuildPhasesTemplateService buildPhasesTemplateService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, Optional<ExamApi> examApi) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.examDateApi = examDateApi;
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.examApi = examApi;
    }

    /**
     * Computes the "Run Tests after Due Date" value for a programming exercise.
     * This method is used when the client needs to see what the date will be set to
     * if the update/create/import is applied.
     *
     * @param relevantData              the relevant data needed for knowing what the due date will be set to
     * @param loadedProgrammingExercise the already loaded programming exercise that has to be set
     * @param loadedExam                the already loaded exam that has to be set
     * @return the computed date or null if the value would not be set
     */
    public ZonedDateTime getAutomaticBuildAndTestDate(final AutomaticAfterDueDatePreviewRequestDTO relevantData, final ProgrammingExercise loadedProgrammingExercise,
            final Exam loadedExam) throws IOException {
        final ZonedDateTime dueDate;
        if (relevantData.examId() != null) { // in an exam
            dueDate = getLatestExamEndDateWithGrace(loadedExam);
        }
        else { // not in an exam
            dueDate = relevantData.dueDate();
        }

        if (dueDate == null) {
            return null;
        }

        final boolean hasAfterDueDatePhase;
        if (relevantData.hasAfterDueDateBuildPhase() != null) { // has been explicitly set
            hasAfterDueDatePhase = relevantData.hasAfterDueDateBuildPhase();
        }
        else if (relevantData.programmingExerciseId() != null) { // has not been overwritten but exercise exists
            final ProgrammingExerciseBuildConfig programmingExerciseBuildConfig = loadedProgrammingExercise.getBuildConfig();
            final Optional<BuildPlanPhasesDTO> buildPlanPhases = programmingExerciseBuildConfig.getBuildPlanPhases();
            final List<BuildPhaseDTO> phases = buildPlanPhases.map(BuildPlanPhasesDTO::phases).orElse(null);
            hasAfterDueDatePhase = hasAfterDueDatePhase(phases);
        }
        else { // check once user saves, after due date phase would be set
            List<BuildPhaseDTO> phases = buildPhasesTemplateService.getBuildPlanPhasesFor(Objects.requireNonNull(relevantData.programmingLanguage()),
                    Optional.ofNullable(relevantData.projectType()), relevantData.staticCodeAnalysisEnabled(), relevantData.sequentialTestRuns());
            if (relevantData.examId() != null) {
                phases = buildPhasesTemplateService.applyExamDefaults(phases);
            }
            hasAfterDueDatePhase = hasAfterDueDatePhase(phases);
        }

        if (!hasAfterDueDatePhase) {
            return null;
        }

        Optional<Exam> originalExamOfExercise = Optional.empty();

        final Duration offset;
        if (relevantData.programmingExerciseId() == null) { // no reference exercise
            offset = null; // no previous offset
        }
        // reference exercise exists and not in exam
        else if (relevantData.examId() == null || (originalExamOfExercise = examApi.flatMap(api -> api.findByExerciseId(loadedProgrammingExercise.getId()))).isEmpty()) {
            offset = loadedProgrammingExercise.getDueDate() == null || loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(loadedProgrammingExercise.getDueDate(), loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        }
        else { // exercise and exam exist
            final ZonedDateTime originalDueDate = loadedExam.getId().equals(originalExamOfExercise.orElseThrow().getId()) ? dueDate
                    : getLatestExamEndDateWithGrace(originalExamOfExercise.orElseThrow());

            offset = loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(originalDueDate, loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        }

        return toOffsetDate(dueDate, offset);
    }

    /**
     * Computes the "Run Tests after Due Date" value for a programming exercise.
     *
     * @param programmingExerciseWithBuildConfig the programming exercise with its build configuration
     * @return the computed date or null if the value would not be set
     */
    public ZonedDateTime computeBuildAndTestDate(ProgrammingExercise programmingExerciseWithBuildConfig) {
        return computeBuildAndTestDate(programmingExerciseWithBuildConfig, null, null, false);
    }

    /**
     * Computes the "Run Tests after Due Date" value for an existing programming exercise.
     *
     * @param programmingExerciseWithBuildConfig the programming exercise with its build configuration
     * @param buildAndTestOffset                 the offset to use for the computation (optional). If provided, the date is always recomputed.
     * @return the computed date or null if the value would not be set
     */
    public ZonedDateTime computeBuildAndTestDate(final ProgrammingExercise programmingExerciseWithBuildConfig, final Duration buildAndTestOffset) {
        return computeBuildAndTestDate(programmingExerciseWithBuildConfig, buildAndTestOffset, null, false);
    }

    /**
     * Computes the original offset between the exercise's reference date and its "Run Tests after Due Date" value.
     * For course exercises, the reference date is the exercise due date.
     * For exam exercises, the reference date is the latest individual exam end date including the exam grace period.
     *
     * @param programmingExercise the programming exercise before an update mutates its timeline values
     * @return the original offset, or null if either the reference date or the build-and-test date is unavailable
     */
    public Duration getOriginalBuildAndTestOffset(ProgrammingExercise programmingExercise) {
        final ZonedDateTime originalReferenceDate = getOriginalBuildAndTestReferenceDate(programmingExercise);
        final ZonedDateTime originalBuildAndTestDate = programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate();
        return originalReferenceDate == null || originalBuildAndTestDate == null ? null : Duration.between(originalReferenceDate, originalBuildAndTestDate);
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
        final ZonedDateTime newLatestEndDate = getLatestExamEndDateWithGrace(examWithExercises);

        final List<ProgrammingExercise> updatedExercises = new ArrayList<>();
        for (ProgrammingExercise programmingExercise : programmingExercises) {
            final Duration offset = originalLatestEndDate == null || programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(originalLatestEndDate, programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());

            if (!Hibernate.isInitialized(programmingExercise.getBuildConfig())) {
                programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow());
            }

            final ZonedDateTime computedBuildAndTestDate = computeBuildAndTestDate(programmingExercise, offset, newLatestEndDate, true);
            if (!Objects.equals(programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate(), computedBuildAndTestDate)) {
                programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(computedBuildAndTestDate);
                updatedExercises.add(programmingExercise);
            }
        }

        if (updatedExercises.isEmpty()) {
            return Collections.emptySet();
        }

        return programmingExerciseRepository.saveAll(updatedExercises).stream().map(ProgrammingExercise::getId).collect(Collectors.toSet());
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

    private ZonedDateTime getOriginalBuildAndTestReferenceDate(ProgrammingExercise programmingExercise) {
        if (!programmingExercise.isExamExercise()) {
            return programmingExercise.getDueDate();
        }

        return examApi.flatMap(api -> api.findByExerciseId(programmingExercise.getId()).map(this::getLatestExamEndDateWithGrace)).orElse(null);
    }

    private ZonedDateTime computeBuildAndTestDate(final ProgrammingExercise exerciseWithBuildConfig, final Duration offset, final ZonedDateTime newLatestWithGraceExamEndDate,
            final boolean forceCompute) {
        final ZonedDateTime dueDate = exerciseWithBuildConfig.isExamExercise() ? newLatestWithGraceExamEndDate == null && examApi.isPresent()
                ? getLatestExamEndDateWithGrace(examApi.orElseThrow().findByExerciseId(exerciseWithBuildConfig.getId()).orElseThrow())
                : newLatestWithGraceExamEndDate : exerciseWithBuildConfig.getDueDate();

        final boolean hasAfterDueDatePhase = hasAfterDueDatePhase(exerciseWithBuildConfig.getBuildConfig().getBuildPlanPhases().map(BuildPlanPhasesDTO::phases).orElse(null));

        if (!hasAfterDueDatePhase || dueDate == null) {
            return null;
        }

        // if correctly set already then keep as is to allow client to modify the build and test date
        final ZonedDateTime currentBuildAndTestDate = exerciseWithBuildConfig.getBuildAndTestStudentSubmissionsAfterDueDate();
        if (!forceCompute && currentBuildAndTestDate != null && !dueDate.isAfter(currentBuildAndTestDate)) {
            return currentBuildAndTestDate;
        }

        return toOffsetDate(dueDate, offset);
    }

    private boolean hasAfterDueDatePhase(final List<BuildPhaseDTO> phases) {
        return phases != null && phases.stream().anyMatch(phase -> phase.condition() == BuildPhaseCondition.AFTER_DUE_DATE);
    }

    private ZonedDateTime toOffsetDate(ZonedDateTime referenceDate, Duration offset) {
        if (offset != null) {
            return referenceDate.plus(offset);
        }
        return referenceDate.plusMinutes(BUILD_AND_TEST_OFFSET_MINUTES);
    }
}
