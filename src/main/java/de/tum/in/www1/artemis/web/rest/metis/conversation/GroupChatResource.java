package de.tum.in.www1.artemis.web.rest.metis.conversation;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_GROUP_CHAT_PARTICIPANTS;
import static de.tum.in.www1.artemis.service.metis.conversation.ChannelService.CHANNEL_ENTITY_NAME;
import static de.tum.in.www1.artemis.service.metis.conversation.GroupChatService.GROUP_CHAT_ENTITY_NAME;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationDTOService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.GroupChatService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.GroupChatAuthorizationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;

@RestController
@RequestMapping("/api/courses")
public class GroupChatResource {

    private final Logger log = LoggerFactory.getLogger(GroupChatResource.class);

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final GroupChatAuthorizationService groupChatAuthorizationService;

    private final ConversationService conversationService;

    private final GroupChatService groupChatService;

    private final ConversationDTOService conversationDTOService;

    public GroupChatResource(UserRepository userRepository, CourseRepository courseRepository, GroupChatAuthorizationService groupChatAuthorizationService,
            ConversationService conversationService, GroupChatService groupChatService, ConversationDTOService conversationDTOService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.groupChatAuthorizationService = groupChatAuthorizationService;
        this.conversationService = conversationService;
        this.groupChatService = groupChatService;
        this.conversationDTOService = conversationDTOService;
    }

    @PostMapping("/{courseId}/group-chats")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<GroupChatDTO> startGroupChat(@PathVariable Long courseId, @RequestBody List<String> otherChatParticipantsLogins) throws URISyntaxException {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to create group chat in course {} between: {} and : {}", courseId, requestingUser.getLogin(), otherChatParticipantsLogins);
        var course = courseRepository.findByIdElseThrow(courseId);
        groupChatAuthorizationService.isAllowedToCreateGroupChat(course, requestingUser);

        var loginsToSearchFor = new HashSet<>(otherChatParticipantsLogins);
        loginsToSearchFor.add(requestingUser.getLogin());
        var chatMembers = conversationService.findUsersInDatabase(loginsToSearchFor.stream().toList());

        if (chatMembers.size() >= MAX_GROUP_CHAT_PARTICIPANTS) {
            throw new BadRequestAlertException("You can only add " + MAX_GROUP_CHAT_PARTICIPANTS + " participants to a group chat", "groupChat", "tooManyParticipants");
        }
        if (!chatMembers.contains(requestingUser)) {
            throw new BadRequestAlertException("The requesting user must be part of the group chat", "groupChat", "invalidUser");
        }

        var groupChat = groupChatService.startGroupChat(course, chatMembers);
        return ResponseEntity.created(new URI("/api/group-chats/" + groupChat.getId())).body(conversationDTOService.convertGroupChatToDto(requestingUser, groupChat));
    }

    @PostMapping("/{courseId}/group-chats/{groupChatId}/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> registerUsersToGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody List<String> userLogins) {
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", CHANNEL_ENTITY_NAME, "userLoginsEmpty");
        }
        log.debug("REST request to register {} users to group chat: {}", userLogins.size(), groupChatId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var groupChatFromDatabase = this.groupChatService.getGroupChatOrThrow(groupChatId);
        checkEntityIdMatchesPathIds(groupChatFromDatabase, Optional.of(courseId), Optional.of(groupChatId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        groupChatAuthorizationService.isAllowedToAddUsersToGroupChat(groupChatFromDatabase, requestingUser);
        var usersToRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.registerUsersToConversation(course, usersToRegister, groupChatFromDatabase, Optional.of(MAX_GROUP_CHAT_PARTICIPANTS));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/group-chats/{groupChatId}/deregister")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deregisterUsersFromGroupChat(@PathVariable Long courseId, @PathVariable Long groupChatId, @RequestBody List<String> userLogins) {
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", CHANNEL_ENTITY_NAME, "userLoginsEmpty");
        }
        log.debug("REST request to deregister {} users from the channel : {}", userLogins.size(), groupChatId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var groupChatFromDatabase = this.groupChatService.getGroupChatOrThrow(groupChatId);
        checkEntityIdMatchesPathIds(groupChatFromDatabase, Optional.of(courseId), Optional.of(groupChatId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        groupChatAuthorizationService.isAllowedToRemoveUsersFromGroupChat(groupChatFromDatabase, requestingUser);
        var usersToDeRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.deregisterUsersFromAConversation(course, usersToDeRegister, groupChatFromDatabase);
        return ResponseEntity.noContent().build();
    }

    private void checkEntityIdMatchesPathIds(GroupChat groupChat, Optional<Long> courseId, Optional<Long> conversationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!groupChat.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the groupChat", GROUP_CHAT_ENTITY_NAME, "courseIdMismatch");
            }
        });
        conversationId.ifPresent(conversationIdValue -> {
            if (!groupChat.getId().equals(conversationIdValue)) {
                throw new BadRequestAlertException("The conversationId in the path does not match the channelId in the groupChat", GROUP_CHAT_ENTITY_NAME, "channelIdMismatch");
            }
        });
    }

}
