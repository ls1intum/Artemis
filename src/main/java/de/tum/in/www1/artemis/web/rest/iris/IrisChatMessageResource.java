package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisChatMessageResource extends IrisMessageResource {

    private final IrisChatWebsocketService irisChatWebsocketService;

    public IrisChatMessageResource(IrisSessionRepository irisSessionRepository, IrisSessionService irisSessionService, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisRateLimitService rateLimitService, UserRepository userRepository, IrisChatWebsocketService irisChatWebsocketService) {
        super(irisSessionRepository, irisSessionService, irisMessageService, irisMessageRepository, rateLimitService, userRepository);
        this.irisChatWebsocketService = irisChatWebsocketService;
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
        return super.createMessage(sessionId, message);
    }

    @Override
    String sendMessageAndReturnUri(IrisSession session, IrisMessage savedMessage) {
        irisChatWebsocketService.sendMessage(savedMessage);
        return "/api/iris/sessions/" + session.getId() + "/messages/" + savedMessage.getId();
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
        return super.resendMessage(sessionId, messageId);
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
