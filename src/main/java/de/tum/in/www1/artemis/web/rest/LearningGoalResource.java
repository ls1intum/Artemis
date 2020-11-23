package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class LearningGoalResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(LearningGoalResource.class);

    private static final String ENTITY_NAME = "learningGoal";

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserService userService;

    private final LearningGoalRepository learningGoalRepository;

    public LearningGoalResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserService userService,
            LearningGoalRepository learningGoalRepository) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userService = userService;
        this.learningGoalRepository = learningGoalRepository;
    }

    /**
     * GET /courses/:courseId/goals : gets all the learning goals of a course
     * @param courseId the id of the course for which the learning goals should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found learning goals
     */
    @GetMapping("/courses/{courseId}/goals")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<LearningGoal>> getLearningGoals(@PathVariable Long courseId) {
        log.debug("REST request to get learning goals for course with id: {}", courseId);
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            return notFound();
        }
        Course course = courseOptional.get();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            return forbidden();
        }

        List<LearningGoal> learningGoals = learningGoalRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok(learningGoals);
    }

    /**
     * POST /courses/:courseId/goals : creates a new learning goal.
     *
     * @param courseId      the id of the course to which the learning goal should be added
     * @param learningGoal the learning goal that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new learning goal
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<LearningGoal> createLearningGoal(@PathVariable Long courseId, @RequestBody LearningGoal learningGoal) throws URISyntaxException {
        log.debug("REST request to create LearningGoal : {}", learningGoal);
        if (learningGoal.getId() != null || learningGoal.getTitle() == null) {
            return badRequest();
        }
        Optional<Course> courseOptional = courseRepository.findWithEagerLearningGoalsById(courseId);
        if (courseOptional.isEmpty()) {
            return badRequest();
        }
        Course course = courseOptional.get();

        if (course.getLearningGoals().stream().map(LearningGoal::getTitle).anyMatch(title -> title.equals(learningGoal.getTitle()))) {
            return badRequest();
        }

        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        course.addLearningGoal(learningGoal);
        Course updatedCourse = courseRepository.save(course);
        LearningGoal persistedLearningGoal = updatedCourse.getLearningGoals().stream().filter(goal -> goal.getTitle().equals(learningGoal.getTitle())).findAny().get();

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/goals/" + persistedLearningGoal.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(persistedLearningGoal);

    }

}
