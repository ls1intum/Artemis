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

import de.tum.cit.aet.artemis.communication.dto.OneToOneChatDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationDTOService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.service.conversation.OneToOneChatService;
import de.tum.cit.aet.artemis.communication.service.conversation.auth.OneToOneChatAuthorizationService;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/communication/courses/")
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
     * POST courses/:courseId/one-to-one-chats/: Starts a new one to one chat in a course
     *
     * @param courseId                   the id of the course
     * @param otherChatParticipantLogins logins of other participants (must be 1 for one to one chat) excluding the requesting user
     *
     * @return ResponseEntity according to createOneToOneChat function
     */
    @PostMapping("{courseId}/one-to-one-chats")
    @EnforceAtLeastStudent
    public ResponseEntity<OneToOneChatDTO> startOneToOneChat(@PathVariable Long courseId, @RequestBody List<String> otherChatParticipantLogins) throws URISyntaxException {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to create one to one chat in course {} between : {} and : {}", courseId, requestingUser.getLogin(), otherChatParticipantLogins);
        var course = courseRepository.findByIdElseThrow(courseId);

        validateInputElseThrow(requestingUser, course);

        var loginsToSearchFor = new HashSet<>(otherChatParticipantLogins);
        loginsToSearchFor.add(requestingUser.getLogin());
        var chatMembers = new ArrayList<>(conversationService.findUsersInDatabase(loginsToSearchFor.stream().toList()));

        if (chatMembers.size() != 2) {
            throw new BadRequestAlertException("A one-to-one chat can only be started with two users", "OneToOneChat", "invalidUserCount");
        }

        var userA = chatMembers.getFirst();
        var userB = chatMembers.get(1);

        var userToBeNotified = userA.getLogin().equals(requestingUser.getLogin()) ? userB : userA;
        return createOneToOneChat(requestingUser, userToBeNotified, course);
    }

    /**
     * POST courses/:courseId/one-to-one-chats/:userId: Starts a new one to one chat in a course
     *
     * @param courseId the id of the course
     * @param userId   the id of the participating user
     *
     * @return ResponseEntity according to createOneToOneChat function
     */
    @PostMapping("{courseId}/one-to-one-chats/{userId}")
    @EnforceAtLeastStudent
    public ResponseEntity<OneToOneChatDTO> startOneToOneChat(@PathVariable Long courseId, @PathVariable Long userId) throws URISyntaxException {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var otherUser = userRepository.findByIdElseThrow(userId);
        log.debug("REST request to create one to one chat by id in course {} between : {} and : {}", courseId, requestingUser.getLogin(), otherUser.getLogin());
        var course = courseRepository.findByIdElseThrow(courseId);

        validateInputElseThrow(requestingUser, course);

        return createOneToOneChat(requestingUser, otherUser, course);
    }

    /**
     * Function to validate incoming request data
     *
     * @param requestingUser user that wants to create the one to one chat
     * @param course         course to create the one to one chat
     */
    private void validateInputElseThrow(User requestingUser, Course course) {
        checkMessagingEnabledElseThrow(course);
        oneToOneChatAuthorizationService.isAllowedToCreateOneToOneChat(course, requestingUser);
    }

    /**
     * Function to create a one to one chat and return the corresponding response to the client
     *
     * @param requestingUser   user that wants to create the one to one chat
     * @param userToBeNotified user that is invited into the one to one chat
     * @param course           course to create the one to one chat
     *
     * @return ResponseEntity with status 201 (Created) and with body containing the created one to one chat
     */
    private ResponseEntity<OneToOneChatDTO> createOneToOneChat(User requestingUser, User userToBeNotified, Course course) throws URISyntaxException {
        var oneToOneChat = oneToOneChatService.startOneToOneChat(course, requestingUser, userToBeNotified);
        return ResponseEntity.created(new URI("/api/one-to-one-chats/" + oneToOneChat.getId())).body(conversationDTOService.convertOneToOneChatToDto(requestingUser, oneToOneChat));
    }
}
