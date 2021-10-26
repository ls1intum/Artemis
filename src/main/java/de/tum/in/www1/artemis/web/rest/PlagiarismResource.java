package de.tum.in.www1.artemis.web.rest;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.*;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismNotificationDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("/api")
public class PlagiarismResource {

    private final Logger log = LoggerFactory.getLogger(PlagiarismResource.class);

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final AuthorizationCheckService authenticationCheckService;

    private final UserRepository userRepository;

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
     * PUT /plagiarism-comparisons/{comparisonId}/status
     * <p>
     * Update the status of the plagiarism comparison with the given ID.
     *
     * @param comparisonId  ID of the plagiarism comparison to update the status of
     * @param statusDTO     new status for the given comparison
     * @param finalDecision whether or not the final status should be updated
     * @param studentLogin  which student the final status should be updated for
     * @return The ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the
     * parameters are invalid
     */
    @PutMapping("/plagiarism-comparisons/{comparisonId}/status")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> updatePlagiarismComparisonStatus(@PathVariable long comparisonId, @RequestBody PlagiarismComparisonStatusDTO statusDTO,
            @RequestParam(value = "finalDecision", defaultValue = "false", required = false) boolean finalDecision,
            @RequestParam(value = "studentLogin", required = false) String studentLogin) {
        if (finalDecision && studentLogin == null) {
            return ResponseEntity.badRequest().build();
        }
        else if (finalDecision) { // response to students statement on plagiarism notification. -> only instructors
            var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
            var exercise = comparison.getPlagiarismResult().getExercise();
            if (!authenticationCheckService.isAtLeastInstructorForExercise(exercise)) {
                throw new AccessForbiddenException("Only instructors can make final plagiarism decisions.");
            }
            if (comparison.getSubmissionA().getStudentLogin().equals(studentLogin)) {
                plagiarismComparisonRepository.updatePlagiarismComparisonFinalStatusA(comparisonId, statusDTO.getStatus());
                if (comparison.getNotificationA() != null) {
                    var notification = SingleUserNotificationFactory.createPlagiarismUpdateNotification(comparisonId, 1L, userRepository.getUserByLoginElseThrow(studentLogin),
                            userRepository.getUser());
                    singleUserNotificationService.notifyUserAboutPlagiarismCase(notification);
                }
            }
            else if (comparison.getSubmissionB().getStudentLogin().equals(studentLogin)) {
                plagiarismComparisonRepository.updatePlagiarismComparisonFinalStatusB(comparisonId, statusDTO.getStatus());
                if (comparison.getNotificationB() != null) {
                    var notification = SingleUserNotificationFactory.createPlagiarismUpdateNotification(comparisonId, 1L, userRepository.getUserByLoginElseThrow(studentLogin),
                            userRepository.getUser());
                    singleUserNotificationService.notifyUserAboutPlagiarismCase(notification);
                }
            }
            else {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok().build();
        }
        else {
            // TODO: check that the editor has access to the corresponding course (add the exerciseId to the URL)
            log.debug("REST request to update the status of the plagiarism comparison with id: {}", comparisonId);
            var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
            plagiarismComparisonRepository.updatePlagiarismComparisonStatus(comparison.getId(), statusDTO.getStatus());
            return ResponseEntity.ok().body(null);
        }
    }

    /**
     * Retrieves the plagiarismComparison specified by its Id. The submissions are anonymized for the student.
     * StudentIds are replaced with "Your Submission" and "Other Submission" based on the requesting user.
     *
     * @param comparisonId the id of the PlagiarismComparison
     * @return the PlagiarismComparison
     * @throws AccessForbiddenException if the requesting user is not affected by the plagiarism case.
     */
    @GetMapping("/plagiarism-comparisons/{comparisonId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismCaseDTO> getAnonymousPlagiarismComparison(@PathVariable("comparisonId") Long comparisonId) {
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
        var user = userRepository.getUser();

        // anonymize:
        if (comparison.getSubmissionA().getStudentLogin().equals(user.getLogin())) {
            comparison.getSubmissionA().setStudentLogin("Your submission");
            comparison.getSubmissionB().setStudentLogin("Other submission");
            comparison.setNotificationB(null);
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(user.getLogin())) {
            comparison.getSubmissionA().setStudentLogin("Other submission");
            comparison.getSubmissionB().setStudentLogin("Your submission");
            comparison.setNotificationA(null);
        }
        else {
            throw new AccessForbiddenException("This plagiarism comparison is not related to the requesting user.");
        }
        return ResponseEntity.ok(new PlagiarismCaseDTO(comparison.getPlagiarismResult().getExercise(), Set.of(comparison)));
    }

    /**
     * Retrieves all plagiarismCases related to a course that were previously confirmed.
     *
     * @param courseId the id of the course
     * @return all plagiarism cases
     */
    @GetMapping("/plagiarism-cases/{courseId}")
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

    /**
     * helper class for plagiarism statement update requests
     */
    private static class PlagiarismStatementDTO {

        public String statement;
    }

    /**
     * Creates a students statement on the plagiarismComparison.
     *
     * @param plagiarismComparisonId Id of the comparison
     * @param statement              the students statement
     * @return nothing
     */
    @PutMapping("/plagiarism-cases/{plagiarismComparisonId}/statement")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> sendStatement(@PathVariable("plagiarismComparisonId") long plagiarismComparisonId, @RequestBody PlagiarismStatementDTO statement) {
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(plagiarismComparisonId);
        var user = userRepository.getUser();
        if (comparison.getNotificationA() != null && ((SingleUserNotification) comparison.getNotificationA()).getRecipient().equals(user)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonStatementA(comparison.getId(), statement.statement);
        }
        else if (comparison.getNotificationB() != null && ((SingleUserNotification) comparison.getNotificationB()).getRecipient().equals(user)) {
            plagiarismComparisonRepository.updatePlagiarismComparisonStatementB(comparison.getId(), statement.statement);
        }
        else {
            throw new AccessForbiddenException("User tried updating plagiarism case they're not affected by.");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Creates a Plagiarism Notification. Requesting user is the sender.
     *
     * @param plagiarismNotificationDTO contains the required information to build the plagiarismNotification
     * @return the created notification (if any)
     */
    @PutMapping("/plagiarism-cases/notification")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Notification> sendPlagiarismNotification(@RequestBody PlagiarismNotificationDTO plagiarismNotificationDTO) {
        log.debug("REST request to create Plagiarism Notification");
        log.debug("id: {}", plagiarismNotificationDTO.getPlagiarismComparisonId());
        Optional<PlagiarismComparison<?>> plagiarismComparisonOptional = plagiarismComparisonRepository.findById(plagiarismNotificationDTO.getPlagiarismComparisonId());
        if (plagiarismComparisonOptional.isEmpty()) {
            throw new EntityNotFoundException("PlagiarismComparison not found");
        }
        var plagiarismComparison = plagiarismComparisonOptional.get();
        User affectedUser = userRepository.getUserByLoginElseThrow(plagiarismNotificationDTO.getStudentLogin());
        SingleUserNotification plagiarismNotification = SingleUserNotificationFactory.createPlagiarismNotification(plagiarismNotificationDTO.getPlagiarismComparisonId(), 1L,// todo
                affectedUser, userRepository.getUser(), plagiarismNotificationDTO.getInstructorMessage());
        var sentNotification = singleUserNotificationService.notifyUserAboutPlagiarismCase(plagiarismNotification);
        if (plagiarismNotificationDTO.getStudentLogin().equals(plagiarismComparison.getSubmissionA().getStudentLogin())) {
            plagiarismComparisonRepository.updatePlagiarismComparisonNotificationA(plagiarismComparison.getId(), sentNotification);
        }
        else if (plagiarismNotificationDTO.getStudentLogin().equals(plagiarismComparison.getSubmissionB().getStudentLogin())) {
            plagiarismComparisonRepository.updatePlagiarismComparisonNotificationB(plagiarismComparison.getId(), sentNotification);
        }
        else {
            throw new EntityNotFoundException("Student with id not found in plagiarism comparison");
        }
        return ResponseEntity.ok(plagiarismNotification);
    }
}
