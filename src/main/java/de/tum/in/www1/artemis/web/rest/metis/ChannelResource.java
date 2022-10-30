package de.tum.in.www1.artemis.web.rest.metis;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.ChannelService;
import de.tum.in.www1.artemis.service.metis.ChannelService.ChannelOverviewDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@RestController
@RequestMapping("/api/courses")
public class ChannelResource {

    private final Logger log = LoggerFactory.getLogger(ChannelResource.class);

    private final ChannelService channelService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public ChannelResource(ChannelService channelService, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository, UserRepository userRepository) {
        this.channelService = channelService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET /courses/{courseId}/channels : get all conversations for which the user has access
     *
     * @param courseId the courseId to which the channels belong to
     * @return the ResponseEntity with status 200 (OK)
     */
    @GetMapping("/{courseId}/channels")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ChannelOverviewDTO>> getChannels(@PathVariable Long courseId) {
        log.debug("REST request to all channels of course: {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), user);
        List<Conversation> channels = channelService.getChannels(courseId);
        var result = channels.parallelStream().map(channel -> {
            var noOfMembers = 0;
            if (channel.getConversationParticipants() != null) {
                noOfMembers = channel.getConversationParticipants().size();
            }
            var isMember = false;
            if (channel.getConversationParticipants() != null) {
                isMember = channel.getConversationParticipants().stream().anyMatch(conversationParticipant -> conversationParticipant.getUser().equals(user));
            }
            var channelName = "";
            if (channel.getName() != null) {
                channelName = channel.getName();
            }
            return new ChannelOverviewDTO(channel.getId(), channelName, isMember, noOfMembers);
        }).toList();
        return new ResponseEntity<>(result, null, HttpStatus.OK);
    }

    @PostMapping("/{courseId}/channels/{channelId}/register/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> registerStudent(@PathVariable Long courseId, @PathVariable Long channelId, @PathVariable String studentLogin) {
        log.debug("REST request to register {} student to channel : {}", studentLogin, channelId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var channelFromDatabase = this.channelService.getChannelElseThrow(channelId);
        var userToRegister = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));

        // ToDo: Allow users others user to register someone to a channel
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, userToRegister);
        if (!userToRegister.equals(requestingUser)) {
            throw new BadRequestAlertException("Only self registration is possible to a channel", "channel", "onlySelfRegistration");
        }

        channelService.registerStudent(userToRegister, channelFromDatabase);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{courseId}/channels/{channelId}/deregister/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deregisterStudent(@PathVariable Long courseId, @PathVariable Long channelId, @PathVariable String studentLogin) {
        log.debug("REST request to deregister {} student from channel : {}", studentLogin, channelId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var channelFromDatabase = this.channelService.getChannelElseThrow(channelId);
        var userToRegister = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        checkEntityIdMatchesPathIds(channelFromDatabase, Optional.of(courseId), Optional.of(channelId));

        // ToDo: Allow users others user to register someone to a channel
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, userToRegister);
        if (!userToRegister.equals(requestingUser)) {
            throw new BadRequestAlertException("Only self deregistrations is possible from a channel", "channel", "onlySelfDeregistration");
        }

        channelService.deregisterStudent(userToRegister, channelFromDatabase);
        return ResponseEntity.noContent().build();
    }

    private void checkEntityIdMatchesPathIds(Conversation channel, Optional<Long> courseId, Optional<Long> channelId) {
        courseId.ifPresent(courseIdValue -> {
            if (!channel.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the channel", "channel", "courseIdMismatch");
            }
        });
        channelId.ifPresent(channelIdValue -> {
            if (!channel.getId().equals(channelIdValue)) {
                throw new BadRequestAlertException("The channelId in the path does not match the channelId in the channel", "channel", "channelIdMismatch");
            }
        });
    }

}
