package de.tum.in.www1.artemis.web.rest.metis.conversation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationDTOService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.OneToOneChatService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.OneToOneChatAuthorizationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.OneToOneChatDTO;

@RestController
@RequestMapping("/api/courses")
public class OneToOneChatResource extends ConversationManagementResource {

    private final Logger log = LoggerFactory.getLogger(OneToOneChatResource.class);

    private final OneToOneChatAuthorizationService oneToOneChatAuthorizationService;

    private final ConversationDTOService conversationDTOService;

    private final UserRepository userRepository;

    private final OneToOneChatService oneToOneChatService;

    private final ConversationService conversationService;

    public OneToOneChatResource(OneToOneChatAuthorizationService oneToOneChatAuthorizationService, ConversationDTOService conversationDTOService, UserRepository userRepository,
            CourseRepository courseRepository, OneToOneChatService oneToOneChatService, ConversationService conversationService) {
        super(courseRepository);
        this.oneToOneChatAuthorizationService = oneToOneChatAuthorizationService;
        this.conversationDTOService = conversationDTOService;
        this.userRepository = userRepository;
        this.oneToOneChatService = oneToOneChatService;
        this.conversationService = conversationService;
    }

    /**
     * POST /api/courses/:courseId/one-to-one-chats/: Starts a new one to one chat in a course
     *
     * @param courseId                   the id of the course
     * @param otherChatParticipantLogins logins of other participants (must be 1 for one to one chat) excluding the requesting user
     * @return ResponseEntity with status 201 (Created) and with body containing the created one to one chat
     */
    @PostMapping("/{courseId}/one-to-one-chats")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OneToOneChatDTO> startOneToOneChat(@PathVariable Long courseId, @RequestBody List<String> otherChatParticipantLogins) throws URISyntaxException {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to create one to one chat in course {} between : {} and : {}", courseId, requestingUser.getLogin(), otherChatParticipantLogins);
        var course = courseRepository.findByIdElseThrow(courseId);
        checkMessagingEnabledElseThrow(course);
        oneToOneChatAuthorizationService.isAllowedToCreateOneToOneChat(course, requestingUser);

        var loginsToSearchFor = new HashSet<>(otherChatParticipantLogins);
        loginsToSearchFor.add(requestingUser.getLogin());
        var chatMembers = new ArrayList<>(conversationService.findUsersInDatabase(loginsToSearchFor.stream().toList()));

        if (chatMembers.size() != 2) {
            throw new BadRequestAlertException("A one-to-one chat can only be started with two users", "OneToOneChat", "invalidUserCount");
        }

        var userA = chatMembers.get(0);
        var userB = chatMembers.get(1);

        var oneToOneChat = oneToOneChatService.startOneToOneChat(course, userA, userB);
        return ResponseEntity.created(new URI("/api/one-to-one-chats/" + oneToOneChat.getId())).body(conversationDTOService.convertOneToOneChatToDto(requestingUser, oneToOneChat));
    }
}
