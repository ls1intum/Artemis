package de.tum.in.www1.artemis.web.rest.metis.conversation;

import static de.tum.in.www1.artemis.service.metis.conversation.ChannelService.CHANNEL_ENTITY_NAME;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelAuthorizationService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@RestController
@RequestMapping("/api/courses")
public class ChannelResource {

    private final Logger log = LoggerFactory.getLogger(ChannelResource.class);

    private final ChannelService channelService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ConversationService conversationService;

    public ChannelResource(ChannelService channelService, ChannelAuthorizationService channelAuthorizationService, AuthorizationCheckService authorizationCheckService,
            CourseRepository courseRepository, UserRepository userRepository, ConversationParticipantRepository conversationParticipantRepository,
            ConversationService conversationService) {
        this.channelService = channelService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationService = conversationService;
    }

    public record ChannelOverviewDTO(Long channelId, String channelName, String channelDescription, Boolean isPublic, boolean isMember, int noOfMembers) {
    }

    @GetMapping("/{courseId}/channels/overview")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ChannelOverviewDTO>> getCourseChannelsOverview(@PathVariable Long courseId) {
        log.debug("REST request to all channels of course: {}", courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), user);
        var channels = channelService.getChannels(courseId);
        var participationsOfUser = conversationParticipantRepository.findConversationParticipantByUserId(user.getId());
        var result = new ArrayList<ChannelOverviewDTO>();
        for (Channel channel : channels) {
            var isMember = participationsOfUser.stream().anyMatch(participation -> participation.getConversation().getId().equals(channel.getId()));
            // we do not show private channels where the user is not a member
            if (Boolean.FALSE.equals(channel.getIsPublic()) && !isMember) {
                continue;
            }
            var noOfMembers = channelService.getMemberCount(channel.getId());
            var channelName = channel.getName();
            var channelDescription = "";
            if (channel.getDescription() != null) {
                channelDescription = channel.getDescription();
            }
            result.add(new ChannelOverviewDTO(channel.getId(), channelName, channelDescription, channel.getIsPublic(), isMember, noOfMembers));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{courseId}/channels")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Channel> createChannel(@PathVariable Long courseId, @Valid @RequestBody Channel channel) throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);
        channelAuthorizationService.isAllowedToCreateChannelElseThrow(course, null);
        var createdChannel = channelService.createChannel(course, channel);
        createdChannel.setNumberOfMembers(channelService.getMemberCount(channel.getId()));
        return ResponseEntity.created(new URI("/api/channels/" + createdChannel.getId())).body(createdChannel);
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

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        channelAuthorizationService.isAllowedToRegisterUsersToChannel(course, channelFromDatabase, userLogins, requestingUser);
        var usersToRegister = findUsersInDatabase(userLogins);
        conversationService.registerUsers(usersToRegister, channelFromDatabase);
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

        var usersToDeRegister = findUsersInDatabase(userLogins);

        conversationService.deregisterUsers(usersToDeRegister, channelFromDatabase);
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

    private Set<User> findUsersInDatabase(@RequestBody List<String> userLogins) {
        Set<User> users = new HashSet<>();
        for (String userLogin : userLogins) {
            if (userLogin == null || userLogin.isEmpty()) {
                continue;
            }
            var userToRegister = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
            userToRegister.ifPresent(users::add);
        }
        return users;
    }

}
