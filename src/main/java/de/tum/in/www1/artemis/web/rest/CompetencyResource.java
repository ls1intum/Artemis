package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;
import de.tum.in.www1.artemis.service.competency.CompetencyRelationService;
import de.tum.in.www1.artemis.service.competency.CompetencyService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
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

    private final LectureUnitService lectureUnitService;

    private final CompetencyRelationService competencyRelationService;

    public CompetencyResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            CompetencyRepository competencyRepository, CompetencyRelationRepository competencyRelationRepository, CompetencyService competencyService,
            CompetencyProgressRepository competencyProgressRepository, CompetencyProgressService competencyProgressService, ExerciseService exerciseService,
            LectureUnitService lectureUnitService, CompetencyRelationService competencyRelationService) {
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
    }

    /**
     * GET /competencies/:competencyId/title : Returns the title of the competency with the given id
     *
     * @param competencyId the id of the competency
     * @return the title of the competency wrapped in an ResponseEntity or 404 Not Found if no competency with that id exists
     */
    @GetMapping("/competencies/{competencyId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getCompetencyTitle(@PathVariable long competencyId) {
        final var title = competencyRepository.getCompetencyTitle(competencyId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * Search for all competencies by title and course title. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("competencies")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<Competency>> getAllCompetenciesOnPage(PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(competencyService.getAllOnPageWithSize(search, user));
    }

    /**
     * GET /courses/:courseId/competencies : gets all the competencies of a course
     *
     * @param courseId the id of the course for which the competencies should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found competencies
     */
    @GetMapping("/courses/{courseId}/competencies")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Competency>> getCompetenciesWithProgress(@PathVariable long courseId) {
        log.debug("REST request to get competencies for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        final var competencies = competencyRepository.findByCourseIdWithProgressOfUser(courseId, user.getId());
        return ResponseEntity.ok(competencies);
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId : gets the competency with the specified id including its related exercises and lecture units
     * This method also calculates the user progress
     *
     * @param competencyId the id of the competency to retrieve
     * @param courseId     the id of the course to which the competency belongs
     * @return the ResponseEntity with status 200 (OK) and with body the competency, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Competency> getCompetency(@PathVariable long competencyId, @PathVariable long courseId) {
        log.info("REST request to get Competency : {}", competencyId);
        long start = System.nanoTime();
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithExercisesAndLectureUnitsAndProgressForUserElseThrow(competencyId, currentUser.getId());
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

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
     * PUT /courses/:courseId/competencies : Updates an existing competency.
     *
     * @param courseId   the id of the course to which the competencies belong
     * @param competency the competency to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated competency
     */
    @PutMapping("/courses/{courseId}/competencies")
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> updateCompetency(@PathVariable long courseId, @RequestBody Competency competency) {
        log.debug("REST request to update Competency : {}", competency);
        if (competency.getId() == null) {
            throw new BadRequestException();
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var existingCompetency = competencyRepository.findByIdWithLectureUnitsElseThrow(competency.getId());
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, existingCompetency);

        var persistedCompetency = competencyService.updateCompetency(existingCompetency, competency);
        lectureUnitService.linkLectureUnitsToCompetency(persistedCompetency, competency.getLectureUnits(), existingCompetency.getLectureUnits());

        return ResponseEntity.ok(persistedCompetency);
    }

    /**
     * POST /courses/:courseId/competencies : creates a new competency.
     *
     * @param courseId   the id of the course to which the competency should be added
     * @param competency the competency that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/competencies")
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> createCompetency(@PathVariable long courseId, @RequestBody Competency competency) throws URISyntaxException {
        log.debug("REST request to create Competency : {}", competency);
        if (competency.getId() != null || competency.getTitle() == null || competency.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        final var persistedCompetency = competencyService.createCompetency(competency, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/" + persistedCompetency.getId())).body(persistedCompetency);
    }

    /**
     * POST /courses/:courseId/competencies/import : imports a new competency.
     *
     * @param courseId           the id of the course to which the competency should be imported to
     * @param competencyToImport the competency that should be imported
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/competencies/import")
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> importCompetency(@PathVariable long courseId, @RequestBody Competency competencyToImport) throws URISyntaxException {
        log.info("REST request to import a competency: {}", competencyToImport.getId());

        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competencyToImport.getCourse(), null);

        if (competencyToImport.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The competency is already added to this course", ENTITY_NAME, "competencyCycle");
        }

        competencyToImport = competencyService.createCompetency(competencyToImport, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/" + competencyToImport.getId())).body(competencyToImport);
    }

    /**
     * Imports all competencies of the source course (and optionally their relations) into another
     *
     * @param courseId        the id of the course to import into
     * @param sourceCourseId  the id of the course to import from
     * @param importRelations if relations should be imported aswell
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competencies (and relations)
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/competencies/import-all/{sourceCourseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<CompetencyWithTailRelationDTO>> importAllCompetenciesFromCourse(@PathVariable long courseId, @PathVariable long sourceCourseId,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to all competencies from course {} into course {}", sourceCourseId, courseId);

        if (courseId == sourceCourseId) {
            throw new BadRequestAlertException("Cannot import from a course into itself", "Course", "courseCycle");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var sourceCourse = courseRepository.findByIdElseThrow(sourceCourseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, sourceCourse, null);

        List<CompetencyWithTailRelationDTO> importedCompetencies = competencyService.importAllCompetenciesFromCourse(course, sourceCourse, importRelations);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(importedCompetencies);
    }

    /**
     * DELETE /courses/:courseId/competencies/:competencyId
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/competencies/{competencyId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteCompetency(@PathVariable long competencyId, @PathVariable long courseId) {
        log.info("REST request to delete a Competency : {}", competencyId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        competencyService.deleteCompetency(competency, course);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, competency.getTitle())).build();
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId/student-progress gets the competency progress for a user
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to get the progress
     * @param refresh      whether to update the student progress or fetch it from the database (default)
     * @return the ResponseEntity with status 200 (OK) and with the competency course performance in the body
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}/student-progress")
    @EnforceAtLeastStudent
    public ResponseEntity<CompetencyProgress> getCompetencyStudentProgress(@PathVariable long courseId, @PathVariable long competencyId,
            @RequestParam(defaultValue = "false") Boolean refresh) {
        log.debug("REST request to get student progress for competency: {}", competencyId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

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
     * GET /courses/:courseId/competencies/:competencyId/course-progress gets the competency progress for the whole course
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the competency course performance in the body
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}/course-progress")
    @EnforceAtLeastInstructor
    public ResponseEntity<CourseCompetencyProgressDTO> getCompetencyCourseProgress(@PathVariable long courseId, @PathVariable long competencyId) {
        log.debug("REST request to get course progress for competency: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithLectureUnitsAndCompletionsElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var progress = competencyProgressService.getCompetencyCourseProgress(competency, course);

        return ResponseEntity.ok().body(progress);
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId/relations get the relations for the competency
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to fetch all relations
     * @return the ResponseEntity with status 200 (OK) and with a list of relations for the competency in the body
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}/relations")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<CompetencyRelation>> getCompetencyRelations(@PathVariable long competencyId, @PathVariable long courseId) {
        log.debug("REST request to get relations for Competency : {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

        var relations = competencyRelationRepository.findAllByCompetencyId(competency.getId());

        return ResponseEntity.ok().body(relations);
    }

    /**
     * POST /courses/:courseId/competencies/:tailCompetencyId/relations/headCompetencyId
     *
     * @param courseId         the id of the course to which the competencies belong
     * @param tailCompetencyId the id of the competency at the tail of the relation
     * @param headCompetencyId the id of the competency at the head of the relation
     * @param type             the type of the relation as request parameter
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/competencies/{tailCompetencyId}/relations/{headCompetencyId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<CompetencyRelation> createCompetencyRelation(@PathVariable long courseId, @PathVariable long tailCompetencyId, @PathVariable long headCompetencyId,
            @RequestParam(defaultValue = "") String type) {
        log.info("REST request to create a relation between competencies {} and {}", tailCompetencyId, headCompetencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var tailCompetency = competencyRepository.findByIdElseThrow(tailCompetencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, tailCompetency);
        var headCompetency = competencyRepository.findByIdElseThrow(headCompetencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, headCompetency);

        var relation = competencyRelationService.createCompetencyRelation(tailCompetency, headCompetency, type, course);

        return ResponseEntity.ok().body(relation);
    }

    /**
     * DELETE /courses/:courseId/competencies/:competencyId/relations/:competencyRelationId
     *
     * @param courseId             the id of the course
     * @param competencyId         the id of the competency to which the relation belongs
     * @param competencyRelationId the id of the competency relation
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/competencies/{competencyId}/relations/{competencyRelationId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeCompetencyRelation(@PathVariable long competencyId, @PathVariable long courseId, @PathVariable long competencyRelationId) {
        log.info("REST request to remove a competency relation: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var relation = competencyRelationRepository.findById(competencyRelationId).orElseThrow();
        if (!relation.getTailCompetency().getId().equals(competency.getId())) {
            throw new BadRequestException("The relation does not belong to the specified competency");
        }

        competencyRelationRepository.delete(relation);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/:courseId/prerequisites
     *
     * @param courseId the id of the course for which the competencies should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found competencies
     */
    @GetMapping("/courses/{courseId}/prerequisites")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Competency>> getPrerequisites(@PathVariable long courseId) {
        log.debug("REST request to get prerequisites for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        // Authorization check is skipped when course is open to self-enrollment
        if (!course.isEnrollmentEnabled()) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        }

        Set<Competency> prerequisites = competencyService.findAllPrerequisitesForCourse(course);

        return ResponseEntity.ok(new ArrayList<>(prerequisites));
    }

    /**
     * POST /courses/:courseId/prerequisites/:competencyId
     *
     * @param courseId     the id of the course for which the competency should be a prerequisite
     * @param competencyId the id of the prerequisite (competency) to add
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/prerequisites/{competencyId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> addPrerequisite(@PathVariable long competencyId, @PathVariable long courseId) {
        log.info("REST request to add a prerequisite: {}", competencyId);
        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithConsecutiveCoursesElseThrow(competencyId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competency.getCourse(), null);

        if (competency.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The competency of a course can not be a prerequisite to the same course", ENTITY_NAME, "competencyCycle");
        }

        course.addPrerequisite(competency);
        courseRepository.save(course);
        return ResponseEntity.ok().body(competency);
    }

    /**
     * DELETE /courses/:courseId/prerequisites/:competencyId
     *
     * @param courseId     the id of the course for which the competency is a prerequisite
     * @param competencyId the id of the prerequisite (competency) to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/prerequisites/{competencyId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removePrerequisite(@PathVariable long competencyId, @PathVariable long courseId) {
        log.info("REST request to remove a prerequisite: {}", competencyId);
        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithConsecutiveCoursesElseThrow(competencyId);
        if (!competency.getConsecutiveCourses().stream().map(Course::getId).toList().contains(courseId)) {
            throw new BadRequestAlertException("The competency is not a prerequisite of the given course", ENTITY_NAME, "prerequisiteWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competency.getCourse(), null);

        course.removePrerequisite(competency);
        courseRepository.save(course);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, competency.getTitle())).build();
    }

    /**
     * Checks if the user has the necessary permissions and the competency matches the course.
     *
     * @param role       The minimal role the user must have in the course
     * @param course     The course for which to check the authorization role for
     * @param competency The competency to be accessed by the user
     */
    private void checkAuthorizationForCompetency(Role role, @NotNull Course course, @NotNull Competency competency) {
        if (competency.getCourse() == null) {
            throw new BadRequestAlertException("A competency must belong to a course", ENTITY_NAME, "competencyNoCourse");
        }
        if (!competency.getCourse().getId().equals(course.getId())) {
            throw new BadRequestAlertException("The competency does not belong to the correct course", ENTITY_NAME, "competencyWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, null);
    }
}
