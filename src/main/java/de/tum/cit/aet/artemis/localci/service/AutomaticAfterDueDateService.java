package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.api.ExamApi;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.AutomaticAfterDueDatePreviewRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseBuildPlanConfigurationDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

@Service
@Lazy
@Profile(PROFILE_LOCALCI)
public class AutomaticAfterDueDateService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticAfterDueDateService.class);

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
            final ProgrammingExerciseBuildConfig programmingExerciseBuildConfig = programmingExerciseBuildConfigRepository
                    .getProgrammingExerciseBuildConfigElseThrow(relevantData.programmingExerciseId());
            final List<BuildPhaseDTO> phases = BuildPlanPhasesDTO.fromBuildPlanConfiguration(programmingExerciseBuildConfig.getBuildPlanConfiguration()).phases();
            hasAfterDueDatePhase = hasAfterDueDatePhase(phases);
        }
        else { // check once user saves, after due date phase would be set
            List<BuildPhaseDTO> phases = buildPhasesTemplateService.getBuildPlanPhasesFor(relevantData.programmingLanguage(), Optional.ofNullable(relevantData.projectType()),
                    relevantData.staticCodeAnalysisEnabled(), relevantData.sequentialTestRuns());
            if (relevantData.examId() != null) {
                phases = buildPhasesTemplateService.applyExamDefaults(phases);
            }
            hasAfterDueDatePhase = hasAfterDueDatePhase(phases);
        }

        if (!hasAfterDueDatePhase) {
            return null;
        }

        final Duration offset;
        if (relevantData.programmingExerciseId() == null) { // no reference exercise
            offset = null; // no previous offset
        }
        else {
            final Optional<Exam> originalExamOfExercise = examApi.flatMap(api -> api.findByExerciseId(loadedProgrammingExercise.getId()));
            final ZonedDateTime originalDueDate = originalExamOfExercise
                    .map(originalExam -> relevantData.examId() != null && loadedExam.getId().equals(originalExam.getId()) ? dueDate : getLatestExamEndDateWithGrace(originalExam))
                    .orElse(loadedProgrammingExercise.getDueDate());

            offset = originalDueDate == null || loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(originalDueDate, loadedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        }

        return toOffsetDate(dueDate, offset);
    }

    /**
     * Computes the "Run Tests after Due Date" value for a programming exercise.
     *
     * @param programmingExercise the programming exercise
     * @param buildConfig         its build configuration, which is stored separately and read by the caller
     * @return the computed date or null if the value would not be set
     */
    public ZonedDateTime computeBuildAndTestDate(ProgrammingExercise programmingExercise, ProgrammingExerciseBuildConfig buildConfig) {
        return computeBuildAndTestDate(programmingExercise, buildConfig.getBuildPlanConfiguration(), null, null, false);
    }

    /**
     * Computes the "Run Tests after Due Date" value for an existing programming exercise.
     *
     * @param programmingExercise the programming exercise
     * @param buildConfig         its build configuration, which is stored separately and read by the caller
     * @param buildAndTestOffset  the offset to use for the computation (optional). If provided, the date is always recomputed.
     * @return the computed date or null if the value would not be set
     */
    public ZonedDateTime computeBuildAndTestDate(final ProgrammingExercise programmingExercise, final ProgrammingExerciseBuildConfig buildConfig,
            final Duration buildAndTestOffset) {
        return computeBuildAndTestDate(programmingExercise, buildConfig.getBuildPlanConfiguration(), buildAndTestOffset, null, false);
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
        if (programmingExercises.isEmpty()) {
            return Set.of();
        }
        final ZonedDateTime newLatestEndDate = getLatestExamEndDateWithGrace(examWithExercises);

        // The configuration is a row of its own, so it is read here rather than through the exercise - once for the
        // whole exam, and projected down to the build plan, which is all the computation below reads.
        final Map<Long, ProgrammingExerciseBuildPlanConfigurationDTO> buildPlansByExerciseId = programmingExerciseBuildConfigRepository
                .findBuildPlanConfigurationsByProgrammingExerciseIds(programmingExercises.stream().map(ProgrammingExercise::getId).toList()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseBuildPlanConfigurationDTO::exerciseId, Function.identity()));

        final List<ProgrammingExercise> updatedExercises = new ArrayList<>();
        for (ProgrammingExercise programmingExercise : programmingExercises) {
            final Duration offset = originalLatestEndDate == null || programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null ? null
                    : Duration.between(originalLatestEndDate, programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());

            final ProgrammingExerciseBuildPlanConfigurationDTO buildPlan = buildPlansByExerciseId.get(programmingExercise.getId());
            if (buildPlan == null) {
                throw new EntityNotFoundException("ProgrammingExerciseBuildConfig", programmingExercise.getId());
            }

            final ZonedDateTime computedBuildAndTestDate;
            try {
                computedBuildAndTestDate = computeBuildAndTestDate(programmingExercise, buildPlan.buildPlanConfiguration(), offset, newLatestEndDate, true);
            }
            catch (JacksonException e) {
                log.error("Skipping automatic build-and-test date recomputation for programming exercise {} due to invalid build plan configuration in build config {}.",
                        programmingExercise.getId(), buildPlan.buildConfigId(), e);
                continue;
            }
            if (!Objects.equals(programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate(), computedBuildAndTestDate)) {
                programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(computedBuildAndTestDate);
                updatedExercises.add(programmingExercise);
            }
        }

        if (updatedExercises.isEmpty()) {
            return Set.of();
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

    private ZonedDateTime computeBuildAndTestDate(final ProgrammingExercise exercise, @Nullable final String buildPlanConfiguration, final Duration offset,
            final ZonedDateTime newLatestWithGraceExamEndDate, final boolean forceCompute) {
        final ZonedDateTime dueDate = exercise.isExamExercise() ? newLatestWithGraceExamEndDate == null && examApi.isPresent()
                ? getLatestExamEndDateWithGrace(examApi.orElseThrow().findByExerciseId(exercise.getId()).orElseThrow())
                : newLatestWithGraceExamEndDate : exercise.getDueDate();

        final boolean hasAfterDueDatePhase = hasAfterDueDatePhase(BuildPlanPhasesDTO.fromBuildPlanConfiguration(buildPlanConfiguration).phases());

        if (!hasAfterDueDatePhase || dueDate == null) {
            return null;
        }

        // if correctly set already then keep as is to allow client to modify the build and test date
        final ZonedDateTime currentBuildAndTestDate = exercise.getBuildAndTestStudentSubmissionsAfterDueDate();
        if (!forceCompute && offset == null && currentBuildAndTestDate != null && !dueDate.isAfter(currentBuildAndTestDate)) {
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
