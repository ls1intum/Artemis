package de.tum.in.www1.artemis.web.rest;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningPathService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

@RestController
@RequestMapping("/api")
public class LearningPathResource {

    private final Logger log = LoggerFactory.getLogger(LearningPathResource.class);

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final CompetencyRepository competencyRepository;

    private final LearningPathRepository learningPathRepository;

    private final LearningPathService learningPathService;

    public LearningPathResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            CompetencyRepository competencyRepository, LearningPathService learningPathService, LearningPathRepository learningPathRepository) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.competencyRepository = competencyRepository;
        this.learningPathService = learningPathService;
        this.learningPathRepository = learningPathRepository;
    }

    /**
     * PUT /courses/:courseId/learning-paths/enable : enables and generates learning paths for the course
     *
     * @param courseId the id of the course for which the learning paths should be enabled
     * @return the ResponseEntity with status 200 (OK) and with body the updated course
     */
    @PutMapping("/courses/{courseId}/learning-paths/enable")
    @EnforceAtLeastInstructor
    public ResponseEntity<Course> enableLearningPathsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to enable learning paths for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        if (course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are already enabled for this course.");
        }

        learningPathService.generateLearningPaths(course);

        course.setLeanringPathsEnabled(true);
        course = courseRepository.save(course);

        return ResponseEntity.ok(course);
    }

    /**
     * GET /course/:courseId/learning-paths : gets all the learning paths of a course
     *
     * @param courseId the id of the course for which the learning paths should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the desired page, sorted and matching the given query
     */
    @GetMapping("/course/{courseId}/learning-paths")
    @EnforceAtLeastInstructor
    public ResponseEntity<SearchResultPageDTO<LearningPath>> getLearningPathsOnPage(@PathVariable Long courseId, PageableSearchDTO<String> search) {
        log.debug("REST request to get learning paths for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        if (!course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are not enabled for this course.");
        }

        return ResponseEntity.ok(learningPathService.getAllOfCourseOnPageWithSize(search, course));
    }
}
