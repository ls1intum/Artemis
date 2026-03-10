package de.tum.cit.aet.artemis.iris.web;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.BotAnswerPostDTO;
import de.tum.cit.aet.artemis.iris.service.BotMessagingService;

/**
 * REST controller for bot messaging operations.
 * Allows authenticated bot accounts to post answers to existing posts.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/bot/")
public class BotMessagingResource {

    private final BotMessagingService botMessagingService;

    private final UserRepository userRepository;

    public BotMessagingResource(BotMessagingService botMessagingService, UserRepository userRepository) {
        this.botMessagingService = botMessagingService;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/iris/bot/posts/{postId}/answers : Post an answer to a thread as the bot.
     *
     * @param postId the ID of the post to reply to
     * @param dto    the answer content
     * @return 201 Created on success
     * @throws URISyntaxException if the URI cannot be constructed
     */
    @PostMapping("posts/{postId}/answers")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> postBotAnswer(@PathVariable long postId, @RequestBody BotAnswerPostDTO dto) throws URISyntaxException {
        var currentUser = userRepository.getUser();
        if (!currentUser.isBot()) {
            throw new org.springframework.security.access.AccessDeniedException("Only bot accounts can use this endpoint");
        }

        botMessagingService.postAnswerAsBot(currentUser, postId, dto.content());
        return ResponseEntity.created(new URI("/api/iris/bot/posts/" + postId + "/answers")).build();
    }
}
