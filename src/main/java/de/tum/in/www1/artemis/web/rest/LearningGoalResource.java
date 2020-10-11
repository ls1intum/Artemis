package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.conflict;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningGoalService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

// **
// * REST controller for managing learning goals
// *
@RestController
@RequestMapping("/api")
public class LearningGoalResource {

    private static final String ENTITY_NAME = "learningGoal";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(LearningGoalResource.class);

    private final LearningGoalService learningGoalService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserService userService;

    public LearningGoalResource(LearningGoalService learningGoalService, AuthorizationCheckService authorizationCheckService, UserService userService) {
        this.learningGoalService = learningGoalService;
        this.authorizationCheckService = authorizationCheckService;
        this.userService = userService;
    }

    /**
     * GET /courses/:courseId/goals : get all the learning goals associated with a course
     *
     * @param courseId the courseId of the course for which all learning goals should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of learning goals in body
     */
    @GetMapping("/courses/{courseId}/goals")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<LearningGoal>> getLearningGoalsByCourseId(@PathVariable Long courseId) {
        log.debug("REST request to get all learning goals for the course with id : {}", courseId);
        return ResponseEntity.ok().body(learningGoalService.findAllByCourseId(courseId));
    }

    /**
     * GET /goals/:goalId : gets the learning goal with the id of :goalId.
     *
     * @param goalId the id of the learning goal to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the learning goal, or with status 404 (Not Found)
     */
    @GetMapping("/goals/{goalId}")
    public ResponseEntity<LearningGoal> getLearningGoalById(@PathVariable Long goalId) {
        log.debug("REST request to get the learning goal with the id : {}", goalId);
        Optional<LearningGoal> learningGoal = learningGoalService.findById(goalId);
        if (learningGoal.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(learningGoal.get());
    }

    /**
     * DELETE /goals/:goalid : deletes the learning goal with the id of :goalId.
     *
     * @param goalId the id of the learning goal to delete
     * @return the ResponseEntity with status 200 (OK)
     * or with status 403 (Forbidden) if the user is not an administrator or instructor
     * or with status 404 (Not Found) if the learning goal with the id can not be found
     * or with status 400 (Bad Request) if the learning goal is not associated with a course
     */
    @DeleteMapping("/goals/{goalId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteLearningGoal(@PathVariable Long goalId) {
        log.debug("REST request to delete the learning goal with the id : {}", goalId);
        Optional<LearningGoal> learningGoalOptional = learningGoalService.findById(goalId);
        if (learningGoalOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        LearningGoal learningGoal = learningGoalOptional.get();

        Course course = learningGoal.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        learningGoalService.delete(learningGoal);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, goalId.toString())).build();

    }

    /**
     * POST /goals : Create a new learning goal.
     *
     * @param learningGoal the learning goal to create
     * @return the ResponseEntity with status 201 (Created) and with body the new learning goal
     * or with status 400 (Bad Request) if the learning goal has already an ID
     * or with status 409 (Conflict) if the learning goal is not associated with a course
     * or with status 403 (Forbidden) if the user is not an administrator or instructor
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<LearningGoal> createLearningGoal(@Valid @RequestBody LearningGoal learningGoal) throws URISyntaxException {
        log.debug("REST request to create a learning goal: {}", learningGoal);
        if (learningGoal.getId() != null) {
            throw new BadRequestAlertException("A new exam can not already have an ID", ENTITY_NAME, "idexists");
        }

        if (learningGoal.getCourse() == null) {
            return conflict();
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(learningGoal.getCourse(), user)) {
            return forbidden();
        }

        LearningGoal persistedLearningGoal = learningGoalService.save(learningGoal);
        return ResponseEntity.created(new URI("/api/goals/" + persistedLearningGoal.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, persistedLearningGoal.getTitle())).body(persistedLearningGoal);
    }

    /**
     * PUT /goals : Updates an existing learning goal
     *
     * @param learningGoal the learning to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated learning goal,
     * or with status 400 (Bad Request) if the learning goal has no ID
     * or with status 409 (Conflict) if the learning goal is not associated with a course
     * or with status 403 (Forbidden) if the user is not an administrator or instructor
     */
    @PutMapping("/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<LearningGoal> updateLearningGoal(@Valid @RequestBody LearningGoal learningGoal) {
        log.debug("REST request to update learning goal: {}", learningGoal);
        if (learningGoal.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (learningGoal.getCourse() == null) {
            return conflict();
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(learningGoal.getCourse(), user)) {
            return forbidden();
        }
        LearningGoal updatedLearningGoal = learningGoalService.save(learningGoal);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedLearningGoal.getId().toString()))
                .body(updatedLearningGoal);

    }

}
