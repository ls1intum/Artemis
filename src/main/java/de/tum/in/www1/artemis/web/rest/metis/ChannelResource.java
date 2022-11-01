package de.tum.in.www1.artemis.web.rest.metis;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.ChannelService;
import de.tum.in.www1.artemis.service.metis.ChannelService.ChannelOverviewDTO;

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
            var channelDescription = "";
            if (channel.getDescription() != null) {
                channelDescription = channel.getDescription();
            }
            return new ChannelOverviewDTO(channel.getId(), channelName, channelDescription, channel.getIsPublic(), isMember, noOfMembers);
        }).toList();
        return new ResponseEntity<>(result, null, HttpStatus.OK);
    }

}
