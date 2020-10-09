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

    @GetMapping("/courses/{courseId}/goals")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<LearningGoal>> getLearningGoalsByCourseId(@PathVariable Long courseId) {
        log.debug("REST request to get all learning goals for the course with id : {}", courseId);
        return ResponseEntity.ok().body(learningGoalService.findAllByCourseId(courseId));
    }

    @GetMapping("/goals/{goalId}")
    public ResponseEntity<LearningGoal> getLearningGoalById(@PathVariable Long goalId) {
        log.debug("REST request to get the learning goal with the id : {}", goalId);
        Optional<LearningGoal> learningGoal = learningGoalService.findById(goalId);
        if (learningGoal.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(learningGoal.get());
    }

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
