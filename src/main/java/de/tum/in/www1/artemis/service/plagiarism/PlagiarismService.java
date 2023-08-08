package de.tum.in.www1.artemis.service.plagiarism;

import java.util.HashSet;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
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

    public long getNumberOfPotentialPlagiarismCasesForExercise(long exerciseId) {
        var comparisons = plagiarismComparisonRepository.findAllByExerciseId(exerciseId);
        var comparisonsWithoutDeletedUsers = new HashSet<>();
        for (var comparison : comparisons) {
            addSubmissionIfUserHasNotBeenDeletedForComparison(comparisonsWithoutDeletedUsers, comparison);
        }
        // every comparison can lead to two plagiarism cases, that's why we multiply by two
        return comparisonsWithoutDeletedUsers.size() * 2L;
    }

    private void addSubmissionIfUserHasNotBeenDeletedForComparison(HashSet<Object> comparisonsWithoutDeletedUsers, PlagiarismComparison<?> comparison) {
        var submissionA = submissionRepository.findById(comparison.getSubmissionA().getSubmissionId()).orElseThrow();
        var submissionB = submissionRepository.findById(comparison.getSubmissionB().getSubmissionId()).orElseThrow();
        checkIfUserForSubmissionStillExistsAndAddIfExistent(comparisonsWithoutDeletedUsers, comparison, submissionA);
        checkIfUserForSubmissionStillExistsAndAddIfExistent(comparisonsWithoutDeletedUsers, comparison, submissionB);
    }

    private void checkIfUserForSubmissionStillExistsAndAddIfExistent(HashSet<Object> comparisonsWithoutDeletedUsers, PlagiarismComparison<?> comparison, Submission submission) {
        if (submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            var user = userRepository.findOneByLogin(studentParticipation.getParticipant().getParticipantIdentifier()).orElseThrow();
            if (!user.isDeleted()) {
                comparisonsWithoutDeletedUsers.add(comparison);
            }
        }
    }
}
