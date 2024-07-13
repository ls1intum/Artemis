package de.tum.in.www1.artemis.web.rest.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.in.www1.artemis.service.competency.CourseCompetencyService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class CourseCompetencyResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final Logger log = LoggerFactory.getLogger(CourseCompetencyResource.class);

    private final UserRepository userRepository;

    private final CourseCompetencyService courseCompetencyService;

    public CourseCompetencyResource(UserRepository userRepository, CourseCompetencyService courseCompetencyService) {
        this.userRepository = userRepository;
        this.courseCompetencyService = courseCompetencyService;
    }

    /**
     * GET courses/:courseId/competencies : gets all the course competencies of a course
     *
     * @param courseId the id of the course for which the competencies should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found competencies
     */
    @GetMapping("courses/{courseId}/course-competencies")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<CourseCompetency>> getCourseCompetenciesWithProgress(@PathVariable long courseId) {
        log.debug("REST request to get competencies for course with id: {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        final var competencies = courseCompetencyService.findCourseCompetenciesWithProgressForUserByCourseId(courseId, user.getId());
        return ResponseEntity.ok(competencies);
    }
}
