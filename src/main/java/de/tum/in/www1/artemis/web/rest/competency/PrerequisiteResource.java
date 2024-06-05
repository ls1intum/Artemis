package de.tum.in.www1.artemis.web.rest.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.PrerequisiteRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.competency.PrerequisiteService;
import de.tum.in.www1.artemis.web.rest.dto.competency.PrerequisiteRequestDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.PrerequisiteResponseDTO;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.competency.Prerequisite Prerequisite} entities.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class PrerequisiteResource {

    private static final Logger log = LoggerFactory.getLogger(PrerequisiteResource.class);

    private final PrerequisiteService prerequisiteService;

    private final PrerequisiteRepository prerequisiteRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public PrerequisiteResource(PrerequisiteService prerequisiteService, PrerequisiteRepository prerequisiteRepository, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.prerequisiteService = prerequisiteService;
        this.prerequisiteRepository = prerequisiteRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /courses/:courseId/competencies/prerequisites : Gets all prerequisite competencies for a course.
     * This endpoint allows all students to view prerequisites of a course if self-enrollment is activated (and thus only uses @EnforceAtLeastStudent)
     *
     * @param courseId the id of the course for which the prerequisites should be fetched for
     * @return the ResponseEntity with status 200 (OK) and with body the found prerequisites
     */
    @GetMapping("courses/{courseId}/competencies/prerequisites")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PrerequisiteResponseDTO>> getPrerequisites(@PathVariable long courseId) {
        log.debug("REST request to get prerequisites for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);

        // Allow any student to see the prerequisites if the course is open to self-enrollment
        if (!course.isEnrollmentEnabled()) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        }

        var prerequisites = prerequisiteRepository.findByCourseIdOrderById(courseId);

        return ResponseEntity.ok(prerequisites.stream().map(PrerequisiteResponseDTO::of).toList());
    }

    /**
     * GET /courses/:courseId/competencies/prerequisites/:prerequisiteId : Gets the prerequisite competency with the given id for a course.
     *
     * @param prerequisiteId the id of the prerequisite to fetch
     * @param courseId       the id of the course in which the prerequisite should exist
     * @return the ResponseEntity with status 200 (OK) and with body the found prerequisite or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/competencies/prerequisites/{prerequisiteId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<PrerequisiteResponseDTO> getPrerequisite(@PathVariable long prerequisiteId, @PathVariable long courseId) {
        log.debug("REST request to get prerequisite with id: {}", prerequisiteId);

        var prerequisite = prerequisiteRepository.findByIdAndCourseIdElseThrow(prerequisiteId, courseId);

        return ResponseEntity.ok(PrerequisiteResponseDTO.of(prerequisite));
    }

    /**
     * POST /courses/:courseId/prerequisites : creates a new prerequisite competency.
     *
     * @param courseId     the id of the course to which the competency should be added
     * @param prerequisite the prerequisite that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new prerequisite
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/prerequisites")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<PrerequisiteResponseDTO> createPrerequisite(@PathVariable long courseId, @RequestBody PrerequisiteRequestDTO prerequisite) throws URISyntaxException {
        log.debug("REST request to create Prerequisite : {}", prerequisite);

        final var savedPrerequisite = prerequisiteService.createPrerequisite(prerequisite, courseId);
        final var uri = new URI("/api/courses/" + courseId + "/prerequisites/" + savedPrerequisite.getId());

        return ResponseEntity.created(uri).body(PrerequisiteResponseDTO.of(savedPrerequisite));
    }

    /**
     * PUT /courses/:courseId/prerequisites/:prerequisiteId : updates an existing prerequisite
     *
     * @param courseId           the id of the course to which the prerequisite belongs
     * @param prerequisiteId     the id of the prerequisite to update
     * @param prerequisiteValues the new prerequisite values
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/competencies/prerequisites/{prerequisiteId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<PrerequisiteResponseDTO> updatePrerequisite(@PathVariable long courseId, @PathVariable long prerequisiteId,
            @RequestBody PrerequisiteRequestDTO prerequisiteValues) {
        log.info("REST request to update Prerequisite with id : {}", prerequisiteId);

        final var savedPrerequisite = prerequisiteService.updatePrerequisite(prerequisiteValues, prerequisiteId, courseId);

        return ResponseEntity.ok(PrerequisiteResponseDTO.of(savedPrerequisite));
    }

    /**
     * DELETE /courses/:courseId/prerequisites/:prerequisiteId : deletes an existing prerequisite
     *
     * @param courseId       the id of the course to which the prerequisite belongs
     * @param prerequisiteId the id of the prerequisite to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/competencies/prerequisites/{prerequisiteId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> deletePrerequisite(@PathVariable long prerequisiteId, @PathVariable long courseId) {
        log.info("REST request to delete Prerequisite with id : {}", prerequisiteId);

        prerequisiteService.deletePrerequisite(prerequisiteId, courseId);

        return ResponseEntity.ok().build();
    }

    /**
     * POST /courses/:courseId/prerequisites/import : imports a number of CourseCompetencies as Prerequisites
     *
     * @param courseId            the id of the course to which the prerequisites should be imported to
     * @param courseCompetencyIds the ids of the CourseCompetencies to import
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported prerequisites
     * @throws URISyntaxException if the location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/competencies/prerequisites/import")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<PrerequisiteResponseDTO>> importPrerequisites(@PathVariable long courseId, @RequestBody List<Long> courseCompetencyIds) throws URISyntaxException {
        log.info("REST request to import courseCompetencies with ids {} as prerequisites", courseCompetencyIds);

        var importedPrerequisites = prerequisiteService.importPrerequisites(courseId, courseCompetencyIds);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/prerequisites"))
                .body(importedPrerequisites.stream().map(PrerequisiteResponseDTO::of).toList());
    }
}
