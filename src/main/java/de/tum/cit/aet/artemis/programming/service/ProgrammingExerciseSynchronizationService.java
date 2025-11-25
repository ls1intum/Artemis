package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseSynchronizationDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseSynchronizationDTO.SynchronizationTarget;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseSynchronizationService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseSynchronizationService.class);

    public static final String CLIENT_INSTANCE_HEADER = "X-Artemis-Client-Instance-ID";

    private final WebsocketMessagingService websocketMessagingService;

    public ProgrammingExerciseSynchronizationService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    public static String getSynchronizationTopic(long exerciseId) {
        return "/topic/programming-exercises/" + exerciseId + "/synchronization";
    }

    /**
     * Retrieves the client instance id from the current request, if available.
     *
     * @return the client instance id or null if no request context is available
     */
    public static String getClientInstanceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest().getHeader(CLIENT_INSTANCE_HEADER);
        }
        return null;
    }

    /**
     * Compare two exercise snapshots and return the change that should be broadcast to clients.
     *
     * @param newSnapshot      the new snapshot
     * @param previousSnapshot the previous snapshot (optional)
     * @return detected change or null if none
     */
    public ProgrammingExerciseSynchronizationDTO determineChange(ExerciseSnapshotDTO newSnapshot, ExerciseSnapshotDTO previousSnapshot) {
        if (previousSnapshot == null || newSnapshot.programmingData() == null || previousSnapshot.programmingData() == null) {
            return null;
        }

        var newProgrammingData = newSnapshot.programmingData();
        var previousProgrammingData = previousSnapshot.programmingData();
        SynchronizationTarget target = null;
        Long auxiliaryRepositoryId = null;

        if (commitIdChanged(previousProgrammingData.templateParticipation(), newProgrammingData.templateParticipation())) {
            target = SynchronizationTarget.TEMPLATE_REPOSITORY;
        }
        else if (commitIdChanged(previousProgrammingData.solutionParticipation(), newProgrammingData.solutionParticipation())) {
            target = SynchronizationTarget.SOLUTION_REPOSITORY;
        }
        else if (!Objects.equals(previousProgrammingData.testsCommitId(), newProgrammingData.testsCommitId())) {
            target = SynchronizationTarget.TESTS_REPOSITORY;
        }
        else {
            Map<Long, String> previousAuxiliaries = Optional.ofNullable(previousProgrammingData.auxiliaryRepositories()).orElseGet(List::of).stream().collect(
                    Collectors.toMap(ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::id, ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::commitId));
            for (var auxiliary : Optional.ofNullable(newProgrammingData.auxiliaryRepositories()).orElseGet(List::of)) {
                var previousCommitId = previousAuxiliaries.get(auxiliary.id());
                if (!Objects.equals(previousCommitId, auxiliary.commitId())) {
                    target = SynchronizationTarget.AUXILIARY_REPOSITORY;
                    auxiliaryRepositoryId = auxiliary.id();
                    break;
                }
            }
        }

        if (target == null && !Objects.equals(previousSnapshot.problemStatement(), newSnapshot.problemStatement())) {
            target = SynchronizationTarget.PROBLEM_STATEMENT;
        }

        return target == null ? null : new ProgrammingExerciseSynchronizationDTO(target, auxiliaryRepositoryId, null);
    }

    /**
     * Broadcast a single change to all active editors.
     *
     * @param exerciseId the exercise id
     * @param change     change to broadcast
     */
    public void broadcastChange(long exerciseId, ProgrammingExerciseSynchronizationDTO change) {
        if (change == null) {
            return;
        }
        var payload = new ProgrammingExerciseSynchronizationDTO(change.target(), change.auxiliaryRepositoryId(),
                change.clientInstanceId() != null ? change.clientInstanceId() : getClientInstanceId());
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send synchronization message for exercise {}", exerciseId, exception);
            return null;
        });
    }

    public void broadcastChange(long exerciseId, SynchronizationTarget target, Long auxiliaryRepositoryId) {
        broadcastChange(exerciseId, new ProgrammingExerciseSynchronizationDTO(target, auxiliaryRepositoryId, null));
    }

    private boolean commitIdChanged(ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO previousParticipation,
            ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO newParticipation) {
        if (previousParticipation == null && newParticipation == null) {
            return false;
        }
        var previousCommitId = previousParticipation == null ? null : previousParticipation.commitId();
        var newCommitId = newParticipation == null ? null : newParticipation.commitId();
        return !Objects.equals(previousCommitId, newCommitId);
    }
}
