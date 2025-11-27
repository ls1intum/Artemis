package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
import de.tum.cit.aet.artemis.programming.domain.SynchronizationTarget;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseSynchronizationDTO;

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
     * Broadcast a single change to all active editors.
     *
     * @param exerciseId            the exercise id
     * @param target                the target data type associated with this change (e.g. template repository, solution repository, auxiliary repository, problem statement)
     * @param auxiliaryRepositoryId (optional) the id of the auxiliary repository associated with this change
     */
    public void broadcastChange(long exerciseId, SynchronizationTarget target, @Nullable Long auxiliaryRepositoryId) {
        var payload = new ProgrammingExerciseSynchronizationDTO(target, auxiliaryRepositoryId, getClientInstanceId());
        websocketMessagingService.sendMessage(getSynchronizationTopic(exerciseId), payload).exceptionally(exception -> {
            log.warn("Cannot send synchronization message for exercise {}", exerciseId, exception);
            return null;
        });
    }

}
