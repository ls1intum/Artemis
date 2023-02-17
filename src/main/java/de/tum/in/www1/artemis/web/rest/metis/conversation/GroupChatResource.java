package de.tum.in.www1.artemis.web.rest.metis.conversation;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_GROUP_CHAT_PARTICIPANTS;
import static de.tum.in.www1.artemis.service.metis.conversation.GroupChatService.GROUP_CHAT_ENTITY_NAME;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationDTOService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.GroupChatService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.GroupChatAuthorizationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

@RestController
@RequestMapping("/api/courses")
public class GroupChatResource {

    private final Logger log = LoggerFactory.getLogger(GroupChatResource.class);

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final GroupChatAuthorizationService groupChatAuthorizationService;

    private final ConversationService conversationService;

    private final GroupChatService groupChatService;

    private final GroupChatRepository groupChatRepository;

    private final ConversationDTOService conversationDTOService;

    public GroupChatResource(UserRepository userRepository, CourseRepository courseRepository, GroupChatAuthorizationService groupChatAuthorizationService,
            ConversationService conversationService, GroupChatService groupChatService, GroupChatRepository groupChatRepository, ConversationDTOService conversationDTOService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.groupChatAuthorizationService = groupChatAuthorizationService;
        this.conversationService = conversationService;
        this.groupChatService = groupChatService;
        this.groupChatRepository = groupChatRepository;
        this.conversationDTOService = conversationDTOService;
    }

    /**
     * POST /api/courses/:courseId/group-chats/: Starts a new group chat in a course
     *
     * @param courseId                    the id of the course
     * @param otherChatParticipantsLogins logins of the starting members of the group chat (excluding the requesting user)
     * @return ResponseEntity with status 201 (Created) and with body containing the created group chat
     */
    @PostMapping("/{courseId}/group-chats")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<GroupChatDTO> startGroupChat(@PathVariable Long courseId, @RequestBody List<String> otherChatParticipantsLogins) throws URISyntaxException {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to create group chat in course {} between: {} and : {}", courseId, requestingUser.getLogin(), otherChatParticipantsLogins);
        if (!(courseRepository.isMessagingEnabled(courseId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        groupChatAuthorizationService.isAllowedToCreateGroupChat(course, requestingUser);

        var loginsToSearchFor = new HashSet<>(otherChatParticipantsLogins);
        loginsToSearchFor.add(requestingUser.getLogin());
        var chatMembers = conversationService.findUsersInDatabase(loginsToSearchFor.stream().toList());

        if (chatMembers.size() < 2 || chatMembers.size() > MAX_GROUP_CHAT_PARTICIPANTS) {
            throw new BadRequestAlertException("The number of participants in a group chat must be between 2 and " + MAX_GROUP_CHAT_PARTICIPANTS, GROUP_CHAT_ENTITY_NAME,
                    "invalidNumberOfParticipants");
        }

        var groupChat = groupChatService.startGroupChat(course, chatMembers);

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
    @PutMapping("/{courseId}/group-chats/{groupChatId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<GroupChatDTO> updateGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody GroupChatDTO groupChatDTO) {
        log.debug("REST request to update groupChat {} with properties : {}", groupChatId, groupChatDTO);
        if (!(courseRepository.isMessagingEnabled(courseId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }

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
    @PostMapping("/{courseId}/group-chats/{groupChatId}/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> registerUsersToGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody List<String> userLogins) {
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", GROUP_CHAT_ENTITY_NAME, "userLoginsEmpty");
        }
        log.debug("REST request to register {} users to group chat: {}", userLogins.size(), groupChatId);
        if (!(courseRepository.isMessagingEnabled(courseId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var groupChatFromDatabase = groupChatRepository.findByIdElseThrow(groupChatId);
        checkEntityIdMatchesPathIds(groupChatFromDatabase, Optional.of(courseId), Optional.of(groupChatId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        groupChatAuthorizationService.isAllowedToAddUsersToGroupChat(groupChatFromDatabase, requestingUser);
        var usersToRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.registerUsersToConversation(course, usersToRegister, groupChatFromDatabase, Optional.of(MAX_GROUP_CHAT_PARTICIPANTS));
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
    @PostMapping("/{courseId}/group-chats/{groupChatId}/deregister")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deregisterUsersFromGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody List<String> userLogins) {
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", GROUP_CHAT_ENTITY_NAME, "userLoginsEmpty");
        }
        log.debug("REST request to deregister {} users from the group chat : {}", userLogins.size(), groupChatId);
        if (!(courseRepository.isMessagingEnabled(courseId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }
        var course = courseRepository.findByIdElseThrow(courseId);

        var groupChatFromDatabase = groupChatRepository.findByIdElseThrow(groupChatId);
        checkEntityIdMatchesPathIds(groupChatFromDatabase, Optional.of(courseId), Optional.of(groupChatId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        groupChatAuthorizationService.isAllowedToRemoveUsersFromGroupChat(groupChatFromDatabase, requestingUser);
        var usersToDeRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.deregisterUsersFromAConversation(course, usersToDeRegister, groupChatFromDatabase);
        // ToDo: Discuss if we should delete the group chat if it has no participants left, but maybe we want to keep it for data analysis purposes
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
