package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.learningpath.LearningPathService;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyProgressForLearningPathDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathHealthDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.NgxLearningPathDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class LearningPathResource {

    private static final Logger log = LoggerFactory.getLogger(LearningPathResource.class);

    private final CourseService courseService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final LearningPathService learningPathService;

    private final LearningPathRepository learningPathRepository;

    private final UserRepository userRepository;

    private final CompetencyProgressService competencyProgressService;

    public LearningPathResource(CourseService courseService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            LearningPathService learningPathService, LearningPathRepository learningPathRepository, UserRepository userRepository,
            CompetencyProgressService competencyProgressService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.learningPathService = learningPathService;
        this.learningPathRepository = learningPathRepository;
        this.userRepository = userRepository;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * PUT courses/:courseId/learning-paths/enable : Enables and generates learning paths for the course
     *
     * @param courseId the id of the course for which the learning paths should be enabled
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/learning-paths/enable")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> enableLearningPathsForCourse(@PathVariable long courseId) {
        log.debug("REST request to enable learning paths for course with id: {}", courseId);
        Course course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        if (course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are already enabled for this course.");
        }

        learningPathService.enableLearningPathsForCourse(course);

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
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> generateMissingLearningPathsForCourse(@PathVariable long courseId) {
        log.debug("REST request to generate missing learning paths for course with id: {}", courseId);
        Course course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        courseService.checkLearningPathsEnabledElseThrow(course);
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
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<SearchResultPageDTO<LearningPathInformationDTO>> getLearningPathsOnPage(@PathVariable long courseId, SearchTermPageableSearchDTO<String> search) {
        log.debug("REST request to get learning paths for course with id: {}", courseId);
        courseService.checkLearningPathsEnabledElseThrow(courseId);
        return ResponseEntity.ok(learningPathService.getAllOfCourseOnPageWithSize(search, courseId));
    }

    /**
     * GET courses/:courseId/learning-path-health : Gets the health status of learning paths for the course.
     *
     * @param courseId the id of the course for which the health status should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the health status
     */
    @GetMapping("courses/{courseId}/learning-path-health")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<LearningPathHealthDTO> getHealthStatusForCourse(@PathVariable long courseId) {
        log.debug("REST request to get health status of learning paths in course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(learningPathService.getHealthStatusForCourse(course));
    }

    /**
     * GET learning-path/:learningPathId : Gets the learning path information.
     *
     * @param learningPathId the id of the learning path that should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the learning path
     */
    @GetMapping("learning-path/{learningPathId}")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<LearningPathInformationDTO> getLearningPath(@PathVariable long learningPathId) {
        log.debug("REST request to get learning path with id: {}", learningPathId);
        final var learningPath = learningPathRepository.findWithEagerUserByIdElseThrow(learningPathId);
        final var user = userRepository.getUser();
        if (!user.getId().equals(learningPath.getUser().getId())) {
            throw new AccessForbiddenException("You are not the owner of the learning path.");
        }
        return ResponseEntity.ok(LearningPathInformationDTO.of(learningPath));
    }

    /**
     * GET learning-path/:learningPathId/graph : Gets the ngx representation of the learning path as a graph.
     *
     * @param learningPathId the id of the learning path that should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the ngx representation of the learning path
     */
    @GetMapping("learning-path/{learningPathId}/graph")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<NgxLearningPathDTO> getLearningPathNgxGraph(@PathVariable long learningPathId) {
        log.debug("REST request to get ngx graph representation of learning path with id: {}", learningPathId);
        return getLearningPathNgx(learningPathId, NgxRequestType.GRAPH);
    }

    /**
     * GET learning-path/:learningPathId/path : Gets the ngx representation of the learning path as a sequential path.
     *
     * @param learningPathId the id of the learning path that should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the ngx representation of the learning path
     */
    @GetMapping("learning-path/{learningPathId}/path")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<NgxLearningPathDTO> getLearningPathNgxPath(@PathVariable long learningPathId) {
        log.debug("REST request to get ngx path representation of learning path with id: {}", learningPathId);
        return getLearningPathNgx(learningPathId, NgxRequestType.PATH);
    }

    private ResponseEntity<NgxLearningPathDTO> getLearningPathNgx(@PathVariable long learningPathId, NgxRequestType type) {
        LearningPath learningPath = learningPathService.findWithCompetenciesAndLearningObjectsAndCompletedUsersById(learningPathId);
        Course course = courseRepository.findByIdElseThrow(learningPath.getCourse().getId());
        courseService.checkLearningPathsEnabledElseThrow(course);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (authorizationCheckService.isAtLeastStudentInCourse(course, user) && !authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            if (!user.getId().equals(learningPath.getUser().getId())) {
                throw new AccessForbiddenException("You are not allowed to access another users learning path.");
            }
        }
        else if (!authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access another users learning path.");
        }

        NgxLearningPathDTO ngxLearningPathDTO = switch (type) {
            case GRAPH -> learningPathService.generateNgxGraphRepresentation(learningPath);
            case PATH -> learningPathService.generateNgxPathRepresentation(learningPath);
        };
        return ResponseEntity.ok(ngxLearningPathDTO);
    }

    /**
     * GET courses/:courseId/learning-path-id : Gets the id of the learning path.
     * If the learning path has not been generated although the course has learning paths enabled, the corresponding learning path will be created.
     *
     * @param courseId the id of the course from which the learning path id should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the id of the learning path
     */
    @GetMapping("courses/{courseId}/learning-path-id")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Long> getLearningPathId(@PathVariable long courseId) {
        log.debug("REST request to get learning path id for course with id: {}", courseId);
        courseService.checkLearningPathsEnabledElseThrow(courseId);

        // generate learning path if missing
        User user = userRepository.getUser();
        final var learningPathOptional = learningPathRepository.findByCourseIdAndUserId(courseId, user.getId());
        LearningPath learningPath;
        if (learningPathOptional.isEmpty()) {
            final var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
            learningPath = learningPathService.generateLearningPathForUser(course, user);
        }
        else {
            learningPath = learningPathOptional.get();
        }
        return ResponseEntity.ok(learningPath.getId());
    }

    /**
     * GET learning-path/:learningPathId : Gets the competency progress in a learning path
     *
     * @param learningPathId the id of the learning path for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the progress in the body
     */
    @GetMapping("learning-path/{learningPathId}/competency-progress")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<CompetencyProgressForLearningPathDTO>> getCompetencyProgressForLearningPath(@PathVariable long learningPathId) {
        log.debug("REST request to get competency progress for learning path: {}", learningPathId);
        final var learningPath = learningPathRepository.findWithEagerCourseAndCompetenciesByIdElseThrow(learningPathId);
        final var user = userRepository.getUserWithGroupsAndAuthorities();

        if (!user.getId().equals(learningPath.getUser().getId()) && !authorizationCheckService.isAtLeastInstructorInCourse(learningPath.getCourse(), user)) {
            throw new AccessForbiddenException("You are not authorized to access other students competency progress.");
        }

        // update progress and construct DTOs
        final var progressDTOs = learningPath.getCompetencies().stream().map(competency -> {
            var progress = competencyProgressService.updateCompetencyProgress(competency.getId(), learningPath.getUser());
            return new CompetencyProgressForLearningPathDTO(competency.getId(), competency.getMasteryThreshold(), progress.getProgress(), progress.getConfidence());
        }).collect(Collectors.toSet());
        return ResponseEntity.ok(progressDTOs);
    }

    /**
     * Enum representing the different graph representations that can be requested.
     */
    public enum NgxRequestType {

        GRAPH("graph"), PATH("path");

        private final String url;

        NgxRequestType(String url) {
            this.url = url;
        }

        @Override
        public String toString() {
            return url;
        }
    }
}
