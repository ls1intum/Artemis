package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisChatMessageResource extends IrisMessageResource {

    private final IrisChatSessionService irisChatSessionService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    public IrisChatMessageResource(IrisSessionRepository irisSessionRepository, IrisChatSessionService irisChatSessionService, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisRateLimitService rateLimitService, UserRepository userRepository, IrisChatWebsocketService irisChatWebsocketService) {
        super(irisSessionRepository, irisChatSessionService, irisMessageService, irisMessageRepository, rateLimitService, userRepository);
        this.irisChatSessionService = irisChatSessionService;
        this.irisChatWebsocketService = irisChatWebsocketService;
    }

    /**
     * GET session/{sessionId}/message: Retrieve the messages for the iris session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the list of messages, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @GetMapping("sessions/{sessionId}/messages")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IrisMessage>> getMessages(@PathVariable Long sessionId) {
        return super.getMessages(sessionId);
    }

    /**
     * POST sessions/{sessionId}/messages: Send a new message from the user to the LLM
     *
     * @param sessionId of the session
     * @param message   to send
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("sessions/{sessionId}/messages")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisMessage> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessage message) throws URISyntaxException {
        var fromDB = irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);
        if (!(fromDB instanceof IrisChatSession session)) {
            throw new BadRequestException("Session is not a chat session");
        }
        var savedMessage = super.postMessage(session, message);

        irisChatWebsocketService.sendMessage(savedMessage);
        String uriString = "/api/iris/sessions/" + sessionId + "/messages/" + savedMessage.getId();
        return ResponseEntity.created(new URI(uriString)).body(savedMessage);
    }

    /**
     * POST sessions/{sessionId}/messages/{messageId}/resend: Resend a message if there was previously an error when sending it to the LLM
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the existing message, or with status {@code 404 (Not Found)} if the session or message could
     *         not be found.
     */
    @PostMapping("sessions/{sessionId}/messages/{messageId}/resend")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisMessage> resendMessage(@PathVariable Long sessionId, @PathVariable Long messageId) {
        var fromDB = irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);
        if (!(fromDB instanceof IrisChatSession session)) {
            throw new BadRequestException("Session is not a chat session");
        }
        var message = super.getMessageToResend(session, messageId);
        irisChatSessionService.requestAndHandleResponse(session);
        return ResponseEntity.ok(message);
    }

    /**
     * PUT sessions/{sessionId}/messages/{messageId}/helpful/{helpful}: Set the helpful attribute of the message
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param helpful   true if the message was helpful, false otherwise, null as default
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated message, or with status {@code 404 (Not Found)} if the session or message could not
     *         be found.
     */
    @PutMapping(value = { "sessions/{sessionId}/messages/{messageId}/helpful/null", "sessions/{sessionId}/messages/{messageId}/helpful/undefined",
            "sessions/{sessionId}/messages/{messageId}/helpful/{helpful}" })
    @EnforceAtLeastStudent
    public ResponseEntity<IrisMessage> rateMessage(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable(required = false) Boolean helpful) {
        return super.rateMessage(sessionId, messageId, helpful);
    }

}
