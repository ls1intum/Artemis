package de.tum.cit.aet.artemis.communication.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;

/**
 * Lets the file handling decide whether a user may use the files of a conversation.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class ConversationFileAccessApi implements AbstractApi {

    private final ConversationRepository conversationRepository;

    public ConversationFileAccessApi(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    /**
     * Checks that the conversation belongs to the course and that the user takes part in it or that it is a course-wide channel.
     *
     * @param courseId       the id of the course in the request path
     * @param conversationId the id of the conversation in the request path
     * @param userId         the id of the requesting user
     * @throws AccessForbiddenException if the user is not allowed to use the files of the conversation
     */
    public void checkCanAccessConversationFilesElseThrow(long courseId, long conversationId, long userId) {
        if (!conversationRepository.isConversationOfCourseAccessibleByUser(conversationId, courseId, userId)) {
            throw new AccessForbiddenException("You are not allowed to access the files of this conversation.");
        }
    }
}
