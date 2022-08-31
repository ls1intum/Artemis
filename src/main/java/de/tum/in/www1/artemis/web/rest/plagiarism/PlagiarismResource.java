package de.tum.in.www1.artemis.web.rest.plagiarism;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("api/")
public class PlagiarismResource {

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final Logger log = LoggerFactory.getLogger(PlagiarismResource.class);

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismService plagiarismService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final ExerciseRepository exerciseRepository;

    // correspond to the translation files (suffix) used in the client
    private static final String YOUR_SUBMISSION = "Your submission";

    private static final String OTHER_SUBMISSION = "Other submission";

    public PlagiarismResource(PlagiarismComparisonRepository plagiarismComparisonRepository, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            UserRepository userRepository, PlagiarismService plagiarismService, PlagiarismResultRepository plagiarismResultRepository, ExerciseRepository exerciseRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.plagiarismService = plagiarismService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.exerciseRepository = exerciseRepository;
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
        log.info("REST request to update the status {} of the plagiarism comparison with id: {}", statusDTO.status(), comparisonId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        // TODO: this check can take up to a few seconds in the worst case, we should do it directly in the database
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparisonId);
        if (!Objects.equals(comparison.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }

        plagiarismService.updatePlagiarismComparisonStatus(comparisonId, statusDTO.status());
        log.info("Finished updating the status {} of the plagiarism comparison with id: {}", statusDTO.status(), comparisonId);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves the plagiarismComparison specified by its ID.
     * If a studentLogin is passed the comparison is anonymized
     *
     * @param courseId the id of the course
     * @param comparisonId the id of the PlagiarismComparison
     * @return the PlagiarismComparison
     * @throws AccessForbiddenException if the requesting user is not affected by the plagiarism case.
     */
    @GetMapping("courses/{courseId}/plagiarism-comparisons/{comparisonId}/for-split-view")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PlagiarismComparison<?>> getPlagiarismComparisonForSplitView(@PathVariable("courseId") long courseId, @PathVariable("comparisonId") Long comparisonId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        var comparisonA = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsAndElementsAElseThrow(comparisonId);
        var comparisonB = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsAndElementsBElseThrow(comparisonId);

        if (!Objects.equals(comparisonA.getPlagiarismResult().getExercise().getCourseViaExerciseGroupOrCourseMember().getId(), courseId)) {
            throw new BadRequestAlertException("The courseId does not belong to the given comparisonId", "PlagiarismComparison", "idMismatch");
        }

        comparisonA.setSubmissionB(comparisonB.getSubmissionB());
        if (authCheckService.isOnlyStudentInCourse(course, user)) {
            // Note: this calls also checks that the student is allowed to see the complaint, and throws otherwise
            checkStudentAccess(comparisonA, user.getLogin());
        }

        // hide unnecessary details
        comparisonA.getSubmissionA().setPlagiarismComparison(null);
        comparisonA.getSubmissionA().setPlagiarismCase(null);
        comparisonA.getSubmissionB().setPlagiarismComparison(null);
        comparisonA.getSubmissionB().setPlagiarismCase(null);

        // hide the chain to plagiarism result, exercise and course to avoid leaks and keep the response small
        comparisonA.setPlagiarismResult(null);
        return ResponseEntity.ok(comparisonA);
    }

    /**
     * Check if the passed userLogin is related to the plagiarism comparison. If this is not the case, the user is now allowed to access.
     * Also anonymizes the comparison for the student view.
     * A student should not have sensitive information (e.g. the userLogin of the other student)
     *
     * @param comparison to anonymize.
     * @param userLogin of the student asking to see his plagiarism comparison.
     */
    private void checkStudentAccess(PlagiarismComparison<?> comparison, String userLogin) {
        if (comparison.getSubmissionA().getStudentLogin().equals(userLogin)) {
            comparison.getSubmissionA().setStudentLogin(YOUR_SUBMISSION);
            comparison.getSubmissionB().setStudentLogin(OTHER_SUBMISSION);
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(userLogin)) {
            comparison.getSubmissionA().setStudentLogin(OTHER_SUBMISSION);
            comparison.getSubmissionB().setStudentLogin(YOUR_SUBMISSION);
        }
        else {
            throw new AccessForbiddenException("This plagiarism comparison is not related to the requesting user.");
        }
    }

    /**
     * Cleans up plagiarism results and comparisons
     * If deleteAll is set to true, all plagiarism results belonging to the exercise are deleted, otherwise only plagiarism comparisons or with status DENIED or CONFIRMED are deleted and old results are deleted as well.
     *
     * @param exerciseId the id of the exercise
     * @param plagiarismResultId the id of plagiarism result
     * @param deleteAll optional parameter whether all plagiarism results belonging to the exercise and all dependent data should be deleted
     * @return the ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the parameters are invalid
     */
    @DeleteMapping("exercises/{exerciseId}/plagiarism-results/{plagiarismResultId}/plagiarism-comparisons")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deletePlagiarismComparisons(@PathVariable("exerciseId") long exerciseId, @PathVariable("plagiarismResultId") long plagiarismResultId,
            @RequestParam() boolean deleteAll) {
        log.info("REST request to clean up plagiarism comparisons for exercise with id: {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        if (deleteAll) {
            // delete all elements for the given exercise
            plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exerciseId);
        }
        else {
            // delete all plagiarism comparisons which are not approved or denied
            plagiarismComparisonRepository.deletePlagiarismComparisonsByPlagiarismResultIdAndStatus(plagiarismResultId, PlagiarismStatus.NONE);
            // also clean up any old and unused plagiarism results
            plagiarismResultRepository.deletePlagiarismResultsByIdNotAndExerciseId(plagiarismResultId, exerciseId);
        }
        return ResponseEntity.ok().build();
    }
}
