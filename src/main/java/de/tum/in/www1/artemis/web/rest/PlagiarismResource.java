package de.tum.in.www1.artemis.web.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("api/")
public class PlagiarismResource {

    /**
     * helper class for plagiarism statement update requests
     */
    public static class PlagiarismStatementDTO {

        public String statement;
    }

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final AuthorizationCheckService authenticationCheckService;

    private final UserRepository userRepository;

    private final Logger log = LoggerFactory.getLogger(PlagiarismResource.class);

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    public PlagiarismResource(PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismResultRepository plagiarismResultRepository,
            ExerciseRepository exerciseRepository, CourseRepository courseRepository, SingleUserNotificationService singleUserNotificationService,
            AuthorizationCheckService authenticationCheckService, UserRepository userRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.authenticationCheckService = authenticationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * Update the status of the plagiarism comparison with the given ID.
     * I.e. An editor or instructor sees a possible plagiarism case for the first time and decides if it should be further examined, or it is not a plagiarism.
     *
     * @param comparisonId of the plagiarism comparison to update the status of
     * @param statusDTO new status for the given comparison
     * @return the ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the parameters are invalid
     */
    @PutMapping("plagiarism-comparisons/{comparisonId}/status")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> updatePlagiarismComparisonStatus(@PathVariable long comparisonId, @RequestBody PlagiarismComparisonStatusDTO statusDTO) {
        // TODO: check that the editor has access to the corresponding course (add the exerciseId to the URL)
        log.debug("REST request to update the status of the plagiarism comparison with id: {}", comparisonId);
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
        plagiarismComparisonRepository.updatePlagiarismComparisonStatus(comparison.getId(), statusDTO.getStatus());
        return ResponseEntity.ok().body(null);
    }

    /**
     * Retrieves all plagiarismCases related to a course that were previously confirmed.
     *
     * @param courseId the id of the course
     * @return all plagiarism cases
     */
    @GetMapping("courses/{courseId}/plagiarism-cases")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<PlagiarismCaseDTO>> getPlagiarismCasesForCourse(@PathVariable long courseId) {
        log.debug("REST request to get all plagiarism cases in course with id: {}", courseId);
        var res = new ArrayList<PlagiarismCaseDTO>();
        var course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors can get all plagiarism cases.");
        }
        var exerciseIDs = exerciseRepository.findAllIdsByCourseId(courseId);
        exerciseIDs.forEach(id -> {
            var exerciseOptional = exerciseRepository.findById(id);
            if (exerciseOptional.isPresent()) {
                PlagiarismResult<?> result = plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(exerciseOptional.get().getId());
                if (result != null) {
                    Set<PlagiarismComparison<?>> filteredComparisons = result.getComparisons().stream().filter(c -> c.getStatus() == PlagiarismStatus.CONFIRMED)
                            .collect(Collectors.toSet());
                    if (filteredComparisons.size() > 0) {
                        res.add(new PlagiarismCaseDTO(exerciseOptional.get(), filteredComparisons));
                    }
                }
            }
        });
        return ResponseEntity.ok(res);
    }

    /// An instructor sends his statement/message about his verdict to student A or B
    @PutMapping("plagiarism-comparisons/{comparisonId}/{studentLogin}/instructor-statement")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<PlagiarismStatementDTO> updatePlagiarismComparisonInstructorStatement(@PathVariable("comparisonId") long comparisonId,
            @PathVariable("studentLogin") String studentLogin, @RequestBody PlagiarismStatementDTO statement) {
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
        User affectedUser = userRepository.getUserByLoginElseThrow(studentLogin); // TODO maybe remove if not used by notifications

        if (comparison.getSubmissionA().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonInstructorStatementA(comparison.getId(), statement.statement);
            // TODO send notification to student
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonInstructorStatementB(comparison.getId(), statement.statement);
            // TODO send notification to student
        }
        else {
            throw new EntityNotFoundException("Student with id not found in plagiarism comparison");
        }
        return ResponseEntity.ok(statement);
    }

    /**
     * Creates a student statement on the plagiarismComparison.
     *
     * @param comparisonId of the comparison
     * @param statement the students statement
     * @return the student statement
     */
    @PutMapping("plagiarism-comparisons/{comparisonId}/student-statement")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismStatementDTO> updatePlagiarismComparisonStudentStatement(@PathVariable("comparisonId") long comparisonId,
            @RequestBody PlagiarismStatementDTO statement) {
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
        var user = userRepository.getUser();
        var studentLogin = user.getLogin();

        if (comparison.getInstructorStatementA() != null && comparison.getSubmissionA().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonStudentStatementA(comparison.getId(), statement.statement);
        }
        else if (comparison.getInstructorStatementB() != null && comparison.getSubmissionB().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonStudentStatementB(comparison.getId(), statement.statement);
        }
        else {
            throw new AccessForbiddenException("User tried updating plagiarism case they're not affected by.");
        }
        return ResponseEntity.ok(statement);
    }

    /// TODO An instructor send his final verdict/status/decision
    @PutMapping("plagiarism-comparisons/{comparisonId}/{studentLogin}/final-status")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<PlagiarismComparisonStatusDTO> updatePlagiarismComparisonFinalStatus(@PathVariable("comparisonId") long comparisonId,
            @PathVariable("studentLogin") String studentLogin, @RequestBody PlagiarismComparisonStatusDTO statusDTO) {
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
        var exercise = comparison.getPlagiarismResult().getExercise(); // TODO check if needed for notification / check if user is instructor of this exercise/course
        if (comparison.getSubmissionA().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonFinalStatusA(comparisonId, statusDTO.getStatus());
            // TODO singleUserNotificationService.notifyUserAboutPlagiarismCase(notification);
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(studentLogin)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonFinalStatusB(comparisonId, statusDTO.getStatus());
            // TODO singleUserNotificationService.notifyUserAboutPlagiarismCase(notification);
        }
        else {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(statusDTO);
    }
}
