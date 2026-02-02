package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

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
import de.tum.cit.aet.artemis.exercise.domain.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncEventType;
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
    public void broadcastNewCommitAlert(long exerciseId, ExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId) {
        ExerciseNewCommitAlertDTO payload = new ExerciseNewCommitAlertDTO(ExerciseEditorSyncEventType.NEW_COMMIT_ALERT, target, auxiliaryRepositoryId, getClientSessionId(),
                System.currentTimeMillis());
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send new commit alert for exercise {}", exerciseId, exception);
            return null;
        });
    }

    public void broadcastNewExerciseVersionAlert(Long exerciseId, Long exerciseVersionId, User author, Set<String> changedFields) {
        ExerciseNewVersionAlertDTO payload = new ExerciseNewVersionAlertDTO(ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT, ExerciseEditorSyncTarget.EXERCISE_METADATA,
                exerciseVersionId, new UserPublicInfoDTO(author), changedFields, getClientSessionId());
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send new exercise version alert for exercise {}", exerciseId, exception);
            return null;
        });
    }

}
