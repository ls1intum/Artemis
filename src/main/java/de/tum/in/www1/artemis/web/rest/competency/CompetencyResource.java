package de.tum.in.www1.artemis.web.rest.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseCompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.service.competency.CompetencyJolService;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;
import de.tum.in.www1.artemis.service.competency.CompetencyRelationService;
import de.tum.in.www1.artemis.service.competency.CompetencyService;
import de.tum.in.www1.artemis.service.competency.CourseCompetencyService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.iris.session.IrisCompetencyGenerationSessionService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyImportResponseDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolPairDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyRelationDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.CompetencyPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class CompetencyResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final Logger log = LoggerFactory.getLogger(CompetencyResource.class);

    private static final String ENTITY_NAME = "competency";

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final CompetencyRepository competencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyService competencyService;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final CompetencyProgressService competencyProgressService;

    private final ExerciseService exerciseService;

    private final Optional<IrisCompetencyGenerationSessionService> irisCompetencyGenerationSessionService;

    private final LectureUnitService lectureUnitService;

    private final CompetencyRelationService competencyRelationService;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CompetencyJolService competencyJolService;

    private final CourseCompetencyService courseCompetencyService;

    public CompetencyResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            CompetencyRepository competencyRepository, CompetencyRelationRepository competencyRelationRepository, CompetencyService competencyService,
            CompetencyProgressRepository competencyProgressRepository, CompetencyProgressService competencyProgressService, ExerciseService exerciseService,
            LectureUnitService lectureUnitService, CompetencyRelationService competencyRelationService,
            Optional<IrisCompetencyGenerationSessionService> irisCompetencyGenerationSessionService, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyJolService competencyJolService, CourseCompetencyService courseCompetencyService) {
        this.courseRepository = courseRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.competencyRepository = competencyRepository;
        this.competencyService = competencyService;
        this.competencyProgressRepository = competencyProgressRepository;
        this.competencyProgressService = competencyProgressService;
        this.exerciseService = exerciseService;
        this.lectureUnitService = lectureUnitService;
        this.competencyRelationService = competencyRelationService;
        this.irisCompetencyGenerationSessionService = irisCompetencyGenerationSessionService;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyJolService = competencyJolService;
        this.courseCompetencyService = courseCompetencyService;
    }

    /**
     * GET /competencies/:competencyId/title : Returns the title of the competency with the given id
     *
     * @param competencyId the id of the competency
     * @return the title of the competency wrapped in an ResponseEntity or 404 Not Found if no competency with that id exists
     */
    @GetMapping("competencies/{competencyId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getCompetencyTitle(@PathVariable long competencyId) {
        final var title = competencyRepository.getCompetencyTitle(competencyId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * Search for all competencies by title, description, course title and semester. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and search terms
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("competencies/for-import")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<Competency>> getCompetenciesForImport(CompetencyPageableSearchDTO search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(competencyService.getOnPageWithSizeForImport(search, user));
    }

    /**
     * GET courses/:courseId/competencies : gets all the competencies of a course
     *
     * @param courseId the id of the course for which the competencies should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found competencies
     */
    @GetMapping("courses/{courseId}/competencies")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<Competency>> getCompetenciesWithProgress(@PathVariable long courseId) {
        log.debug("REST request to get competencies for course with id: {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        final var competencies = competencyService.findCompetenciesWithProgressForUserByCourseId(courseId, user.getId());
        return ResponseEntity.ok(competencies);
    }

    /**
     * GET courses/:courseId/competencies/:competencyId : gets the competency with the specified id including its related exercises and lecture units
     * This method also calculates the user progress
     *
     * @param competencyId the id of the competency to retrieve
     * @param courseId     the id of the course to which the competency belongs
     * @return the ResponseEntity with status 200 (OK) and with body the competency, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/competencies/{competencyId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Competency> getCompetency(@PathVariable long competencyId, @PathVariable long courseId) {
        log.info("REST request to get Competency : {}", competencyId);
        long start = System.nanoTime();
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyService.findCompetencyWithExercisesAndLectureUnitsAndProgressForUser(competencyId, currentUser.getId());
        checkAuthorizationForCompetency(course, competency);

        competency.setLectureUnits(competency.getLectureUnits().stream().filter(lectureUnit -> authorizationCheckService.isAllowedToSeeLectureUnit(lectureUnit, currentUser))
                .peek(lectureUnit -> lectureUnit.setCompleted(lectureUnit.isCompletedFor(currentUser))).collect(Collectors.toSet()));

        Set<Exercise> exercisesUserIsAllowedToSee = exerciseService.filterOutExercisesThatUserShouldNotSee(competency.getExercises(), currentUser);
        Set<Exercise> exercisesWithAllInformationNeeded = exerciseService
                .loadExercisesWithInformationForDashboard(exercisesUserIsAllowedToSee.stream().map(Exercise::getId).collect(Collectors.toSet()), currentUser);
        competency.setExercises(exercisesWithAllInformationNeeded);

        log.info("getCompetency took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok().body(competency);
    }

    /**
     * PUT courses/:courseId/competencies : Updates an existing competency.
     *
     * @param courseId   the id of the course to which the competencies belong
     * @param competency the competency to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated competency
     */
    @PutMapping("courses/{courseId}/competencies")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Competency> updateCompetency(@PathVariable long courseId, @RequestBody Competency competency) {
        log.debug("REST request to update Competency : {}", competency);
        if (competency.getId() == null) {
            throw new BadRequestException();
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var existingCompetency = competencyRepository.findByIdWithLectureUnitsElseThrow(competency.getId());
        checkAuthorizationForCompetency(course, existingCompetency);

        var persistedCompetency = competencyService.updateCompetency(existingCompetency, competency);
        lectureUnitService.linkLectureUnitsToCompetency(persistedCompetency, competency.getLectureUnits(), existingCompetency.getLectureUnits());

        return ResponseEntity.ok(persistedCompetency);
    }

    /**
     * POST courses/:courseId/competencies : creates a new competency.
     *
     * @param courseId   the id of the course to which the competency should be added
     * @param competency the competency that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Competency> createCompetency(@PathVariable long courseId, @RequestBody Competency competency) throws URISyntaxException {
        log.debug("REST request to create Competency : {}", competency);
        if (competency.getId() != null || competency.getTitle() == null || competency.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        final var persistedCompetency = competencyService.createCompetency(competency, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/" + persistedCompetency.getId())).body(persistedCompetency);
    }

    /**
     * POST courses/:courseId/competencies/bulk : creates a number of new competencies
     *
     * @param courseId     the id of the course to which the competencies should be added
     * @param competencies the competencies that should be created
     * @return the ResponseEntity with status 201 (Created) and body the created competencies
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/bulk")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<Competency>> createCompetencies(@PathVariable Long courseId, @RequestBody List<Competency> competencies) throws URISyntaxException {
        log.debug("REST request to create Competencies : {}", competencies);
        for (Competency competency : competencies) {
            if (competency.getId() != null || competency.getTitle() == null || competency.getTitle().trim().isEmpty()) {
                throw new BadRequestException();
            }
        }
        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        var createdCompetencies = competencyService.createCompetencies(competencies, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(createdCompetencies);
    }

    /**
     * POST courses/:courseId/competencies/import : imports a new competency.
     *
     * @param courseId           the id of the course to which the competency should be imported to
     * @param competencyToImport the competency that should be imported
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/import")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Competency> importCompetency(@PathVariable long courseId, @RequestBody Competency competencyToImport) throws URISyntaxException {
        log.info("REST request to import a competency: {}", competencyToImport.getId());

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competencyToImport.getCourse(), null);

        if (competencyToImport.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The competency is already added to this course", ENTITY_NAME, "competencyCycle");
        }

        competencyToImport = competencyService.createCompetency(competencyToImport, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/" + competencyToImport.getId())).body(competencyToImport);
    }

    /**
     * POST courses/:courseId/competencies/import/bulk : imports a number of competencies (and optionally their relations) into a course.
     *
     * @param courseId             the id of the course to which the competencies should be imported to
     * @param competenciesToImport the competencies that should be imported
     * @param importRelations      if relations should be imported aswell
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competencies
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/import/bulk")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<CompetencyWithTailRelationDTO>> importCompetencies(@PathVariable long courseId, @RequestBody List<Competency> competenciesToImport,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to import competencies: {}", competenciesToImport);

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        var competencies = new HashSet<>(competenciesToImport);
        List<CompetencyWithTailRelationDTO> importedCompetencies;

        if (importRelations) {
            var competencyIds = competenciesToImport.stream().map(DomainObject::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            var relations = competencyRelationRepository.findAllByHeadCompetencyIdInAndTailCompetencyIdIn(competencyIds, competencyIds);
            importedCompetencies = competencyService.importCompetenciesAndRelations(course, competencies, relations);
        }
        else {
            importedCompetencies = competencyService.competenciesToCompetencyWithTailRelationDTOs(competencyService.importCompetencies(course, competencies));
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(importedCompetencies);
    }

    /**
     * POST courses/{courseId}/competencies/import-all/{sourceCourseId} : Imports all competencies of the source course (and optionally their relations) into another.
     *
     * @param courseId        the id of the course to import into
     * @param sourceCourseId  the id of the course to import from
     * @param importRelations if relations should be imported aswell
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competencies (and relations)
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/import-all/{sourceCourseId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<CompetencyWithTailRelationDTO>> importAllCompetenciesFromCourse(@PathVariable long courseId, @PathVariable long sourceCourseId,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to all competencies from course {} into course {}", sourceCourseId, courseId);

        if (courseId == sourceCourseId) {
            throw new BadRequestAlertException("Cannot import from a course into itself", "Course", "courseCycle");
        }
        var targetCourse = courseRepository.findByIdElseThrow(courseId);
        var sourceCourse = courseRepository.findByIdElseThrow(sourceCourseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, sourceCourse, null);

        var competencies = competencyRepository.findAllForCourse(sourceCourse.getId());
        List<CompetencyWithTailRelationDTO> importedCompetencies;

        if (importRelations) {
            var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(sourceCourse.getId());
            importedCompetencies = competencyService.importCompetenciesAndRelations(targetCourse, competencies, relations);
        }
        else {
            importedCompetencies = competencyService.competenciesToCompetencyWithTailRelationDTOs(competencyService.importCompetencies(targetCourse, competencies));
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(importedCompetencies);
    }

    /**
     * POST courses/:courseId/competencies/import-standardized : imports a number of standardized competencies (as competencies) into a course.
     *
     * @param courseId              the id of the course to which the competencies should be imported to
     * @param competencyIdsToImport the ids of the standardized competencies that should be imported
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competencies
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/import-standardized")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<CompetencyImportResponseDTO>> importStandardizedCompetencies(@PathVariable long courseId, @RequestBody List<Long> competencyIdsToImport)
            throws URISyntaxException {
        log.info("REST request to import standardized competencies with ids: {}", competencyIdsToImport);

        var importedCompetencies = competencyService.importStandardizedCompetencies(competencyIdsToImport, courseId);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(importedCompetencies.stream().map(CompetencyImportResponseDTO::of).toList());
    }

    /**
     * DELETE courses/:courseId/competencies/:competencyId
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/competencies/{competencyId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteCompetency(@PathVariable long competencyId, @PathVariable long courseId) {
        log.info("REST request to delete a Competency : {}", competencyId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(competencyId);
        checkAuthorizationForCompetency(course, competency);

        courseCompetencyService.deleteCourseCompetency(competency, course);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, competency.getTitle())).build();
    }

    /**
     * GET courses/:courseId/competencies/:competencyId/student-progress gets the competency progress for a user
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to get the progress
     * @param refresh      whether to update the student progress or fetch it from the database (default)
     * @return the ResponseEntity with status 200 (OK) and with the competency course performance in the body
     */
    @GetMapping("courses/{courseId}/competencies/{competencyId}/student-progress")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CompetencyProgress> getCompetencyStudentProgress(@PathVariable long courseId, @PathVariable long competencyId,
            @RequestParam(defaultValue = "false") Boolean refresh) {
        log.debug("REST request to get student progress for competency: {}", competencyId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(course, competency);

        CompetencyProgress studentProgress;
        if (refresh) {
            studentProgress = competencyProgressService.updateCompetencyProgress(competencyId, user);
        }
        else {
            studentProgress = competencyProgressRepository.findEagerByCompetencyIdAndUserId(competencyId, user.getId()).orElse(null);
        }

        return ResponseEntity.ok().body(studentProgress);
    }

    /**
     * GET courses/:courseId/competencies/:competencyId/course-progress gets the competency progress for the whole course
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the competency course performance in the body
     */
    @GetMapping("courses/{courseId}/competencies/{competencyId}/course-progress")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CourseCompetencyProgressDTO> getCompetencyCourseProgress(@PathVariable long courseId, @PathVariable long competencyId) {
        log.debug("REST request to get course progress for competency: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithExercisesElseThrow(competencyId);

        var progress = competencyProgressService.getCompetencyCourseProgress(competency, course);

        return ResponseEntity.ok().body(progress);
    }

    // Competency Relation Endpoints

    /**
     * GET courses/:courseId/competencies/relations get the relations for the course
     *
     * @param courseId the id of the course to which the relations belong
     * @return the ResponseEntity with status 200 (OK) and with a list of relations for the course
     */
    @GetMapping("courses/{courseId}/competencies/relations")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Set<CompetencyRelationDTO>> getCompetencyRelations(@PathVariable long courseId) {
        log.debug("REST request to get relations for course: {}", courseId);

        var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(courseId);
        var relationDTOs = relations.stream().map(CompetencyRelationDTO::of).collect(Collectors.toSet());

        return ResponseEntity.ok().body(relationDTOs);
    }

    /**
     * POST courses/:courseId/competencies/relations create a new relation
     *
     * @param courseId the id of the course to which the competencies belong
     * @param relation the relation to create
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("courses/{courseId}/competencies/relations")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CompetencyRelationDTO> createCompetencyRelation(@PathVariable long courseId, @RequestBody CompetencyRelationDTO relation) {
        var tailId = relation.tailCompetencyId();
        var headId = relation.headCompetencyId();
        log.info("REST request to create a relation between competencies {} and {}", tailId, headId);
        var course = courseRepository.findByIdElseThrow(courseId);

        var tailCompetency = courseCompetencyRepository.findByIdElseThrow(tailId);
        checkAuthorizationForCompetency(course, tailCompetency);
        var headCompetency = courseCompetencyRepository.findByIdElseThrow(headId);
        checkAuthorizationForCompetency(course, headCompetency);

        var createdRelation = competencyRelationService.createCompetencyRelation(tailCompetency, headCompetency, relation.relationType(), course);

        return ResponseEntity.ok().body(CompetencyRelationDTO.of(createdRelation));
    }

    /**
     * DELETE courses/:courseId/competencies/relations/:competencyRelationId delete a relation
     *
     * @param courseId             the id of the course
     * @param competencyRelationId the id of the competency relation
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/competencies/relations/{competencyRelationId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> removeCompetencyRelation(@PathVariable long courseId, @PathVariable long competencyRelationId) {
        log.info("REST request to remove a competency relation: {}", competencyRelationId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var relation = competencyRelationRepository.findById(competencyRelationId).orElseThrow();

        checkAuthorizationForCompetency(course, relation.getTailCompetency());
        checkAuthorizationForCompetency(course, relation.getHeadCompetency());

        competencyRelationRepository.delete(relation);

        return ResponseEntity.ok().build();
    }

    /**
     * POST courses/:courseId/competencies/:competencyId/competencies/generate-from-description : Generates a list of competencies from a given course description by using IRIS.
     *
     * @param courseId          the id of the current course
     * @param courseDescription the text description of the course
     * @return the ResponseEntity with status 200 (OK) and body the genrated competencies
     */
    @PostMapping("courses/{courseId}/competencies/generate-from-description")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<Competency>> generateCompetenciesFromCourseDescription(@PathVariable Long courseId, @RequestBody String courseDescription) {
        var irisService = irisCompetencyGenerationSessionService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);

        var session = irisService.getOrCreateSession(course, user);
        irisService.addUserTextMessageToSession(session, courseDescription);
        var competencies = irisService.executeRequest(session);

        return ResponseEntity.ok().body(competencies);
    }

    /**
     * GET courses/{courseId}/competencies/titles : Returns the titles of all course competencies. Used for a validator in the client
     *
     * @param courseId the id of the current course
     * @return the titles of all course competencies
     */
    @GetMapping("courses/{courseId}/competencies/titles")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<String>> getCourseCompetencyTitles(@PathVariable Long courseId) {
        final var titles = courseCompetencyRepository.findAllTitlesByCourseId(courseId);
        return ResponseEntity.ok(titles);
    }

    /**
     * Checks if the competency matches the course.
     *
     * @param course     The course for which to check the authorization role for
     * @param competency The competency to be accessed by the user
     */
    private void checkAuthorizationForCompetency(@NotNull Course course, @NotNull CourseCompetency competency) {
        if (competency.getCourse() == null) {
            throw new BadRequestAlertException("A competency must belong to a course", ENTITY_NAME, "competencyNoCourse");
        }
        if (!competency.getCourse().getId().equals(course.getId())) {
            throw new BadRequestAlertException("The competency does not belong to the correct course", ENTITY_NAME, "competencyWrongCourse");
        }
    }

    /**
     * PUT courses/:courseId/competencies/:competencyId/jol/:jolValue : Sets the judgement of learning for a competency
     *
     * @param courseId     the id of the course for which the competency belongs
     * @param competencyId the id of the competency for which to set the judgement of learning
     * @param jolValue     the value of the judgement of learning
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/competencies/{competencyId}/jol/{jolValue}")
    @FeatureToggle(Feature.StudentCourseAnalyticsDashboard)
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Void> setJudgementOfLearning(@PathVariable long courseId, @PathVariable long competencyId, @PathVariable short jolValue) {
        log.debug("REST request to set judgement of learning for competency: {}", competencyId);

        final var userId = userRepository.getUserIdElseThrow();
        competencyService.checkIfCompetencyBelongsToCourse(competencyId, courseId);
        competencyJolService.setJudgementOfLearning(competencyId, userId, jolValue);

        return ResponseEntity.ok().build();
    }

    /**
     * GET courses/:courseId/competencies/:competencyId/jol : Gets the latest (current and prior) judgement of learning for a competency
     *
     * @param courseId     the id of the course for which the competency belongs
     * @param competencyId the id of the competency for which to set the judgement of learning
     * @return the ResponseEntity with status 200 (OK) and body the judgement of learning values
     */
    @GetMapping("courses/{courseId}/competencies/{competencyId}/jol")
    @FeatureToggle(Feature.StudentCourseAnalyticsDashboard)
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CompetencyJolPairDTO> getLatestJudgementOfLearningForCompetency(@PathVariable long courseId, @PathVariable long competencyId) {
        log.debug("REST request to get judgement of learning for competency: {}", competencyId);

        final var userId = userRepository.getUserIdElseThrow();
        competencyService.checkIfCompetencyBelongsToCourse(competencyId, courseId);
        final var jol = competencyJolService.getLatestJudgementOfLearningPairForUserByCompetencyId(userId, competencyId);

        return ResponseEntity.ok(jol);
    }

    /**
     * GET courses/:courseId/competencies/jol : Gets the latest (current and prior) judgement of learning for all competencies of a course
     *
     * @param courseId the id of the course for which the competency belongs
     * @return the ResponseEntity with status 200 (OK) and body the judgement of learning values for all competencies of the course as a map from competency id to jol value pairs
     */
    @GetMapping("courses/{courseId}/competencies/jol")
    @FeatureToggle(Feature.StudentCourseAnalyticsDashboard)
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Map<Long, CompetencyJolPairDTO>> getLatestJudgementOfLearningForCourse(@PathVariable long courseId) {
        log.debug("REST request to get judgement of learning for competencies of course: {}", courseId);

        final var userId = userRepository.getUserIdElseThrow();
        final var jols = competencyJolService.getLatestJudgementOfLearningForUserByCourseId(userId, courseId);

        return ResponseEntity.ok(jols);
    }
}
