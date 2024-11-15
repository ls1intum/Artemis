package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.SourceType;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ForwardedMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public class ForwardedMessageService {

    private static final String ENTITY_NAME = "forwardedMessage";

    private final ForwardedMessageRepository forwardedMessageRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    public ForwardedMessageService(ForwardedMessageRepository forwardedMessageRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository) {

        this.forwardedMessageRepository = forwardedMessageRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
    }

    /**
     * Creates a new ForwardedMessage.
     *
     * @param courseId         the id of the course
     * @param forwardedMessage the ForwardedMessage to create
     * @return the persisted ForwardedMessage entity
     */
    @Transactional
    public ForwardedMessage createForwardedMessage(Long courseId, ForwardedMessage forwardedMessage) {

        if (forwardedMessage.getId() != null) {
            throw new BadRequestAlertException("A new forwarded message cannot already have an ID", ENTITY_NAME, "idexists");
        }

        // Validate sourceType and sourceId
        if (forwardedMessage.getSourceType() == null || forwardedMessage.getSourceId() == null) {
            throw new BadRequestAlertException("SourceType and SourceId must not be null", ENTITY_NAME, "nullsource");
        }

        // Fetch the source posting (Post or AnswerPost)
        Posting sourcePosting;
        if (forwardedMessage.getSourceType() == SourceType.POST) {
            sourcePosting = postRepository.findByIdElseThrow(forwardedMessage.getSourceId());
        }
        else if (forwardedMessage.getSourceType() == SourceType.ANSWER_POST) {
            sourcePosting = answerPostRepository.findByIdElseThrow(forwardedMessage.getSourceId());
        }
        else {
            throw new BadRequestAlertException("Invalid SourceType", ENTITY_NAME, "invalidsourcetype");
        }

        // Set the destination post or answer post
        if (forwardedMessage.getDestinationPost() != null) {
            Post destinationPost = postRepository.findByIdElseThrow(forwardedMessage.getDestinationPost().getId());
            forwardedMessage.setDestinationPost(destinationPost);
        }
        else if (forwardedMessage.getDestinationAnswerPost() != null) {
            AnswerPost destinationAnswerPost = answerPostRepository.findByIdElseThrow(forwardedMessage.getDestinationAnswerPost().getId());
            forwardedMessage.setDestinationAnswerPost(destinationAnswerPost);
        }
        else {
            throw new BadRequestAlertException("DestinationPostId or DestinationAnswerId must be provided", ENTITY_NAME, "nolocation");
        }

        // Save the ForwardedMessage
        ForwardedMessage savedForwardedMessage = forwardedMessageRepository.save(forwardedMessage);

        // Additional logic, such as broadcasting or notifications, can be added here

        return savedForwardedMessage;
    }

    /**
     * Deletes a ForwardedMessage by id.
     *
     * @param forwardedMessageId the id of the ForwardedMessage to delete
     */
    @Transactional
    public void deleteForwardedMessage(Long forwardedMessageId) {

        ForwardedMessage forwardedMessage = forwardedMessageRepository.findByIdElseThrow(forwardedMessageId);

        // Check if the user has permission to delete the forwarded message
        // Assuming only the creator can delete it
        // Adjust this logic based on your requirements
        // if (!user.equals(forwardedMessage.getUser())) {
        // throw new AccessForbiddenException("ForwardedMessage", forwardedMessageId);
        // }

        // Delete the ForwardedMessage
        forwardedMessageRepository.deleteById(forwardedMessageId);
    }
}
