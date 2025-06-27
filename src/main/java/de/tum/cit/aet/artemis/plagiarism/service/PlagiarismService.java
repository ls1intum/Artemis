package de.tum.cit.aet.artemis.plagiarism.service;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;

@Conditional(PlagiarismEnabled.class)
@Lazy
@Service
public class PlagiarismService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final AuthorizationCheckService authCheckService;

    private final SubmissionRepository submissionRepository;

    private final UserRepository userRepository;

    public PlagiarismService(PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismCaseService plagiarismCaseService, AuthorizationCheckService authCheckService,
            SubmissionRepository submissionRepository, UserRepository userRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.authCheckService = authCheckService;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Update the status of the plagiarism comparison.
     *
     * @param plagiarismComparisonId the ID of the plagiarism comparison
     * @param plagiarismStatus       the status to be set
     */
    public void updatePlagiarismComparisonStatus(long plagiarismComparisonId, PlagiarismStatus plagiarismStatus) {
        plagiarismComparisonRepository.updatePlagiarismComparisonStatus(plagiarismComparisonId, plagiarismStatus);
        if (plagiarismStatus.equals(PlagiarismStatus.CONFIRMED)) {
            plagiarismCaseService.createOrAddToPlagiarismCasesForComparison(plagiarismComparisonId);
        }
        else if (plagiarismStatus.equals(PlagiarismStatus.DENIED)) {
            plagiarismCaseService.removeSubmissionsInPlagiarismCasesForComparison(plagiarismComparisonId);
        }
    }

    /**
     * Retrieves the number of potential plagiarism cases by considering the plagiarism submissions for the exercise
     * Additionally, it filters out cases for deleted user --> deleted = true because we do not delete the user entity entirely.
     *
     * @param exerciseId the exercise id for which the potential plagiarism cases should be retrieved
     * @return the number of potential plagiarism cases
     */
    public long getNumberOfPotentialPlagiarismCasesForExercise(long exerciseId) {
        var comparisons = plagiarismComparisonRepository.findAllByPlagiarismResultExerciseId(exerciseId);
        Set<PlagiarismSubmission> submissionsWithoutDeletedUsers = new HashSet<>();
        for (var comparison : comparisons) {
            addSubmissionsIfUserHasNotBeenDeleted(comparison, submissionsWithoutDeletedUsers);
        }
        return submissionsWithoutDeletedUsers.size();
    }

    /**
     * Add each submission of the plagiarism comparison if the corresponding user has not been deleted
     *
     * @param comparison                     the comparison for which we want check if the user of the submission has been deleted.
     * @param submissionsWithoutDeletedUsers a set of plagiarism submissions for which the user still exists.
     */
    private void addSubmissionsIfUserHasNotBeenDeleted(PlagiarismComparison comparison, Set<PlagiarismSubmission> submissionsWithoutDeletedUsers) {
        var plagiarismSubmissionA = comparison.getSubmissionA();
        var plagiarismSubmissionB = comparison.getSubmissionB();
        var submissionA = submissionRepository.findById(plagiarismSubmissionA.getSubmissionId()).orElseThrow();
        var submissionB = submissionRepository.findById(plagiarismSubmissionB.getSubmissionId()).orElseThrow();
        if (!userForSubmissionDeleted(submissionA)) {
            submissionsWithoutDeletedUsers.add(plagiarismSubmissionA);
        }
        if (!userForSubmissionDeleted(submissionB)) {
            submissionsWithoutDeletedUsers.add(plagiarismSubmissionB);

        }
    }

    /**
     * Checks if the user the submission belongs to, has not the deleted flag set to true
     *
     * @param submission the submission to check
     * @return true if the user is NOT deleted, false otherwise
     */
    private boolean userForSubmissionDeleted(Submission submission) {
        if (submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            var user = userRepository.findOneByLogin(studentParticipation.getParticipant().getParticipantIdentifier());
            if (user.isPresent()) {
                return user.get().isDeleted();
            }
        }
        return true; // if the user is not found, we assume that the user has been deleted
    }

    /**
     * filter a participation based on the last submission and make sure the last result - if available - has a minimum score - in case this is larger than 0
     *
     * @param minimumScore the minimum score
     * @return a predicate that can be used in streams for filtering
     */
    @NotNull
    public static Predicate<ProgrammingExerciseParticipation> filterParticipationMinimumScore(int minimumScore) {
        return participation -> {
            Submission submission = participation.findLatestSubmission().orElse(null);
            // filter empty submissions
            if (submission == null) {
                return false;
            }
            return hasMinimumScore(submission, minimumScore);
        };
    }

    /**
     * filter a submission so that the last result - if available - has a minimum score - in case this is larger than 0
     *
     * @param submission   the submission which latest result should be tested for filtering
     * @param minimumScore the minimum score
     * @return a predicate that can be used in streams for filtering
     */
    public static boolean hasMinimumScore(Submission submission, int minimumScore) {
        return minimumScore == 0
                || submission.getLatestResult() != null && submission.getLatestResult().getScore() != null && submission.getLatestResult().getScore() >= minimumScore;
    }

    /**
     * filter a student participation so that it includes a student that is not at least a tutor, in case of team participations no filter is applied
     *
     * @return a predicate that can be used in streams for filtering
     */
    @NotNull
    public Predicate<StudentParticipation> filterForStudents() {
        return participation -> {
            // this filter excludes individual participations that are not from students
            if (participation.getStudent().isPresent()) {
                return !authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), participation.getStudent().get());
            }
            // however, we make sure that team participations are included (in the unlikely case, neither student and team is defined, we filter)
            else {
                return participation.getTeam().isPresent();
            }
        };
    }
}
