package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseDeletionService.class);

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final ParticipationDeletionService participationDeletionService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ExerciseRepository exerciseRepository;

    private final ParticipationRepository participationRepository;

    public ProgrammingExerciseDeletionService(ProgrammingExerciseRepositoryService programmingExerciseRepositoryService,
            ProgrammingExerciseRepository programmingExerciseRepository, ParticipationDeletionService participationDeletionService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, InstanceMessageSendService instanceMessageSendService,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ExerciseRepository exerciseRepository, ParticipationRepository participationRepository) {
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationDeletionService = participationDeletionService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.exerciseRepository = exerciseRepository;
        this.participationRepository = participationRepository;
    }

    /**
     * Delete a programming exercise, including its template and solution participations.
     *
     * @param programmingExerciseId     id of the programming exercise to delete.
     * @param deleteBaseReposBuildPlans if true will also delete build plans and projects.
     */
    public void delete(Long programmingExerciseId, boolean deleteBaseReposBuildPlans) {
        log.debug("Deleting programming exercise with id: {} with BaseReposBuildPlans {}", programmingExerciseId, deleteBaseReposBuildPlans);
        // Note: This method does not accept a programming exercise to solve issues with nested Transactions.
        // It would be good to refactor the delete calls and move the validity checks down from the resources to the service methods (e.g. EntityNotFound).
        final var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));

        // The delete operation cancels scheduled tasks (like locking/unlocking repositories)
        // As the programming exercise might already be deleted once the scheduling node receives the message, only the
        // id is used to cancel the scheduling. No interaction with the database is required.
        cancelScheduledOperations(programmingExercise.getId());

        if (deleteBaseReposBuildPlans) {
            deleteBuildPlans(programmingExercise);
            programmingExerciseRepositoryService.deleteRepositories(programmingExercise);
        }
        programmingExerciseRepositoryService.deleteLocalRepoCopies(programmingExercise);

        SolutionProgrammingExerciseParticipation solutionProgrammingExerciseParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateProgrammingExerciseParticipation = programmingExercise.getTemplateParticipation();
        // Delete results and submissions of template and solution participations first
        if (solutionProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteResultsAndSubmissionsOfParticipation(solutionProgrammingExerciseParticipation.getId(), true);
        }
        if (templateProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteResultsAndSubmissionsOfParticipation(templateProgrammingExerciseParticipation.getId(), true);
        }

        // Note: We explicitly delete template and solution participations because they have a complex
        // inheritance situation that prevents orphanRemoval from working correctly with NOT NULL FKs.
        // See the comments in ProgrammingExercise.java for details.
        // Clear references on the exercise side to break FK relationships
        programmingExercise.setTemplateParticipation(null);
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);

        // Now delete the participations explicitly by ID
        if (templateProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteParticipationById(templateProgrammingExerciseParticipation.getId());
        }
        if (solutionProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteParticipationById(solutionProgrammingExerciseParticipation.getId());
        }

        // Safety check: Delete any remaining participations that still reference this exercise.
        // This handles edge cases where participations exist but aren't properly linked from the exercise side
        // (e.g., template/solution participations where the exercise's FK reference is NULL but the participation
        // still has exercise_id pointing to this exercise).
        List<Participation> remainingParticipations = participationRepository.findAllByExerciseId(programmingExerciseId);
        if (!remainingParticipations.isEmpty()) {
            log.warn("Found {} remaining participations for exercise {} that weren't deleted through normal flow. Deleting them now.", remainingParticipations.size(),
                    programmingExerciseId);
            for (Participation participation : remainingParticipations) {
                participationDeletionService.deleteParticipationById(participation.getId());
            }
        }

        // Fetch the exercise fresh with studentParticipations to ensure L2 cache has correct data.
        // All participations should now be deleted.
        var exerciseToDelete = exerciseRepository.findByIdWithStudentParticipationsElseThrow(programmingExerciseId);

        // Delete the programming exercise
        exerciseRepository.delete(exerciseToDelete);
    }

    private void deleteBuildPlans(ProgrammingExercise programmingExercise) {
        final var templateBuildPlanId = programmingExercise.getTemplateBuildPlanId();
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        if (templateBuildPlanId != null) {
            continuousIntegration.deleteBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanId);
        }
        final var solutionBuildPlanId = programmingExercise.getSolutionBuildPlanId();
        if (solutionBuildPlanId != null) {
            continuousIntegration.deleteBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanId);
        }
        continuousIntegration.deleteProject(programmingExercise.getProjectKey());
    }

    private void cancelScheduledOperations(long programmingExerciseId) {
        instanceMessageSendService.sendProgrammingExerciseScheduleCancel(programmingExerciseId);
    }

    /**
     * Delete all tasks with solution entries for an existing ProgrammingExercise.
     * This method can be used to reset the mappings in case of unconsidered edge cases.
     *
     * @param exerciseId of the exercise
     */
    public void deleteTasks(long exerciseId) {
        List<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCaseElseThrow(exerciseId);
        programmingExerciseTaskRepository.deleteAll(tasks);
    }
}
