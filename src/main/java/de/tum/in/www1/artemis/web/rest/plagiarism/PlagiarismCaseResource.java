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
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismCaseService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseInfoDTO;
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
     * Retrieves all plagiarism cases related to an exam for the instructor view.
     *
     * @param courseId the id of the course
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/exams/{examId}/plagiarism-cases/for-instructor")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesForExamForInstructor(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request to get all plagiarism cases for instructor in course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }

        // TODO: Ata Either add course id to findByExamIdWithPlagiarismSubmissionsAndComparison or remove it from request params.
        var plagiarismCases = plagiarismCaseRepository.findByExamIdWithPlagiarismSubmissionsAndComparison(examId);
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
     * Retrieves the plagiarismCase related to an exercise for the student if the plagiarism comparison was confirmed and the student was notified
     *
     * @param courseId the id of the course
     * @param exerciseId the id of the exercise
     * @return the plagiarism case id for the exercise and student if and only if the comparison was confirmed and the student was notified
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/plagiarism-case")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismCaseInfoDTO> getPlagiarismCaseForExerciseForStudent(@PathVariable long courseId, @PathVariable long exerciseId) {
        log.debug("REST request to all plagiarism cases for student and exercise with id: {}", exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authenticationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        var plagiarismCaseOptional = plagiarismCaseRepository.findByStudentIdAndExerciseIdWithPost(user.getId(), exerciseId);
        if (plagiarismCaseOptional.isPresent()) {
            // the student was notified if the plagiarism case is available (due to the nature of the query above)
            var plagiarismCase = plagiarismCaseOptional.get();
            // the following line is already checked in the SQL statement, but we want to ensure it 100%
            if (plagiarismCase.getPost() != null) {
                // Note: we only return the ID and verdict to tell the client there is a confirmed plagiarism case with student notification (post) and to support navigating to the
                // detail page
                // all other information might be irrelevant or sensitive and could lead to longer loading times
                var plagiarismCaseInfoDTO = new PlagiarismCaseInfoDTO(plagiarismCase.getId(), plagiarismCase.getVerdict());
                return ResponseEntity.ok(plagiarismCaseInfoDTO);
            }
        }
        // in all other cases the response is empty
        return ResponseEntity.ok(null);
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
        authenticationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        var plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        if (!plagiarismCase.getStudent().getLogin().equals(user.getLogin())) {
            throw new AccessForbiddenException("Students only have access to plagiarism cases by which they are affected");
        }

        // hide potentially sensitive data
        plagiarismCase.getExercise().filterSensitiveInformation();

        return getPlagiarismCaseResponseEntity(plagiarismCase);
    }
}
