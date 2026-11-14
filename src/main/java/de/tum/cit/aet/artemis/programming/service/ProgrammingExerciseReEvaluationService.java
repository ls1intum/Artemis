package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.localci.service.AutomaticAfterDueDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Re-evaluates a programming exercise after its update was applied, and saves the update with all follow-up effects of an ordinary update.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseReEvaluationService {

    private static final String ENTITY_NAME = "programmingExercise";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseUpdateMappingService programmingExerciseUpdateMappingService;

    private final ProgrammingExerciseMutationGuardService programmingExerciseMutationGuard;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ExerciseService exerciseService;

    private final Optional<AutomaticAfterDueDateService> automaticAfterDueDateService;

    private final Optional<SlideApi> slideApi;

    private final ParticipationRepository participationRepository;

    private final ExerciseVersionService exerciseVersionService;

    public ProgrammingExerciseReEvaluationService(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, ProgrammingExerciseUpdateMappingService programmingExerciseUpdateMappingService,
            ProgrammingExerciseMutationGuardService programmingExerciseMutationGuard, ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ExerciseService exerciseService, Optional<AutomaticAfterDueDateService> automaticAfterDueDateService, Optional<SlideApi> slideApi,
            ParticipationRepository participationRepository, ExerciseVersionService exerciseVersionService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.programmingExerciseUpdateMappingService = programmingExerciseUpdateMappingService;
        this.programmingExerciseMutationGuard = programmingExerciseMutationGuard;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.exerciseService = exerciseService;
        this.automaticAfterDueDateService = automaticAfterDueDateService;
        this.slideApi = slideApi;
        this.participationRepository = participationRepository;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * Applies the update to the exercise, re-evaluates its results and saves it.
     *
     * @param exerciseId                                  the id of the exercise
     * @param authorizationExercise                       the exercise as it was loaded for the checks of the request
     * @param updateDTO                                   the update to apply
     * @param deleteFeedbackAfterGradingInstructionUpdate whether the associated feedback is deleted
     * @param hyperionCompetencyIds                       IDs of links inferred through Hyperion's checklist
     * @param user                                        the user who requested the update
     * @return the updated exercise
     */
    public ProgrammingExerciseResponseDTO reEvaluateAndUpdate(long exerciseId, ProgrammingExercise authorizationExercise, UpdateProgrammingExerciseDTO updateDTO,
            Boolean deleteFeedbackAfterGradingInstructionUpdate, Set<Long> hyperionCompetencyIds, User user) {
        try (var lease = programmingExerciseMutationGuard.claimExternalMutation(exerciseId)) {
            // Reload after claiming so a request that waited for another mutation cannot apply a stale managed entity.
            var programmingExercise = lease.coordinated() ? programmingExerciseRepository.findForUpdateByIdElseThrow(exerciseId) : authorizationExercise;
            var buildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(exerciseId);

            // Capture ALL original values BEFORE update() mutates the entity via L1 cache.
            // These are needed for change detection in the service layer (notifications, build plans, etc.)
            final Double originalMaxPoints = programmingExercise.getMaxPoints();
            final Double originalBonusPoints = programmingExercise.getBonusPoints();
            final ZonedDateTime originalDueDate = programmingExercise.getDueDate();
            final ZonedDateTime originalReleaseDate = programmingExercise.getReleaseDate();
            final ZonedDateTime originalAssessmentDueDate = programmingExercise.getAssessmentDueDate();
            final String originalProblemStatement = programmingExercise.getProblemStatement();
            final String originalBuildPlanConfiguration = buildConfig.getBuildPlanConfiguration();
            final Set<Long> originalCompetencyIds = programmingExercise.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());
            final Duration originalBuildAndTestOffset = automaticAfterDueDateService.map(service -> service.getOriginalBuildAndTestOffset(programmingExercise)).orElse(null);
            final String originalBranch = buildConfig.getBranch();
            final var originalSettings = ProgrammingExerciseUpdateMappingService.OriginalSettings.of(programmingExercise, buildConfig);

            // Apply DTO changes BEFORE re-evaluation so that updated grading criteria take effect.
            programmingExerciseUpdateMappingService.apply(updateDTO, programmingExercise, buildConfig, hyperionCompetencyIds == null ? Set.of() : hyperionCompetencyIds);
            // The re-evaluation refuses what the update refuses
            programmingExerciseUpdateMappingService.validateUpdatedExercise(updateDTO, programmingExercise, buildConfig, originalSettings, false);
            // the default branch cannot be changed
            buildConfig.setBranch(originalBranch);

            programmingExerciseCreationUpdateService.prepareAndValidateTimelineForUpdate(programmingExercise, buildConfig, originalBuildAndTestOffset);

            exerciseService.reEvaluateExercise(programmingExercise, deleteFeedbackAfterGradingInstructionUpdate);

            // Call the service directly with the captured originals instead of re-entering the update path
            // (which would re-capture stale "originals" from the already-mutated L1 cache entity).
            ProgrammingExercise savedExercise = programmingExerciseCreationUpdateService.updateProgrammingExercise(programmingExercise, buildConfig, null, originalCompetencyIds,
                    originalBuildPlanConfiguration, originalReleaseDate, originalAssessmentDueDate, originalBuildAndTestOffset, originalProblemStatement);

            // Apply all post-save side effects that the normal update path performs
            exerciseService.logUpdate(savedExercise, savedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
            if (lease.coordinated()) {
                exerciseService.updatePointsInRelatedParticipantScoresSynchronously(originalMaxPoints, originalBonusPoints, savedExercise);
            }
            else {
                exerciseService.updatePointsInRelatedParticipantScores(originalMaxPoints, originalBonusPoints, savedExercise);
            }
            participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedExercise, originalDueDate);
            slideApi.ifPresent(api -> api.handleDueDateChange(originalDueDate, savedExercise));
            if (lease.coordinated()) {
                exerciseVersionService.createExerciseVersionSynchronously(savedExercise, user);
            }
            else {
                exerciseVersionService.createExerciseVersion(savedExercise, user);
            }

            return ProgrammingExerciseResponseDTO.of(savedExercise, buildConfig);
        }
    }
}
