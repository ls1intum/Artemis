package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.conflict;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;

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

    @PostMapping("/courses/{courseId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<LearningGoal> createLearningGoal(@PathVariable Long courseId, @Valid @RequestBody LearningGoal learningGoal) throws URISyntaxException {
        log.debug("REST request to create a learning goal: {}", learningGoal);
        if (learningGoal.getId() != null) {
            throw new BadRequestAlertException("A new exam can not already have an ID", ENTITY_NAME, "idexists");
        }

        if (learningGoal.getCourse() == null) {
            return conflict();
        }

        if (!learningGoal.getCourse().getId().equals(courseId)) {
            return conflict();
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(learningGoal.getCourse(), user)) {
            return forbidden();
        }

        LearningGoal persistedLearningGoal = learningGoalService.save(learningGoal);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/goals/" + persistedLearningGoal.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, persistedLearningGoal.getTitle())).body(persistedLearningGoal);
    }
}
