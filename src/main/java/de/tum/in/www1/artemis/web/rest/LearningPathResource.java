package de.tum.in.www1.artemis.web.rest;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningPathService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathHealthDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.NgxLearningPathDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@RestController
@RequestMapping("api/")
public class LearningPathResource {

    private final Logger log = LoggerFactory.getLogger(LearningPathResource.class);

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final LearningPathService learningPathService;

    private final LearningPathRepository learningPathRepository;

    private final UserRepository userRepository;

    public LearningPathResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, LearningPathService learningPathService,
            LearningPathRepository learningPathRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.learningPathService = learningPathService;
        this.learningPathRepository = learningPathRepository;
        this.userRepository = userRepository;
    }

    /**
     * PUT courses/:courseId/learning-paths/enable : Enables and generates learning paths for the course
     *
     * @param courseId the id of the course for which the learning paths should be enabled
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/learning-paths/enable")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> enableLearningPathsForCourse(@PathVariable long courseId) {
        log.debug("REST request to enable learning paths for course with id: {}", courseId);
        Course course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are already enabled for this course.");
        }

        course.setLearningPathsEnabled(true);
        learningPathService.generateLearningPaths(course);
        courseRepository.save(course);

        return ResponseEntity.ok().build();
    }

    /**
     * PUT courses/:courseId/learning-paths/generate-missing : Generates missing learning paths for the course
     *
     * @param courseId the id of the course for which the learning paths should be created
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/learning-paths/generate-missing")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> generateMissingLearningPathsForCourse(@PathVariable long courseId) {
        log.debug("REST request to generate missing learning paths for course with id: {}", courseId);
        Course course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (!course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths not enabled for this course.");
        }
        learningPathService.generateLearningPaths(course);
        return ResponseEntity.ok().build();
    }

    /**
     * GET courses/:courseId/learning-paths : Gets all the learning paths of a course. The result is pageable.
     *
     * @param courseId the id of the course for which the learning paths should be fetched
     * @param search   the pageable search containing the page size, page number and query string
     * @return the ResponseEntity with status 200 (OK) and with body the desired page, sorted and matching the given query
     */
    @GetMapping("courses/{courseId}/learning-paths")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructor
    public ResponseEntity<SearchResultPageDTO<LearningPathPageableSearchDTO>> getLearningPathsOnPage(@PathVariable long courseId, PageableSearchDTO<String> search) {
        log.debug("REST request to get learning paths for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (!course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are not enabled for this course.");
        }

        return ResponseEntity.ok(learningPathService.getAllOfCourseOnPageWithSize(search, course));
    }

    /**
     * GET courses/:courseId/learning-path-health : Gets the health status of learning paths for the course.
     *
     * @param courseId the id of the course for which the health status should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the health status
     */
    @GetMapping("courses/{courseId}/learning-path-health")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructor
    public ResponseEntity<LearningPathHealthDTO> getHealthStatusForCourse(@PathVariable long courseId) {
        log.debug("REST request to get health status of learning paths in course with id: {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null) && !authorizationCheckService.isAdmin()) {
            throw new AccessForbiddenException("You are not allowed to access the health status of learning paths for this course.");
        }

        return ResponseEntity.ok(learningPathService.getHealthStatusForCourse(course));
    }

    /**
     * GET /learning-path/:learningPathId/graph : Gets the ngx representation of the learning path.
     *
     * @param learningPathId the id of the learning path that should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the ngx representation of the learning path
     */
    @GetMapping("/learning-path/{learningPathId}/graph")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<NgxLearningPathDTO> getLearningPathNgxGraph(@PathVariable Long learningPathId) {
        log.debug("REST request to get ngx representation of learning path with id: {}", learningPathId);
        LearningPath learningPath = learningPathRepository.findWithEagerCompetenciesAndLearningObjectsAndCompletedUsersByIdElseThrow(learningPathId);
        Course course = courseRepository.findByIdElseThrow(learningPath.getCourse().getId());
        if (!course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are not enabled for this course.");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (authorizationCheckService.isStudentInCourse(course, user)) {
            if (!user.getId().equals(learningPath.getUser().getId())) {
                throw new AccessForbiddenException("You are not allowed to access another users learning path.");
            }
        }
        else if (!authorizationCheckService.isAtLeastInstructorInCourse(course, user) && !authorizationCheckService.isAdmin()) {
            throw new AccessForbiddenException("You are not allowed to access another users learning path.");
        }
        NgxLearningPathDTO graph = learningPathService.generateNgxGraphRepresentation(learningPath);
        return ResponseEntity.ok(graph);
    }
}
