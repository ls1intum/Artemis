package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.service.iris.*;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public abstract class IrisMessageResource {

    protected IrisSessionRepository irisSessionRepository;

    public IrisSessionService getIrisSessionService() {
        return irisSessionService;
    }

    public IrisMessageService getIrisMessageService() {
        return irisMessageService;
    }

    protected IrisSessionService irisSessionService;

    protected IrisMessageService irisMessageService;

    public IrisMessageRepository getIrisMessageRepository() {
        return irisMessageRepository;
    }

    protected IrisMessageRepository irisMessageRepository;

    final IrisRateLimitService rateLimitService;

    final UserRepository userRepository;

    public IrisMessageResource(IrisSessionRepository irisSessionRepository, IrisSessionService irisSessionService, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisRateLimitService rateLimitService, UserRepository userRepository) {
        this.irisSessionRepository = irisSessionRepository;
        this.irisSessionService = irisSessionService;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
        this.rateLimitService = rateLimitService;
        this.userRepository = userRepository;
    }

    /**
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the list of messages, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    public ResponseEntity<List<IrisMessage>> getMessages(@PathVariable Long sessionId) {
        IrisSession session = irisSessionRepository.findByIdElseThrow(sessionId);
        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        var messages = irisMessageRepository.findAllExceptSystemMessagesWithContentBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * @param sessionId of the session
     * @param message   to send
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    public ResponseEntity<IrisMessage> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessage message) throws URISyntaxException {
        var session = irisSessionRepository.findByIdElseThrow(sessionId);
        irisSessionService.checkIsIrisActivated(session);
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        rateLimitService.checkRateLimitElseThrow(user);

        var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.USER);
        irisSessionService.requestMessageFromIris(session);
        savedMessage.setMessageDifferentiator(message.getMessageDifferentiator());
        String uriString = setupWebsocketAndUri(session, savedMessage);
        return ResponseEntity.created(new URI(uriString)).body(savedMessage);
    }

    /**
     * @param sessionId of the session
     * @param messageId of the message
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the existing message, or with status {@code 404 (Not Found)} if the session or message could
     *         not be found.
     */
    public ResponseEntity<IrisMessage> resendMessage(@PathVariable Long sessionId, @PathVariable Long messageId) {
        var session = irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);
        irisSessionService.checkIsIrisActivated(session);
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        rateLimitService.checkRateLimitElseThrow(user);

        var message = irisMessageRepository.findByIdElseThrow(messageId);
        if (session.getMessages().lastIndexOf(message) != session.getMessages().size() - 1) {
            throw new BadRequestException("Only the last message can be resent");
        }
        if (message.getSender() != IrisMessageSender.USER) {
            throw new BadRequestException("Only user messages can be resent");
        }
        irisSessionService.requestMessageFromIris(session);
        message.setMessageDifferentiator(message.getMessageDifferentiator());

        return ResponseEntity.ok(message);
    }

    abstract String setupWebsocketAndUri(IrisSession session, IrisMessage savedMessage);

}
