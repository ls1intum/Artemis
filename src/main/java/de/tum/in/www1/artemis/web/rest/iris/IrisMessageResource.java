package de.tum.in.www1.artemis.web.rest.iris;

import java.util.List;
import java.util.Objects;

import javax.ws.rs.BadRequestException;

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
import de.tum.in.www1.artemis.service.iris.session.IrisSessionSubServiceInterface;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * Superclass of REST controllers for managing {@link IrisMessage}.
 * The methods in this class are intended to be called by inheritors.
 * This is done to allow for different endpoints and authentication requirements.
 */
public abstract class IrisMessageResource {

    protected final IrisSessionRepository irisSessionRepository;

    protected final IrisSessionSubServiceInterface irisSessionService;

    protected final IrisMessageService irisMessageService;

    protected final IrisMessageRepository irisMessageRepository;

    protected final IrisRateLimitService rateLimitService;

    protected final UserRepository userRepository;

    public IrisMessageResource(IrisSessionRepository irisSessionRepository, IrisSessionSubServiceInterface irisSessionService, IrisMessageService irisMessageService,
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
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the list of messages, or with
     *         status {@code 404 (Not Found)} if the session could not be found.
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
     * @param messageId of the message
     * @param helpful   true if the message was helpful, false otherwise, null as default
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated message, or with status {@code 404 (Not Found)} if the session or message could not
     *         be found.
     */
    public ResponseEntity<IrisMessage> rateMessage(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable(required = false) Boolean helpful) {
        var message = irisMessageRepository.findByIdElseThrow(messageId);
        var session = message.getSession();
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The message does not belong to the session", "IrisMessage", "irisMessageSessionConflict");
        }
        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        if (message.getSender() != IrisMessageSender.LLM) {
            throw new BadRequestException("You can only rate messages sent by Iris");
        }
        message.setHelpful(helpful);
        var savedMessage = irisMessageRepository.save(message);
        return ResponseEntity.ok(savedMessage);
    }

}
