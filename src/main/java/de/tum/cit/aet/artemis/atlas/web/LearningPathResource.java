package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyNameDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyProgressForLearningPathDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathCompetencyGraphDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathHealthDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO.LearningObjectType;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationOverviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.NgxLearningPathDTO;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathNavigationService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRecommendationService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.CourseService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.lecture.service.LearningObjectService;

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

    private final LearningPathRecommendationService learningPathRecommendationService;

    private final LearningObjectService learningObjectService;

    private final LearningPathNavigationService learningPathNavigationService;

    public LearningPathResource(CourseService courseService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            LearningPathService learningPathService, LearningPathRepository learningPathRepository, UserRepository userRepository,
            CompetencyProgressService competencyProgressService, LearningPathRecommendationService learningPathRecommendationService, LearningObjectService learningObjectService,
            LearningPathNavigationService learningPathNavigationService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.learningPathService = learningPathService;
        this.learningPathRepository = learningPathRepository;
        this.userRepository = userRepository;
        this.competencyProgressService = competencyProgressService;
        this.learningPathRecommendationService = learningPathRecommendationService;
        this.learningObjectService = learningObjectService;
        this.learningPathNavigationService = learningPathNavigationService;
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
        Course course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
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
        Course course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
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
     * GET learning-path/:learningPathId/competency-graph : Gets the competency graph
     *
     * @param learningPathId the id of the learning path for which the graph should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the graph
     */
    @GetMapping("learning-path/{learningPathId}/competency-graph")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<LearningPathCompetencyGraphDTO> getLearningPathCompetencyGraph(@PathVariable long learningPathId) {
        log.debug("REST request to get competency graph for learning path with id: {}", learningPathId);
        LearningPath learningPath = learningPathRepository.findWithEagerCourseAndCompetenciesByIdElseThrow(learningPathId);
        User user = userRepository.getUser();

        checkLearningPathAccessElseThrow(Optional.of(learningPath.getCourse()), learningPath, Optional.of(user));

        return ResponseEntity.ok(learningPathService.generateLearningPathCompetencyGraph(learningPath, user));
    }

    /**
     * GET courses/{courseId}/learning-path/competency-instructor-graph : Gets the competency instructor graph
     *
     * @param courseId the id of the course for which the graph should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the graph
     */
    @GetMapping("courses/{courseId}/learning-path/competency-instructor-graph")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<LearningPathCompetencyGraphDTO> getLearningPathCompetencyInstructorGraph(@PathVariable long courseId) {
        log.debug("REST request to get competency instructor graph for learning path with id: {}", courseId);

        return ResponseEntity.ok(learningPathService.generateLearningPathCompetencyInstructorGraph(courseId));
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

    /**
     * GET learning-path/:learningPathId/relative-navigation : Gets the navigation information for the learning path relative to a learning object.
     *
     * @param learningPathId     the id of the learning path for which the navigation should be fetched
     * @param learningObjectId   the id of the learning object to navigate to
     * @param learningObjectType the type of the learning object to navigate to
     * @param competencyId       the id of the competency the learning object belongs to
     * @return the ResponseEntity with status 200 (OK) and with body the navigation information
     */
    @GetMapping("learning-path/{learningPathId}/relative-navigation")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<LearningPathNavigationDTO> getRelativeLearningPathNavigation(@PathVariable @Valid long learningPathId, @RequestParam long learningObjectId,
            @RequestParam LearningObjectType learningObjectType, @RequestParam long competencyId) {
        log.debug("REST request to get navigation for learning path with id: {} relative to learning object with id: {} and type: {} in competency with id: {}", learningPathId,
                learningObjectId, learningObjectType, competencyId);
        var learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPathId);
        checkLearningPathAccessElseThrow(Optional.empty(), learningPath, Optional.empty());
        return ResponseEntity.ok(learningPathNavigationService.getNavigationRelativeToLearningObject(learningPath, learningObjectId, learningObjectType, competencyId));
    }

    /**
     * GET learning-path/:learningPathId/navigation : Gets the navigation information for the learning path.
     * The current learning object is the next uncompleted learning object in the learning path or the last completed learning object if all are completed.
     *
     * @param learningPathId the id of the learning path for which the navigation information should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the navigation information
     */
    @GetMapping("learning-path/{learningPathId}/navigation")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<LearningPathNavigationDTO> getLearningPathNavigation(@PathVariable long learningPathId) {
        log.debug("REST request to get navigation for learning path with id: {}", learningPathId);
        var learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPathId);
        checkLearningPathAccessElseThrow(Optional.empty(), learningPath, Optional.empty());
        return ResponseEntity.ok(learningPathNavigationService.getNavigation(learningPath));
    }

    /**
     * GET learning-path/:learningPathId/navigation-overview : Gets the navigation overview for the learning path.
     *
     * @param learningPathId the id of the learning path for which the navigation overview should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the navigation overview
     */
    @GetMapping("learning-path/{learningPathId}/navigation-overview")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastStudent
    public ResponseEntity<LearningPathNavigationOverviewDTO> getLearningPathNavigationOverview(@PathVariable @Valid long learningPathId) {
        log.debug("REST request to get navigation overview for learning path with id: {}", learningPathId);
        return ResponseEntity.ok(learningPathService.getLearningPathNavigationOverview(learningPathId));
    }

    private ResponseEntity<NgxLearningPathDTO> getLearningPathNgx(@PathVariable long learningPathId, NgxRequestType type) {
        LearningPath learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPathId);
        Course course = courseRepository.findByIdElseThrow(learningPath.getCourse().getId());
        courseService.checkLearningPathsEnabledElseThrow(course);

        checkLearningPathAccessElseThrow(Optional.of(course), learningPath, Optional.empty());

        NgxLearningPathDTO ngxLearningPathDTO = switch (type) {
            case GRAPH -> learningPathService.generateNgxGraphRepresentation(learningPath);
            case PATH -> learningPathService.generateNgxPathRepresentation(learningPath);
        };
        return ResponseEntity.ok(ngxLearningPathDTO);
    }

    /**
     * GET courses/:courseId/learning-path-id : Gets the id of the learning path.
     *
     * @param courseId the id of the course from which the learning path id should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the id of the learning path
     */
    @GetMapping("courses/{courseId}/learning-path-id")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Long> getLearningPathId(@PathVariable long courseId) {
        log.debug("REST request to get learning path id for course with id: {}", courseId);
        courseService.checkLearningPathsEnabledElseThrow(courseId);
        User user = userRepository.getUser();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(courseId, user.getId());
        return ResponseEntity.ok(learningPath.getId());
    }

    /**
     * POST courses/:courseId/learning-path : Generates a learning path in the course for the logged-in user.
     *
     * @param courseId the id of the course for which the learning path should be created
     * @return the ResponseEntity with status 200 (OK) and with body the id of the learning path
     */
    @PostMapping("courses/{courseId}/learning-path")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Long> generateLearningPath(@PathVariable long courseId) throws URISyntaxException {
        log.debug("REST request to generate learning path for user in course with id: {}", courseId);
        courseService.checkLearningPathsEnabledElseThrow(courseId);

        User user = userRepository.getUser();
        final var learningPathOptional = learningPathRepository.findByCourseIdAndUserId(courseId, user.getId());

        if (learningPathOptional.isPresent()) {
            throw new BadRequestException("Learning path already exists.");
        }

        final var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
        final var learningPath = learningPathService.generateLearningPathForUser(course, user);
        return ResponseEntity.created(new URI("api/learning-path/" + learningPath.getId())).body(learningPath.getId());
    }

    /**
     * GET learning-path/:learningPathId/competency-progress : Gets the competency progress in a learning path
     *
     * @param learningPathId the id of the learning path for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the progress in the body
     */
    @GetMapping("learning-path/{learningPathId}/competency-progress")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<CompetencyProgressForLearningPathDTO>> getCompetencyProgressForLearningPath(@PathVariable long learningPathId) {
        log.debug("REST request to get competency progress for learning path: {}", learningPathId);
        final var learningPath = learningPathRepository.findWithEagerCourseAndCompetenciesByIdElseThrow(learningPathId);

        checkLearningPathAccessElseThrow(Optional.of(learningPath.getCourse()), learningPath, Optional.empty());

        // update progress and construct DTOs
        final var progressDTOs = learningPath.getCompetencies().stream().map(competency -> {
            var progress = competencyProgressService.updateCompetencyProgress(competency.getId(), learningPath.getUser());
            return new CompetencyProgressForLearningPathDTO(competency.getId(), competency.getMasteryThreshold(), progress.getProgress(), progress.getConfidence());
        }).collect(Collectors.toSet());
        return ResponseEntity.ok(progressDTOs);
    }

    /**
     * GET learning-path/:learningPathId/competencies : Gets the recommended order of competencies in a learning path
     *
     * @param learningPathId the id of the learning path for which to get the competencies
     * @return the ResponseEntity with status 200 (OK) and with the competencies in the body
     */
    @GetMapping("learning-path/{learningPathId}/competencies")
    @EnforceAtLeastStudent
    public ResponseEntity<List<CompetencyNameDTO>> getCompetencyOrderForLearningPath(@PathVariable long learningPathId) {
        log.debug("REST request to get competency order for learning path: {}", learningPathId);
        final var learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPathId);

        checkLearningPathAccessElseThrow(Optional.of(learningPath.getCourse()), learningPath, Optional.empty());

        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfAllCompetencies(learningPath);
        List<CompetencyNameDTO> competencyNames = recommendationState.recommendedOrderOfCompetencies().stream()
                .map(competencyId -> recommendationState.competencyIdMap().get(competencyId)).map(CompetencyNameDTO::of).toList();
        return ResponseEntity.ok(competencyNames);
    }

    /**
     * GET learning-path/:learningPathId/competencies/:competencyId/learning-objects : Gets the recommended order of learning objects for a competency in a learning path. The
     * finished lecture units and exercises are at the beginning of the list. After that all pending lecture units and exercises needed to master the competency are added.
     *
     * @param learningPathId the id of the learning path for which to get the learning objects
     * @param competencyId   the id of the competency for which to get the learning objects
     * @return the ResponseEntity with status 200 (OK) and with the learning objects in the body
     */
    @GetMapping("learning-path/{learningPathId}/competencies/{competencyId}/learning-objects")
    @EnforceAtLeastStudent
    public ResponseEntity<List<LearningPathNavigationObjectDTO>> getLearningObjectsForCompetency(@PathVariable long learningPathId, @PathVariable long competencyId) {
        log.debug("REST request to get learning objects for competency: {} in learning path: {}", competencyId, learningPathId);
        final var learningPath = learningPathRepository.findWithEagerCourseAndCompetenciesByIdElseThrow(learningPathId);
        final var user = userRepository.getUserWithGroupsAndAuthorities();

        checkLearningPathAccessElseThrow(Optional.of(learningPath.getCourse()), learningPath, Optional.of(user));

        List<LearningPathNavigationObjectDTO> learningObjects = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(competencyId, user).stream()
                .map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, learningObjectService.isCompletedByUser(learningObject, user), competencyId)).toList();
        return ResponseEntity.ok(learningObjects);
    }

    /**
     * Checks if the user has access to the learning path. This is the case if the user is the owner of the learning path or an instructor in the course.
     * If not, an AccessForbiddenException is thrown.
     *
     * @param optionalCourse the optional course for which to check the access. If empty, the course is not checked.
     * @param learningPath   the learning path to check the access for
     * @param optionalUser   the optional user for which to check the access. If empty, the current user is used.
     */
    private void checkLearningPathAccessElseThrow(Optional<Course> optionalCourse, LearningPath learningPath, Optional<User> optionalUser) {
        User user = optionalUser.orElseGet(userRepository::getUserWithGroupsAndAuthorities);
        if (!user.equals(learningPath.getUser()) && optionalCourse.map(course -> !authorizationCheckService.isAtLeastInstructorInCourse(course, user)).orElse(true)) {
            throw new AccessForbiddenException("You are not allowed to access another user's learning path.");
        }
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
