package de.tum.cit.aet.artemis.presentation.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentStudentDTO;
import de.tum.cit.aet.artemis.presentation.service.PresentationAssessmentService;

/**
 * REST controller for managing course-level presentation assessments.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/")
public class PresentationAssessmentResource {

    private static final Logger log = LoggerFactory.getLogger(PresentationAssessmentResource.class);

    private final PresentationAssessmentService presentationAssessmentService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public PresentationAssessmentResource(PresentationAssessmentService presentationAssessmentService, CourseRepository courseRepository,
            AuthorizationCheckService authCheckService) {
        this.presentationAssessmentService = presentationAssessmentService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /courses/{courseId}/presentation-assessments : get all presentation assessments for a course.
     *
     * @param courseId the course id
     * @return the ResponseEntity with status 200 (OK) and the presentation assessments
     */
    @GetMapping("courses/{courseId}/presentation-assessments")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<PresentationAssessmentDTO>> getPresentationAssessments(@PathVariable long courseId) {
        log.debug("REST request to get presentation assessments for course {}", courseId);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        List<PresentationAssessmentDTO> presentationAssessments = presentationAssessmentService.findAllByCourseId(courseId).stream().map(PresentationAssessmentDTO::of).toList();
        return ResponseEntity.ok(presentationAssessments);
    }

    /**
     * GET /courses/{courseId}/presentation-assessments/{assessmentId} : get a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @return the ResponseEntity with status 200 (OK) and the presentation assessment
     */
    @GetMapping("courses/{courseId}/presentation-assessments/{assessmentId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<PresentationAssessmentDTO> getPresentationAssessment(@PathVariable long courseId, @PathVariable long assessmentId) {
        log.debug("REST request to get presentation assessment {} for course {}", assessmentId, courseId);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return ResponseEntity.ok(PresentationAssessmentDTO.of(presentationAssessmentService.findByIdAndCourseIdElseThrow(courseId, assessmentId)));
    }

    /**
     * POST /courses/{courseId}/presentation-assessments : create a presentation assessment.
     *
     * @param courseId the course id
     * @param dto      the presentation assessment data
     * @return the ResponseEntity with status 201 (Created) and the created presentation assessment
     * @throws URISyntaxException if the Location URI is invalid
     */
    @PostMapping("courses/{courseId}/presentation-assessments")
    @EnforceAtLeastInstructor
    public ResponseEntity<PresentationAssessmentDTO> createPresentationAssessment(@PathVariable long courseId, @Valid @RequestBody PresentationAssessmentDTO dto)
            throws URISyntaxException {
        log.debug("REST request to create presentation assessment for course {}: {}", courseId, dto);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        PresentationAssessment presentationAssessment = presentationAssessmentService.create(course, dto);
        PresentationAssessmentDTO result = PresentationAssessmentDTO.of(presentationAssessment);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/presentation-assessments/" + result.id())).body(result);
    }

    /**
     * PUT /courses/{courseId}/presentation-assessments/{assessmentId} : update a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @param dto          the updated presentation assessment data
     * @return the ResponseEntity with status 200 (OK) and the updated presentation assessment
     */
    @PutMapping("courses/{courseId}/presentation-assessments/{assessmentId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<PresentationAssessmentDTO> updatePresentationAssessment(@PathVariable long courseId, @PathVariable long assessmentId,
            @Valid @RequestBody PresentationAssessmentDTO dto) {
        log.debug("REST request to update presentation assessment {} for course {}: {}", assessmentId, courseId, dto);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return ResponseEntity.ok(PresentationAssessmentDTO.of(presentationAssessmentService.update(courseId, assessmentId, dto)));
    }

    /**
     * DELETE /courses/{courseId}/presentation-assessments/{assessmentId} : delete a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @return the ResponseEntity with status 204 (No Content)
     */
    @DeleteMapping("courses/{courseId}/presentation-assessments/{assessmentId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deletePresentationAssessment(@PathVariable long courseId, @PathVariable long assessmentId) {
        log.debug("REST request to delete presentation assessment {} for course {}", assessmentId, courseId);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        presentationAssessmentService.delete(courseId, assessmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /courses/{courseId}/presentation-assessments/{assessmentId}/students : get students assigned to a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @return the ResponseEntity with status 200 (OK) and the assigned students
     */
    @GetMapping("courses/{courseId}/presentation-assessments/{assessmentId}/students")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<PresentationAssessmentStudentDTO>> getPresentationAssessmentStudents(@PathVariable long courseId, @PathVariable long assessmentId) {
        log.debug("REST request to get students for presentation assessment {} in course {}", assessmentId, courseId);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return ResponseEntity.ok(presentationAssessmentService.findStudents(courseId, assessmentId).stream().map(PresentationAssessmentStudentDTO::of).toList());
    }

    /**
     * POST /courses/{courseId}/presentation-assessments/{assessmentId}/students/{studentLogin} : add a student to a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @param studentLogin the login of the student to add
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("courses/{courseId}/presentation-assessments/{assessmentId}/students/{studentLogin}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addStudentToPresentationAssessment(@PathVariable long courseId, @PathVariable long assessmentId, @PathVariable String studentLogin) {
        log.debug("REST request to add student {} to presentation assessment {} in course {}", studentLogin, assessmentId, courseId);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        presentationAssessmentService.addStudent(course, assessmentId, studentLogin);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /courses/{courseId}/presentation-assessments/{assessmentId}/students/{studentLogin} : remove a student from a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @param studentLogin the login of the student to remove
     * @return the ResponseEntity with status 204 (No Content)
     */
    @DeleteMapping("courses/{courseId}/presentation-assessments/{assessmentId}/students/{studentLogin}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeStudentFromPresentationAssessment(@PathVariable long courseId, @PathVariable long assessmentId, @PathVariable String studentLogin) {
        log.debug("REST request to remove student {} from presentation assessment {} in course {}", studentLogin, assessmentId, courseId);
        Course course = findCourseAndCheckPresentationAssessmentsEnabled(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        presentationAssessmentService.removeStudent(courseId, assessmentId, studentLogin);
        return ResponseEntity.noContent().build();
    }

    private Course findCourseAndCheckPresentationAssessmentsEnabled(long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!course.getPresentationAssessmentsEnabled()) {
            throw new AccessForbiddenException("Presentation assessments are disabled for this course.");
        }
        return course;
    }
}
