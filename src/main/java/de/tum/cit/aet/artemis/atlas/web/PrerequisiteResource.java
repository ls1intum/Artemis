package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.atlas.service.competency.PrerequisiteService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

/**
 * REST controller for managing {@link Prerequisite Prerequisite} entities.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class PrerequisiteResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final Logger log = LoggerFactory.getLogger(PrerequisiteResource.class);

    private static final String ENTITY_NAME = "prerequisite";

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final PrerequisiteRepository prerequisiteRepository;

    private final PrerequisiteService prerequisiteService;

    private final LectureUnitService lectureUnitService;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CourseCompetencyService courseCompetencyService;

    public PrerequisiteResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            PrerequisiteRepository prerequisiteRepository, PrerequisiteService prerequisiteService, LectureUnitService lectureUnitService,
            CourseCompetencyRepository courseCompetencyRepository, CourseCompetencyService courseCompetencyService) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.prerequisiteService = prerequisiteService;
        this.lectureUnitService = lectureUnitService;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.courseCompetencyService = courseCompetencyService;
    }

    /**
     * GET courses/:courseId/prerequisites : gets all the prerequisites of a course
     *
     * @param courseId the id of the course for which the prerequisites should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found prerequisites
     */
    @GetMapping("courses/{courseId}/prerequisites")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Prerequisite>> getPrerequisitesWithProgress(@PathVariable long courseId) {
        log.debug("REST request to get prerequisites for course with id: {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        final var prerequisites = prerequisiteService.findPrerequisitesWithProgressForUserByCourseId(courseId, user.getId());
        return ResponseEntity.ok(prerequisites);
    }

    /**
     * GET courses/:courseId/prerequisites/:prerequisiteId : gets the prerequisite with the specified id including its related exercises and lecture units
     * This method also calculates the user progress
     *
     * @param prerequisiteId the id of the prerequisite to retrieve
     * @param courseId       the id of the course to which the prerequisite belongs
     * @return the ResponseEntity with status 200 (OK) and with body the prerequisite, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/prerequisites/{prerequisiteId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Prerequisite> getPrerequisite(@PathVariable long prerequisiteId, @PathVariable long courseId) {
        log.info("REST request to get Prerequisite : {}", prerequisiteId);
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var prerequisite = prerequisiteService.findPrerequisiteWithExercisesAndLectureUnitsAndProgressForUser(prerequisiteId, currentUser.getId());
        checkCourseForPrerequisite(course, prerequisite);

        courseCompetencyService.filterOutLearningObjectsThatUserShouldNotSee(prerequisite, currentUser);

        return ResponseEntity.ok(prerequisite);
    }

    /**
     * POST courses/:courseId/prerequisites : creates a new prerequisite.
     *
     * @param courseId     the id of the course to which the prerequisite should be added
     * @param prerequisite the prerequisite that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new prerequisite
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Prerequisite> createPrerequisite(@PathVariable long courseId, @RequestBody Prerequisite prerequisite) throws URISyntaxException {
        log.debug("REST request to create Prerequisite : {}", prerequisite);
        if (prerequisite.getId() != null || prerequisite.getTitle() == null || prerequisite.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        final var persistedPrerequisite = prerequisiteService.createCourseCompetency(prerequisite, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/" + persistedPrerequisite.getId())).body(persistedPrerequisite);
    }

    /**
     * POST courses/:courseId/prerequisites/bulk : creates a number of new prerequisites
     *
     * @param courseId      the id of the course to which the prerequisites should be added
     * @param prerequisites the prerequisites that should be created
     * @return the ResponseEntity with status 201 (Created) and body the created prerequisites
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/bulk")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<Prerequisite>> createPrerequisite(@PathVariable Long courseId, @RequestBody List<Prerequisite> prerequisites) throws URISyntaxException {
        log.debug("REST request to create Prerequisites : {}", prerequisites);
        for (Prerequisite prerequisite : prerequisites) {
            if (prerequisite.getId() != null || prerequisite.getTitle() == null || prerequisite.getTitle().trim().isEmpty()) {
                throw new BadRequestException();
            }
        }
        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        var createdPrerequisites = prerequisiteService.createPrerequisites(prerequisites, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/")).body(createdPrerequisites);
    }

    /**
     * POST courses/:courseId/prerequisites/import : imports a new prerequisite.
     *
     * @param courseId       the id of the course to which the prerequisite should be imported to
     * @param prerequisiteId the id of the prerequisite that should be imported
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisite
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/import")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Prerequisite> importPrerequisite(@PathVariable long courseId, @RequestBody long prerequisiteId) throws URISyntaxException {
        log.info("REST request to import a prerequisite: {}", prerequisiteId);

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
        var prerequisiteToImport = courseCompetencyRepository.findByIdElseThrow(prerequisiteId);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, prerequisiteToImport.getCourse(), null);
        if (prerequisiteToImport.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The prerequisite is already added to this course", ENTITY_NAME, "prerequisiteCycle");
        }

        Prerequisite createdPrerequisite = prerequisiteService.createPrerequisite(prerequisiteToImport, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/" + createdPrerequisite.getId())).body(createdPrerequisite);
    }

    /**
     * POST courses/:courseId/prerequisites/import/bulk : imports a number of prerequisites (and optionally their relations) into a course.
     *
     * @param courseId        the id of the course to which the prerequisites should be imported to
     * @param prerequisiteIds the ids of the prerequisites that should be imported
     * @param importRelations if relations should be imported as well
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisites
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/import/bulk")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Set<CompetencyWithTailRelationDTO>> importPrerequisites(@PathVariable long courseId, @RequestBody Set<Long> prerequisiteIds,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to import prerequisites: {}", prerequisiteIds);

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        List<CourseCompetency> prerequisitesToImport = courseCompetencyRepository.findAllById(prerequisiteIds);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        prerequisitesToImport.forEach(prerequisiteToImport -> {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, prerequisiteToImport.getCourse(), user);
            if (prerequisiteToImport.getCourse().getId().equals(courseId)) {
                throw new BadRequestAlertException("The prerequisite is already added to this course", ENTITY_NAME, "prerequisiteCycle");
            }
        });

        Set<CompetencyWithTailRelationDTO> importedPrerequisites;
        if (importRelations) {
            importedPrerequisites = prerequisiteService.importPrerequisitesAndRelations(course, prerequisitesToImport);
        }
        else {
            importedPrerequisites = prerequisiteService.importPrerequisites(course, prerequisitesToImport);
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/")).body(importedPrerequisites);
    }

    /**
     * POST courses/{courseId}/prerequisites/import-all/{sourceCourseId} : Imports all prerequisites of the source course (and optionally their relations) into another.
     *
     * @param courseId        the id of the course to import into
     * @param sourceCourseId  the id of the course to import from
     * @param importRelations if relations should be imported as well
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisites (and relations)
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/import-all/{sourceCourseId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Set<CompetencyWithTailRelationDTO>> importAllPrerequisitesFromCourse(@PathVariable long courseId, @PathVariable long sourceCourseId,
            @RequestParam(defaultValue = "false") boolean importRelations) throws URISyntaxException {
        log.info("REST request to all prerequisites from course {} into course {}", sourceCourseId, courseId);

        if (courseId == sourceCourseId) {
            throw new BadRequestAlertException("Cannot import from a course into itself", "Course", "courseCycle");
        }
        var targetCourse = courseRepository.findByIdElseThrow(courseId);
        var sourceCourse = courseRepository.findByIdElseThrow(sourceCourseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, sourceCourse, null);

        var prerequisites = prerequisiteRepository.findAllForCourse(sourceCourse.getId());
        Set<CompetencyWithTailRelationDTO> importedPrerequisites;

        if (importRelations) {
            importedPrerequisites = prerequisiteService.importPrerequisitesAndRelations(targetCourse, prerequisites);
        }
        else {
            importedPrerequisites = prerequisiteService.importPrerequisites(targetCourse, prerequisites);
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/")).body(importedPrerequisites);
    }

    /**
     * POST courses/:courseId/prerequisites/import-standardized : imports a number of standardized prerequisites (as prerequisites) into a course.
     *
     * @param courseId                the id of the course to which the prerequisites should be imported to
     * @param prerequisiteIdsToImport the ids of the standardized prerequisites that should be imported
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisites
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/import-standardized")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<CompetencyImportResponseDTO>> importStandardizedPrerequisites(@PathVariable long courseId, @RequestBody List<Long> prerequisiteIdsToImport)
            throws URISyntaxException {
        log.info("REST request to import standardized prerequisites with ids: {}", prerequisiteIdsToImport);

        var course = courseRepository.findByIdElseThrow(courseId);
        var importedPrerequisites = prerequisiteService.importStandardizedPrerequisites(prerequisiteIdsToImport, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/")).body(importedPrerequisites.stream().map(CompetencyImportResponseDTO::of).toList());
    }

    /**
     * PUT courses/:courseId/prerequisites : Updates an existing prerequisite.
     *
     * @param courseId     the id of the course to which the prerequisite belongs
     * @param prerequisite the prerequisite to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated prerequisite
     */
    @PutMapping("courses/{courseId}/prerequisites")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Prerequisite> updatePrerequisite(@PathVariable long courseId, @RequestBody Prerequisite prerequisite) {
        log.debug("REST request to update Prerequisite : {}", prerequisite);
        if (prerequisite.getId() == null) {
            throw new BadRequestException();
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var existingPrerequisite = prerequisiteRepository.findByIdWithLectureUnitsElseThrow(prerequisite.getId());
        checkCourseForPrerequisite(course, existingPrerequisite);

        var persistedPrerequisite = prerequisiteService.updateCourseCompetency(existingPrerequisite, prerequisite);
        lectureUnitService.linkLectureUnitsToCompetency(persistedPrerequisite, prerequisite.getLectureUnits(), existingPrerequisite.getLectureUnits());

        return ResponseEntity.ok(persistedPrerequisite);
    }

    /**
     * DELETE courses/:courseId/prerequisites/:prerequisiteId
     *
     * @param courseId       the id of the course to which the prerequisite belongs
     * @param prerequisiteId the id of the prerequisite to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/prerequisites/{prerequisiteId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deletePrerequisite(@PathVariable long prerequisiteId, @PathVariable long courseId) {
        log.info("REST request to delete a Prerequisite : {}", prerequisiteId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var prerequisite = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(prerequisiteId);
        checkCourseForPrerequisite(course, prerequisite);

        courseCompetencyService.deleteCourseCompetency(prerequisite, course);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, prerequisite.getTitle())).build();
    }

    /**
     * Checks if the prerequisite matches the course.
     *
     * @param course       The course for which to check the authorization role for
     * @param prerequisite The prerequisite to be accessed by the user
     */
    private void checkCourseForPrerequisite(@NotNull Course course, @NotNull CourseCompetency prerequisite) {
        if (prerequisite.getCourse() == null) {
            throw new BadRequestAlertException("A prerequisite must belong to a course", ENTITY_NAME, "prerequisiteNoCourse");
        }
        if (!prerequisite.getCourse().getId().equals(course.getId())) {
            throw new BadRequestAlertException("The prerequisite does not belong to the correct course", ENTITY_NAME, "prerequisiteWrongCourse");
        }
    }
}
