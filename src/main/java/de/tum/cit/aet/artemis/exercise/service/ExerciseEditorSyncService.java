package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.jspecify.annotations.NonNull;
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
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncEventType;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseNewCommitAlertDTO;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseNewVersionAlertDTO;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseEditorSyncService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseEditorSyncService.class);

    public static final String CLIENT_SESSION_HEADER = "X-Artemis-Client-Session-ID";

    private final WebsocketMessagingService websocketMessagingService;

    public ExerciseEditorSyncService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Builds the websocket topic used for exercise editor synchronization.
     *
     * @param exerciseId the exercise id
     * @return the topic for exercise synchronization events
     */
    public static String getSynchronizationTopic(long exerciseId) {
        return "/topic/exercises/" + exerciseId + "/synchronization";
    }

    /**
     * Retrieves the client session id from the current request, if available.
     *
     * @return the client session id or null if no request context is available
     */
    @Nullable
    public static String getClientSessionId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest().getHeader(CLIENT_SESSION_HEADER);
        }
        return null;
    }

    /**
     * Broadcast a new commit alert to all active editors.
     * This notifies users that a new commit has been made (potentially from an
     * offline IDE)
     * and they should refresh their editor to get the latest changes.
     *
     * @param exerciseId            the exercise id
     * @param target                the target repository type associated with this
     *                                  commit (e.g. template repository, solution
     *                                  repository, tests repository)
     * @param auxiliaryRepositoryId (optional) the id of the auxiliary repository
     *                                  associated with this commit
     */
    public void broadcastNewCommitAlert(@NonNull Long exerciseId, @NonNull ExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId) {
        ExerciseNewCommitAlertDTO payload = new ExerciseNewCommitAlertDTO(ExerciseEditorSyncEventType.NEW_COMMIT_ALERT, target, auxiliaryRepositoryId, getClientSessionId(),
                System.currentTimeMillis());
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send new commit alert for exercise {}", exerciseId, exception);
            return null;
        });
    }

    /**
     * Broadcast a new exercise version alert to active editors for metadata changes.
     *
     * @param exerciseId        the exercise id
     * @param exerciseVersionId the id of the newly created exercise version
     * @param author            the author of the new version
     * @param changedFields     the changed metadata fields compared to the previous version
     */
    public void broadcastNewExerciseVersionAlert(@NonNull Long exerciseId, @NonNull Long exerciseVersionId, @NonNull User author, @NonNull Set<String> changedFields) {
        ExerciseNewVersionAlertDTO payload = new ExerciseNewVersionAlertDTO(ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT, ExerciseEditorSyncTarget.EXERCISE_METADATA,
                exerciseVersionId, new UserPublicInfoDTO(author), changedFields, getClientSessionId(), System.currentTimeMillis());
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send new exercise version alert for exercise {}", exerciseId, exception);
            return null;
        });
    }

}
