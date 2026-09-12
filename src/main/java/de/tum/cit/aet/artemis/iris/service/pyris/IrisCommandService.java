package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

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

    /**
     * How long to wait for the addressed tab to report back before treating a command as not carried out. The tab answers either way as soon as it has tried, and negatively at
     * once wherever it can already tell that it never will — a closed combined view, or a viewer that is not coming — so this is a backstop for a tab that went away (closed,
     * reloaded, connection lost) rather than a budget the normal case spends. What it has to cover is the path node to broker to browser and back, plus the slow part: a viewer
     * that is rendered but whose document is still loading, which the client deliberately waits out before answering. Opening the combined view is not part of it — only a marker
     * click does that, and no pipeline waits on those.
     * <p>
     * Sized by that wait, and not to be trimmed against it. A budget that expires while the client is still loading its document reports the point-out as not applied and writes
     * no marker, and the client then navigates anyway: its late ack finds no pending future and is dropped, so the student's view moves while the answer says it did not. Pyris'
     * own timeout on the command call must stay above this value, so a client that really is gone surfaces as "not applied" rather than as a transport error.
     */
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
        // Safe to dereference: isValidPointOut guarantees lectureUnitId is present and numeric.
        var lectureUnit = resolveLectureUnitInCourse(command.parameters().get("lectureUnitId").asLong(), job.courseId());
        if (lectureUnit == null) {
            return PyrisCommandResultDTO.notApplied();
        }
        var session = irisSessionRepository.findByIdElseThrow(job.sessionId());
        if (!dispatchToClient(session, command, job.clientId())) {
            return PyrisCommandResultDTO.notApplied();
        }

        // The client already navigated, so the point-out succeeded regardless of the marker write. Persisting the
        // history marker is best-effort: a failure here must not turn into a 500 for Pyris.
        try {
            persistAndPushMarker(session, buildPointOutMarkerContent(command, lectureUnit));
        }
        catch (Exception e) {
            log.error("Point-out command was applied on the client but persisting its marker failed", e);
        }
        return PyrisCommandResultDTO.success();
    }

    /**
     * Pushes a command to the session's user and blocks until the browser reports back whether it carried it out. Type-agnostic, so every command type shares one transport.
     * <p>
     * The send goes to the user, so every tab with the session open receives it and carries it out. What the request restricts is not who acts but who <em>answers</em>: it names
     * the tab the chat run was started from, and only that one acknowledges. Exactly one reply therefore comes back, which is what makes the first ack authoritative — a bystanding
     * tab can neither claim success nor deny it on behalf of the tab the student is actually looking at.
     *
     * @param session        the chat session whose user should execute the command
     * @param command        the command to push
     * @param targetClientId the browser tab expected to answer, or null to let any tab of the user answer (runs started without a client, e.g. event-triggered ones)
     * @return whether the client applied it; {@code false} on rejection as well as on timeout
     */
    private boolean dispatchToClient(IrisSession session, PyrisCommandDTO command, @Nullable String targetClientId) {
        var userLogin = userRepository.findByIdElseThrow(session.getUserId()).getLogin();
        var correlationId = UUID.randomUUID().toString();
        // Registered before the send so an ack cannot arrive before there is a future to complete. The registration is
        // cleaned up by that future settling, which the ack or the timeout below always does — IrisWebsocketService#send
        // reports delivery failures through its own future rather than throwing, so it cannot skip past them.
        var ackFuture = coordinationService.register(correlationId, userLogin);

        var request = new IrisCommandRequestWebsocketDTO(correlationId, command.type(), command.parameters(), targetClientId);
        irisWebsocketService.send(userLogin, session.getId() + COMMAND_TOPIC_SUFFIX, request);
        log.debug("Iris command {} of type {} sent to user {} (session {}, client {}), awaiting client ack", correlationId, command.type(), userLogin, session.getId(),
                targetClientId);

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
     * system note Pyris builds from the marker JSON (Artemis forwards it unchanged). For a point-out, the stored {@code page} is the slide's index in the deck — the value the
     * client navigates by, which need not be the number printed on the slide. Pyris therefore sends that printed number along as {@code displayPage} whenever the slide has one,
     * and the chip labels itself with it so it agrees with the page number Iris names in its answer text. A slide without a printed number gets no {@code displayPage}; Iris names
     * no page for those either, so the chip falls back to the deck index without contradicting anything.
     */
    private void persistAndPushMarker(IrisSession session, ObjectNode markerContent) {
        var message = new IrisMessage();
        message.addContent(new IrisJsonMessageContent(markerContent));
        var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.COMMAND);
        irisChatWebsocketService.sendMessage(session, savedMessage, null, null);
    }

    /**
     * Builds a point-out marker's JSON content: the executed command in the same {@code {type, parameters}} shape it arrived in — so readers of the chat history parse markers
     * exactly like commands rather than a second, flattened format — plus the two things about the unit that only Artemis can resolve.
     * <p>
     * The unit's name is the chip's label. The id of its lecture is what a click on the chip navigates by: a marker outlives the context of the chat it was made in, which can be
     * switched to another lecture later on, so the chip must carry the lecture it belongs to rather than let the client read the one the conversation happens to sit in now.
     *
     * @param command     the applied point-out command
     * @param lectureUnit the unit the command points into, resolved by {@link #resolveLectureUnitInCourse}
     * @return the marker content to persist
     */
    private ObjectNode buildPointOutMarkerContent(PyrisCommandDTO command, LectureUnit lectureUnit) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", command.type());
        ObjectNode parameters = node.putObject("parameters");
        command.parameters().forEach(parameters::set);
        var lectureUnitName = lectureUnit.getName();
        if (lectureUnitName != null && !lectureUnitName.isBlank()) {
            parameters.put("lectureUnitName", lectureUnitName);
        }
        // The join fetch guarantees the lecture; only a lecture that was never persisted could lack an id.
        var lectureId = lectureUnit.getLecture().getId();
        if (lectureId != null) {
            parameters.put("lectureId", lectureId);
        }
        return node;
    }

    /**
     * Resolves the lecture unit a command points into, scoped to the course the chat belongs to, or {@code null} if it does not exist there.
     * <p>
     * The id is model-generated, so it is not trusted: a unit from another course must neither be dispatched to the client nor have its name persisted into this chat's history.
     * Scoping here also spares the pipeline the full ack timeout when Iris hallucinates an id — no client would ever navigate to it, so waiting for an ack that cannot come only
     * stalls the answer.
     *
     * @param lectureUnitId the unit id named by the command
     * @param courseId      the course of the chat job the command belongs to
     * @return the lecture unit, or {@code null} if it does not exist or belongs to another course
     */
    private @Nullable LectureUnit resolveLectureUnitInCourse(long lectureUnitId, long courseId) {
        if (lectureUnitRepositoryApi.isEmpty()) {
            // Without the lecture module there are no units to point into.
            return null;
        }
        // The join fetch carries the lecture, and with it the course the unit is scoped by. A missing id yields an
        // empty result rather than an exception, and the inner join means a returned unit always has its lecture.
        var lectureUnit = lectureUnitRepositoryApi.get().findAllByIdsWithLecture(List.of(lectureUnitId)).stream().findFirst().orElse(null);
        if (lectureUnit == null) {
            log.debug("Ignoring point-out command for unknown lecture unit {}", lectureUnitId);
            return null;
        }
        var course = lectureUnit.getLecture().getCourse();
        if (course == null || !Objects.equals(course.getId(), courseId)) {
            log.warn("Ignoring point-out command for lecture unit {}, which does not belong to course {}", lectureUnitId, courseId);
            return null;
        }
        return lectureUnit;
    }
}
