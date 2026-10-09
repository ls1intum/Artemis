package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstancesBatchCreateDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStatisticsDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStudentRowDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.assessment.service.PresentationAssessmentService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.web.util.PaginationUtil;

/**
 * REST controller for managing course-level presentation assessments.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
@FeatureToggle(Feature.PresentationAssessments)
@FeatureUsage(UserFeature.PRESENTATION_ASSESSMENT)
public class PresentationAssessmentResource {

    private static final Logger log = LoggerFactory.getLogger(PresentationAssessmentResource.class);

    private final PresentationAssessmentService presentationAssessmentService;

    private final PresentationAssessmentRepository presentationAssessmentRepository;

    public PresentationAssessmentResource(PresentationAssessmentService presentationAssessmentService, PresentationAssessmentRepository presentationAssessmentRepository) {
        this.presentationAssessmentService = presentationAssessmentService;
        this.presentationAssessmentRepository = presentationAssessmentRepository;
    }

    /**
     * GET /api/assessment/courses/{courseId}/presentation-assessments : get all presentation assessments for a course.
     *
     * @param courseId the course id
     * @return the ResponseEntity with status 200 (OK) and the presentation assessments
     */
    @GetMapping("courses/{courseId}/presentation-assessments")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<PresentationAssessmentDTO>> getPresentationAssessments(@PathVariable long courseId) {
        log.debug("REST request to get presentation assessments for course {}", courseId);
        presentationAssessmentService.checkPresentationAssessmentsEnabled(courseId);
        List<PresentationAssessmentDTO> presentationAssessments = presentationAssessmentRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok(presentationAssessments);
    }

    /**
     * Gets a filtered page of individual presentation assessment rows.
     *
     * @param courseId         the course id
     * @param assessmentId     optional presentation assessment filter
     * @param assessed         optional assessment status filter
     * @param linkedToExercise optional exercise-link filter
     * @param searchTerm       optional search term
     * @param page             zero-based page index
     * @param size             page size
     * @param sortField        field to sort by
     * @param direction        sorting direction
     * @return the rows with pagination headers
     */
    @GetMapping("courses/{courseId}/presentation-assessments/student-rows")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<PresentationAssessmentStudentRowDTO>> getPresentationAssessmentStudentRows(@PathVariable long courseId,
            @RequestParam(required = false) Long assessmentId, @RequestParam(required = false) Boolean assessed, @RequestParam(required = false) Boolean linkedToExercise,
            @RequestParam(required = false) String searchTerm, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "studentLogin") String sortField, @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        Page<PresentationAssessmentStudentRowDTO> rows = presentationAssessmentService.getStudentRows(courseId, assessmentId, assessed, linkedToExercise, searchTerm, page, size,
                sortField, direction);

        return ResponseEntity.ok().headers(PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), rows)).body(rows.getContent());
    }

    /**
     * Gets course-wide presentation assessment counts.
     *
     * @param courseId the course id
     * @return the total and assessed instance counts
     */
    @GetMapping("courses/{courseId}/presentation-assessments/statistics")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<PresentationAssessmentStatisticsDTO> getPresentationAssessmentStatistics(@PathVariable long courseId) {
        return ResponseEntity.ok(presentationAssessmentService.getStatistics(courseId));
    }

    /**
     * POST /api/assessment/courses/{courseId}/presentation-assessments : create a presentation assessment.
     *
     * @param courseId the course id
     * @param dto      the presentation assessment data
     * @return the ResponseEntity with status 201 (Created) and the created presentation assessment
     * @throws URISyntaxException if the Location URI is invalid
     */
    @PostMapping("courses/{courseId}/presentation-assessments")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<PresentationAssessmentDTO> createPresentationAssessment(@PathVariable long courseId, @Valid @RequestBody PresentationAssessmentDTO dto)
            throws URISyntaxException {
        log.debug("REST request to create presentation assessment for course {}: {}", courseId, dto);
        validatePresentationAssessmentCourseId(courseId, dto);
        presentationAssessmentService.checkPresentationAssessmentsEnabled(courseId);
        PresentationAssessmentDTO result = presentationAssessmentService.create(courseId, dto);
        return ResponseEntity.created(new URI("/api/assessment/courses/" + courseId + "/presentation-assessments/" + result.id())).body(result);
    }

    /**
     * PUT /api/assessment/courses/{courseId}/presentation-assessments/{assessmentId} : update a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @param dto          the updated presentation assessment data
     * @return the ResponseEntity with status 200 (OK) and the updated presentation assessment
     */
    @PutMapping("courses/{courseId}/presentation-assessments/{assessmentId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<PresentationAssessmentDTO> updatePresentationAssessment(@PathVariable long courseId, @PathVariable long assessmentId,
            @Valid @RequestBody PresentationAssessmentDTO dto) {
        log.debug("REST request to update presentation assessment {} for course {}: {}", assessmentId, courseId, dto);
        validatePresentationAssessmentCourseId(courseId, dto);
        presentationAssessmentService.checkPresentationAssessmentsEnabled(courseId);
        return ResponseEntity.ok(presentationAssessmentService.update(courseId, assessmentId, dto));
    }

    /**
     * DELETE /api/assessment/courses/{courseId}/presentation-assessments/{assessmentId} : delete a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @return the ResponseEntity with status 204 (No Content)
     */
    @DeleteMapping("courses/{courseId}/presentation-assessments/{assessmentId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deletePresentationAssessment(@PathVariable long courseId, @PathVariable long assessmentId) {
        log.debug("REST request to delete presentation assessment {} for course {}", assessmentId, courseId);
        presentationAssessmentService.checkPresentationAssessmentsEnabled(courseId);
        presentationAssessmentService.delete(courseId, assessmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/assessment/courses/{courseId}/presentation-assessments/{assessmentId}/instances : create individual instances for the selected students.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @param dto          the creation request
     * @return the created presentation assessment instances
     */
    @PostMapping("courses/{courseId}/presentation-assessments/{assessmentId}/instances")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<PresentationAssessmentInstanceDTO>> savePresentationAssessmentInstances(@PathVariable long courseId, @PathVariable long assessmentId,
            @Valid @RequestBody PresentationAssessmentInstancesBatchCreateDTO dto) {
        presentationAssessmentService.checkPresentationAssessmentsEnabled(courseId);
        return ResponseEntity.ok(presentationAssessmentService.saveInstances(courseId, assessmentId, dto).stream().map(PresentationAssessmentInstanceDTO::of).toList());
    }

    /**
     * Updates an individual presentation assessment instance.
     *
     * @param courseId     the owning course id
     * @param assessmentId the presentation assessment id
     * @param instanceId   the instance id
     * @param dto          the updated instance data
     * @return the ResponseEntity with status 200 (OK) and the updated instance including student details
     */
    @PutMapping("courses/{courseId}/presentation-assessments/{assessmentId}/instances/{instanceId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<PresentationAssessmentInstanceDTO> updatePresentationAssessmentInstance(@PathVariable long courseId, @PathVariable long assessmentId,
            @PathVariable long instanceId, @Valid @RequestBody PresentationAssessmentInstanceRequestDTO dto) {
        presentationAssessmentService.checkPresentationAssessmentsEnabled(courseId);
        return ResponseEntity.ok(PresentationAssessmentInstanceDTO.of(presentationAssessmentService.updateInstance(courseId, assessmentId, instanceId, dto)));
    }

    /**
     * Deletes an individual presentation assessment instance.
     *
     * @param courseId     the owning course id
     * @param assessmentId the presentation assessment id
     * @param instanceId   the instance id
     * @return the ResponseEntity with status 204 (No Content)
     */
    @DeleteMapping("courses/{courseId}/presentation-assessments/{assessmentId}/instances/{instanceId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deletePresentationAssessmentInstance(@PathVariable long courseId, @PathVariable long assessmentId, @PathVariable long instanceId) {
        presentationAssessmentService.checkPresentationAssessmentsEnabled(courseId);
        presentationAssessmentService.deleteInstance(courseId, assessmentId, instanceId);
        return ResponseEntity.noContent().build();
    }

    private void validatePresentationAssessmentCourseId(long courseId, PresentationAssessmentDTO dto) {
        if (dto.courseId() != null && dto.courseId() != courseId) {
            throw new BadRequestAlertException("The path course id and body course id must match", PresentationAssessment.ENTITY_NAME, "courseIdMismatch");
        }
    }
}
