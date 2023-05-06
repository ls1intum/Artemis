package de.tum.in.www1.artemis.service.iris;

import java.util.ArrayList;

import javax.ws.rs.BadRequestException;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisMessageContentRepository;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;

/**
 * Service for managing Iris messages.
 */
@Service
public class IrisMessageService {

    private final IrisMessageRepository irisMessageRepository;

    private final IrisMessageContentRepository irisMessageContentRepository;

    private final IrisSessionRepository irisSessionRepository;

    public IrisMessageService(IrisMessageRepository irisMessageRepository, IrisMessageContentRepository irisMessageContentRepository, IrisSessionRepository irisSessionRepository) {
        this.irisMessageRepository = irisMessageRepository;
        this.irisMessageContentRepository = irisMessageContentRepository;
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * Saves a new message to the database. The message must have a session and a sender.
     * This method ensures that the message is saved in the session and the contents are saved.
     *
     * @param message The message to save
     * @param session The session the message belongs to
     * @param sender  The sender of the message
     * @return The saved message
     */
    public IrisMessage saveNewMessage(IrisMessage message, IrisSession session, IrisMessageSender sender) {
        if (message.getContent().isEmpty()) {
            throw new BadRequestException("Message must have at least one content element");
        }

        message.setSession(null);
        message.setSender(sender);
        var contents = message.getContent();
        message.setContent(new ArrayList<>());
        var savedMessage = irisMessageRepository.saveAndFlush(message);
        var sessionWithMessages = irisSessionRepository.findByIdWithMessages(session.getId());
        message.setSession(sessionWithMessages);
        sessionWithMessages.getMessages().add(savedMessage);
        irisSessionRepository.save(sessionWithMessages);

        // Save contents of message
        for (IrisMessageContent content : contents) {
            content.setMessage(null);
        }
        contents = irisMessageContentRepository.saveAllAndFlush(contents);
        for (IrisMessageContent content : contents) {
            content.setMessage(message);
        }
        message.getContent().addAll(contents);
        savedMessage = irisMessageRepository.save(message);
        // TODO: Send to LLM

        return savedMessage;
    }
}
