package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisCommandRequestWebsocketDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisCommandDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisCommandResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisPointOutCommandDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Executes commands that Iris performs on the client mid-pipeline (before its answer). A command is pushed to the user's browser over WebSocket and this service blocks until the
 * browser acknowledges whether it was carried out, so the Pyris agent tool learns the real outcome. On success, the corresponding COMMAND marker is persisted into the chat
 * history.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisCommandService {

    private static final Logger log = LoggerFactory.getLogger(IrisCommandService.class);

    /**
     * WebSocket topic suffix (appended to the per-session Iris topic) on which command requests are pushed to the client.
     */
    public static final String COMMAND_TOPIC_SUFFIX = "/commands";

    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final IrisCommandCoordinationService coordinationService;

    private final IrisWebsocketService irisWebsocketService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final IrisMessageService irisMessageService;

    private final IrisSessionRepository irisSessionRepository;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    public IrisCommandService(IrisCommandCoordinationService coordinationService, IrisWebsocketService irisWebsocketService, IrisChatWebsocketService irisChatWebsocketService,
            IrisMessageService irisMessageService, IrisSessionRepository irisSessionRepository, UserRepository userRepository, ObjectMapper objectMapper,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.coordinationService = coordinationService;
        this.irisWebsocketService = irisWebsocketService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.irisMessageService = irisMessageService;
        this.irisSessionRepository = irisSessionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

    /**
     * Executes a command for the given chat job, blocking until the client has (or has not) carried it out.
     *
     * @param job     the chat job the command belongs to
     * @param command the command to execute
     * @return the result reported back to Pyris
     */
    public PyrisCommandResultDTO executeCommand(ChatJob job, PyrisCommandDTO command) {
        return switch (command) {
            case PyrisPointOutCommandDTO pointOut -> executePointOut(job, pointOut);
        };
    }

    private PyrisCommandResultDTO executePointOut(ChatJob job, PyrisPointOutCommandDTO pointOut) {
        if (pointOut.lectureUnitId() == null || (pointOut.page() == null && pointOut.timestamp() == null)) {
            return PyrisCommandResultDTO.notApplied();
        }
        var session = irisSessionRepository.findByIdElseThrow(job.sessionId());
        var userLogin = userRepository.findByIdElseThrow(session.getUserId()).getLogin();
        var correlationId = UUID.randomUUID().toString();
        var ackFuture = coordinationService.register(correlationId, userLogin);

        var request = new IrisCommandRequestWebsocketDTO(correlationId, pointOut.type(), pointOut.lectureUnitId(), pointOut.page(), pointOut.timestamp());
        irisWebsocketService.send(userLogin, session.getId() + COMMAND_TOPIC_SUFFIX, request);
        log.debug("Point-out command {} sent to user {} (session {}), awaiting client ack", correlationId, userLogin, session.getId());

        // orTimeout completes the pending future exceptionally, which also unregisters it in the coordination service.
        boolean applied;
        try {
            applied = ackFuture.orTimeout(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS).join().applied();
        }
        catch (Exception e) {
            log.debug("Point-out command {} received no client ack within {}s", correlationId, ACK_TIMEOUT_SECONDS);
            return PyrisCommandResultDTO.notApplied();
        }
        if (!applied) {
            log.debug("Point-out command {} was not applied by the client", correlationId);
            return PyrisCommandResultDTO.notApplied();
        }

        // The client already navigated, so the point-out succeeded regardless of the marker write. Persisting the
        // history marker is best-effort: a failure here must not turn into a 500 for Pyris.
        try {
            persistAndPushMarker(session, buildPointOutContent(pointOut));
        }
        catch (Exception e) {
            log.error("Point-out command {} was applied on the client but persisting its marker failed", correlationId, e);
        }
        return PyrisCommandResultDTO.success();
    }

    /**
     * Persists the point-out as a COMMAND marker in the chat history and pushes it to the client.
     */
    private void persistAndPushMarker(IrisSession session, ObjectNode markerContent) {
        var message = new IrisMessage();
        message.addContent(new IrisJsonMessageContent(markerContent));
        var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.COMMAND);
        irisChatWebsocketService.sendMessage(session, savedMessage, null, null);
    }

    private ObjectNode buildPointOutContent(PyrisPointOutCommandDTO pointOut) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", pointOut.type());
        node.put("lectureUnitId", pointOut.lectureUnitId());
        if (pointOut.page() != null) {
            node.put("page", pointOut.page());
        }
        if (pointOut.timestamp() != null) {
            node.put("timestamp", pointOut.timestamp());
        }
        var name = resolveLectureUnitName(pointOut.lectureUnitId());
        if (name != null && !name.isBlank()) {
            node.put("lectureUnitName", name);
        }
        return node;
    }

    /**
     * Resolves the lecture unit's display name for the marker, or {@code null} if it cannot be resolved. The name is only a label, so a lookup failure must not cost us the marker.
     */
    private @Nullable String resolveLectureUnitName(long lectureUnitId) {
        try {
            return lectureUnitRepositoryApi.map(api -> api.findByIdElseThrow(lectureUnitId).getName()).orElse(null);
        }
        catch (Exception e) {
            log.warn("Could not resolve lecture unit name for point-out marker (unitId={})", lectureUnitId);
            return null;
        }
    }
}
