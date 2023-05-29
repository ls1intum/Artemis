package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@RequestMapping("api/iris/")
public class IrisMessageResource {

    private final Logger log = LoggerFactory.getLogger(IrisMessageResource.class);

    private final IrisSessionRepository irisSessionRepository;

    private final IrisSessionService irisSessionService;

    private final IrisMessageService irisMessageService;

    private final IrisMessageRepository irisMessageRepository;

    public IrisMessageResource(IrisSessionRepository irisSessionRepository, IrisSessionService irisSessionService, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository) {
        this.irisSessionRepository = irisSessionRepository;
        this.irisSessionService = irisSessionService;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
    }

    /**
     * GET session/{sessionId}/message: Retrieve the messages for the iris session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the list of messages, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @GetMapping("sessions/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<IrisMessage>> getMessages(@PathVariable Long sessionId) {
        IrisSession irisSession = irisSessionRepository.findByIdElseThrow(sessionId);
        irisSessionService.checkHasAccessToIrisSession(irisSession, null);
        var messages = irisMessageRepository.findAllExceptSystemMessagesWithContentBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * POST sessions/{sessionId}/messages: Send a new message from the user to the LLM
     *
     * @param sessionId of the session
     * @param message   to send
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("sessions/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisMessage> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessage message) throws URISyntaxException {
        var session = irisSessionRepository.findByIdElseThrow(sessionId);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.USER);
        irisSessionService.requestMessageFromIris(session);

        var uriString = "/api/iris/sessions/" + session.getId() + "/messages/" + savedMessage.getId();
        return ResponseEntity.created(new URI(uriString)).body(savedMessage);
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
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisMessage> rateMessage(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable(required = false) Boolean helpful) {
        var message = irisMessageRepository.findByIdElseThrow(messageId);
        if (!Objects.equals(message.getSession().getId(), sessionId)) {
            throw new ConflictException("The message does not belong to the session", "IrisMessage", "irisMessageSessionConflict");
        }
        irisSessionService.checkHasAccessToIrisSession(message.getSession(), null);
        if (message.getSender() != IrisMessageSender.LLM) {
            throw new BadRequestException("You can only rate messages send by Iris");
        }
        message.setHelpful(helpful);
        var savedMessage = irisMessageRepository.save(message);
        return ResponseEntity.ok(savedMessage);
    }
}
