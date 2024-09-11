package de.tum.cit.aet.artemis.web.rest.metis.conversation;

import static de.tum.cit.aet.artemis.communication.ConversationSettings.MAX_GROUP_CHAT_PARTICIPANTS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.metis.conversation.GroupChatService.GROUP_CHAT_ENTITY_NAME;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.repository.conversation.GroupChatRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.service.metis.conversation.ConversationDTOService;
import de.tum.cit.aet.artemis.service.metis.conversation.ConversationService;
import de.tum.cit.aet.artemis.service.metis.conversation.GroupChatService;
import de.tum.cit.aet.artemis.service.metis.conversation.auth.GroupChatAuthorizationService;
import de.tum.cit.aet.artemis.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.MetisCrudAction;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/courses/")
public class GroupChatResource extends ConversationManagementResource {

    private static final Logger log = LoggerFactory.getLogger(GroupChatResource.class);

    private final UserRepository userRepository;

    private final GroupChatAuthorizationService groupChatAuthorizationService;

    private final ConversationService conversationService;

    private final GroupChatService groupChatService;

    private final GroupChatRepository groupChatRepository;

    private final ConversationDTOService conversationDTOService;

    private final SingleUserNotificationService singleUserNotificationService;

    public GroupChatResource(SingleUserNotificationService singleUserNotificationService, UserRepository userRepository, CourseRepository courseRepository,
            GroupChatAuthorizationService groupChatAuthorizationService, ConversationService conversationService, GroupChatService groupChatService,
            GroupChatRepository groupChatRepository, ConversationDTOService conversationDTOService) {
        super(courseRepository);
        this.userRepository = userRepository;
        this.groupChatAuthorizationService = groupChatAuthorizationService;
        this.conversationService = conversationService;
        this.groupChatService = groupChatService;
        this.groupChatRepository = groupChatRepository;
        this.conversationDTOService = conversationDTOService;
        this.singleUserNotificationService = singleUserNotificationService;
    }

    /**
     * POST /api/courses/:courseId/group-chats/: Starts a new group chat in a course
     *
     * @param courseId                    the id of the course
     * @param otherChatParticipantsLogins logins of the starting members of the group chat (excluding the requesting user)
     * @return ResponseEntity with status 201 (Created) and with body containing the created group chat
     */
    @PostMapping("{courseId}/group-chats")
    @EnforceAtLeastStudent
    public ResponseEntity<GroupChatDTO> startGroupChat(@PathVariable Long courseId, @RequestBody List<String> otherChatParticipantsLogins) throws URISyntaxException {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to create group chat in course {} between: {} and : {}", courseId, requestingUser.getLogin(), otherChatParticipantsLogins);
        var course = courseRepository.findByIdElseThrow(courseId);
        checkMessagingEnabledElseThrow(course);
        groupChatAuthorizationService.isAllowedToCreateGroupChat(course, requestingUser);

        var loginsToSearchFor = new HashSet<>(otherChatParticipantsLogins);
        loginsToSearchFor.add(requestingUser.getLogin());
        var chatMembers = conversationService.findUsersInDatabase(loginsToSearchFor.stream().toList());

        if (chatMembers.size() < 2 || chatMembers.size() > MAX_GROUP_CHAT_PARTICIPANTS) {
            throw new BadRequestAlertException("The number of participants in a group chat must be between 2 and " + MAX_GROUP_CHAT_PARTICIPANTS, GROUP_CHAT_ENTITY_NAME,
                    "invalidNumberOfParticipants");
        }

        var groupChat = groupChatService.startGroupChat(course, chatMembers);
        chatMembers.forEach(user -> singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(groupChat, user, requestingUser,
                NotificationType.CONVERSATION_CREATE_GROUP_CHAT));

        conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, groupChat, chatMembers);

        return ResponseEntity.created(new URI("/api/group-chats/" + groupChat.getId())).body(conversationDTOService.convertGroupChatToDto(requestingUser, groupChat));
    }

    /**
     * PUT /api/courses/:courseId/group-chats/:groupChatId: Updates a group chat in a course
     *
     * @param courseId     the id of the course
     * @param groupChatId  the id of the group chat to be updated
     * @param groupChatDTO dto containing the properties of the group chat to be updated
     * @return ResponseEntity with status 200 (Ok) and with body containing the updated group chat
     */
    @PutMapping("{courseId}/group-chats/{groupChatId}")
    @EnforceAtLeastStudent
    public ResponseEntity<GroupChatDTO> updateGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody GroupChatDTO groupChatDTO) {
        log.debug("REST request to update groupChat {} with properties : {}", groupChatId, groupChatDTO);
        checkMessagingEnabledElseThrow(courseId);

        var originalGroupChat = groupChatRepository.findByIdElseThrow(groupChatId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        if (!originalGroupChat.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The group chat does not belong to the course", GROUP_CHAT_ENTITY_NAME, "groupChat.course.mismatch");
        }
        groupChatAuthorizationService.isAllowedToUpdateGroupChat(originalGroupChat, requestingUser);
        var updatedGroupChat = groupChatService.updateGroupChat(originalGroupChat.getId(), groupChatDTO);
        return ResponseEntity.ok().body(conversationDTOService.convertGroupChatToDto(requestingUser, updatedGroupChat));
    }

    /**
     * POST /api/courses/:courseId/group-chats/:groupChatId/register : Registers users to a group chat of a course
     *
     * @param courseId    the id of the course
     * @param groupChatId the id of the group chat
     * @param userLogins  the logins of the course users to be registered to a group chat
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/group-chats/{groupChatId}/register")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> registerUsersToGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody List<String> userLogins) {
        log.debug("REST request to register {} users to group chat: {}", userLogins.size(), groupChatId);
        var course = courseRepository.findByIdElseThrow(courseId);
        checkMessagingEnabledElseThrow(course);
        if (userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", GROUP_CHAT_ENTITY_NAME, "userLoginsEmpty");
        }
        var groupChatFromDatabase = groupChatRepository.findByIdElseThrow(groupChatId);
        checkEntityIdMatchesPathIds(groupChatFromDatabase, Optional.of(courseId), Optional.of(groupChatId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        groupChatAuthorizationService.isAllowedToAddUsersToGroupChat(groupChatFromDatabase, requestingUser);
        var usersToRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.registerUsersToConversation(course, usersToRegister, groupChatFromDatabase, Optional.of(MAX_GROUP_CHAT_PARTICIPANTS));
        usersToRegister.forEach(user -> singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(groupChatFromDatabase, user, requestingUser,
                NotificationType.CONVERSATION_ADD_USER_GROUP_CHAT));
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/group-chats/:groupChatId/deregister : Deregisters users from a group chat of a course
     *
     * @param courseId    the id of the course
     * @param groupChatId the id of the group chat
     * @param userLogins  the logins of the course users to be deregistered from a group chat
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/group-chats/{groupChatId}/deregister")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deregisterUsersFromGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody List<String> userLogins) {
        log.debug("REST request to deregister {} users from the group chat : {}", userLogins.size(), groupChatId);
        var course = courseRepository.findByIdElseThrow(courseId);
        checkMessagingEnabledElseThrow(course);
        if (userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", GROUP_CHAT_ENTITY_NAME, "userLoginsEmpty");
        }

        var groupChatFromDatabase = groupChatRepository.findByIdElseThrow(groupChatId);
        checkEntityIdMatchesPathIds(groupChatFromDatabase, Optional.of(courseId), Optional.of(groupChatId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        groupChatAuthorizationService.isAllowedToRemoveUsersFromGroupChat(groupChatFromDatabase, requestingUser);
        var usersToDeRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.deregisterUsersFromAConversation(course, usersToDeRegister, groupChatFromDatabase);
        // ToDo: Discuss if we should delete the group chat if it has no participants left, but maybe we want to keep it for data analysis purposes

        usersToDeRegister.forEach(user -> singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(groupChatFromDatabase, user, requestingUser,
                NotificationType.CONVERSATION_REMOVE_USER_GROUP_CHAT));
        return ResponseEntity.ok().build();
    }

    private void checkEntityIdMatchesPathIds(GroupChat groupChat, Optional<Long> courseId, Optional<Long> conversationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!groupChat.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the groupChat", GROUP_CHAT_ENTITY_NAME, "courseIdMismatch");
            }
        });
        conversationId.ifPresent(conversationIdValue -> {
            if (!groupChat.getId().equals(conversationIdValue)) {
                throw new BadRequestAlertException("The conversationId in the path does not match the groupChatId in the groupChat", GROUP_CHAT_ENTITY_NAME, "groupIdMismatch");
            }
        });
    }

}
