package de.tum.in.www1.artemis.web.rest.plagiarism;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismCaseService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismVerdictDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * REST controller for managing Plagiarism Cases.
 */
@RestController
@RequestMapping("api/")
public class PlagiarismCaseResource {

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authenticationCheckService;

    private final UserRepository userRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final Logger log = LoggerFactory.getLogger(PlagiarismCaseResource.class);

    public PlagiarismCaseResource(CourseRepository courseRepository, AuthorizationCheckService authenticationCheckService, UserRepository userRepository,
            PlagiarismCaseService plagiarismCaseService, PlagiarismCaseRepository plagiarismCaseRepository) {
        this.courseRepository = courseRepository;
        this.authenticationCheckService = authenticationCheckService;
        this.userRepository = userRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
    }

    /**
     * Retrieves all plagiarism cases related to a course for the instructor view.
     *
     * @param courseId the id of the course
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/plagiarism-cases/for-instructor")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesForCourseForInstructor(@PathVariable long courseId) {
        log.debug("REST request to get all plagiarism cases for instructor in course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        var plagiarismCases = plagiarismCaseRepository.findPlagiarismCasesForCourse(courseId);
        return getPlagiarismCasesResponseEntity(plagiarismCases);
    }

    /**
     * Retrieves the plagiarism case with the given ID for the instructor view.
     *
     * @param courseId the id of the course
     * @param plagiarismCaseId the id of the plagiarism case
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/for-instructor")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<PlagiarismCase> getPlagiarismCaseForInstructor(@PathVariable long courseId, @PathVariable long plagiarismCaseId) {
        log.debug("REST request to get plagiarism case for instructor with id: {}", plagiarismCaseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        return getPlagiarismCaseResponseEntity(plagiarismCaseId);
    }

    private ResponseEntity<PlagiarismCase> getPlagiarismCaseResponseEntity(@PathVariable long plagiarismCaseId) {
        var plagiarismCase = plagiarismCaseRepository.findByIdWithExerciseAndPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
            submission.setPlagiarismCase(null);
            submission.getPlagiarismComparison().getPlagiarismResult().setExercise(null);
            submission.getPlagiarismComparison().setSubmissionA(null);
            submission.getPlagiarismComparison().setSubmissionB(null);
        }
        return ResponseEntity.ok(plagiarismCase);
    }

    /**
     * Update the verdict of the plagiarism case with the given ID.
     *
     * @param courseId the id of the course
     * @param plagiarismCaseId the id of the plagiarism case
     * @param plagiarismVerdictDTO the verdict of the plagiarism case
     * @return the updated plagiarism case
     */
    @PutMapping("courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/verdict")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<PlagiarismCase> savePlagiarismCaseVerdict(@PathVariable long courseId, @PathVariable long plagiarismCaseId,
            @RequestBody PlagiarismVerdictDTO plagiarismVerdictDTO) {
        log.debug("REST request to save plagiarism verdict for plagiarism case with id: {}", plagiarismCaseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        var plagiarismCase = plagiarismCaseService.updatePlagiarismCaseVerdict(plagiarismCaseId, plagiarismVerdictDTO);
        return ResponseEntity.ok(plagiarismCase);
    }

    /**
     * Retrieves all plagiarismCases related to a course for the student view.
     *
     * @param courseId the id of the course
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/plagiarism-cases/for-student")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesForCourseForStudent(@PathVariable long courseId) {
        log.debug("REST request to get all plagiarism cases for student in course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authenticationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Only students of this course have access to its plagiarism cases.");
        }
        var plagiarismCases = plagiarismCaseRepository.findPlagiarismCasesForStudentForCourse(user.getId(), courseId);
        return getPlagiarismCasesResponseEntity(plagiarismCases);
    }

    private ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesResponseEntity(List<PlagiarismCase> plagiarismCases) {
        for (var plagiarismCase : plagiarismCases) {
            if (plagiarismCase.getPost() != null) {
                plagiarismCase.getPost().setPlagiarismCase(null);
            }
            for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
                submission.setPlagiarismCase(null);
                submission.getPlagiarismComparison().getPlagiarismResult().setExercise(null);
                submission.getPlagiarismComparison().setSubmissionA(null);
                submission.getPlagiarismComparison().setSubmissionB(null);
            }
        }
        return ResponseEntity.ok(plagiarismCases);
    }

    /**
     * Retrieves the plagiarism case with the given ID for the student view.
     *
     * @param courseId the id of the course
     * @param plagiarismCaseId the id of the plagiarism case
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/for-student")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismCase> getPlagiarismCaseForStudent(@PathVariable long courseId, @PathVariable long plagiarismCaseId) {
        log.debug("REST request to get plagiarism case for student with id: {}", plagiarismCaseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastStudentInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        return getPlagiarismCaseResponseEntity(plagiarismCaseId);
    }
}
