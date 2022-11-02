package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.ChannelService;
import de.tum.in.www1.artemis.service.metis.ConversationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Conversation.
 */
@RestController
@RequestMapping("/api/courses")
public class ConversationResource {

    private final Logger log = LoggerFactory.getLogger(ConversationResource.class);

    private final ConversationService conversationService;

    private final ChannelService channelService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public ConversationResource(ConversationService conversationService, ChannelService channelService, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, CourseRepository courseRepository) {
        this.conversationService = conversationService;
        this.channelService = channelService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * GET /courses/{courseId}/conversations : get all conversations for user within course by courseId
     *
     * @param courseId the courseId which the searched conversations belong to
     * @return the ResponseEntity with status 200 (OK) and with body
     */
    @GetMapping("/{courseId}/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Conversation>> getConversationsOfUser(@PathVariable Long courseId) {
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), null);

        List<Conversation> conversations = conversationService.getConversationsOfUser(courseId);
        return new ResponseEntity<>(conversations, null, HttpStatus.OK);
    }

    /**
     * POST /courses/{courseId}/conversations : create a new conversation
     *
     * @param courseId        course to associate the new conversation
     * @param conversation    conversation to create
     * @return ResponseEntity with status 201 (Created) containing the created conversation in the response body,
     * or with status 400 (Bad Request) if the checks on user or course validity fail
     */
    @PostMapping("/{courseId}/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Conversation> createConversation(@PathVariable Long courseId, @Valid @RequestBody Conversation conversation) throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);
        if (conversation.isChannel()) { // Only instructors can create channels
            channelService.isAllowedToCreateChannelElseThrow(course, null);
            var createdChannel = channelService.createChannel(courseId, conversation);
            return ResponseEntity.created(new URI("/api/channels/" + createdChannel.getId())).body(createdChannel);
        }
        else { // Everybody can create a direct conversation
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
            var createdConversation = conversationService.createDirectConversation(courseId, conversation);
            return ResponseEntity.created(new URI("/api/conversations/" + createdConversation.getId())).body(createdConversation);
        }
    }

    @GetMapping("/{courseId}/conversations/{conversationId}/members/search")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<User>> searchMembersOfConversation(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestParam("loginOrName") String loginOrName,
            Pageable pageable) {
        log.debug("REST request to get members of conversation : {} with login or name : {} in course: {}", conversationId, loginOrName, courseId);
        if (pageable.getPageSize() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The page size must not be greater than 20");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        var conversationFromDatabase = this.conversationService.getConversationById(conversationId);
        checkEntityIdMatchesPathIds(conversationFromDatabase, Optional.of(courseId), Optional.of(conversationId));
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var isMember = conversationService.isMember(conversationId, requestingUser.getId());
        if (!isMember) {
            throw new AccessForbiddenException("Only members of a conversation can search the members of a conversation.");
        }
        var searchTerm = loginOrName != null ? loginOrName.toLowerCase().trim() : "";
        var page = conversationService.searchMembersOfConversation(pageable, conversationId, searchTerm);
        var headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @PostMapping("/{courseId}/conversations/{conversationId}/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> registerUsers(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestBody List<String> userLogins) {
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", "conversation", "userLoginsEmpty");
        }
        // ToDo: maybe limit how many users can be registered at once?

        log.debug("REST request to register {} users to conversation : {}", userLogins.size(), conversationId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var conversationFromDatabase = this.conversationService.getConversationByIdWithConversationParticipants(conversationId);
        checkEntityIdMatchesPathIds(conversationFromDatabase, Optional.of(courseId), Optional.of(conversationId));

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var isSelfRegistration = userLogins.size() == 1 && userLogins.get(0).equals(requestingUser.getLogin());
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);

        // ToDo: Discuss who should be able to register students to a channel / conversation

        if (conversationFromDatabase.isChannel()) {
            // PUBLIC CHANNELS -> Self Registration or Instructor Registration
            if (Boolean.TRUE.equals(conversationFromDatabase.getIsPublic())) {
                if (!isSelfRegistration) {
                    authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, requestingUser);
                }
            }
            else { // PRIVATE CHANNELS -> Only Instructor Registration
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, requestingUser);
            }
        }
        else {
            // Only members of a direct conversation can register other users
            var isMemberOfConversation = conversationFromDatabase.getConversationParticipants().stream().anyMatch(user -> user.getId().equals(requestingUser.getId()));
            if (isMemberOfConversation) {
                throw new AccessForbiddenException("User is not a member of the conversation");
            }
        }

        Set<User> usersToRegister = new HashSet<>();
        for (String userLogin : userLogins) {
            if (userLogin == null || userLogin.isEmpty()) {
                continue;
            }
            var userToRegister = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
            userToRegister.ifPresent(usersToRegister::add);
        }

        conversationService.registerUsers(usersToRegister, conversationFromDatabase);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/conversations/{conversationId}/deregister")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deregisterUsers(@PathVariable Long courseId, @PathVariable Long conversationId, @RequestBody List<String> userLogins) {
        if (userLogins == null || userLogins.isEmpty()) {
            throw new BadRequestAlertException("No user logins provided", "conversation", "userLoginsEmpty");
        }
        // ToDo: maybe limit how many users can be deregistered at once?
        log.debug("REST request to deregister {} users from the conversation : {}", userLogins.size(), conversationId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var conversationFromDatabase = this.conversationService.getConversationByIdWithConversationParticipants(conversationId);
        checkEntityIdMatchesPathIds(conversationFromDatabase, Optional.of(courseId), Optional.of(conversationId));

        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var isSelfDeRegistration = userLogins.size() == 1 && userLogins.get(0).equals(requestingUser.getLogin());
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);

        // ToDo: Discuss who should be able to deregister students to a channel / conversation

        // Self Deregistration is always allowed
        if (!isSelfDeRegistration) {
            // only instructors can deregister other users from a channel
            if (conversationFromDatabase.isChannel()) {
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, requestingUser);
            }
            else {
                throw new AccessForbiddenException("You can not deregister other users from a direct conversation");
            }

        }

        Set<User> usersToDeregister = new HashSet<>();
        for (String userLogin : userLogins) {
            if (userLogin == null || userLogin.isEmpty()) {
                continue;
            }
            var userToDeregister = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
            userToDeregister.ifPresent(usersToDeregister::add);
        }

        conversationService.deregisterUsers(usersToDeregister, conversationFromDatabase);
        return ResponseEntity.noContent().build();
    }

    private void checkEntityIdMatchesPathIds(Conversation conversation, Optional<Long> courseId, Optional<Long> conversationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!conversation.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the conversation", "conversation", "courseIdMismatch");
            }
        });
        conversationId.ifPresent(conversationIdValue -> {
            if (!conversation.getId().equals(conversationIdValue)) {
                throw new BadRequestAlertException("The conversationId in the path does not match the channelId in the conversation", "conversation", "channelIdMismatch");
            }
        });
    }
}
