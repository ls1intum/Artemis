package de.tum.in.www1.artemis.service.plagiarism;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class PlagiarismService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final SubmissionRepository submissionRepository;

    private final UserRepository userRepository;

    public PlagiarismService(PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismCaseService plagiarismCaseService, SubmissionRepository submissionRepository,
            UserRepository userRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Anonymize the submission for the student view.
     * A student should not see sensitive information but be able to retrieve both answers from both students for the comparison
     *
     * @param submission the submission to anonymize.
     * @param userLogin  the user login of the student asking to see his plagiarism comparison.
     */
    public void checkAccessAndAnonymizeSubmissionForStudent(Submission submission, String userLogin) {
        if (!wasUserNotifiedByInstructor(submission.getId(), userLogin)) {
            throw new AccessForbiddenException("This plagiarism submission is not related to the requesting user or the user has not been notified yet.");
        }
        submission.setParticipation(null);
        submission.setResults(null);
        submission.setSubmissionDate(null);
    }

    /**
     * Checks whether the student with the given user login is involved in a plagiarism case which contains the given submissionId and the student is notified by the instructor.
     *
     * @param submissionId the id of a submissions that will be checked in plagiarism cases
     * @param userLogin    the user login of the student
     * @return true if the student with user login owns one of the submissions in a PlagiarismComparison which contains the given submissionId and is notified by the instructor,
     *         otherwise false
     */
    public boolean wasUserNotifiedByInstructor(Long submissionId, String userLogin) {
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(submissionId, submissionId);

        // disallow requests from users who are not notified about this case:
        if (comparisonOptional.isPresent()) {
            var comparisons = comparisonOptional.get();
            return comparisons.stream()
                    .anyMatch(comparison -> (comparison.getSubmissionA().getPlagiarismCase() != null
                            && (comparison.getSubmissionA().getPlagiarismCase().getPost() != null || comparison.getSubmissionA().getPlagiarismCase().getVerdict() != null)
                            && (comparison.getSubmissionA().getStudentLogin().equals(userLogin)))
                            || (comparison.getSubmissionB().getPlagiarismCase() != null
                                    && (comparison.getSubmissionB().getPlagiarismCase().getPost() != null || comparison.getSubmissionB().getPlagiarismCase().getVerdict() != null)
                                    && (comparison.getSubmissionB().getStudentLogin().equals(userLogin))));
        }
        return false;
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
     * Additionally, it filters out cases for deleted user --> isDeleted = true because we do not delete the user entity entirely.
     *
     * @param exerciseId the exercise id for which the potential plagiarism cases should be retrieved
     * @return the number of potential plagiarism cases
     */
    public long getNumberOfPotentialPlagiarismCasesForExercise(long exerciseId) {
        var comparisons = plagiarismComparisonRepository.findAllByPlagiarismResultExerciseId(exerciseId);
        Set<PlagiarismSubmission<?>> submissionsWithoutDeletedUsers = new HashSet<>();
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
    private void addSubmissionsIfUserHasNotBeenDeleted(PlagiarismComparison<?> comparison, Set<PlagiarismSubmission<?>> submissionsWithoutDeletedUsers) {
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
     * Checks if the user the submission belongs to, has not the isDeleted flag set to true
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
}
