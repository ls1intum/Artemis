package de.tum.in.www1.artemis.web.rest.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

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
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseCompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.service.competency.CompetencyService;
import de.tum.in.www1.artemis.service.competency.CourseCompetencyService;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyImportResponseDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;
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

    private final CompetencyService competencyService;

    private final LectureUnitService lectureUnitService;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CourseCompetencyService courseCompetencyService;

    public CompetencyResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            CompetencyRepository competencyRepository, CompetencyService competencyService, LectureUnitService lectureUnitService,
            CourseCompetencyRepository courseCompetencyRepository, CourseCompetencyService courseCompetencyService) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.competencyRepository = competencyRepository;
        this.competencyService = competencyService;
        this.lectureUnitService = lectureUnitService;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.courseCompetencyService = courseCompetencyService;
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
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyService.findCompetencyWithExercisesAndLectureUnitsAndProgressForUser(competencyId, currentUser.getId());
        checkCourseForCompetency(course, competency);

        courseCompetencyService.filterOutLearningObjectsThatUserShouldNotSee(competency, currentUser);

        return ResponseEntity.ok(competency);
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

        final var persistedCompetency = competencyService.createCourseCompetency(competency, course);

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
     * @param courseId     the id of the course to which the competency should be imported to
     * @param competencyId the id of the competency that should be imported
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/import")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Competency> importCompetency(@PathVariable long courseId, @RequestBody long competencyId) throws URISyntaxException {
        log.info("REST request to import a competency: {}", competencyId);

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
        var competencyToImport = courseCompetencyRepository.findByIdElseThrow(competencyId);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competencyToImport.getCourse(), null);
        if (competencyToImport.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The competency is already added to this course", ENTITY_NAME, "competencyCycle");
        }

        Competency createdCompetency = competencyService.createCompetency(competencyToImport, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/" + createdCompetency.getId())).body(createdCompetency);
    }

    /**
     * POST courses/:courseId/competencies/import/bulk : imports a number of competencies (and optionally their relations) into a course.
     *
     * @param courseId        the id of the course to which the competencies should be imported to
     * @param competencyIds   the ids of the competencies that should be imported
     * @param importRelations if relations should be imported as well
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competencies
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/import/bulk")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Set<CompetencyWithTailRelationDTO>> importCompetencies(@PathVariable long courseId, @RequestBody Set<Long> competencyIds,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to import competencies: {}", competencyIds);

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        List<CourseCompetency> competenciesToImport = courseCompetencyRepository.findAllById(competencyIds);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        competenciesToImport.forEach(competencyToImport -> {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competencyToImport.getCourse(), user);
            if (competencyToImport.getCourse().getId().equals(courseId)) {
                throw new BadRequestAlertException("The competency is already added to this course", ENTITY_NAME, "competencyCycle");
            }
        });

        Set<CompetencyWithTailRelationDTO> importedCompetencies;
        if (importRelations) {
            importedCompetencies = competencyService.importCompetenciesAndRelations(course, competenciesToImport);
        }
        else {
            importedCompetencies = competencyService.importCompetencies(course, competenciesToImport);
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(importedCompetencies);
    }

    /**
     * POST courses/{courseId}/competencies/import-all/{sourceCourseId} : Imports all competencies of the source course (and optionally their relations) into another.
     *
     * @param courseId        the id of the course to import into
     * @param sourceCourseId  the id of the course to import from
     * @param importRelations if relations should be imported as well
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competencies (and relations)
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/import-all/{sourceCourseId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Set<CompetencyWithTailRelationDTO>> importAllCompetenciesFromCourse(@PathVariable long courseId, @PathVariable long sourceCourseId,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to all competencies from course {} into course {}", sourceCourseId, courseId);

        if (courseId == sourceCourseId) {
            throw new BadRequestAlertException("Cannot import from a course into itself", "Course", "courseCycle");
        }
        var targetCourse = courseRepository.findByIdElseThrow(courseId);
        var sourceCourse = courseRepository.findByIdElseThrow(sourceCourseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, sourceCourse, null);

        var competencies = competencyRepository.findAllForCourse(sourceCourse.getId());
        Set<CompetencyWithTailRelationDTO> importedCompetencies;

        if (importRelations) {
            importedCompetencies = competencyService.importCompetenciesAndRelations(targetCourse, competencies);
        }
        else {
            importedCompetencies = competencyService.importCompetencies(targetCourse, competencies);
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

        var course = courseRepository.findByIdElseThrow(courseId);
        var importedCompetencies = competencyService.importStandardizedCompetencies(competencyIdsToImport, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/")).body(importedCompetencies.stream().map(CompetencyImportResponseDTO::of).toList());
    }

    /**
     * PUT courses/:courseId/competencies : Updates an existing competency.
     *
     * @param courseId   the id of the course to which the competency belongs
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
        checkCourseForCompetency(course, existingCompetency);

        var persistedCompetency = competencyService.updateCourseCompetency(existingCompetency, competency);
        lectureUnitService.linkLectureUnitsToCompetency(persistedCompetency, competency.getLectureUnits(), existingCompetency.getLectureUnits());

        return ResponseEntity.ok(persistedCompetency);
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
        checkCourseForCompetency(course, competency);

        courseCompetencyService.deleteCourseCompetency(competency, course);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, competency.getTitle())).build();
    }

    /**
     * Checks if the competency matches the course.
     *
     * @param course     The course for which to check the authorization role for
     * @param competency The competency to be accessed by the user
     */
    private void checkCourseForCompetency(@NotNull Course course, @NotNull CourseCompetency competency) {
        if (competency.getCourse() == null) {
            throw new BadRequestAlertException("A competency must belong to a course", ENTITY_NAME, "competencyNoCourse");
        }
        if (!competency.getCourse().getId().equals(course.getId())) {
            throw new BadRequestAlertException("The competency does not belong to the correct course", ENTITY_NAME, "competencyWrongCourse");
        }
    }
}
