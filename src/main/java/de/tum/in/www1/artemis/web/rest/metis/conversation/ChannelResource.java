package de.tum.in.www1.artemis.web.rest.metis.conversation;

import static de.tum.in.www1.artemis.service.metis.conversation.ChannelService.CHANNEL_ENTITY_NAME;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationDTOService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupChannelManagementService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;

@RestController
@RequestMapping("/api/courses")
public class ChannelResource extends ConversationManagementResource {

    private final Logger log = LoggerFactory.getLogger(ChannelResource.class);

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ConversationDTOService conversationDTOService;

    private final UserRepository userRepository;

    private final ConversationService conversationService;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final SingleUserNotificationService singleUserNotificationService;

    private final ConversationParticipantRepository conversationParticipantRepository;

    public ChannelResource(ConversationParticipantRepository conversationParticipantRepository, SingleUserNotificationService singleUserNotificationService,
            ChannelService channelService, ChannelRepository channelRepository, ChannelAuthorizationService channelAuthorizationService,
            AuthorizationCheckService authorizationCheckService, ConversationDTOService conversationDTOService, CourseRepository courseRepository, UserRepository userRepository,
            ConversationService conversationService, TutorialGroupChannelManagementService tutorialGroupChannelManagementService) {
        super(courseRepository);
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.conversationDTOService = conversationDTOService;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.conversationParticipantRepository = conversationParticipantRepository;
    }

    /**
     * GET /api/courses/:courseId/channels/overview: Returns an overview of all channels in a course
     *
     * @param courseId the id of the course
     * @return ResponseEntity with status 200 (OK) and with body containing the list of channels the user is authorized to see
     */
    @GetMapping("/{courseId}/channels/overview")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ChannelDTO>> getCourseChannelsOverview(@PathVariable Long courseId) {
        log.debug("REST request to all channels of course: {}", courseId);
        checkMessagingEnabledElseThrow(courseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(courseRepository.findByIdElseThrow(courseId), requestingUser);
        var result = channelRepository.findChannelsByCourseId(courseId).stream().map(channel -> conversationDTOService.convertChannelToDto(requestingUser, channel));
        var filteredStream = result;
        // only instructors / system admins can see all channels
        if (!isAtLeastInstructor) {
            filteredStream = result
                    // we only want to show public channels and in addition private channels that the requestingUser is a member of
                    .filter(channelDTO -> channelDTO.getIsPublic() || channelDTO.getIsMember());
        }
        return ResponseEntity.ok(filteredStream.sorted(Comparator.comparing(ChannelDTO::getName)).toList());
    }

    /**
     * POST /api/courses/:courseId/channels/: Creates a new channel in a course
     *
     * @param courseId   the id of the course
     * @param channelDTO the dto containing the properties of the channel to be created
     * @return ResponseEntity with status 201 (Created) and with body containing the created channel
     */
    @PostMapping("/{courseId}/channels")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ChannelDTO> createChannel(@PathVariable Long courseId, @RequestBody ChannelDTO channelDTO) throws URISyntaxException {
        log.debug("REST request to create channel in course {} with properties : {}", courseId, channelDTO);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        checkMessagingEnabledElseThrow(course);
        channelAuthorizationService.isAllowedToCreateChannel(course, requestingUser);

        var channelToCreate = new Channel();
        channelToCreate.setName(channelDTO.getName());
        channelToCreate.setIsPublic(channelDTO.getIsPublic());
        channelToCreate.setIsAnnouncementChannel(channelDTO.getIsAnnouncementChannel());
        channelToCreate.setIsArchived(false);
        channelToCreate.setDescription(channelDTO.getDescription());

        if (channelToCreate.getName() != null && channelToCreate.getName().trim().startsWith("$")) {
            throw new BadRequestAlertException("User generated channels cannot start with $", "channel", "channelNameInvalid");
        }

        var createdChannel = channelService.createChannel(course, channelToCreate, Optional.of(userRepository.getUserWithGroupsAndAuthorities()));
        return ResponseEntity.created(new URI("/api/channels/" + createdChannel.getId())).body(conversationDTOService.convertChannelToDto(requestingUser, createdChannel));
    }

    /**
     * PUT /api/courses/:courseId/channels/:channelId: Updates a channel in a course
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel to be updated
     * @param channelDTO the dto containing the properties of the channel to be updated
     * @return ResponseEntity with status 200 (Ok) and with body containing the updated channel
     */
    @PutMapping("/{courseId}/channels/{channelId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ChannelDTO> updateChannel(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody ChannelDTO channelDTO) {
        log.debug("REST request to update channel {} with properties : {}", channelId, channelDTO);
        checkMessagingEnabledElseThrow(courseId);

        var originalChannel = channelRepository.findByIdElseThrow(channelId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        if (!originalChannel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToUpdateChannel(originalChannel, requestingUser);

        if (channelDTO.getName() != null && channelDTO.getName().trim().startsWith("$")) {
            throw new BadRequestAlertException("User generated channels cannot start with $", "channel", "channelNameInvalid");
        }

        var updatedChannel = channelService.updateChannel(originalChannel.getId(), courseId, channelDTO);
        return ResponseEntity.ok().body(conversationDTOService.convertChannelToDto(requestingUser, updatedChannel));
    }

    /**
     * DELETE /api/courses/:courseId/channels/:channelId: Deletes a channel in a course
     *
     * @param courseId  the id of the course
     * @param channelId the id of the channel to be deleted
     * @return ResponseEntity with status 200 (Ok)
     */
    @DeleteMapping("/{courseId}/channels/{channelId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to delete channel {}", channelId);
        checkMessagingEnabledElseThrow(courseId);
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        channelAuthorizationService.isAllowedToDeleteChannel(channel, requestingUser);

        tutorialGroupChannelManagementService.getTutorialGroupBelongingToChannel(channel).ifPresentOrElse(tutorialGroup -> {
            throw new BadRequestAlertException("The channel belongs to tutorial group " + tutorialGroup.getTitle(), CHANNEL_ENTITY_NAME, "channel.tutorialGroup.mismatch");
        }, Optional::empty);

        var usersToNotify = conversationParticipantRepository.findConversationParticipantByConversationId(channel.getId()).stream().map(ConversationParticipant::getUser)
                .collect(Collectors.toSet());
        conversationService.deleteConversation(channel);
        usersToNotify.forEach(
                user -> singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(channel, user, requestingUser, NotificationType.CONVERSATION_DELETE_CHANNEL));
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/archive : Archives a channel in a course
     *
     * @param courseId  the id of the course
     * @param channelId the id of the channel to be archived
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/archive")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> archiveChannel(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to archive channel : {}", channelId);
        checkMessagingEnabledElseThrow(courseId);
        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        channelAuthorizationService.isAllowedToArchiveChannel(channelFromDatabase, userRepository.getUserWithGroupsAndAuthorities());
        channelService.archiveChannel(channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/unarchive : Unarchives an archived channel in a course
     *
     * @param courseId  the id of the course
     * @param channelId the id of the archived channel to be unarchived
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/unarchive")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> unArchiveChannel(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to unarchive channel : {}", channelId);
        checkMessagingEnabledElseThrow(courseId);
        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        channelAuthorizationService.isAllowedToUnArchiveChannel(channelFromDatabase, userRepository.getUserWithGroupsAndAuthorities());
        channelService.unarchiveChannel(channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/grant-channel-moderator : Grants members of a channel the channel moderator role
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the channel members to be granted the channel moderator role
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/grant-channel-moderator")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> grantChannelModeratorRole(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        log.debug("REST request to grant channel moderator role to users {} in channel {}", userLogins.toString(), channelId);
        checkMessagingEnabledElseThrow(courseId);
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToGrantChannelModeratorRole(channel, userRepository.getUserWithGroupsAndAuthorities());
        var usersToGrant = conversationService.findUsersInDatabase(userLogins);
        channelService.grantChannelModeratorRole(channel, usersToGrant);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/revoke-channel-moderator : Revokes the channel moderator role
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the channel members to be revoked the channel moderator role
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/revoke-channel-moderator")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> revokeChannelModeratorRole(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        log.debug("REST request to revoke channel moderator role from users {} in channel {}", userLogins.toString(), channelId);
        checkMessagingEnabledElseThrow(courseId);
        var channel = channelRepository.findByIdElseThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToRevokeChannelModeratorRole(channel, userRepository.getUserWithGroupsAndAuthorities());
        var usersToGrantChannelModeratorRole = conversationService.findUsersInDatabase(userLogins);
        if (usersToGrantChannelModeratorRole.contains(channel.getCreator())) {
            throw new BadRequestAlertException("The creator of the channel cannot lose the channel moderator role", CHANNEL_ENTITY_NAME, "channel.creator.revoke");
        }

        channelService.revokeChannelModeratorRole(channel, usersToGrantChannelModeratorRole);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/register : Registers users to a channel of a course
     *
     * @param courseId          the id of the course
     * @param channelId         the id of the channel
     * @param userLogins        the logins of the course users to be registered for a channel
     * @param addAllStudents    true if all course students should be added
     * @param addAllTutors      true if all course tutors and editors should be added
     * @param addAllInstructors true if all course instructors should be added
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> registerUsersToChannel(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody(required = false) List<String> userLogins,
            @RequestParam(defaultValue = "false") Boolean addAllStudents, @RequestParam(defaultValue = "false") Boolean addAllTutors,
            @RequestParam(defaultValue = "false") Boolean addAllInstructors) {
        checkMessagingEnabledElseThrow(courseId);
        List<String> usersLoginsToRegister = new ArrayList<>();
        if (userLogins != null) {
            usersLoginsToRegister.addAll(userLogins);
        }
        usersLoginsToRegister = usersLoginsToRegister.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()).stream().toList();

        if (!usersLoginsToRegister.isEmpty()) {
            log.debug("REST request to register {} users to channel : {}", usersLoginsToRegister.size(), channelId);
        }
        if (addAllStudents || addAllTutors || addAllInstructors) {
            var registerAllString = "addAllStudents: " + addAllStudents + ", addAllTutors: " + addAllTutors + ", addAllInstructors: " + addAllInstructors;
            log.debug("REST request to register {} to channel : {}", registerAllString, channelId);
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        channelAuthorizationService.isAllowedToRegisterUsersToChannel(channelFromDatabase, usersLoginsToRegister, requestingUser);
        Set<User> registeredUsers = channelService.registerUsersToChannel(addAllStudents, addAllTutors, addAllInstructors, usersLoginsToRegister, course, channelFromDatabase);
        registeredUsers.forEach(user -> singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(channelFromDatabase, user, requestingUser,
                NotificationType.CONVERSATION_ADD_USER_CHANNEL));
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/deregister : Deregisters users from a channel of a course
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the course users to be deregistered from a channel
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/deregister")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deregisterUsers(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        checkMessagingEnabledElseThrow(courseId);
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", CHANNEL_ENTITY_NAME, "userLoginsEmpty");
        }
        // ToDo: maybe limit how many users can be deregistered at once?
        log.debug("REST request to deregister {} users from the channel : {}", userLogins.size(), channelId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var channelFromDatabase = channelRepository.findByIdElseThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        channelAuthorizationService.isAllowedToDeregisterUsersFromChannel(channelFromDatabase, userLogins, requestingUser);
        var usersToDeRegister = conversationService.findUsersInDatabase(userLogins);
        // you are not allowed to deregister the creator of a channel
        var creator = channelFromDatabase.getCreator();
        if (usersToDeRegister.contains(creator)) {
            throw new BadRequestAlertException("You are not allowed to deregister the creator of a channel", "conversation", "creatorDeregistration");
        }

        conversationService.deregisterUsersFromAConversation(course, usersToDeRegister, channelFromDatabase);
        usersToDeRegister.forEach(user -> singleUserNotificationService.notifyClientAboutConversationCreationOrDeletion(channelFromDatabase, user, requestingUser,
                NotificationType.CONVERSATION_REMOVE_USER_CHANNEL));
        return ResponseEntity.ok().build();
    }

    private void checkEntityIdMatchesPathIds(Channel channel, Optional<Long> courseId, Optional<Long> conversationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!channel.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the channel", CHANNEL_ENTITY_NAME, "courseIdMismatch");
            }
        });
        conversationId.ifPresent(conversationIdValue -> {
            if (!channel.getId().equals(conversationIdValue)) {
                throw new BadRequestAlertException("The conversationId in the path does not match the channelId in the channel", CHANNEL_ENTITY_NAME, "channelIdMismatch");
            }
        });
    }

}
