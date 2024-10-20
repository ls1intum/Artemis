package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

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
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
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
        checkPrerequisitesAttributesForCreation(prerequisite);

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
            checkPrerequisitesAttributesForCreation(prerequisite);
        }
        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        var createdPrerequisites = prerequisiteService.createPrerequisites(prerequisites, course);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/")).body(createdPrerequisites);
    }

    /**
     * POST courses/:courseId/prerequisites/import : imports a new prerequisite.
     *
     * @param courseId      the id of the course to which the prerequisite should be imported to
     * @param importOptions the options for the import
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisite
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/import")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Prerequisite> importPrerequisite(@PathVariable long courseId, @RequestBody CompetencyImportOptionsDTO importOptions) throws URISyntaxException {
        log.info("REST request to import a prerequisite: {}", importOptions.competencyIds());

        if (importOptions.competencyIds() == null || importOptions.competencyIds().size() != 1) {
            throw new BadRequestAlertException("Exactly one prerequisite must be imported", ENTITY_NAME, "noPrerequisite");
        }
        long prerequisiteId = importOptions.competencyIds().iterator().next();

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
        var prerequisiteToImport = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLecturesElseThrow(prerequisiteId);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, prerequisiteToImport.getCourse(), null);
        if (prerequisiteToImport.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The prerequisite is already added to this course", ENTITY_NAME, "prerequisiteCycle");
        }

        Set<CompetencyWithTailRelationDTO> createdPrerequisites = prerequisiteService.importPrerequisites(course, Set.of(prerequisiteToImport), importOptions);
        Prerequisite createdPrerequisite = (Prerequisite) createdPrerequisites.iterator().next().competency();

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/" + createdPrerequisite.getId())).body(createdPrerequisite);
    }

    /**
     * POST courses/:courseId/prerequisites/import/bulk : imports a number of prerequisites (and optionally their relations) into a course.
     *
     * @param courseId      the id of the course to which the prerequisites should be imported to
     * @param importOptions the options for the import
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisites
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/import/bulk")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Set<CompetencyWithTailRelationDTO>> importPrerequisites(@PathVariable long courseId, @RequestBody CompetencyImportOptionsDTO importOptions)
            throws URISyntaxException {
        log.info("REST request to import prerequisites: {}", importOptions.competencyIds());

        if (importOptions.competencyIds() == null || importOptions.competencyIds().isEmpty()) {
            throw new BadRequestAlertException("No prerequisites to import", ENTITY_NAME, "noPrerequisites");
        }

        var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);

        Set<CourseCompetency> prerequisitesToImport = courseCompetencyRepository.findAllByIdWithExercisesAndLectureUnitsAndLecturesAndAttachments(importOptions.competencyIds());

        User user = userRepository.getUserWithGroupsAndAuthorities();
        prerequisitesToImport.forEach(prerequisiteToImport -> {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, prerequisiteToImport.getCourse(), user);
            if (prerequisiteToImport.getCourse().getId().equals(courseId)) {
                throw new BadRequestAlertException("The prerequisite is already added to this course", ENTITY_NAME, "prerequisiteCycle");
            }
        });

        Set<CompetencyWithTailRelationDTO> importedPrerequisites = prerequisiteService.importPrerequisites(course, prerequisitesToImport, importOptions);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/prerequisites/")).body(importedPrerequisites);
    }

    /**
     * POST courses/{courseId}/prerequisites/import-all : Imports all prerequisites of the source course (and optionally their relations) into another.
     *
     * @param courseId      the id of the course to import into
     * @param importOptions the options for the import
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisites (and relations)
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/prerequisites/import-all")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Set<CompetencyWithTailRelationDTO>> importAllPrerequisitesFromCourse(@PathVariable long courseId, @RequestBody CompetencyImportOptionsDTO importOptions)
            throws URISyntaxException {
        log.info("REST request to all prerequisites from course {} into course {}", importOptions.sourceCourseId(), courseId);

        if (importOptions.sourceCourseId().isEmpty()) {
            throw new BadRequestAlertException("No source course specified", ENTITY_NAME, "noSourceCourse");
        }
        else if (courseId == importOptions.sourceCourseId().get()) {
            throw new BadRequestAlertException("Cannot import from a course into itself", ENTITY_NAME, "courseCycle");
        }
        var targetCourse = courseRepository.findByIdElseThrow(courseId);
        var sourceCourse = courseRepository.findByIdElseThrow(importOptions.sourceCourseId().get());
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, sourceCourse, null);

        var prerequisites = prerequisiteRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(sourceCourse.getId());
        Set<CompetencyWithTailRelationDTO> importedPrerequisites = prerequisiteService.importPrerequisites(targetCourse, prerequisites, importOptions);

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
        checkPrerequisitesAttributesForUpdate(prerequisite);

        var course = courseRepository.findByIdElseThrow(courseId);
        var existingPrerequisite = prerequisiteRepository.findByIdWithLectureUnitsElseThrow(prerequisite.getId());
        checkCourseForPrerequisite(course, existingPrerequisite);

        var persistedPrerequisite = prerequisiteService.updateCourseCompetency(existingPrerequisite, prerequisite);

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

    private void checkPrerequisitesAttributesForCreation(Prerequisite prerequisite) {
        if (prerequisite.getId() != null) {
            throw new BadRequestAlertException("A new prerequiste should not have an id", ENTITY_NAME, "existingPrerequisiteId");
        }
        checkPrerequisitesAttributes(prerequisite);
    }

    private void checkPrerequisitesAttributesForUpdate(Prerequisite prerequisite) {
        if (prerequisite.getId() == null) {
            throw new BadRequestAlertException("An updated prerequiste should have an id", ENTITY_NAME, "missingPrerequisiteId");
        }
        checkPrerequisitesAttributes(prerequisite);
    }

    private void checkPrerequisitesAttributes(Prerequisite prerequisite) {
        if (prerequisite.getTitle() == null || prerequisite.getTitle().trim().isEmpty() || prerequisite.getMasteryThreshold() < 1 || prerequisite.getMasteryThreshold() > 100) {
            throw new BadRequestAlertException("The attributes of the competency are invalid!", ENTITY_NAME, "invalidPrerequisiteAttributes");
        }
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
