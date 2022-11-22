package de.tum.in.www1.artemis.web.rest;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.statistics.tutor.effort.TutorEffort;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.TutorEffortService;

/**
 * REST controller for managing TutorEffortResource.
 */
@RestController
@RequestMapping("api/")
public class TutorEffortResource {

    private final Logger log = LoggerFactory.getLogger(TutorEffortResource.class);

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final TutorEffortService tutorEffortService;

    public TutorEffortResource(AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository, UserRepository userRepository,
            TutorEffortService tutorEffortService, CourseRepository courseRepository) {
        this.exerciseRepository = exerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.tutorEffortService = tutorEffortService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET courses/{courseId}/exercises/{exerciseId}/tutor-effort : Calculates and returns tutor effort as a list for the respective course and exercise
     * @param courseId the id of the course to query for
     * @param exerciseId the id of the exercise to query for
     * @return list of TutorEffort objects or no content if no results found
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/tutor-effort")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TutorEffort>> calculateTutorEfforts(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        log.debug("tutor-effort with argument[s] course = {}, exercise = {}", courseId, exerciseId);

        // check courseId and exerciseId exist and are linked to each other
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!course.getId().equals(exercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            return ResponseEntity.noContent().build();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);

        List<TutorEffort> tutorEffortList = tutorEffortService.buildTutorEffortList(courseId, exerciseId);
        if (tutorEffortList.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok().body(tutorEffortList);
    }
}
