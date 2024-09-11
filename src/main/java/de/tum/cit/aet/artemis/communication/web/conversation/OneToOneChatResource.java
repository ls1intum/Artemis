package de.tum.cit.aet.artemis.communication.web.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationDTOService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.service.conversation.OneToOneChatService;
import de.tum.cit.aet.artemis.communication.service.conversation.auth.OneToOneChatAuthorizationService;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.communication.web.conversation.dtos.OneToOneChatDTO;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/courses/")
public class OneToOneChatResource extends ConversationManagementResource {

    private static final Logger log = LoggerFactory.getLogger(OneToOneChatResource.class);

    private final OneToOneChatAuthorizationService oneToOneChatAuthorizationService;

    private final ConversationDTOService conversationDTOService;

    private final UserRepository userRepository;

    private final OneToOneChatService oneToOneChatService;

    private final ConversationService conversationService;

    private final SingleUserNotificationService singleUserNotificationService;

    public OneToOneChatResource(SingleUserNotificationService singleUserNotificationService, OneToOneChatAuthorizationService oneToOneChatAuthorizationService,
            ConversationDTOService conversationDTOService, UserRepository userRepository, CourseRepository courseRepository, OneToOneChatService oneToOneChatService,
            ConversationService conversationService) {
        super(courseRepository);
        this.oneToOneChatAuthorizationService = oneToOneChatAuthorizationService;
        this.conversationDTOService = conversationDTOService;
        this.userRepository = userRepository;
        this.oneToOneChatService = oneToOneChatService;
        this.conversationService = conversationService;
        this.singleUserNotificationService = singleUserNotificationService;
    }

    /**
     * POST /api/courses/:courseId/one-to-one-chats/: Starts a new one to one chat in a course
     *
     * @param courseId                   the id of the course
     * @param otherChatParticipantLogins logins of other participants (must be 1 for one to one chat) excluding the requesting user
     * @return ResponseEntity with status 201 (Created) and with body containing the created one to one chat
     */
    @PostMapping("{courseId}/one-to-one-chats")
    @EnforceAtLeastStudent
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

        var userA = chatMembers.getFirst();
        var userB = chatMembers.get(1);

        var oneToOneChat = oneToOneChatService.startOneToOneChat(course, userA, userB);
        var userToBeNotified = userA.getLogin().equals(requestingUser.getLogin()) ? userB : userA;
        singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(oneToOneChat, userToBeNotified, requestingUser,
                NotificationType.CONVERSATION_CREATE_ONE_TO_ONE_CHAT);
        // also send notification to the author in order for the author to subscribe to the new chat (this notification won't be saved and shown to author)
        singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(oneToOneChat, requestingUser, requestingUser,
                NotificationType.CONVERSATION_CREATE_ONE_TO_ONE_CHAT);
        return ResponseEntity.created(new URI("/api/one-to-one-chats/" + oneToOneChat.getId())).body(conversationDTOService.convertOneToOneChatToDto(requestingUser, oneToOneChat));
    }
}
