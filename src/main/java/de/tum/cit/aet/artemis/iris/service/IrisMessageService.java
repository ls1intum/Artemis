package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;

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

        // Always reload immediately before the cascade, rather than trusting an already-initialized collection.
        // saveAndFlush below merges the whole session aggregate, so a stale messages list is written back over the
        // committed rows: a column another transaction has set in the meantime (proactiveOutcome) is reset to its
        // stale value, and because the collection declares orphanRemoval, a row missing from the stale list is
        // deleted outright. Saving the message on its own instead is not an option: @OrderColumn on
        // IrisSession#messages is maintained from the owner side, so a standalone insert leaves iris_message_order
        // null and the next read fails with "Illegal null value for list index".
        var callerSession = session;
        session = irisSessionRepository.findByIdWithMessagesElseThrow(session.getId());

        message.setSender(sender);
        message.setSentAt(ZonedDateTime.now());
        message.setSession(session);
        message.getContent().forEach(content -> content.setMessage(message));

        session.getMessages().add(message);
        // saveAndFlush so the cascaded message has its generated id.
        var savedSession = irisSessionRepository.saveAndFlush(session);
        if (Hibernate.isInitialized(callerSession.getMessages())) {
            // Keep the caller's own instance consistent, as before; an uninitialized one is left alone so it
            // still loads the committed state lazily.
            callerSession.setMessages(savedSession.getMessages());
        }

        return savedSession.getMessages().getLast();
    }
}
