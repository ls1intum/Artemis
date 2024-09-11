package de.tum.cit.aet.artemis.web.rest.competency;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.competency.CompetencyJolService;
import de.tum.cit.aet.artemis.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.service.competency.CompetencyRelationService;
import de.tum.cit.aet.artemis.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyExtractionInputDTO;
import de.tum.cit.aet.artemis.service.feature.Feature;
import de.tum.cit.aet.artemis.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.service.iris.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyJolPairDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.CompetencyPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class CourseCompetencyResource {

    private static final String ENTITY_NAME = "courseCompetency";

    private static final Logger log = LoggerFactory.getLogger(CourseCompetencyResource.class);

    private final UserRepository userRepository;

    private final CourseCompetencyService courseCompetencyService;

    private final CourseRepository courseRepository;

    private final CompetencyProgressService competencyProgressService;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyRelationService competencyRelationService;

    private final Optional<IrisCompetencyGenerationService> irisCompetencyGenerationService;

    private final CompetencyJolService competencyJolService;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public CourseCompetencyResource(UserRepository userRepository, CourseCompetencyService courseCompetencyService, CourseCompetencyRepository courseCompetencyRepository,
            CourseRepository courseRepository, CompetencyProgressService competencyProgressService, CompetencyProgressRepository competencyProgressRepository,
            CompetencyRelationRepository competencyRelationRepository, CompetencyRelationService competencyRelationService,
            Optional<IrisCompetencyGenerationService> irisCompetencyGenerationService, CompetencyJolService competencyJolService,
            AuthorizationCheckService authorizationCheckService) {
        this.userRepository = userRepository;
        this.courseCompetencyService = courseCompetencyService;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.courseRepository = courseRepository;
        this.competencyProgressService = competencyProgressService;
        this.competencyProgressRepository = competencyProgressRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyRelationService = competencyRelationService;
        this.irisCompetencyGenerationService = irisCompetencyGenerationService;
        this.competencyJolService = competencyJolService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /course-competencies/:competencyId/title : Returns the title of the course competency with the given id
     *
     * @param competencyId the id of the course competency
     * @return the title of the course competency wrapped in an ResponseEntity or 404 Not Found if no competency with that id exists
     */
    @GetMapping("course-competencies/{competencyId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getCompetencyTitle(@PathVariable long competencyId) {
        final var title = courseCompetencyRepository.getCompetencyTitle(competencyId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * GET courses/{courseId}/course-competencies/titles : Returns the titles of all course competencies. Used for a validator in the client
     *
     * @param courseId the id of the current course
     * @return the titles of all course competencies
     */
    @GetMapping("courses/{courseId}/course-competencies/titles")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<String>> getCourseCompetencyTitles(@PathVariable Long courseId) {
        final var titles = courseCompetencyRepository.findAllTitlesByCourseId(courseId);
        return ResponseEntity.ok(titles);
    }

    /**
     * GET courses/:courseId/competencies/:competencyId : gets the competency with the specified id including its related exercises and lecture units
     * This method also calculates the user progress
     *
     * @param competencyId the id of the competency to retrieve
     * @param courseId     the id of the course to which the competency belongs
     * @return the ResponseEntity with status 200 (OK) and with body the competency, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/course-competencies/{competencyId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CourseCompetency> getCourseCompetency(@PathVariable long competencyId, @PathVariable long courseId) {
        log.info("REST request to get Competency : {}", competencyId);
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = courseCompetencyService.findCompetencyWithExercisesAndLectureUnitsAndProgressForUser(competencyId, currentUser.getId());
        checkCourseForCompetency(course, competency);

        courseCompetencyService.filterOutLearningObjectsThatUserShouldNotSee(competency, currentUser);

        return ResponseEntity.ok(competency);
    }

    /**
     * GET courses/:courseId/course-competencies : gets all the course competencies of a course
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

    /**
     * GET courses/:courseId/course-competencies/:competencyId/student-progress gets the course competency progress for a user
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the course competency for which to get the progress
     * @param refresh      whether to update the student progress or fetch it from the database (default)
     * @return the ResponseEntity with status 200 (OK) and with the competency course performance in the body
     */
    @GetMapping("courses/{courseId}/course-competencies/{competencyId}/student-progress")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CompetencyProgress> getCompetencyStudentProgress(@PathVariable long courseId, @PathVariable long competencyId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        log.debug("REST request to get student progress for competency: {}", competencyId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = courseCompetencyRepository.findByIdElseThrow(competencyId);
        checkCourseForCompetency(course, competency);

        CompetencyProgress studentProgress;
        if (refresh) {
            studentProgress = competencyProgressService.updateCompetencyProgress(competencyId, user);
        }
        else {
            studentProgress = competencyProgressRepository.findEagerByCompetencyIdAndUserId(competencyId, user.getId()).orElse(null);
        }

        return ResponseEntity.ok(studentProgress);
    }

    /**
     * GET courses/:courseId/course-competencies/:competencyId/course-progress gets the competency progress for the whole course
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the course competency for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the course competency course performance in the body
     */
    @GetMapping("courses/{courseId}/course-competencies/{competencyId}/course-progress")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CourseCompetencyProgressDTO> getCompetencyCourseProgress(@PathVariable long courseId, @PathVariable long competencyId) {
        log.debug("REST request to get course progress for competency: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = courseCompetencyRepository.findByIdWithExercisesElseThrow(competencyId);

        var progress = competencyProgressService.getCompetencyCourseProgress(competency, course);

        return ResponseEntity.ok(progress);
    }

    /**
     * Search for all course competencies by title, description, course title and semester. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and search terms
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("course-competencies/for-import")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<CourseCompetency>> getCompetenciesForImport(CompetencyPageableSearchDTO search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(courseCompetencyService.getOnPageWithSizeForImport(search, user));
    }

    /**
     * POST courses/{courseId}/course-competencies/import-all/{sourceCourseId} : Imports all course competencies of the source course (and optionally their relations) into another.
     *
     * @param courseId        the id of the course to import into
     * @param sourceCourseId  the id of the course to import from
     * @param importRelations if relations should be imported as well
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competencies (and relations)
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/course-competencies/import-all/{sourceCourseId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Set<CompetencyWithTailRelationDTO>> importAllCompetenciesFromCourse(@PathVariable long courseId, @PathVariable long sourceCourseId,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to all course competencies from course {} into course {}", sourceCourseId, courseId);

        if (courseId == sourceCourseId) {
            throw new BadRequestAlertException("Cannot import from a course into itself", "Course", "courseCycle");
        }
        var targetCourse = courseRepository.findByIdElseThrow(courseId);
        var sourceCourse = courseRepository.findByIdElseThrow(sourceCourseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, sourceCourse, null);

        var competencies = courseCompetencyRepository.findAllForCourse(sourceCourse.getId());
        Set<CompetencyWithTailRelationDTO> importedCompetencies;

        if (importRelations) {
            importedCompetencies = courseCompetencyService.importCourseCompetenciesAndRelations(targetCourse, competencies);
        }
        else {
            importedCompetencies = courseCompetencyService.importCourseCompetencies(targetCourse, competencies);
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(importedCompetencies);
    }

    // Competency Relation Endpoints

    /**
     * GET courses/:courseId/course-competencies/relations get the relations for the course
     *
     * @param courseId the id of the course to which the relations belong
     * @return the ResponseEntity with status 200 (OK) and with a list of relations for the course
     */
    @GetMapping("courses/{courseId}/course-competencies/relations")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Set<CompetencyRelationDTO>> getCompetencyRelations(@PathVariable long courseId) {
        log.debug("REST request to get relations for course: {}", courseId);

        var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(courseId);
        var relationDTOs = relations.stream().map(CompetencyRelationDTO::of).collect(Collectors.toSet());

        return ResponseEntity.ok(relationDTOs);
    }

    /**
     * POST courses/:courseId/course-competencies/relations create a new relation
     *
     * @param courseId the id of the course to which the competencies belong
     * @param relation the relation to create
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("courses/{courseId}/course-competencies/relations")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CompetencyRelationDTO> createCompetencyRelation(@PathVariable long courseId, @RequestBody CompetencyRelationDTO relation) {
        var tailId = relation.tailCompetencyId();
        var headId = relation.headCompetencyId();
        log.info("REST request to create a relation between competencies {} and {}", tailId, headId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var tailCompetency = courseCompetencyRepository.findByIdElseThrow(tailId);
        checkCourseForCompetency(course, tailCompetency);
        var headCompetency = courseCompetencyRepository.findByIdElseThrow(headId);
        checkCourseForCompetency(course, headCompetency);

        var createdRelation = competencyRelationService.createCompetencyRelation(tailCompetency, headCompetency, relation.relationType(), course);

        return ResponseEntity.ok(CompetencyRelationDTO.of(createdRelation));
    }

    /**
     * DELETE courses/:courseId/course-competencies/relations/:competencyRelationId delete a relation
     *
     * @param courseId             the id of the course
     * @param competencyRelationId the id of the competency relation
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/course-competencies/relations/{competencyRelationId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> removeCompetencyRelation(@PathVariable long courseId, @PathVariable long competencyRelationId) {
        log.info("REST request to remove a competency relation: {}", competencyRelationId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var relation = competencyRelationRepository.findById(competencyRelationId).orElseThrow();

        checkCourseForCompetency(course, relation.getTailCompetency());
        checkCourseForCompetency(course, relation.getHeadCompetency());

        competencyRelationRepository.delete(relation);

        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/course-competencies/:competencyId/competencies/generate-from-description
     * Generates a list of competencies from a given course description with IRIS.
     *
     * @param courseId the id of the current course
     * @param input    the course description and current competencies
     * @return the ResponseEntity with status 202 (Accepted)
     */
    @PostMapping("courses/{courseId}/course-competencies/generate-from-description")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> generateCompetenciesFromCourseDescription(@PathVariable Long courseId, @RequestBody PyrisCompetencyExtractionInputDTO input) {
        var competencyGenerationService = irisCompetencyGenerationService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);

        // Start the Iris competency generation pipeline for the given course.
        // The generated competencies will be sent async over the websocket on the topic /topic/iris/competencies/{courseId}
        competencyGenerationService.executeCompetencyExtractionPipeline(user, course, input.courseDescription(), input.currentCompetencies());

        return ResponseEntity.accepted().build();
    }

    /**
     * PUT courses/:courseId/course-competencies/:competencyId/jol/:jolValue : Sets the judgement of learning for a competency
     *
     * @param courseId     the id of the course for which the competency belongs
     * @param competencyId the id of the competency for which to set the judgement of learning
     * @param jolValue     the value of the judgement of learning
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/course-competencies/{competencyId}/jol/{jolValue}")
    @FeatureToggle(Feature.StudentCourseAnalyticsDashboard)
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Void> setJudgementOfLearning(@PathVariable long courseId, @PathVariable long competencyId, @PathVariable short jolValue) {
        log.debug("REST request to set judgement of learning for competency: {}", competencyId);

        final var userId = userRepository.getUserIdElseThrow();
        courseCompetencyService.checkIfCompetencyBelongsToCourse(competencyId, courseId);
        competencyJolService.setJudgementOfLearning(competencyId, userId, jolValue);

        return ResponseEntity.ok().build();
    }

    /**
     * GET courses/:courseId/course-competencies/:competencyId/jol : Gets the latest (current and prior) judgement of learning for a competency
     *
     * @param courseId     the id of the course for which the competency belongs
     * @param competencyId the id of the competency for which to set the judgement of learning
     * @return the ResponseEntity with status 200 (OK) and body the judgement of learning values
     */
    @GetMapping("courses/{courseId}/course-competencies/{competencyId}/jol")
    @FeatureToggle(Feature.StudentCourseAnalyticsDashboard)
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CompetencyJolPairDTO> getLatestJudgementOfLearningForCompetency(@PathVariable long courseId, @PathVariable long competencyId) {
        log.debug("REST request to get judgement of learning for competency: {}", competencyId);

        final var userId = userRepository.getUserIdElseThrow();
        courseCompetencyService.checkIfCompetencyBelongsToCourse(competencyId, courseId);
        final var jol = competencyJolService.getLatestJudgementOfLearningPairForUserByCompetencyId(userId, competencyId);

        return ResponseEntity.ok(jol);
    }

    /**
     * GET courses/:courseId/course-competencies/jol : Gets the latest (current and prior) judgement of learning for all competencies of a course
     *
     * @param courseId the id of the course for which the competency belongs
     * @return the ResponseEntity with status 200 (OK) and body the judgement of learning values for all competencies of the course as a map from competency id to jol value pairs
     */
    @GetMapping("courses/{courseId}/course-competencies/jol")
    @FeatureToggle(Feature.StudentCourseAnalyticsDashboard)
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Map<Long, CompetencyJolPairDTO>> getLatestJudgementOfLearningForCourse(@PathVariable long courseId) {
        log.debug("REST request to get judgement of learning for competencies of course: {}", courseId);

        final var userId = userRepository.getUserIdElseThrow();
        final var jols = competencyJolService.getLatestJudgementOfLearningForUserByCourseId(userId, courseId);

        return ResponseEntity.ok(jols);
    }

    /**
     * Checks if the course competency matches the course.
     *
     * @param course     The course for which to check the authorization role for
     * @param competency The course competency to be accessed by the user
     */
    private void checkCourseForCompetency(@NotNull Course course, @NotNull CourseCompetency competency) {
        if (competency.getCourse() == null) {
            throw new BadRequestAlertException("A course competency must belong to a course", ENTITY_NAME, "courseCompetencyNoCourse");
        }
        if (!competency.getCourse().getId().equals(course.getId())) {
            throw new BadRequestAlertException("The course competency does not belong to the correct course", ENTITY_NAME, "courseCompetencyWrongCourse");
        }
    }
}
