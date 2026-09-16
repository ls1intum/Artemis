package de.tum.cit.aet.artemis.iris.service;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;

/**
 * Service for managing Iris messages.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisMessageService {

    private final IrisSessionRepository irisSessionRepository;

    public IrisMessageService(IrisSessionRepository irisSessionRepository) {
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * Saves a new message to the database. The method sets session and a sender to the message.
     * This method ensures that the message and the contents are saved to the session.
     *
     * @param message The message to save
     * @param session The session the message belongs to
     * @param sender  The sender of the message
     * @return The saved message
     */
    public IrisMessage saveMessage(IrisMessage message, IrisSession session, IrisMessageSender sender) {
        if (message.getContent().isEmpty()) {
            throw new BadRequestException("Message must have at least one content element");
        }

        // The caller's own session instance is deliberately left alone: adding the message to a list it loaded
        // earlier would give it a list index the committed rows already use.
        return irisSessionRepository.appendMessage(session.getId(), message, sender);
    }
}
