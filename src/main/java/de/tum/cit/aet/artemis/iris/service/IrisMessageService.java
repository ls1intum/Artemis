package de.tum.cit.aet.artemis.iris.service;

import jakarta.ws.rs.BadRequestException;

import org.hibernate.Hibernate;
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

        // The write itself is a repository operation: it locks the session row, reloads the ordered message list under
        // that lock, appends and cascades in ONE transaction, because a stale list merged back over the committed rows
        // loses a concurrent append. See IrisSessionWriteRepository#appendMessage for why each of those steps is there.
        var savedSession = irisSessionRepository.appendMessage(session.getId(), message, sender);
        if (Hibernate.isInitialized(session.getMessages())) {
            // Keep the caller's own instance consistent, as before; an uninitialized one is left alone so it
            // still loads the committed state lazily.
            session.setMessages(savedSession.getMessages());
        }

        return savedSession.getMessages().getLast();
    }
}
