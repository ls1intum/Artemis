package de.tum.in.www1.artemis.web.rest;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningPathService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.learningpath.LearningPathRecommendation;
import de.tum.in.www1.artemis.web.rest.dto.learningpath.NgxLearningPathDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@RestController
@RequestMapping("/api")
public class LearningPathResource {

    private final Logger log = LoggerFactory.getLogger(LearningPathResource.class);

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final LearningPathService learningPathService;

    private final LearningPathRepository learningPathRepository;

    public LearningPathResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            LearningPathService learningPathService, LearningPathRepository learningPathRepository) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.learningPathService = learningPathService;
        this.learningPathRepository = learningPathRepository;
    }

    /**
     * PUT /courses/:courseId/learning-paths/enable : Enables and generates learning paths for the course
     *
     * @param courseId the id of the course for which the learning paths should be enabled
     * @return the ResponseEntity with status 200 (OK) and with body the updated course
     */
    @PutMapping("/courses/{courseId}/learning-paths/enable")
    @EnforceAtLeastInstructor
    public ResponseEntity<Course> enableLearningPathsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to enable learning paths for course with id: {}", courseId);
        Course course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
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
     * GET /courses/:courseId/learning-paths : Gets all the learning paths of a course. The result is pageable.
     *
     * @param courseId the id of the course for which the learning paths should be fetched
     * @param search   the pageable search containing the page size, page number and query string
     * @return the ResponseEntity with status 200 (OK) and with body the desired page, sorted and matching the given query
     */
    @GetMapping("/courses/{courseId}/learning-paths")
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

    /**
     * GET /courses/:courseId/learning-path-id : Gets the id of the learning path.
     *
     * @param courseId the id of the course from which the learning path id should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the id of the learning path
     */
    @GetMapping("/courses/{courseId}/learning-path-id")
    @EnforceAtLeastStudent
    public ResponseEntity<Long> getLearningPathId(@PathVariable Long courseId) {
        log.debug("REST request to get learning path id for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.isStudentInCourse(course, user);
        if (!course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are not enabled for this course.");
        }
        LearningPath learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), user.getId());
        return ResponseEntity.ok(learningPath.getId());
    }

    /**
     * GET /learning-path/:learningPathId : Gets the ngx representation of the learning path.
     *
     * @param learningPathId the id of the learning path that should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the ngx representation of the learning path
     */
    @GetMapping("/learning-path/{learningPathId}")
    @EnforceAtLeastStudent
    public ResponseEntity<NgxLearningPathDTO> getNgxLearningPath(@PathVariable Long learningPathId) {
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
        NgxLearningPathDTO graph = learningPathService.generateNgxRepresentation(learningPath);
        return ResponseEntity.ok(graph);
    }

    /**
     * GET /learning-path/:learningPathId/recommendation : Gets the next recommended learning object for the learning path.
     *
     * @param learningPathId the id of the learning path from which the recommendation should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the recommended learning object
     */
    @GetMapping("/learning-path/{learningPathId}/recommendation")
    @EnforceAtLeastStudent
    public ResponseEntity<LearningPathRecommendation> getRecommendation(@PathVariable Long learningPathId) {
        log.debug("REST request to get recommendation for learning path with id: {}", learningPathId);
        LearningPath learningPath = learningPathRepository.findWithEagerCompetenciesAndLearningObjectsByIdElseThrow(learningPathId);
        LearningObject recommendation = learningPathService.getRecommendation(learningPath);
        if (recommendation == null) {
            return ResponseEntity.ok(new LearningPathRecommendation(-1, -1, LearningPathRecommendation.RecommendationType.EMPTY));
        }
        else if (recommendation instanceof LectureUnit lectureUnit) {
            return ResponseEntity
                    .ok(new LearningPathRecommendation(recommendation.getId(), lectureUnit.getLecture().getId(), LearningPathRecommendation.RecommendationType.LECTURE_UNIT));
        }
        else {
            return ResponseEntity.ok(new LearningPathRecommendation(recommendation.getId(), -1, LearningPathRecommendation.RecommendationType.EXERCISE));
        }
    }
}
