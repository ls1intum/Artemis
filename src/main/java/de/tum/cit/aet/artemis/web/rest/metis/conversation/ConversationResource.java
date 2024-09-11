package de.tum.cit.aet.artemis.web.rest.metis.conversation;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.domain.metis.conversation.Conversation;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ConductAgreementService;
import de.tum.cit.aet.artemis.service.dto.ResponsibleUserDTO;
import de.tum.cit.aet.artemis.service.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.service.metis.conversation.ConversationService;
import de.tum.cit.aet.artemis.service.metis.conversation.ConversationService.ConversationMemberSearchFilters;
import de.tum.cit.aet.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.cit.aet.artemis.web.rest.metis.conversation.dtos.ConversationUserDTO;
import tech.jhipster.web.util.PaginationUtil;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/courses/")
public class ConversationResource extends ConversationManagementResource {

    private static final Logger log = LoggerFactory.getLogger(ConversationResource.class);

    private final ConversationService conversationService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final ConductAgreementService conductAgreementService;

    public ConversationResource(ConversationService conversationService, ChannelAuthorizationService channelAuthorizationService,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository,
            ConductAgreementService conductAgreementService) {
        super(courseRepository);
        this.conversationService = conversationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.conductAgreementService = conductAgreementService;
    }

    /**
     * GET /api/courses/:courseId/conversations: Returns all conversations of a course where the requesting user is a member
     *
     * @param courseId the id of the course
     * @return ResponseEntity with status 200 (OK) and with body containing the list of conversations where the requesting user is a member
     */
    @GetMapping("{courseId}/conversations")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ConversationDTO>> getConversationsOfUser(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        var conversations = conversationService.getConversationsOfUser(course, requestingUser);
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
    @PostMapping("{courseId}/conversations/{conversationId}/favorite")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> updateIsFavorite(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestParam boolean isFavorite) {
        checkCommunicationEnabledElseThrow(courseId);
        var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        conversationService.setIsFavorite(conversationId, requestingUser, isFavorite);
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
    @PostMapping("{courseId}/conversations/{conversationId}/hidden")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> updateIsHidden(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestParam boolean isHidden) {
        checkCommunicationEnabledElseThrow(courseId);
        var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        conversationService.setIsHidden(conversationId, requestingUser, isHidden);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/courses/:courseId/conversations/:conversationId/muted : Updates a conversation's muted status for the requesting user
     *
     * @param courseId       the id of the course
     * @param conversationId the id of the conversation
     * @param isMuted        the new muted status
     * @return ResponseEntity with status 200 (Ok)
     */
    @PostMapping("{courseId}/conversations/{conversationId}/muted")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> updateIsMuted(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestParam boolean isMuted) {
        checkCommunicationEnabledElseThrow(courseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        conversationService.setIsMuted(conversationId, requestingUser, isMuted);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/courses/:courseId/unread-messages : Checks for unread messages of the current user
     *
     * @param courseId the id of the course
     * @return ResponseEntity with status 200 (Ok) and the information if the user has unread messages
     */
    @GetMapping("{courseId}/unread-messages")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> hasUnreadMessages(@PathVariable Long courseId) {
        checkCommunicationEnabledElseThrow(courseId);

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        return ResponseEntity.ok(conversationService.userHasUnreadMessages(courseId, requestingUser));
    }

    /**
     * PATCH /api/courses/:courseId/conversations/:conversationId/mark-as-read : Marks all messages as read for the requesting user in the given conversation
     *
     * @param courseId       the id of the course
     * @param conversationId the id of the conversation
     * @return ResponseEntity with status 200 (Ok)
     */
    @PatchMapping("{courseId}/conversations/{conversationId}/mark-as-read")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> markAsRead(@PathVariable Long courseId, @PathVariable Long conversationId) {
        checkCommunicationEnabledElseThrow(courseId);

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);

        conversationService.markAsRead(conversationId, requestingUser.getId());

        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/courses/:courseId/code-of-conduct/agreement : Checks if the user agrees to the code of conduct
     *
     * @param courseId the course's ID
     * @return ResponseEntity with status 200 (Ok) and body is true if the user agreed to the course's code of conduct
     */
    @GetMapping("{courseId}/code-of-conduct/agreement")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> isCodeOfConductAccepted(@PathVariable Long courseId) {
        checkCommunicationEnabledElseThrow(courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        return ResponseEntity.ok(conductAgreementService.fetchUserAgreesToCodeOfConductInCourse(requestingUser, course));
    }

    /**
     * PATCH /api/courses/:courseId/code-of-conduct/agreement : Accept the course's code of conduct
     *
     * @param courseId the course's ID
     * @return ResponseEntity with status 200 (Ok)
     */
    @PatchMapping("{courseId}/code-of-conduct/agreement")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> acceptCodeOfConduct(@PathVariable Long courseId) {
        checkCommunicationEnabledElseThrow(courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);
        conductAgreementService.setUserAgreesToCodeOfConductInCourse(requestingUser, course);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/courses/:courseId/code-of-conduct/responsible-users : Users responsible for the course
     *
     * @param courseId the course's ID
     * @return ResponseEntity with the status 200 (Ok) and a list of users responsible for the course
     */
    @GetMapping("{courseId}/code-of-conduct/responsible-users")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ResponsibleUserDTO>> getResponsibleUsersForCodeOfConduct(@PathVariable Long courseId) {
        checkCommunicationEnabledElseThrow(courseId);

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);

        var responsibleUsers = userRepository.searchAllWithGroupsByLoginOrNameInGroups(Pageable.unpaged(), "", Set.of(course.getInstructorGroupName()))
                .map((user) -> new ResponsibleUserDTO(user.getName(), user.getEmail())).toList();

        return ResponseEntity.ok(responsibleUsers);
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
    @GetMapping("{courseId}/conversations/{conversationId}/members/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ConversationUserDTO>> searchMembersOfConversation(@PathVariable Long courseId, @PathVariable Long conversationId,
            @RequestParam("loginOrName") String loginOrName, @RequestParam(value = "filter", required = false) ConversationMemberSearchFilters filter, Pageable pageable) {
        log.debug("REST request to get members of conversation : {} with login or name : {} in course: {}", conversationId, loginOrName, courseId);
        if (pageable.getPageSize() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The page size must not be greater than 20");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        checkCommunicationEnabledElseThrow(course);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        var conversationFromDatabase = this.conversationService.getConversationById(conversationId);
        checkEntityIdMatchesPathIds(conversationFromDatabase, Optional.of(courseId), Optional.of(conversationId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var isAllowedToSearchForMembers = (conversationFromDatabase instanceof Channel channel && channel.getIsCourseWide())
                || conversationService.isMember(conversationId, requestingUser.getId());
        if (!isAllowedToSearchForMembers) {
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
}
