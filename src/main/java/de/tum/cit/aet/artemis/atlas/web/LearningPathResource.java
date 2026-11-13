package de.tum.cit.aet.artemis.atlas.web;

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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyNameDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyProgressForLearningPathDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathAverageProgressDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathCompetencyGraphDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathHealthDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO.LearningObjectType;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationOverviewDTO;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathNavigationService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRecommendationService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRepositoryService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Conditional(AtlasEnabled.class)
@FeatureToggle(Feature.LearningPaths)
@Lazy
@FeatureUsage(UserFeature.LEARNING_PATHS)
@RestController
@RequestMapping("api/atlas/")
public class LearningPathResource {

    private static final Logger log = LoggerFactory.getLogger(LearningPathResource.class);

    // The access rules below are enforced by the annotations and checks of each handler. They are repeated in the specification so that the
    // generated client documents them; the generated client itself enforces nothing.
    private static final String GATE = " Requires the Atlas module and the LearningPaths feature toggle.";

    private static final String INSTRUCTOR = "Requires at least the instructor role in the course." + GATE;

    private static final String COURSE_STUDENT = "Requires at least the student role in the course and learning paths enabled in the course. Applies to the current user only."
            + GATE;

    private static final String OWNER = "Only the student who owns the learning path may call this operation." + GATE;

    private static final String OWNER_OR_INSTRUCTOR = "The student who owns the learning path or an instructor of its course may call this operation." + GATE;

    private static final String FORBIDDEN = "The caller lacks the required role or access to the learning path, or the LearningPaths feature toggle is disabled.";

    private static final String NOT_ENABLED = "Learning paths are not enabled for the course.";

    private final CourseService courseService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final LearningPathService learningPathService;

    private final LearningPathRepository learningPathRepository;

    private final LearningPathRepositoryService learningPathRepositoryService;

    private final UserRepository userRepository;

    private final CompetencyProgressService competencyProgressService;

    private final LearningPathRecommendationService learningPathRecommendationService;

    private final LearningObjectService learningObjectService;

    private final LearningPathNavigationService learningPathNavigationService;

    public LearningPathResource(CourseService courseService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            LearningPathService learningPathService, LearningPathRepository learningPathRepository, LearningPathRepositoryService learningPathRepositoryService,
            UserRepository userRepository, CompetencyProgressService competencyProgressService, LearningPathRecommendationService learningPathRecommendationService,
            LearningObjectService learningObjectService, LearningPathNavigationService learningPathNavigationService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.learningPathService = learningPathService;
        this.learningPathRepository = learningPathRepository;
        this.learningPathRepositoryService = learningPathRepositoryService;
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
    @FeatureUsage(UserFeature.LEARNING_PATH_MANAGEMENT)
    @PutMapping("courses/{courseId}/learning-paths/enable")
    @EnforceAtLeastInstructorInCourse
    @Operation(summary = "Enable learning paths for a course", description = "Enables learning paths and generates one for every student of the course. " + INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "Learning paths were enabled. The response has no body.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Learning paths are already enabled for the course.", content = @Content)
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
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
    @FeatureUsage(UserFeature.LEARNING_PATH_MANAGEMENT)
    @PutMapping("courses/{courseId}/learning-paths/generate-missing")
    @EnforceAtLeastInstructorInCourse
    @Operation(summary = "Generate missing learning paths", description = "Generates a learning path for every student of the course who has none yet. " + INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The missing learning paths were generated. The response has no body.", content = @Content)
    @ApiResponse(responseCode = "400", description = NOT_ENABLED, content = @Content)
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
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
    @FeatureUsage(UserFeature.LEARNING_PATH_MANAGEMENT)
    @GetMapping("courses/{courseId}/learning-paths")
    @EnforceAtLeastInstructorInCourse
    @Operation(summary = "Search the learning paths of a course", description = "Returns one page of the learning paths of the course whose student login or name matches the search term. "
            + INSTRUCTOR)
    // The search is bound from five flat query parameters, described explicitly because the inherited generic sortedColumn cannot be introspected.
    @Parameter(name = "page", in = ParameterIn.QUERY, required = true, description = "The one-based page number", schema = @Schema(type = "integer", format = "int32"))
    @Parameter(name = "pageSize", in = ParameterIn.QUERY, required = true, description = "The positive number of learning paths per page", schema = @Schema(type = "integer", format = "int32"))
    @Parameter(name = "sortedColumn", in = ParameterIn.QUERY, required = true, description = "The column to sort by: ID, USER_LOGIN, USER_NAME or PROGRESS", schema = @Schema(type = "string"))
    @Parameter(name = "sortingOrder", in = ParameterIn.QUERY, description = "The sort direction. Any value other than ASCENDING, including none, sorts descending", schema = @Schema(type = "string", allowableValues = {
            "ASCENDING", "DESCENDING" }))
    @Parameter(name = "searchTerm", in = ParameterIn.QUERY, description = "The login or name fragment to match. Empty or absent matches every learning path", schema = @Schema(type = "string", defaultValue = ""))
    @ApiResponse(responseCode = "200", description = "The requested page. resultsOnPage is omitted when the page is empty.")
    @ApiResponse(responseCode = "400", description = NOT_ENABLED, content = @Content)
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<SearchResultPageDTO<LearningPathInformationDTO>> getLearningPathsOnPage(@PathVariable long courseId,
            @Parameter(hidden = true) SearchTermPageableSearchDTO<String> search) {
        log.debug("REST request to get learning paths for course with id: {}", courseId);
        courseService.checkLearningPathsEnabledElseThrow(courseId);
        return ResponseEntity.ok(learningPathService.getAllOfCourseOnPageWithSize(search, courseId));
    }

    /**
     * GET courses/:courseId/learning-path/average-progress : Gets the average learning path progress for all students in the course
     *
     * @param courseId the id of the course for which the average progress should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the average progress information
     */
    @FeatureUsage(UserFeature.LEARNING_PATH_MANAGEMENT)
    @GetMapping("courses/{courseId}/learning-path/average-progress")
    @EnforceAtLeastInstructorInCourse
    @Operation(summary = "Get the average learning path progress of a course", description = INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The average progress of the students of the course")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<LearningPathAverageProgressDTO> getAverageProgressForCourse(@PathVariable long courseId) {
        log.debug("REST request to get average learning path progress for course: {}", courseId);

        LearningPathAverageProgressDTO averageProgressDto = learningPathService.getAverageProgressForCourse(courseId);
        return ResponseEntity.ok(averageProgressDto);
    }

    /**
     * GET courses/:courseId/learning-path-health : Gets the health status of learning paths for the course.
     *
     * @param courseId the id of the course for which the health status should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the health status
     */
    @FeatureUsage(UserFeature.LEARNING_PATH_MANAGEMENT)
    @GetMapping("courses/{courseId}/learning-path-health")
    @EnforceAtLeastInstructorInCourse
    @Operation(summary = "Get the health of the learning paths of a course", description = INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The health status. A healthy course has an empty status, which is omitted from the response.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<LearningPathHealthDTO> getHealthStatusForCourse(@PathVariable long courseId) {
        log.debug("REST request to get health status of learning paths in course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(learningPathService.getHealthStatusForCourse(course));
    }

    /**
     * GET learning-paths/:learningPathId : Gets the learning path information.
     *
     * @param learningPathId the id of the learning path that should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the learning path
     */
    @GetMapping("learning-paths/{learningPathId}")
    @EnforceAtLeastStudent
    @Operation(summary = "Get a learning path", description = OWNER)
    @ApiResponse(responseCode = "200", description = "The learning path")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    @ApiResponse(responseCode = "404", description = "The learning path does not exist.", content = @Content)
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
     * GET learning-paths/:learningPathId/competency-graph : Gets the competency graph
     *
     * @param learningPathId the id of the learning path for which the graph should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the graph
     */
    @GetMapping("learning-paths/{learningPathId}/competency-graph")
    @EnforceAtLeastStudent
    @Operation(summary = "Get the competency graph of a learning path", description = "Node values are the mastery progress of the owner (MASTERY_PROGRESS). "
            + OWNER_OR_INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The competency graph. Empty node or edge lists are omitted.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<LearningPathCompetencyGraphDTO> getLearningPathCompetencyGraph(@PathVariable long learningPathId) {
        log.debug("REST request to get competency graph for learning path with id: {}", learningPathId);
        LearningPath learningPath = learningPathRepositoryService.findWithEagerUserAndCourseAndCompetenciesByIdElseThrow(learningPathId);
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
    @FeatureUsage(UserFeature.LEARNING_PATH_MANAGEMENT)
    @GetMapping("courses/{courseId}/learning-path/competency-instructor-graph")
    @FeatureToggle(Feature.LearningPaths)
    @EnforceAtLeastInstructorInCourse
    @Operation(summary = "Get the competency graph of a course", description = "Node values are the average mastery progress of the students (AVERAGE_MASTERY_PROGRESS). "
            + INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The competency graph. Empty node or edge lists are omitted.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<LearningPathCompetencyGraphDTO> getLearningPathCompetencyInstructorGraph(@PathVariable long courseId) {
        log.debug("REST request to get competency instructor graph for learning path with id: {}", courseId);

        return ResponseEntity.ok(learningPathService.generateLearningPathCompetencyInstructorGraph(courseId));
    }

    /**
     * GET learning-paths/:learningPathId/relative-navigation : Gets the navigation information for the learning path relative to a learning object.
     *
     * @param learningPathId     the id of the learning path for which the navigation should be fetched
     * @param learningObjectId   the id of the learning object to navigate to
     * @param learningObjectType the type of the learning object to navigate to
     * @param competencyId       the id of the competency the learning object belongs to
     * @param repeatedTest       whether the learning object is part of a repeated test
     * @return the ResponseEntity with status 200 (OK) and with body the navigation information
     */
    @GetMapping("learning-paths/{learningPathId}/relative-navigation")
    @EnforceAtLeastStudent
    @Operation(summary = "Get the navigation of a learning path relative to a learning object", description = OWNER)
    @ApiResponse(responseCode = "200", description = "The navigation. A missing predecessor, current or successor learning object is omitted.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<LearningPathNavigationDTO> getRelativeLearningPathNavigation(@PathVariable @Valid long learningPathId, @RequestParam long learningObjectId,
            @RequestParam LearningObjectType learningObjectType, @RequestParam long competencyId, @RequestParam(defaultValue = "false") boolean repeatedTest) {
        log.debug("REST request to get navigation for learning path with id: {} relative to learning object with id: {} and type: {} in competency with id: {}", learningPathId,
                learningObjectId, learningObjectType, competencyId);
        var learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPathId);
        checkLearningPathAccessElseThrow(Optional.empty(), learningPath, Optional.empty());

        return ResponseEntity
                .ok(learningPathNavigationService.getNavigationRelativeToLearningObject(learningPath, learningObjectId, learningObjectType, competencyId, repeatedTest));
    }

    /**
     * GET learning-paths/:learningPathId/navigation : Gets the navigation information for the learning path.
     * The current learning object is the next uncompleted learning object in the learning path or the last completed learning object if all are completed.
     *
     * @param learningPathId the id of the learning path for which the navigation information should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the navigation information
     */
    @GetMapping("learning-paths/{learningPathId}/navigation")
    @EnforceAtLeastStudent
    @Operation(summary = "Get the navigation of a learning path", description = "The current learning object is the next uncompleted one, or the last completed one if all are completed. "
            + OWNER)
    @ApiResponse(responseCode = "200", description = "The navigation. A missing predecessor, current or successor learning object is omitted.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<LearningPathNavigationDTO> getLearningPathNavigation(@PathVariable long learningPathId) {
        log.debug("REST request to get navigation for learning path with id: {}", learningPathId);
        var learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPathId);
        checkLearningPathAccessElseThrow(Optional.empty(), learningPath, Optional.empty());
        return ResponseEntity.ok(learningPathNavigationService.getNavigation(learningPath));
    }

    /**
     * GET learning-paths/:learningPathId/navigation-overview : Gets the navigation overview for the learning path.
     *
     * @param learningPathId the id of the learning path for which the navigation overview should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the navigation overview
     */
    @GetMapping("learning-paths/{learningPathId}/navigation-overview")
    @EnforceAtLeastStudent
    @Operation(summary = "Get the navigation overview of a learning path", description = OWNER)
    @ApiResponse(responseCode = "200", description = "The learning objects in recommended order. An empty list is omitted.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<LearningPathNavigationOverviewDTO> getLearningPathNavigationOverview(@PathVariable @Valid long learningPathId) {
        log.debug("REST request to get navigation overview for learning path with id: {}", learningPathId);
        return ResponseEntity.ok(learningPathService.getLearningPathNavigationOverview(learningPathId));
    }

    /**
     * GET courses/:courseId/learning-path/me : Gets the learning path of the current user in the course.
     *
     * @param courseId the id of the course for which the learning path should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the learning path
     */
    @GetMapping("courses/{courseId}/learning-path/me")
    @EnforceAtLeastStudentInCourse
    @Operation(summary = "Get your learning path in a course", description = COURSE_STUDENT)
    @ApiResponse(responseCode = "200", description = "The learning path of the current user")
    @ApiResponse(responseCode = "400", description = NOT_ENABLED, content = @Content)
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    @ApiResponse(responseCode = "404", description = "The current user has no learning path in the course yet.", content = @Content)
    public ResponseEntity<LearningPathDTO> getLearningPathForCurrentUser(@PathVariable long courseId) {
        log.debug("REST request to get learning path of current user for course with id: {}", courseId);
        courseService.checkLearningPathsEnabledElseThrow(courseId);
        return ResponseEntity.ok(learningPathService.getLearningPathForCurrentUser(courseId));
    }

    /**
     * PATCH learning-paths/:learningPathId/start : Starts the learning path for the current user.
     *
     * @param learningPathId the id of the learning path to start
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @PatchMapping("learning-paths/{learningPathId}/start")
    @EnforceAtLeastStudent
    @Operation(summary = "Start your learning path", description = OWNER)
    @ApiResponse(responseCode = "204", description = "The learning path was started. The response has no body.", content = @Content)
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    @ApiResponse(responseCode = "409", description = "The learning path was already started.", content = @Content)
    public ResponseEntity<Void> startLearningPathForCurrentUser(@PathVariable long learningPathId) {
        log.debug("REST request to start learning path with id: {}", learningPathId);
        learningPathService.startLearningPathForCurrentUser(learningPathId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST courses/:courseId/learning-path : Generates a learning path in the course for the logged-in user.
     *
     * @param courseId the id of the course for which the learning path should be created
     * @return the ResponseEntity with status 201 (Created), the location of the new learning path and with body the learning path
     * @throws URISyntaxException if the location URI is invalid
     */
    @PostMapping("courses/{courseId}/learning-path")
    @EnforceAtLeastStudentInCourse
    @Operation(summary = "Generate your learning path in a course", description = COURSE_STUDENT)
    @ApiResponse(responseCode = "201", description = "The learning path was generated.", headers = @Header(name = "Location", description = "The path of the new learning path, /api/atlas/learning-paths/{learningPathId}", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = NOT_ENABLED, content = @Content)
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    @ApiResponse(responseCode = "409", description = "The current user already has a learning path in the course.", content = @Content)
    public ResponseEntity<LearningPathDTO> generateLearningPathForCurrentUser(@PathVariable long courseId) throws URISyntaxException {
        log.debug("REST request to generate learning path for current user in course with id: {}", courseId);
        courseService.checkLearningPathsEnabledElseThrow(courseId);
        final var learningPathDTO = learningPathService.generateLearningPathForCurrentUser(courseId);
        return ResponseEntity.created(new URI("/api/atlas/learning-paths/" + learningPathDTO.id())).body(learningPathDTO);
    }

    /**
     * GET learning-paths/:learningPathId/competency-progress : Gets the competency progress in a learning path
     *
     * @param learningPathId the id of the learning path for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the progress in the body
     */
    @GetMapping("learning-paths/{learningPathId}/competency-progress")
    @EnforceAtLeastStudent
    @Operation(summary = "Get the competency progress of a learning path", description = "Recomputes and stores the progress of the owner in every competency of the learning path. "
            + OWNER_OR_INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The progress in each competency, in no particular order")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<Set<CompetencyProgressForLearningPathDTO>> getCompetencyProgressForLearningPath(@PathVariable long learningPathId) {
        log.debug("REST request to get competency progress for learning path: {}", learningPathId);
        final var learningPath = learningPathRepositoryService.findWithEagerUserAndCourseAndCompetenciesByIdElseThrow(learningPathId);

        checkLearningPathAccessElseThrow(Optional.of(learningPath.getCourse()), learningPath, Optional.empty());

        // update progress and construct DTOs
        final var progressDTOs = learningPath.getCompetencies().stream().map(competency -> {
            var progress = competencyProgressService.updateCompetencyProgress(competency.getId(), learningPath.getUser());
            return new CompetencyProgressForLearningPathDTO(competency.getId(), competency.getMasteryThreshold(), progress.getProgress(), progress.getConfidence());
        }).collect(Collectors.toSet());
        return ResponseEntity.ok(progressDTOs);
    }

    /**
     * GET learning-paths/:learningPathId/competencies : Gets the recommended order of competencies in a learning path
     *
     * @param learningPathId the id of the learning path for which to get the competencies
     * @return the ResponseEntity with status 200 (OK) and with the competencies in the body
     */
    @GetMapping("learning-paths/{learningPathId}/competencies")
    @EnforceAtLeastStudent
    @Operation(summary = "Get the competencies of a learning path in recommended order", description = OWNER_OR_INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The competencies in recommended order")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
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
     * GET learning-paths/:learningPathId/competencies/:competencyId/learning-objects : Gets the recommended order of learning objects for a competency in a learning path. The
     * finished lecture units and exercises are at the beginning of the list. After that all pending lecture units and exercises needed to master the competency are added.
     *
     * @param learningPathId the id of the learning path for which to get the learning objects
     * @param competencyId   the id of the competency for which to get the learning objects
     * @return the ResponseEntity with status 200 (OK) and with the learning objects in the body
     */
    @GetMapping("learning-paths/{learningPathId}/competencies/{competencyId}/learning-objects")
    @EnforceAtLeastStudent
    @Operation(summary = "Get the learning objects of a competency in recommended order", description = "Completed learning objects come first, followed by the pending ones needed to master the competency. "
            + OWNER_OR_INSTRUCTOR)
    @ApiResponse(responseCode = "200", description = "The learning objects in recommended order")
    @ApiResponse(responseCode = "403", description = FORBIDDEN, content = @Content)
    public ResponseEntity<List<LearningPathNavigationObjectDTO>> getLearningObjectsForCompetency(@PathVariable long learningPathId, @PathVariable long competencyId) {
        log.debug("REST request to get learning objects for competency: {} in learning path: {}", competencyId, learningPathId);
        final var learningPath = learningPathRepositoryService.findWithEagerUserAndCourseAndCompetenciesByIdElseThrow(learningPathId);
        final var user = userRepository.getUserWithAuthorities();

        checkLearningPathAccessElseThrow(Optional.of(learningPath.getCourse()), learningPath, Optional.of(user));

        List<LearningPathNavigationObjectDTO> learningObjects = learningPathRecommendationService.getOrderOfLearningObjectsForCompetency(competencyId, user).stream()
                .map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, false, learningObjectService.isCompletedByUser(learningObject, user), competencyId))
                .toList();
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
        User user = optionalUser.orElseGet(userRepository::getUserWithAuthorities);
        if (!user.equals(learningPath.getUser()) && optionalCourse.map(course -> !authorizationCheckService.isAtLeastInstructorInCourse(course, user)).orElse(true)) {
            throw new AccessForbiddenException("You are not allowed to access another user's learning path.");
        }
    }
}
