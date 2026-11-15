package de.tum.cit.aet.artemis.communication.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.communication.service.ForwardedMessageService;
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

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ForwardedMessageService forwardedMessageService;

    public ConversationFileAccessApi(ConversationRepository conversationRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            ForwardedMessageService forwardedMessageService) {
        this.conversationRepository = conversationRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.forwardedMessageService = forwardedMessageService;
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

    /**
     * Checks that the user may read a file of a conversation. That is the case for a user who may use the files of the conversation, and for a user who can read a message that
     * forwards a message of the conversation that shows the file, as the forward shows the file to its readers.
     *
     * @param courseId       the id of the course in the request path
     * @param conversationId the id of the conversation in the request path
     * @param filename       the name of the file in the request path
     * @param user           the requesting user
     * @throws AccessForbiddenException if the user is not allowed to read the file
     */
    public void checkCanReadConversationFileElseThrow(long courseId, long conversationId, String filename, User user) {
        if (conversationRepository.isConversationOfCourseAccessibleByUser(conversationId, courseId, user.getId())) {
            return;
        }
        String reference = "courses/" + courseId + "/conversations/" + conversationId + "/" + filename;
        Set<Long> postIds = postRepository.findIdsOfPostsReferencing(conversationId, courseId, reference);
        if (!postIds.isEmpty() && !forwardedMessageService.findAccessibleSourceIds(postIds, PostingType.POST, courseId, user).isEmpty()) {
            return;
        }
        Set<Long> answerIds = answerPostRepository.findIdsOfAnswersReferencing(conversationId, courseId, reference);
        if (!answerIds.isEmpty() && !forwardedMessageService.findAccessibleSourceIds(answerIds, PostingType.ANSWER, courseId, user).isEmpty()) {
            return;
        }
        throw new AccessForbiddenException("You are not allowed to access the files of this conversation.");
    }
}
