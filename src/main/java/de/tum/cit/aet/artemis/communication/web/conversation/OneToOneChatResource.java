package de.tum.cit.aet.artemis.communication.web.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.dto.OneToOneChatCreationDTO;
import de.tum.cit.aet.artemis.communication.dto.OneToOneChatDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationDTOService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.service.conversation.OneToOneChatService;
import de.tum.cit.aet.artemis.communication.service.conversation.auth.OneToOneChatAuthorizationService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/communication/courses/")
public class OneToOneChatResource extends ConversationManagementResource {

    private static final Logger log = LoggerFactory.getLogger(OneToOneChatResource.class);

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
     * POST courses/:courseId/one-to-one-chats : Starts a new one to one chat in a course with exactly one other participant.
     * <p>
     * The partner is identified in the request body by either {@code userId} or {@code login} (see {@link OneToOneChatCreationDTO}). The body also tolerates the deprecated
     * single-login array form for backwards compatibility. The canonical path is {@code courses/{courseId}/one-to-one-chats}; the legacy {@code .../one-to-one-chats/{userId}} path
     * (partner id in the URL) is kept as a deprecated alias and resolved here.
     *
     * @param courseId    the id of the course
     * @param userId      the id of the other participant, only set when the deprecated {@code .../one-to-one-chats/{userId}} path is used
     * @param chatPartner the other participant (by {@code userId} or {@code login}), set when the canonical path with a request body is used
     * @return ResponseEntity according to createOneToOneChat function
     */
    @PostMapping({ "{courseId}/one-to-one-chats", "{courseId}/one-to-one-chats/{userId}" })
    @EnforceAtLeastStudent
    public ResponseEntity<OneToOneChatDTO> startOneToOneChat(@PathVariable Long courseId, @PathVariable(name = "userId", required = false) Long userId,
            @RequestBody(required = false) OneToOneChatCreationDTO chatPartner) throws URISyntaxException {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        validateInputElseThrow(requestingUser, course);

        // Resolve the single other participant. Precedence: deprecated path id, then body userId, then body login.
        Long partnerId = userId != null ? userId : (chatPartner != null ? chatPartner.userId() : null);
        User otherUser;
        if (partnerId != null) {
            otherUser = userRepository.findByIdElseThrow(partnerId);
        }
        else if (chatPartner != null && chatPartner.login() != null) {
            otherUser = findSingleOtherChatParticipantElseThrow(requestingUser, chatPartner.login());
        }
        else {
            throw new BadRequestAlertException("A one-to-one chat must specify the other participant by userId or login", "oneToOneChat", "missingPartner");
        }
        log.debug("REST request to create one to one chat in course {} between : {} and : {}", courseId, requestingUser.getLogin(), otherUser.getLogin());
        return createOneToOneChat(requestingUser, otherUser, course);
    }

    /**
     * Resolves the single other participant of a one-to-one chat from a login, ensuring exactly two distinct users (the requesting user and the partner) are involved.
     *
     * @param requestingUser the user creating the chat
     * @param partnerLogin   the login of the other participant
     * @return the other participant
     */
    private User findSingleOtherChatParticipantElseThrow(User requestingUser, String partnerLogin) {
        var loginsToSearchFor = new HashSet<>(List.of(partnerLogin, requestingUser.getLogin()));
        var chatMembers = new ArrayList<>(conversationService.findUsersInDatabase(loginsToSearchFor.stream().toList()));
        if (chatMembers.size() != 2) {
            throw new BadRequestAlertException("A one-to-one chat can only be started with two users", "OneToOneChat", "invalidUserCount");
        }
        var userA = chatMembers.getFirst();
        var userB = chatMembers.get(1);
        return userA.getLogin().equals(requestingUser.getLogin()) ? userB : userA;
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
