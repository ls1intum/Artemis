package de.tum.cit.aet.artemis.plagiarism.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismAnswerPostService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismPostService;

@Profile(PROFILE_CORE)
@Controller
public class PlagiarismPostApi extends AbstractPlagiarismApi {

    private final PlagiarismPostService plagiarismPostService;

    private final PlagiarismAnswerPostService plagiarismAnswerPostService;

    public PlagiarismPostApi(PlagiarismPostService plagiarismPostService, PlagiarismAnswerPostService plagiarismAnswerPostService) {
        this.plagiarismPostService = plagiarismPostService;
        this.plagiarismAnswerPostService = plagiarismAnswerPostService;
    }

    public AnswerPost findAnswerPostOrAnswerMessageById(Long messageId) {
        return plagiarismAnswerPostService.findAnswerPostOrAnswerMessageById(messageId);
    }

    public Post findPostOrMessagePostById(Long postOrMessageId) {
        return plagiarismPostService.findPostOrMessagePostById(postOrMessageId);
    }

    public void preparePostForBroadcast(Post post) {
        plagiarismAnswerPostService.preparePostForBroadcast(post);
    }

    public void preparePostAndBroadcast(AnswerPost updatedAnswerPost, Course course) {
        plagiarismAnswerPostService.preparePostAndBroadcast(updatedAnswerPost, course);
    }

    public void broadcastForPost(PostDTO postDTO, Long courseId, Set<ConversationNotificationRecipientSummary> recipients, Set<User> mentionedUsers) {
        plagiarismPostService.broadcastForPost(postDTO, courseId, recipients, mentionedUsers);
    }

    public void preCheckUserAndCourseForCommunicationOrMessaging(User user, Course course) {
        plagiarismPostService.preCheckUserAndCourseForCommunicationOrMessaging(user, course);
    }
}
