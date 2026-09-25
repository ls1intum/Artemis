package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscription;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;

/**
 * The websocket topics of the communication module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class CommunicationWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Posts, answers and reactions in the course-wide channels of a course that students can see.
     */
    public static final WebsocketTopic COURSE_WIDE_POSTS = WebsocketTopic.of("/topic/communication/courses/{courseId}", WebsocketTopicAccess.atLeastStudentInCourse("courseId"));

    /**
     * Posts, answers and reactions for one user: direct messages, group chats, non-course-wide channels, and posts that are only visible to some members, such as posts
     * with a pending Iris reply or posts in channels students cannot see yet.
     */
    public static final WebsocketTopic USER_CONVERSATION_POSTS = WebsocketTopic.of("/topic/user/{userId}/notifications/conversations", WebsocketTopicAccess.ownUser("userId"));

    /**
     * The discussion of a plagiarism case between the instructors and the student or team the case is about.
     */
    public static final WebsocketTopic PLAGIARISM_CASE_POSTS = WebsocketTopic.of("/topic/communication/plagiarismCase/{plagiarismCaseId}",
            WebsocketTopicAccess.custom(CommunicationWebsocketTopics.class, CommunicationWebsocketTopics::isPartyOfPlagiarismCase));

    /**
     * Changes to the conversations a user is a member of, computed per user.
     */
    public static final WebsocketUserTopic CONVERSATION_MEMBERSHIP = WebsocketUserTopic.of("/topic/communication/courses/{courseId}/conversations/user/{userId}");

    private final Optional<PlagiarismCaseApi> plagiarismCaseApi;

    public CommunicationWebsocketTopics(Optional<PlagiarismCaseApi> plagiarismCaseApi) {
        this.plagiarismCaseApi = plagiarismCaseApi;
    }

    private boolean isPartyOfPlagiarismCase(WebsocketSubscription subscription) {
        long plagiarismCaseId = subscription.id("plagiarismCaseId");
        return plagiarismCaseApi.map(api -> api.isStudentOrInstructorOfPlagiarismCase(plagiarismCaseId, subscription.login())).orElse(false)
                || subscription.hasAdministratorAccess();
    }
}
