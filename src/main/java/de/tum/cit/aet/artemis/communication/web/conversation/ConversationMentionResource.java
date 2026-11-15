package de.tum.cit.aet.artemis.communication.web.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@FeatureUsage(UserFeature.MESSAGING)
@RequestMapping("api/communication/courses/")
public class ConversationMentionResource extends ConversationManagementResource {

    private final ConversationService conversationService;

    private final ConversationParticipantRepository participantRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ConversationMentionResource(CourseRepository courseRepository, ConversationService conversationService, ConversationParticipantRepository participantRepository,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        super(courseRepository);
        this.conversationService = conversationService;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Suggests conversation members for a mention, ordered by their most recent message or reply.
     *
     * @param courseId       the owning course
     * @param conversationId the current conversation
     * @param loginOrName    the optional search term
     * @return up to ten eligible recipients, with only their display name and login
     */
    @GetMapping("{courseId}/conversations/{conversationId}/members/mentions")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserNameAndLoginDTO>> suggestMentionRecipients(@PathVariable long courseId, @PathVariable long conversationId,
            @RequestParam(defaultValue = "") String loginOrName) {
        var course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);
        var user = userRepository.getUserWithAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        var conversation = conversationService.getConversationById(conversationId);
        if (!conversation.getCourse().getId().equals(courseId)) {
            throw new AccessForbiddenException("The conversation does not belong to the requested course.");
        }
        conversationService.checkConversationVisibilityElseThrow(conversation, user);
        boolean courseWide = conversation instanceof Channel channel && channel.getIsCourseWide();
        if (!courseWide && !conversationService.isMember(conversationId, user.getId()) && !authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("Only conversation members or instructors may search mention recipients.");
        }
        var searchTerm = loginOrName.toLowerCase(Locale.ROOT).trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var users = participantRepository.findMentionCandidates(conversationId, courseId, courseWide, searchTerm, Instant.EPOCH.atZone(ZoneOffset.UTC), Pageable.ofSize(10));
        return ResponseEntity.ok(users.stream()
                .map(candidate -> new UserNameAndLoginDTO(User.displayName(candidate.firstName(), candidate.lastName(), candidate.login()), candidate.login())).toList());
    }
}
