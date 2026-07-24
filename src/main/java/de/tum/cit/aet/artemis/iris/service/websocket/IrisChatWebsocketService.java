package de.tum.cit.aet.artemis.iris.service.websocket;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisChatWebsocketService {

    private final IrisWebsocketService websocketService;

    private final IrisRateLimitService rateLimitService;

    private final UserRepository userRepository;

    public IrisChatWebsocketService(IrisWebsocketService websocketService, IrisRateLimitService rateLimitService, UserRepository userRepository) {
        this.websocketService = websocketService;
        this.rateLimitService = rateLimitService;
        this.userRepository = userRepository;
    }

    /**
     * Sends a message and/or a status update over the websocket to the user
     * involved in the session. At least one of the message or the run state must be
     * non-null, otherwise there is no need to send a message.
     * This is currently used for both the exercise and course chat sessions, but
     * this could be split up in the future.
     *
     * @param session     the session to send the message to
     * @param irisMessage that should be sent over the websocket
     * @param runState    that should be sent over the websocket
     * @param error       optional Pyris status error
     */
    public void sendMessage(IrisSession session, IrisMessage irisMessage, PyrisRunState runState, PyrisStatusErrorDTO error) {
        this.sendMessage(session, irisMessage, runState, error, null, null);
    }

    /**
     * Sends a message and/or a status update over the websocket to the user
     * involved in the session. At least one of the message or the run state must be
     * non-null, otherwise there is no need to send a message.
     * This is currently used for both the exercise and course chat sessions, but
     * this could be split up in the future.
     *
     * @param session      the session to send the message to
     * @param irisMessage  that should be sent over the websocket
     * @param runState     that should be sent over the websocket
     * @param error        optional Pyris status error
     * @param sessionTitle the session title to send
     * @param citationInfo the citation metadata to send
     */
    public void sendMessage(IrisSession session, IrisMessage irisMessage, PyrisRunState runState, PyrisStatusErrorDTO error, String sessionTitle,
            List<IrisCitationMetaDTO> citationInfo) {
        this.sendMessage(session, irisMessage, runState, error, sessionTitle, citationInfo, null, null, null, null);
    }

    /**
     * Sends a message and/or a status update over the websocket to the user
     * involved in the session. At least one of the message or the run state must be
     * non-null, otherwise there is no need to send a message.
     * This is currently used for both the exercise and course chat sessions, but
     * this could be split up in the future.
     *
     * @param session      the session to send the message to
     * @param irisMessage  that should be sent over the websocket
     * @param runState     that should be sent over the websocket
     * @param error        optional Pyris status error
     * @param sessionTitle the session title to send
     * @param citationInfo the citation metadata to send
     * @param runId        the id of the Pyris run that produced the message
     * @param activities   current Pyris activity snapshot
     * @param activitySeq  monotonic sequence number for the activity snapshot
     * @param finalResult  whether the message is the final answer for the run; false means intermediate
     */
    public void sendMessage(IrisSession session, IrisMessage irisMessage, PyrisRunState runState, PyrisStatusErrorDTO error, String sessionTitle,
            List<IrisCitationMetaDTO> citationInfo, String runId, List<PyrisActivityDTO> activities, Integer activitySeq, Boolean finalResult) {
        var messageDTO = irisMessage != null ? IrisMessageResponseDTO.of(irisMessage) : null;
        var user = userRepository.findByIdElseThrow(session.getUserId());
        var rateLimitInfo = rateLimitService.getRateLimitInformation(session, user);
        var topic = "" + session.getId(); // Todo: add more specific topic
        var payload = new IrisChatWebsocketDTO(messageDTO, rateLimitInfo, runState, error, sessionTitle, null, null, citationInfo, runId, null, null, activities, activitySeq,
                finalResult);
        websocketService.send(user.getLogin(), topic, payload);
    }

    /**
     * Pushes a struggle event (lamp / open-notice) to the student on the per-user struggle topic (spec §5.5): the
     * server publishes to {@code /topic/iris/struggle-intervention} via {@code sendMessageToUser}, which the student
     * receives on {@code /user/topic/iris/struggle-intervention}. After unify-persistence (spec §7) the event
     * references a persisted proactive message via its {@code sessionId}/{@code messageId}.
     *
     * @param user  the student to notify
     * @param event the struggle event payload
     */
    public void sendStruggleEvent(User user, StruggleInterventionEventDTO event) {
        websocketService.send(user.getLogin(), "struggle-intervention", event);
    }

    /**
     * Sends a status update over the websocket to a specific user
     *
     * @param session  the session to send the status update to
     * @param runId    the id of the Pyris run that produced the status update
     * @param runState the current Pyris run state
     * @param error    optional Pyris status error
     */
    public void sendStatusUpdate(IrisSession session, String runId, PyrisRunState runState, PyrisStatusErrorDTO error) {
        this.sendStatusUpdate(session, runId, runState, error, null, null, null, null, null);
    }

    /**
     * Sends a status update over the websocket to a specific user
     *
     * @param session      the session to send the status update to
     * @param runId        the id of the Pyris run that produced the status update
     * @param runState     the current Pyris run state
     * @param error        optional Pyris status error
     * @param sessionTitle the session title to send
     * @param suggestions  the suggestions to send
     * @param tokens       token usage and cost send by Pyris
     * @param activities   current Pyris activity snapshot
     * @param activitySeq  monotonic sequence number for the activity snapshot
     */
    public void sendStatusUpdate(IrisSession session, String runId, PyrisRunState runState, PyrisStatusErrorDTO error, String sessionTitle, List<String> suggestions,
            List<LLMRequest> tokens, List<PyrisActivityDTO> activities, Integer activitySeq) {
        var user = userRepository.findByIdElseThrow(session.getUserId());
        var rateLimitInfo = rateLimitService.getRateLimitInformation(session, user);
        var topic = "" + session.getId(); // Todo: add more specific topic
        var payload = new IrisChatWebsocketDTO(null, rateLimitInfo, runState, error, sessionTitle, suggestions, tokens, null, runId, null, null, activities, activitySeq);
        websocketService.send(user.getLogin(), topic, payload);
    }

    /**
     * Sends a partial response update over the websocket to a specific user.
     * <p>
     * Partial updates arrive many times per streamed answer and never change the user's quota, so we deliberately omit the rate limit information here: computing it can run
     * the (potentially expensive) LLM-response-count query, and this streaming path must stay off that hot path. The rate limit information is refreshed by the final
     * MESSAGE / status update instead.
     *
     * @param session       the session to send the partial update to
     * @param partialResult the current accumulated partial response text
     * @param partialSeq    the monotonic sequence number of the partial response
     * @param runId         the id of the Pyris run that produced the partial response
     */
    public void sendPartialUpdate(IrisSession session, String partialResult, Integer partialSeq, String runId) {
        var user = userRepository.findByIdElseThrow(session.getUserId());
        var topic = "" + session.getId(); // Todo: add more specific topic
        var payload = new IrisChatWebsocketDTO(null, null, PyrisRunState.RUNNING, null, null, null, null, null, runId, partialResult, partialSeq, null, null);
        websocketService.send(user.getLogin(), topic, payload);
    }
}
