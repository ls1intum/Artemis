package de.tum.in.www1.artemis.web.rest;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("api/")
public class PlagiarismResource {

    private final CourseRepository courseRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final AuthorizationCheckService authenticationCheckService;

    private final UserRepository userRepository;

    private final Logger log = LoggerFactory.getLogger(PlagiarismResource.class);

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismService plagiarismService;

    /**
     * helper class for plagiarism statement update requests
     */
    public static class PlagiarismStatementDTO {

        public String statement;
    }

    public PlagiarismResource(PlagiarismComparisonRepository plagiarismComparisonRepository, CourseRepository courseRepository,
            SingleUserNotificationService singleUserNotificationService, AuthorizationCheckService authenticationCheckService, UserRepository userRepository,
            PlagiarismService plagiarismService) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.courseRepository = courseRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.authenticationCheckService = authenticationCheckService;
        this.userRepository = userRepository;
        this.plagiarismService = plagiarismService;
    }

    /**
     * Update the status of the plagiarism comparison with the given ID.
     * I.e. An editor or instructor sees a possible plagiarism case for the first time and decides if it should be further examined, or if it is not a plagiarism.
     *
     * @param comparisonId of the plagiarism comparison to update the status of
     * @param courseId the id of the course
     * @param statusDTO new status for the given comparison
     * @return the ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the parameters are invalid
     */
    @PutMapping("courses/{courseId}/plagiarism-comparisons/{comparisonId}/status")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> updatePlagiarismComparisonStatus(@PathVariable("courseId") long courseId, @PathVariable("comparisonId") long comparisonId,
            @RequestBody PlagiarismComparisonStatusDTO statusDTO) {
        log.info("REST request to update the status {} of the plagiarism comparison with id: {}", statusDTO.getStatus(), comparisonId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authenticationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        // TODO: this check can take up to a few seconds in the worst case, we should do it directly in the database
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparisonId);
        if (!Objects.equals(comparison.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }
        plagiarismComparisonRepository.updatePlagiarismComparisonStatus(comparisonId, statusDTO.getStatus());
        log.info("Finished updating the status {} of the plagiarism comparison with id: {}", statusDTO.getStatus(), comparisonId);
        return ResponseEntity.ok().body(null);
    }

    /**
     * Retrieves all plagiarismComparisons related to a course that were previously confirmed.
     *
     * @param courseId the id of the course
     * @return all plagiarism cases
     */
    @GetMapping("courses/{courseId}/plagiarism-cases")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<PlagiarismComparison<?>>> getPlagiarismComparisonsForCourse(@PathVariable long courseId) {
        log.debug("REST request to get all plagiarism cases in course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        var foundPlagiarismCasesForCourse = plagiarismComparisonRepository.findCasesForCourse(PlagiarismStatus.CONFIRMED, courseId);
        return ResponseEntity.ok(foundPlagiarismCasesForCourse);
    }

    /**
     * Updates an instructor statement on a plagiarismComparison (for one side).
     * This process will send a notification to the respective student.
     * I.e. the instructor sets a personal message to one of the accused students.
     *
     * @param courseId the id of the course
     * @param comparisonId the id of the PlagiarismComparison
     * @param studentLogin of one of accused students
     * @param statement of the instructor directed to one of the accused students
     * @return the instructor statement (convention)
     */
    @PutMapping("courses/{courseId}/plagiarism-comparisons/{comparisonId}/instructor-statement/{studentLogin}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<PlagiarismStatementDTO> updatePlagiarismComparisonInstructorStatement(@PathVariable("courseId") long courseId,
            @PathVariable("comparisonId") long comparisonId, @PathVariable("studentLogin") String studentLogin, @RequestBody PlagiarismStatementDTO statement) {

        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparisonId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User affectedUser = userRepository.getUserByLoginElseThrow(studentLogin);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        String instructorStatement = statement.statement;

        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("Only instructors responsible for this course can access this plagiarism case.");
        }
        if (!Objects.equals(comparison.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }

        if (comparison.getSubmissionA().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonInstructorStatementA(comparison.getId(), instructorStatement);
            comparison.setInstructorStatementA(instructorStatement); // needed for notifications
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonInstructorStatementB(comparison.getId(), instructorStatement);
            comparison.setInstructorStatementB(instructorStatement); // needed for notifications
        }
        else {
            throw new EntityNotFoundException("Student with id not found in plagiarism comparison");
        }
        singleUserNotificationService.notifyUserAboutNewPossiblePlagiarismCase(comparison, affectedUser);
        return ResponseEntity.ok(statement);
    }

    /**
     * Retrieves the plagiarismComparison specified by its Id. The submissions are anonymized for the student.
     * StudentIds are replaced with "Your Submission" and "Other Submission" based on the requesting user.
     *
     * @param courseId the id of the course
     * @param comparisonId the id of the PlagiarismComparison
     * @return the PlagiarismComparison
     * @throws AccessForbiddenException if the requesting user is not affected by the plagiarism case.
     */
    @GetMapping("courses/{courseId}/plagiarism-comparisons/{comparisonId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismCaseDTO> getPlagiarismComparisonForStudent(@PathVariable("courseId") long courseId, @PathVariable("comparisonId") Long comparisonId) {
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparisonId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authenticationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Only students registered for this course can access this plagiarism comparison.");
        }
        if (!Objects.equals(comparison.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }

        // check if current user is part of the comparison or not
        if (!(comparison.getSubmissionA().getStudentLogin().equals(user.getLogin()) || comparison.getSubmissionB().getStudentLogin().equals(user.getLogin()))) {
            log.error("User {} tried accessing plagiarism case with comparison id {} they're not affected by.", user.getLogin(), comparisonId);
            throw new AccessForbiddenException("User tried accessing plagiarism case they're not affected by.");
        }

        PlagiarismComparison<?> anonymizedComparisonForStudentView = this.plagiarismService.anonymizeComparisonForStudentView(comparison, user.getLogin());
        return ResponseEntity.ok(new PlagiarismCaseDTO(anonymizedComparisonForStudentView.getPlagiarismResult().getExercise(), Set.of(anonymizedComparisonForStudentView)));
    }

    /**
     * Retrieves the plagiarismComparison specified by its Id. The submissions are anonymized for the student.
     * StudentIds are replaced with "Your Submission" and "Other Submission" based on the requesting user.
     *
     * @param courseId the id of the course
     * @param comparisonId the id of the PlagiarismComparison
     * @return the PlagiarismComparison
     * @throws AccessForbiddenException if the requesting user is not affected by the plagiarism case.
     */
    @GetMapping("courses/{courseId}/plagiarism-comparisons/{comparisonId}/for-editor")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<PlagiarismComparison<?>> getPlagiarismComparisonForEditor(@PathVariable("courseId") long courseId, @PathVariable("comparisonId") Long comparisonId) {
        var comparisonA = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsAndElementsAElseThrow(comparisonId);
        var comparisonB = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsAndElementsBElseThrow(comparisonId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authenticationCheckService.isAtLeastEditorInCourse(course, user)) {
            throw new AccessForbiddenException("Only editors registered for this course can access this plagiarism comparison.");
        }
        if (!Objects.equals(comparisonA.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }

        comparisonA.setSubmissionB(comparisonB.getSubmissionB());
        return ResponseEntity.ok(comparisonA);
    }

    /**
     * Updates a student statement on a plagiarismComparison.
     * I.e. one of the students that is accused of plagiarising updates/sets the respective/individual response/defence
     *
     * @param courseId the id of the course
     * @param comparisonId of the comparison
     * @param statement the students statement
     * @return the student statement
     */
    @PutMapping("courses/{courseId}/plagiarism-comparisons/{comparisonId}/student-statement")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismStatementDTO> updatePlagiarismComparisonStudentStatement(@PathVariable("courseId") long courseId,
            @PathVariable("comparisonId") long comparisonId, @RequestBody PlagiarismStatementDTO statement) {
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparisonId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        String studentLogin = user.getLogin();
        String studentStatement = statement.statement;

        if (!authenticationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Only students registered for this course can access this plagiarism comparison.");
        }
        if (!Objects.equals(comparison.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }

        if (comparison.getInstructorStatementA() != null && comparison.getSubmissionA().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonStudentStatementA(comparison.getId(), studentStatement);
        }
        else if (comparison.getInstructorStatementB() != null && comparison.getSubmissionB().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonStudentStatementB(comparison.getId(), studentStatement);
        }
        else {
            throw new AccessForbiddenException("User tried updating plagiarism case they're not affected by.");
        }
        return ResponseEntity.ok(statement);
    }

    /**
     * Updates the final status of a plagiarism comparison concerning one of both students.
     * This process will send a notification to the respective student.
     * I.e. an instructor sends his final verdict/decision
     *
     * @param courseId the id of the course
     * @param comparisonId of the comparison
     * @param studentLogin of the student
     * @param statusDTO is the final status of this plagiarism comparison concerning one of both students
     * @return the final (updated) status of this plagiarism comparison concerning one of both students
     */
    @PutMapping("courses/{courseId}/plagiarism-comparisons/{comparisonId}/final-status/{studentLogin}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<PlagiarismComparisonStatusDTO> updatePlagiarismComparisonFinalStatus(@PathVariable("courseId") long courseId,
            @PathVariable("comparisonId") long comparisonId, @PathVariable("studentLogin") String studentLogin, @RequestBody PlagiarismComparisonStatusDTO statusDTO) {

        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparisonId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User affectedUser = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        PlagiarismStatus finalStatus = statusDTO.getStatus();

        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("Only instructors responsible for this course can access this plagiarism comparison.");
        }
        if (!Objects.equals(comparison.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }

        if (comparison.getSubmissionA().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonFinalStatusA(comparisonId, finalStatus);
            comparison.setStatusA(finalStatus); // needed for notifications
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonFinalStatusB(comparisonId, finalStatus);
            comparison.setStatusB(finalStatus); // needed for notifications
        }
        else {
            return ResponseEntity.notFound().build();
        }
        singleUserNotificationService.notifyUserAboutFinalPlagiarismState(comparison, affectedUser);
        return ResponseEntity.ok(statusDTO);
    }
}
