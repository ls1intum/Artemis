package de.tum.in.www1.artemis.web.rest.metis.conversation;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelAuthorizationService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService.ConversationMemberSearchFilters;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationUserDTO;
import tech.jhipster.web.util.PaginationUtil;

@RestController
@RequestMapping("/api/courses")
public class ConversationResource {

    private final Logger log = LoggerFactory.getLogger(ConversationResource.class);

    private final ConversationService conversationService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public ConversationResource(ConversationService conversationService, ChannelAuthorizationService channelAuthorizationService,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository) {
        this.conversationService = conversationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    @GetMapping("/{courseId}/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ConversationDTO>> getConversationsOfUser(@PathVariable Long courseId) {
        var requestingUser = this.userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), requestingUser);
        var conversations = conversationService.getConversationsOfUser(courseId, requestingUser);
        return ResponseEntity.ok(new ArrayList<>(conversations));
    }

    @GetMapping("/{courseId}/conversations/{conversationId}/members/search")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ConversationUserDTO>> searchMembersOfConversation(@PathVariable Long courseId, @PathVariable Long conversationId,
            @RequestParam("loginOrName") String loginOrName, @RequestParam(value = "filter", required = false) ConversationMemberSearchFilters filter, Pageable pageable) {
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
        var originalPage = conversationService.searchMembersOfConversation(course, conversationFromDatabase, pageable, searchTerm, Optional.ofNullable(filter));

        var resultDTO = new ArrayList<ConversationUserDTO>();
        for (var user : originalPage) {
            var dto = new ConversationUserDTO(user);
            UserPublicInfoDTO.assignRoleProperties(course, user, dto);
            if (conversationFromDatabase instanceof Channel channel) {
                dto.setIsChannelAdmin(channelAuthorizationService.isChannelAdmin(channel.getId(), user.getId()));
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
                throw new BadRequestAlertException("The conversationId in the path does not match the channelId in the conversation", "conversation", "channelIdMismatch");
            }
        });
    }
}
