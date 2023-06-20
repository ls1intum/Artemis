package de.tum.in.www1.artemis.web.rest.metis.conversation;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService.ConversationMemberSearchFilters;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationUserDTO;
import tech.jhipster.web.util.PaginationUtil;

@RestController
@RequestMapping("/api/courses")
public class ConversationResource extends ConversationManagementResource {

    private final Logger log = LoggerFactory.getLogger(ConversationResource.class);

    private final ConversationService conversationService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    public ConversationResource(ConversationService conversationService, ChannelAuthorizationService channelAuthorizationService,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository) {
        super(courseRepository);
        this.conversationService = conversationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/courses/:courseId/conversations: Returns all conversations of a course where the requesting user is a member
     *
     * @param courseId the id of the course
     * @return ResponseEntity with status 200 (OK) and with body containing the list of conversations where the requesting user is a member
     */
    @GetMapping("/{courseId}/conversations")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ConversationDTO>> getConversationsOfUser(@PathVariable Long courseId) {
        checkMessagingEnabledElseThrow(courseId);

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        var conversations = conversationService.getConversationsOfUser(courseId, requestingUser);
        return ResponseEntity.ok(new ArrayList<>(conversations));
    }

    /**
     * POST /api/courses/:courseId/conversations/:conversationId/favorite : Updates the favorite status of a conversation for the requesting user
     *
     * @param courseId       the id of the course
     * @param conversationId the id of the conversation
     * @param isFavorite     the new favorite status
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/conversations/{conversationId}/favorite")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> changeFavoriteStatus(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestParam Boolean isFavorite) {
        checkMessagingEnabledElseThrow(courseId);
        var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        conversationService.switchFavoriteStatus(conversationId, requestingUser, isFavorite);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/conversations/:conversationId/hidden : Updates the hidden status of a conversation for the requesting user
     *
     * @param courseId       the id of the course
     * @param conversationId the id of the conversation
     * @param isHidden       the new hidden status
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("/{courseId}/conversations/{conversationId}/hidden")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> switchHiddenStatus(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestParam Boolean isHidden) {
        checkMessagingEnabledElseThrow(courseId);
        var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        conversationService.switchHiddenStatus(conversationId, requestingUser, isHidden);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/courses/:courseId/unread-messages : Checks for unread messages of the current user
     *
     * @param courseId the id of the course
     * @return ResponseEntity with status 200 (Ok) and the information if the user has unread messages
     */
    @GetMapping("/{courseId}/unread-messages")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> hasUnreadMessages(@PathVariable Long courseId) {
        checkMessagingEnabledElseThrow(courseId);

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        return ResponseEntity.ok(conversationService.userHasUnreadMessages(courseId, requestingUser));
    }

    /**
     * GET /api/courses/:courseId/conversations/:conversationId/members/search: Searches for members of a conversation
     *
     * @param courseId       the id of the course
     * @param conversationId the id of the conversation
     * @param loginOrName    the search term to search login and names by
     * @param filter         an additional role filter to only search for users of a specific role
     * @param pageable       containing the pageable information
     * @return ResponseEntity with status 200 (OK) and with body containing the list of found members matching the criteria
     */
    @GetMapping("/{courseId}/conversations/{conversationId}/members/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ConversationUserDTO>> searchMembersOfConversation(@PathVariable Long courseId, @PathVariable Long conversationId,
            @RequestParam("loginOrName") String loginOrName, @RequestParam(value = "filter", required = false) ConversationMemberSearchFilters filter, Pageable pageable) {
        log.debug("REST request to get members of conversation : {} with login or name : {} in course: {}", conversationId, loginOrName, courseId);
        if (pageable.getPageSize() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The page size must not be greater than 20");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        checkMessagingEnabledElseThrow(course);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        var conversationFromDatabase = this.conversationService.getConversationById(conversationId);
        checkEntityIdMatchesPathIds(conversationFromDatabase, Optional.of(courseId), Optional.of(conversationId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var isMember = conversationService.isMember(conversationId, requestingUser.getId());
        if (!isMember) {
            var atLeastInstructorInCourse = authorizationCheckService.isAtLeastInstructorInCourse(course, requestingUser);
            if (!atLeastInstructorInCourse) {
                throw new AccessForbiddenException("Only members of a conversation or instructors can search the members of a conversation.");
            }
        }
        var searchTerm = loginOrName != null ? loginOrName.toLowerCase().trim() : "";
        var originalPage = conversationService.searchMembersOfConversation(course, conversationFromDatabase, pageable, searchTerm, Optional.ofNullable(filter));

        var resultDTO = new ArrayList<ConversationUserDTO>();
        for (var user : originalPage) {
            var dto = new ConversationUserDTO(user);
            UserPublicInfoDTO.assignRoleProperties(course, user, dto);
            if (conversationFromDatabase instanceof Channel channel) {
                dto.setIsChannelModerator(channelAuthorizationService.isChannelModerator(channel.getId(), user.getId()));
            }
            resultDTO.add(dto);
        }
        var dtoPage = new PageImpl<>(resultDTO, originalPage.getPageable(), originalPage.getTotalElements());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), dtoPage);
        return new ResponseEntity<>(dtoPage.getContent(), headers, HttpStatus.OK);
    }

    private void checkEntityIdMatchesPathIds(Conversation conversation, Optional<Long> courseId, Optional<Long> conversationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!conversation.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the conversation", "conversation", "courseIdMismatch");
            }
        });
        conversationId.ifPresent(conversationIdValue -> {
            if (!conversation.getId().equals(conversationIdValue)) {
                throw new BadRequestAlertException("The conversationId in the path does not match the conversationId in the conversation", "conversation",
                        "conversationIdMismatch");
            }
        });
    }

    /**
     * GET api/courses/conversations-for-notifications : Get all conversations for which the current user should receive notifications
     *
     * @return the list of Conversations for which the current user should receive notifications about
     */
    @GetMapping("/conversations-for-notifications")
    @EnforceAtLeastStudent
    public List<Conversation> getAllConversationsForNotifications() {
        log.debug("REST request to get all tutorial groups for which the current user should receive notifications");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return conversationService.findAllConversationsForNotifications(user, false);
    }
}
