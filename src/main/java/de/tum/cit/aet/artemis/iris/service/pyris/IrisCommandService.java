package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
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
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Executes commands that Iris performs on the client mid-pipeline (before its answer). A command is pushed to the user's browser over WebSocket and this service blocks until the
 * browser acknowledges whether it was carried out, so the Pyris agent tool learns the real outcome. On success, the corresponding COMMAND marker is persisted into the chat
 * history.
 * <p>
 * Transport and marker format are type-agnostic; what a command <em>means</em> is not. {@link #executeCommand} switches on the type, and point-out is the only case defined so
 * far — every other type is dropped without side effects.
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

    /**
     * The only command type Artemis implements so far. Every other type is dropped in {@link #executeCommand}.
     */
    private static final String POINT_OUT_TYPE = "pointOut";

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
     * <p>
     * Dispatches on the command type: supporting a new one means adding a case here plus the client-side code that executes it. A type without a case is dropped instantly — no
     * client could carry it out, so forwarding it would only block the pipeline until the ack timeout expires for nobody.
     *
     * @param job     the chat job the command belongs to
     * @param command the command to execute
     * @return the result reported back to Pyris
     */
    public PyrisCommandResultDTO executeCommand(ChatJob job, PyrisCommandDTO command) {
        return switch (command.type()) {
            case POINT_OUT_TYPE -> executePointOut(job, command);
            case null, default -> {
                log.debug("Ignoring Iris command of unsupported type {}", command.type());
                yield PyrisCommandResultDTO.notApplied();
            }
        };
    }

    /**
     * Points the student to a position in the lecture combined view and, once the client confirms it moved there, records the point-out in the chat history.
     *
     * @param job     the chat job the command belongs to
     * @param command the point-out command to execute
     * @return the result reported back to Pyris
     */
    private PyrisCommandResultDTO executePointOut(ChatJob job, PyrisCommandDTO command) {
        if (!isValidPointOut(command.parameters())) {
            return PyrisCommandResultDTO.notApplied();
        }
        var session = irisSessionRepository.findByIdElseThrow(job.sessionId());
        if (!dispatchToClient(session, command)) {
            return PyrisCommandResultDTO.notApplied();
        }

        // The client already navigated, so the point-out succeeded regardless of the marker write. Persisting the
        // history marker is best-effort: a failure here must not turn into a 500 for Pyris.
        try {
            persistAndPushMarker(session, buildPointOutMarkerContent(command));
        }
        catch (Exception e) {
            log.error("Point-out command was applied on the client but persisting its marker failed", e);
        }
        return PyrisCommandResultDTO.success();
    }

    /**
     * Pushes a command to the session's user and blocks until the browser reports back whether it carried it out. Type-agnostic, so every command type shares one transport.
     *
     * @param session the chat session whose user should execute the command
     * @param command the command to push
     * @return whether the client applied it; {@code false} on rejection as well as on timeout
     */
    private boolean dispatchToClient(IrisSession session, PyrisCommandDTO command) {
        var userLogin = userRepository.findByIdElseThrow(session.getUserId()).getLogin();
        var correlationId = UUID.randomUUID().toString();
        var ackFuture = coordinationService.register(correlationId, userLogin);

        var request = new IrisCommandRequestWebsocketDTO(correlationId, command.type(), command.parameters());
        irisWebsocketService.send(userLogin, session.getId() + COMMAND_TOPIC_SUFFIX, request);
        log.debug("Iris command {} of type {} sent to user {} (session {}), awaiting client ack", correlationId, command.type(), userLogin, session.getId());

        // orTimeout completes the pending future exceptionally, which also unregisters it in the coordination service.
        boolean applied;
        try {
            applied = ackFuture.orTimeout(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS).join().applied();
        }
        catch (Exception e) {
            log.debug("Iris command {} received no client ack within {}s", correlationId, ACK_TIMEOUT_SECONDS);
            return false;
        }
        if (!applied) {
            log.debug("Iris command {} was not applied by the client", correlationId);
        }
        return applied;
    }

    /**
     * A point-out must name the lecture unit to navigate in and at least one position within it. {@link #buildPointOutMarkerContent} relies on this having passed.
     */
    private boolean isValidPointOut(Map<String, JsonNode> parameters) {
        return isPositiveIntegral(parameters.get("lectureUnitId")) && (isPositiveIntegral(parameters.get("page")) || isNonNegativeNumber(parameters.get("timestamp")));
    }

    private boolean isPositiveIntegral(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.asLong() > 0;
    }

    private boolean isNonNegativeNumber(JsonNode value) {
        return value != null && value.isNumber() && value.asDouble() >= 0;
    }

    /**
     * Persists an applied command as a COMMAND marker in the chat history and pushes it to the client.
     * <p>
     * Only commands whose marker can be rendered for both audiences are persisted (see {@link #executeCommand}): the student, who gets a clickable chip, and Iris, which gets the
     * system note Pyris builds from the marker JSON (Artemis forwards it unchanged). For a point-out, the stored {@code page} is the slide's index in the deck, the same value the
     * client navigates by, so the chip labels the slide with that index while Iris refers to it in its answer text by the number printed on the slide. Both can differ.
     */
    private void persistAndPushMarker(IrisSession session, ObjectNode markerContent) {
        var message = new IrisMessage();
        message.addContent(new IrisJsonMessageContent(markerContent));
        var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.COMMAND);
        irisChatWebsocketService.sendMessage(session, savedMessage, null, null);
    }

    /**
     * Builds a point-out marker's JSON content: the executed command in the same {@code {type, parameters}} shape it arrived in — so readers of the chat history parse markers
     * exactly like commands rather than a second, flattened format — plus the lecture unit's name, which only Artemis can resolve and which the chip needs as a label.
     *
     * @param command the applied point-out command
     * @return the marker content to persist
     */
    private ObjectNode buildPointOutMarkerContent(PyrisCommandDTO command) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", command.type());
        ObjectNode parameters = node.putObject("parameters");
        command.parameters().forEach(parameters::set);
        // Safe to dereference: isValidPointOut guarantees lectureUnitId is present and numeric.
        var name = resolveLectureUnitName(command.parameters().get("lectureUnitId").asLong());
        if (name != null && !name.isBlank()) {
            parameters.put("lectureUnitName", name);
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
