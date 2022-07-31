package de.tum.in.www1.artemis.web.rest.plagiarism;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
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
        var plagiarismCases = plagiarismCaseRepository.findByCourseIdWithPlagiarismSubmissionsAndComparison(courseId);
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
        var plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        return getPlagiarismCaseResponseEntity(plagiarismCase);
    }

    private ResponseEntity<PlagiarismCase> getPlagiarismCaseResponseEntity(PlagiarismCase plagiarismCase) {
        for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
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
     * Retrieves the plagiarismCase related to an exercise for the student.
     *
     * @param courseId the id of the course
     * @param exerciseId the id of the exercise
     * @return the plagiarism case for the exercise and student
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/plagiarism-case")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismCase> getPlagiarismCaseForExerciseForStudent(@PathVariable long courseId, @PathVariable long exerciseId) {
        log.debug("REST request to all plagiarism cases for student and exercise with id: {}", exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authenticationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Only students of this course have access to its plagiarism cases.");
        }
        var plagiarismCaseOptional = plagiarismCaseRepository.findByStudentIdAndExerciseId(user.getId(), exerciseId);
        if (plagiarismCaseOptional.isPresent()) {
            var plagiarismCase = plagiarismCaseOptional.get();
            // only return the plagiarism case if the student was already notified
            if (plagiarismCase.getPost() == null) {
                // the student was not notified yet and should not yet see the case
                // TODO: we should handle this better in the future, e.g. by storing a field in the plagiarism case whether the student was notified or not
                return ResponseEntity.ok().build();
            }
            // the post itself does not need to be part of the response
            plagiarismCase.setPost(null);
            return ResponseEntity.ok(plagiarismCase);
        }
        else {
            return ResponseEntity.ok().build();
        }
    }

    private ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesResponseEntity(List<PlagiarismCase> plagiarismCases) {
        for (var plagiarismCase : plagiarismCases) {
            if (plagiarismCase.getPost() != null) {
                plagiarismCase.getPost().setPlagiarismCase(null);
            }
            for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
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
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authenticationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Only students of this course have access to its plagiarism cases.");
        }
        var plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        if (!plagiarismCase.getStudent().getLogin().equals(user.getLogin())) {
            throw new AccessForbiddenException("Students only have access to plagiarism cases by which they are affected");
        }
        return getPlagiarismCaseResponseEntity(plagiarismCase);
    }
}
