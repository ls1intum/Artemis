package de.tum.in.www1.metis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.metis.domain.AnswerPost;
import de.tum.in.www1.metis.domain.Post;
import de.tum.in.www1.metis.domain.RootPost;
import de.tum.in.www1.metis.repository.AnswerPostRepository;
import de.tum.in.www1.metis.repository.RootPostRepository;

/**
 * REST controller for managing Posts in METIS.
 */
@RestController
@RequestMapping("/api")
public class PostResource {

    private final Logger log = LoggerFactory.getLogger(PostResource.class);

    private static final String ENTITY_NAME = "post";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final RootPostRepository rootPostRepository;

    private final AnswerPostRepository answerPostRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationService singleUserNotificationService;

    public PostResource(RootPostRepository rootPostRepository, AnswerPostRepository answerPostRepository, GroupNotificationService groupNotificationService,
            SingleUserNotificationService singleUserNotificationService, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            CourseRepository courseRepository) {
        this.rootPostRepository = rootPostRepository;
        this.answerPostRepository = answerPostRepository;
        this.courseRepository = courseRepository;
        this.groupNotificationService = groupNotificationService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * POST /courses/{courseId}/metis/post : Create a new metis post.
     *
     * @param courseId the id of the course the answer belongs to
     * @param post the post to create
     * @return the ResponseEntity with status 201 (Created) and with body the new post, or with status 400 (Bad Request) if the post has already
     *         an ID or there are inconsistencies within the data
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/metis/post")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> createPost(@PathVariable Long courseId, @RequestBody Post post) throws URISyntaxException {
        log.debug("REST request to save Post : {}", post);
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new post cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (!post.getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId does not match the courseId of post in request body.");
        }
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        // set author to current user
        post.setAuthor(user);
        if (post instanceof RootPost) {
            post = rootPostRepository.save((RootPost) post);
        }
        else if (post instanceof AnswerPost) {
            AnswerPost answerPost = (AnswerPost) post;
            Long rootPostId = answerPost.getRootPost().getId();
            RootPost rootPost = rootPostRepository.findById(rootPostId).orElseThrow(() -> new EntityNotFoundException("", rootPostId));
            rootPost.addAnswer(answerPost);
            // answer is automatically approved if written by an instructor
            answerPost.setTutorApproved(this.authorizationCheckService.isAtLeastInstructorInCourse(course, user));
            rootPostRepository.save(rootPost);
            post = answerPostRepository.save(answerPost);
        }
        else {
            // TODO: throw appropriate error here
            throw new BadRequestAlertException("A new post cannot already have an ID", ENTITY_NAME, "idexists");
        }

        // TODO: send notifications

        return ResponseEntity.created(new URI("/api/courses" + courseId + "/metis/post" + post.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, post.getId().toString())).body(post);
    }
}
