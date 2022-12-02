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
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationDTOService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;

@RestController
@RequestMapping("/api/courses")
public class ChannelResource {

    private final Logger log = LoggerFactory.getLogger(ChannelResource.class);

    private final ChannelService channelService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ConversationDTOService conversationDTOService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ConversationService conversationService;

    public ChannelResource(ChannelService channelService, ChannelAuthorizationService channelAuthorizationService, AuthorizationCheckService authorizationCheckService,
            ConversationDTOService conversationDTOService, CourseRepository courseRepository, UserRepository userRepository, ConversationService conversationService) {
        this.channelService = channelService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.conversationDTOService = conversationDTOService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
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
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(courseRepository.findByIdElseThrow(courseId), requestingUser);
        var result = channelService.getChannels(courseId).stream().map(channel -> conversationDTOService.convertChannelToDto(requestingUser, channel));
        var filteredStream = result;
        // only instructors / admins can see all channels
        if (!isAtLeastInstructor) {
            filteredStream = result // we only want to show archived channels where the requestingUser is a member
                    .filter(channelDTO -> !channelDTO.getIsArchived() || channelDTO.getIsMember())
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
        channelAuthorizationService.isAllowedToCreateChannel(course, null);

        var channelToCreate = new Channel();
        channelToCreate.setName(channelDTO.getName());
        channelToCreate.setIsPublic(channelDTO.getIsPublic());
        channelToCreate.setIsArchived(false);
        channelToCreate.setDescription(channelDTO.getDescription());

        var createdChannel = channelService.createChannel(course, channelToCreate);
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

        var originalChannel = channelService.getChannelOrThrow(channelId);
        if (originalChannel.getIsArchived()) {
            throw new BadRequestAlertException("Archived channels cannot be edited", CHANNEL_ENTITY_NAME, "channelIsArchived");
        }
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        if (!originalChannel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToUpdateChannel(originalChannel, requestingUser);
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
        var channel = channelService.getChannelOrThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        channelAuthorizationService.isAllowedToDeleteChannel(course, null);
        channelService.deleteChannel(channel);
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
        var channelFromDatabase = this.channelService.getChannelOrThrow(channelId);
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
        var channelFromDatabase = this.channelService.getChannelOrThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        channelAuthorizationService.isAllowedToUnArchiveChannel(channelFromDatabase, userRepository.getUserWithGroupsAndAuthorities());
        channelService.unarchiveChannel(channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/grant-channel-admin : Grants members of a channel the channel admin role
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the channel members to be granted the channel admin role
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/grant-channel-admin")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> grantChannelAdmin(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        log.debug("REST request to grant channel admin rights to users {} in channel {}", userLogins.toString(), channelId);
        var channel = channelService.getChannelOrThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToGrantChannelAdmin(channel, userRepository.getUserWithGroupsAndAuthorities());
        var usersToGrantChannelAdmin = conversationService.findUsersInDatabase(userLogins);
        channelService.grantChannelAdmin(channel, usersToGrantChannelAdmin);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/revoke-channel-admin : Revokes members of a channel the channel admin role
     *
     * @param courseId   the id of the course
     * @param channelId  the id of the channel
     * @param userLogins the logins of the channel members to be revokes the channel admin role
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/revoke-channel-admin")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> revokeChannelAdmin(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        log.debug("REST request to revoke channel admin rights from users {} in channel {}", userLogins.toString(), channelId);
        var channel = channelService.getChannelOrThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToRevokeChannelAdmin(channel, userRepository.getUserWithGroupsAndAuthorities());
        var usersToGrantChannelAdmin = conversationService.findUsersInDatabase(userLogins);
        // nobody is allowed to revoke admin rights from the creator of the channel throw bad request
        if (usersToGrantChannelAdmin.contains(channel.getCreator())) {
            throw new BadRequestAlertException("The creator of the channel cannot be revoked from the channel admin role", CHANNEL_ENTITY_NAME, "channel.creator.revoke");
        }

        channelService.revokeChannelAdmin(channel, usersToGrantChannelAdmin);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/channels/:channelId/register : Registers users to a channel of a course
     *
     * @param courseId          the id of the course
     * @param channelId         the id of the channel
     * @param userLogins        the logins of the course users to be registered for a channel
     * @param addAllStudents    true if all course students should be added
     * @param addAllTutors      true if all course tutors should be added
     * @param addAllEditors     true if all course editors should be added
     * @param addAllInstructors true if all course instructors should be added
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/channels/{channelId}/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> registerUsersToChannel(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins,
            @RequestParam(defaultValue = "false") Boolean addAllStudents, @RequestParam(defaultValue = "false") Boolean addAllTutors,
            @RequestParam(defaultValue = "false") Boolean addAllEditors, @RequestParam(defaultValue = "false") Boolean addAllInstructors) {
        var usersLoginsToRegister = Objects.requireNonNullElseGet(userLogins, () -> new HashSet<>(userLogins)).stream().filter(Objects::nonNull).map(String::trim)
                .collect(Collectors.toSet());
        if (!userLogins.isEmpty()) {
            log.debug("REST request to register {} users to channel : {}", userLogins.size(), channelId);
        }
        if (addAllStudents || addAllTutors || addAllEditors || addAllInstructors) {
            var registerAllString = "addAllStudents: " + addAllStudents + ", addAllTutors: " + addAllTutors + ", addAllEditors: " + addAllEditors + ", addAllInstructors: "
                    + addAllInstructors;
            log.debug("REST request to register {} to channel : {}", registerAllString, channelId);
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var channelFromDatabase = this.channelService.getChannelOrThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        if (channelFromDatabase.getIsArchived()) {
            throw new BadRequestAlertException("Users can not be registered to an archived channel.", CHANNEL_ENTITY_NAME, "channelIsArchived");
        }
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        channelAuthorizationService.isAllowedToRegisterUsersToChannel(channelFromDatabase, userLogins, requestingUser);
        Set<User> usersToRegister = new HashSet<>();
        usersToRegister.addAll(conversationService.findUsersInDatabase(course, addAllStudents, addAllTutors, addAllEditors, addAllInstructors));
        usersToRegister.addAll(conversationService.findUsersInDatabase(usersLoginsToRegister.stream().toList()));
        conversationService.registerUsersToConversation(course, usersToRegister, channelFromDatabase, Optional.empty());
        return ResponseEntity.noContent().build();
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
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", CHANNEL_ENTITY_NAME, "userLoginsEmpty");
        }
        // ToDo: maybe limit how many users can be deregistered at once?
        log.debug("REST request to deregister {} users from the channel : {}", userLogins.size(), channelId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var channelFromDatabase = this.channelService.getChannelOrThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        channelAuthorizationService.isAllowedToDeregisterUsersFromChannel(channelFromDatabase, userLogins, requestingUser);
        var usersToDeRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.deregisterUsersFromAConversation(course, usersToDeRegister, channelFromDatabase);
        return ResponseEntity.noContent().build();
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
