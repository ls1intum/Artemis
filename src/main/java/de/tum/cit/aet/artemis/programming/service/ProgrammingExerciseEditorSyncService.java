package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseEditorFileSyncDTO;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseEditorSyncEventDTO;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseEditorSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseEditorSyncService.class);

    public static final String CLIENT_INSTANCE_HEADER = "X-Artemis-Client-Instance-ID";

    private final WebsocketMessagingService websocketMessagingService;

    public ProgrammingExerciseEditorSyncService(WebsocketMessagingService websocketMessagingService) {
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
    @Nullable
    public static String getClientInstanceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest().getHeader(CLIENT_INSTANCE_HEADER);
        }
        return null;
    }

    /**
     * Broadcast a new commit alert to all active editors.
     * This notifies users that a new commit has been made (potentially from an offline IDE)
     * and they should refresh their editor to get the latest changes.
     *
     * @param exerciseId            the exercise id
     * @param target                the target repository type associated with this commit (e.g. template repository, solution repository, tests repository)
     * @param auxiliaryRepositoryId (optional) the id of the auxiliary repository associated with this commit
     */
    public void broadcastNewCommitAlert(long exerciseId, ProgrammingExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId) {
        ProgrammingExerciseEditorSyncEventDTO payload = ProgrammingExerciseEditorSyncEventDTO.forNewCommitAlert(target, auxiliaryRepositoryId, getClientInstanceId());
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send new commit alert for exercise {}", exerciseId, exception);
            return null;
        });
    }

    /**
     * Broadcast file-level changes to all active editors.
     *
     * @param exerciseId            the exercise id
     * @param target                the target data type associated with this change (e.g. template repository, solution repository, auxiliary repository, problem statement)
     * @param auxiliaryRepositoryId (optional) the id of the auxiliary repository associated with this change
     * @param filePatch             (optional) the file patch associated with this change
     */
    public void broadcastFileChanges(long exerciseId, ProgrammingExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId,
            @Nullable ProgrammingExerciseEditorFileSyncDTO filePatch) {
        ProgrammingExerciseEditorSyncEventDTO payload = ProgrammingExerciseEditorSyncEventDTO.forFilePatch(target, auxiliaryRepositoryId, getClientInstanceId(),
                filePatch != null ? List.of(filePatch) : null);
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send synchronization message for exercise {}", exerciseId, exception);
            return null;
        });
    }

}
