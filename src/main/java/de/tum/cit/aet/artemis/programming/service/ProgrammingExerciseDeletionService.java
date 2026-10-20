package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.localci.service.SharedQueueManagementService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseDeletionService.class);

    private static final String DELETIONS_IN_PROGRESS = "programming-exercise-deletions-in-progress";

    /**
     * Upper bound for the time a deletion marker survives if the node that set it never removes it, e.g. because it crashed in the middle of the deletion.
     */
    private static final Duration DELETION_MARKER_TIME_TO_LIVE = Duration.ofHours(1);

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final ParticipationDeletionService participationDeletionService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    private final Optional<SharedQueueManagementService> sharedQueueManagementService;

    private final DistributedMap<Long, Boolean> deletionsInProgress;

    public ProgrammingExerciseDeletionService(ProgrammingExerciseRepositoryService programmingExerciseRepositoryService,
            ProgrammingExerciseRepository programmingExerciseRepository, ParticipationDeletionService participationDeletionService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, InstanceMessageSendService instanceMessageSendService,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, RepositoryVcsAccessTokenService repositoryVcsAccessTokenService,
            Optional<SharedQueueManagementService> sharedQueueManagementService, DistributedDataProvider distributedDataProvider) {
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationDeletionService = participationDeletionService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.repositoryVcsAccessTokenService = repositoryVcsAccessTokenService;
        this.sharedQueueManagementService = sharedQueueManagementService;
        this.deletionsInProgress = distributedDataProvider.getExpiringMap(DELETIONS_IN_PROGRESS, DELETION_MARKER_TIME_TO_LIVE);
    }

    /**
     * Announces to the whole cluster that the programming exercise is about to be deleted and stops the builds of all its participations.
     * <p>
     * The build system keeps delivering results while the exercise is taken apart, and the deletion cannot be one transaction. A result that is written after the
     * results of a participation have been deleted but before the exercise row is removed makes that removal fail with a foreign key violation. Every node therefore
     * drops the results of an exercise for which this marker is set, see {@link #isBeingDeleted(long)}. Queued builds are removed and running builds are cancelled.
     * The caller must call {@link #endDeletion(long)} in a {@code finally} block if this method returned {@code true}.
     *
     * @param exerciseId the id of the programming exercise that is going to be deleted
     * @return true if this call set the marker and the caller is responsible for removing it, false if another deletion of the same exercise already holds it
     */
    public boolean beginDeletion(long exerciseId) {
        boolean ownsMarker = deletionsInProgress.putIfAbsent(exerciseId, Boolean.TRUE) == null;
        try {
            sharedQueueManagementService.ifPresent(service -> service.cancelAllJobsForExercise(exerciseId));
        }
        catch (RuntimeException exception) {
            // The caller never learns that it owns the marker, so nobody else would remove it.
            if (ownsMarker) {
                endDeletion(exerciseId);
            }
            throw exception;
        }
        return ownsMarker;
    }

    /**
     * Removes the marker set by {@link #beginDeletion(long)}, so that the results of the exercise are processed again if the deletion failed and the exercise still exists.
     *
     * @param exerciseId the id of the programming exercise whose deletion has finished
     */
    public void endDeletion(long exerciseId) {
        deletionsInProgress.remove(exerciseId);
    }

    /**
     * Checks whether the deletion of the programming exercise has started on any node and has not finished yet.
     * Results of builds of such an exercise must not be written, because they would be removed again or, worse, block the deletion.
     *
     * @param exerciseId the id of the programming exercise
     * @return true if the exercise is being deleted
     */
    public boolean isBeingDeleted(long exerciseId) {
        return deletionsInProgress.get(exerciseId) != null;
    }

    /**
     * Delete a programming exercise, including its template and solution participations.
     *
     * @param programmingExerciseId     id of the programming exercise to delete.
     * @param deleteBaseReposBuildPlans if true will also delete build plans and projects.
     */
    public void delete(Long programmingExerciseId, boolean deleteBaseReposBuildPlans) {
        // Note: This method does not accept a programming exercise to solve issues with nested Transactions.
        // It would be good to refactor the delete calls and move the validity checks down from the resources to the service methods (e.g. EntityNotFound).
        final var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationCategoriesById(programmingExerciseId)
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

        // Note: we fetch the programming exercise again here with student participations to avoid Hibernate issues during the delete operation below
        var programmingExerciseWithStudentParticipations = programmingExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(programmingExerciseId);
        log.debug("Delete programming exercises with student participations: {}", programmingExerciseWithStudentParticipations.getStudentParticipations());
        // Remove the repository-scoped VCS access tokens before deleting the exercise (the exercise_id foreign key uses ON DELETE RESTRICT).
        repositoryVcsAccessTokenService.deleteByExerciseId(programmingExerciseId);

        // Deleted as late as possible: a build that was already running or whose result was already queued when the deletion started can still write a result for the
        // template or solution participation until here, and a result that remains would make the deletion of the exercise fail with a foreign key violation.
        SolutionProgrammingExerciseParticipation solutionProgrammingExerciseParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateProgrammingExerciseParticipation = programmingExercise.getTemplateParticipation();
        if (solutionProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteResultsAndSubmissionsOfParticipation(solutionProgrammingExerciseParticipation.getId(), true);
        }
        if (templateProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteResultsAndSubmissionsOfParticipation(templateProgrammingExerciseParticipation.getId(), true);
        }

        // This will also delete the template & solution participation: we explicitly use deleteById to avoid potential Hibernate issues during deletion
        programmingExerciseRepository.deleteById(programmingExerciseId);
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
