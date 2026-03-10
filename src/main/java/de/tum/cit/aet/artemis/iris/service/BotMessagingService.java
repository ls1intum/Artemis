package de.tum.cit.aet.artemis.iris.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Service for posting messages as a bot user in course communication channels.
 */
@Service
@Conditional(IrisEnabled.class)
@Lazy
public class BotMessagingService {

    private static final Logger log = LoggerFactory.getLogger(BotMessagingService.class);

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final WebsocketMessagingService websocketMessagingService;

    public BotMessagingService(PostRepository postRepository, AnswerPostRepository answerPostRepository, WebsocketMessagingService websocketMessagingService) {
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Posts an answer to a thread as a bot user and broadcasts it via WebSocket.
     *
     * @param botUser the bot user posting the answer
     * @param postId  the ID of the post to reply to
     * @param content the content of the answer
     */
    public void postAnswerAsBot(User botUser, long postId, String content) {
        var post = postRepository.findByIdElseThrow(postId);
        var course = post.getCoursePostingBelongsTo();

        var answerPost = new AnswerPost();
        answerPost.setContent(content);
        answerPost.setPost(post);
        answerPost.setAuthor(botUser);
        answerPost.setResolvesPost(false);
        answerPost.setAuthorRole(UserRole.BOT);

        var savedAnswer = answerPostRepository.save(answerPost);
        post.addAnswerPost(savedAnswer);
        postRepository.save(post);

        broadcastPostUpdate(post, course);
        log.info("Posted bot answer to post {} in course {}", postId, course.getId());
    }

    private void broadcastPostUpdate(de.tum.cit.aet.artemis.communication.domain.Post post, de.tum.cit.aet.artemis.core.domain.Course course) {
        var conversationId = post.getConversation().getId();
        String topic = "/topic/metis/courses/" + course.getId() + "/conversations/" + conversationId;
        websocketMessagingService.sendMessage(topic, List.of(new PostDTO(post, MetisCrudAction.UPDATE)));
    }
}
