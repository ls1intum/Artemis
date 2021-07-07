package de.tum.in.www1.artemis.web.rest.metis;

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
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing AnswerPost.
 */
@RestController
@RequestMapping("/api")
public class AnswerPostResource {

    private final Logger log = LoggerFactory.getLogger(AnswerPostResource.class);

    private static final String ENTITY_NAME = "metis.answerPost";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AnswerPostRepository answerPostRepository;

    private final CourseRepository courseRepository;

    private final PostRepository postRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationService singleUserNotificationService;

    public AnswerPostResource(AnswerPostRepository answerPostRepository, GroupNotificationService groupNotificationService,
            SingleUserNotificationService singleUserNotificationService, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            CourseRepository courseRepository, PostRepository postRepository) {
        this.answerPostRepository = answerPostRepository;
        this.courseRepository = courseRepository;
        this.postRepository = postRepository;
        this.groupNotificationService = groupNotificationService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * POST /courses/{courseId}/answer-posts : Create a new answerPost.
     *
     * @param courseId the id of the course the answer post belongs to
     * @param answerPost the answerPost to create
     * @return the ResponseEntity with status 201 (Created) and with body the new answerPost, or with status 400 (Bad Request) if the answerPost has already
     *         an ID or there are inconsistencies within the data
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/answer-posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AnswerPost> createAnswerPost(@PathVariable Long courseId, @RequestBody AnswerPost answerPost) throws URISyntaxException {
        log.debug("REST request to save AnswerPost : {}", answerPost);
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (answerPost.getId() != null) {
            throw new BadRequestAlertException("A new answerPost cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        if (!answerPost.getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesn't match courseId of the AnswerPost in the body that should be added");
        }
        // answer post is automatically approved if written by an instructor
        answerPost.setTutorApproved(this.authorizationCheckService.isAtLeastInstructorInCourse(course, user));
        // use post from database rather than user input
        Post post = postRepository.findByIdElseThrow(answerPost.getPost().getId());
        answerPost.setPost(post);
        // set author to current user
        answerPost.setAuthor(user);
        AnswerPost result = answerPostRepository.save(answerPost);
        if (result.getPost().getExercise() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise(result);
            singleUserNotificationService.notifyUserAboutNewAnswerForExercise(result);

            // Protect Sample Solution, Grading Instructions, etc.
            result.getPost().getExercise().filterSensitiveInformation();
        }
        if (result.getPost().getLecture() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(result);
            singleUserNotificationService.notifyUserAboutNewAnswerForLecture(result);
        }
        return ResponseEntity.created(new URI("/api/courses" + courseId + "/answer-posts/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /courses/{courseId}/answer-posts : Updates an existing answerPost.
     *
     * @param courseId the id of the course the answer post belongs to
     * @param answerPost the answerPost to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated answerPost, or with status 400 (Bad Request) if the answerPost is not valid,
     *         or with status 500 (Internal Server Error) if the answerPost couldn't be updated
     */
    @PutMapping("courses/{courseId}/answer-posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AnswerPost> updateAnswerPost(@PathVariable Long courseId, @RequestBody AnswerPost answerPost) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to update AnswerPost : {}", answerPost);
        if (answerPost.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var existingAnswerPost = answerPostRepository.findByIdElseThrow(answerPost.getId());
        if (!existingAnswerPost.getPost().getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesn't match courseId of the AnswerPost in the body");
        }
        mayUpdateOrDeleteAnswerPostElseThrow(existingAnswerPost, user);
        // allow overwriting of values only for depicted fields: answerText, verified, tutorApproved
        existingAnswerPost.setContent(answerPost.getContent());
        // tutor approval can only be toggled by a tutor
        if (this.authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            existingAnswerPost.setTutorApproved(answerPost.isTutorApproved());
        }
        AnswerPost result = answerPostRepository.save(existingAnswerPost);

        if (result.getPost().getExercise() != null) {
            // Protect Sample Solution, Grading Instructions, etc.
            result.getPost().getExercise().filterSensitiveInformation();
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, answerPost.getId().toString())).body(result);
    }

    /**
     * DELETE /courses/{courseId}/answer-posts/:id : delete the "id" answerPost.
     *
     * @param courseId the id of the course the answer post belongs to
     * @param answerPostId the id of the answerPost to delete
     * @return the ResponseEntity with status 200 (OK) or 400 (Bad Request) if theres inconsistencies within the data
     */
    @DeleteMapping("courses/{courseId}/answer-posts/{answerPostId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteAnswerPost(@PathVariable Long courseId, @PathVariable Long answerPostId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var existingAnswerPost = answerPostRepository.findByIdElseThrow(answerPostId);
        courseRepository.findByIdElseThrow(courseId);
        Course course = existingAnswerPost.getPost().getCourse();
        String entity = "";
        if (existingAnswerPost.getPost().getLecture() != null) {
            entity = "lecture with id: " + existingAnswerPost.getPost().getLecture().getId();
        }
        else if (existingAnswerPost.getPost().getExercise() != null) {
            entity = "exercise with id: " + existingAnswerPost.getPost().getExercise().getId();
        }
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!course.getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesnt match courseId of the AnswerPost that should be deleted");
        }
        mayUpdateOrDeleteAnswerPostElseThrow(existingAnswerPost, user);
        log.info("AnswerPost deleted by " + user.getLogin() + ". Answer: " + existingAnswerPost.getContent() + " for " + entity);
        answerPostRepository.deleteById(answerPostId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, answerPostId.toString())).build();
    }

    /**
     * Check if user can update or delete AnswerPost, if not throws an AccessForbiddenException
     *
     * @param answerPost answerPost for which to check
     * @param user user for which to check
     */
    private void mayUpdateOrDeleteAnswerPostElseThrow(AnswerPost answerPost, User user) {
        Course course = answerPost.getPost().getCourse();
        if (!user.getId().equals(answerPost.getAuthor().getId())) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }
}
