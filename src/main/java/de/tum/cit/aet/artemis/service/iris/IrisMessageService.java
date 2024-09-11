package de.tum.cit.aet.artemis.service.iris;

import java.time.ZonedDateTime;

import jakarta.ws.rs.BadRequestException;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.iris.message.IrisMessage;
import de.tum.cit.aet.artemis.domain.iris.message.IrisMessageSender;
import de.tum.cit.aet.artemis.domain.iris.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;

/**
 * Service for managing Iris messages.
 */
@Service
@Profile("iris")
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

        if (!Hibernate.isInitialized(session.getMessages())) {
            session = irisSessionRepository.findByIdWithMessagesElseThrow(session.getId());
        }

        message.setSender(sender);
        message.setSentAt(ZonedDateTime.now());
        message.setSession(session);
        message.getContent().forEach(content -> content.setMessage(message));

        session.getMessages().add(message);
        irisSessionRepository.save(session);

        var sessionWithMessages = irisSessionRepository.findByIdWithMessagesElseThrow(session.getId());
        session.setMessages(sessionWithMessages.getMessages()); // Make sure we keep the session up to date as we overrode it. We do this to avoid unnecessarily fetching the
                                                                // session again.

        return sessionWithMessages.getMessages().getLast();
    }
}
