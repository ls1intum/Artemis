package de.tum.in.www1.artemis.web.rest.metis.conversation;

import static de.tum.in.www1.artemis.service.metis.conversation.ChannelService.CHANNEL_ENTITY_NAME;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelAuthorizationService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;

@RestController
@RequestMapping("/api/courses")
public class ChannelResource {

    private final Logger log = LoggerFactory.getLogger(ChannelResource.class);

    private final ChannelService channelService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ConversationService conversationService;

    public ChannelResource(ChannelService channelService, ChannelAuthorizationService channelAuthorizationService, AuthorizationCheckService authorizationCheckService,
            CourseRepository courseRepository, UserRepository userRepository, ConversationService conversationService) {
        this.channelService = channelService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
    }

    @GetMapping("/{courseId}/channels/overview")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ChannelDTO>> getCourseChannelsOverview(@PathVariable Long courseId) {
        log.debug("REST request to all channels of course: {}", courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), user);
        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(courseRepository.findByIdElseThrow(courseId), user);
        var result = channelService.getChannels(courseId).stream().map(channel -> channelService.convertToDTO(channel, user));
        var filteredStream = result;
        // only instructors / admins can see all channels
        if (!isAtLeastInstructor) {
            filteredStream = result // we only want to show archived channels where the user is a member
                    .filter(channelDTO -> !channelDTO.getIsArchived() || channelDTO.getIsMember())
                    // we only want to show public channels and in addition private channels that the user is a member of
                    .filter(channelDTO -> channelDTO.getIsPublic() || channelDTO.getIsMember());
        }
        return ResponseEntity.ok(filteredStream.sorted(Comparator.comparing(ChannelDTO::getName)).toList());
    }

    @PostMapping("/{courseId}/channels")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ChannelDTO> createChannel(@PathVariable Long courseId, @RequestBody ChannelDTO channelDTO) throws URISyntaxException {
        log.debug("REST request to create channel in course {} with properties : {}", courseId, channelDTO);
        var course = courseRepository.findByIdElseThrow(courseId);
        channelAuthorizationService.isAllowedToCreateChannel(course, null);
        var channelToCreate = channelDTO.toChannel();
        var createdChannel = channelService.createChannel(course, channelToCreate);
        return ResponseEntity.created(new URI("/api/channels/" + createdChannel.getId())).body(channelService.convertToDTO(createdChannel, null));
    }

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
        return ResponseEntity.ok().body(channelService.convertToDTO(updatedChannel, requestingUser));
    }

    @DeleteMapping("/{courseId}/channels/{channelId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long courseId, @PathVariable Long channelId) {
        log.debug("REST request to delete channel {}", channelId);
        var channel = channelService.getChannelOrThrow(channelId);
        if (!channel.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The channel does not belong to the course", CHANNEL_ENTITY_NAME, "channel.course.mismatch");
        }
        channelAuthorizationService.isAllowedToDeleteChannel(channel, null);
        channelService.deleteChannel(channel.getId());
        return ResponseEntity.ok().build();
    }

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
        channelService.grantChannelAdmin(channelId, usersToGrantChannelAdmin);
        return ResponseEntity.ok().build();
    }

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
        // nobody is allowed to revoke admin rights from the creator of the channel
        usersToGrantChannelAdmin.removeIf(user -> user.getId().equals(channel.getCreator().getId()));
        channelService.revokeChannelAdmin(channelId, usersToGrantChannelAdmin);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{courseId}/channels/{channelId}/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> registerUsersToChannel(@PathVariable Long courseId, @PathVariable Long channelId, @RequestBody List<String> userLogins) {
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", CHANNEL_ENTITY_NAME, "userLoginsEmpty");
        }
        // ToDo: maybe limit how many users can be registered at once?

        log.debug("REST request to register {} users to channel : {}", userLogins.size(), channelId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var channelFromDatabase = this.channelService.getChannelOrThrow(channelId);
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));
        if (channelFromDatabase.getIsArchived()) {
            throw new BadRequestAlertException("Users can not be registered to an archived channel.", CHANNEL_ENTITY_NAME, "channelIsArchived");
        }

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        channelAuthorizationService.isAllowedToRegisterUsersToChannel(course, channelFromDatabase, userLogins, requestingUser);
        var usersToRegister = conversationService.findUsersInDatabase(userLogins);
        conversationService.registerUsers(course, usersToRegister, channelFromDatabase);
        return ResponseEntity.noContent().build();
    }

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

        channelAuthorizationService.isAllowedToDeregisterUsersFromChannel(course, channelFromDatabase, userLogins, requestingUser);

        var usersToDeRegister = conversationService.findUsersInDatabase(userLogins);

        conversationService.deregisterUsers(course, usersToDeRegister, channelFromDatabase);
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
