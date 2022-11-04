package de.tum.in.www1.artemis.web.rest.metis.conversation;

import java.net.URI;
import java.net.URISyntaxException;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.GroupChatService;

@RestController
@RequestMapping("/api/courses")
public class GroupChatResource {

    private final Logger log = LoggerFactory.getLogger(GroupChatResource.class);

    private final GroupChatService groupChatService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public GroupChatResource(GroupChatService groupChatService, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.groupChatService = groupChatService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    @PostMapping("/{courseId}/groupchats")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<GroupChat> createGroupChat(@PathVariable Long courseId, @Valid @RequestBody GroupChat groupChat) throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.isAtLeastStudentInCourse(course, null);
        var createdGroupChat = groupChatService.createNewGroupChat(course, groupChat);
        return ResponseEntity.created(new URI("/api/group-chats/" + createdGroupChat.getId())).body(createdGroupChat);
    }

}
