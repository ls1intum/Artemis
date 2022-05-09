package de.tum.in.www1.artemis.service.plagiarism;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.repository.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class PlagiarismService {

    // correspond to the translation files (suffix) used in the client
    private static final String YOUR_SUBMISSION = "Your submission";

    private static final String OTHER_SUBMISSION = "Other submission";

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    public PlagiarismService(PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

    /**
     * Anonymize the comparison for the student view.
     * A student should not have sensitive information (e.g. the userLogin of the other student)
     *
     * @param comparison to anonymize.
     * @param userLogin of the student asking to see his plagiarism comparison.
     * @return the anonymized plagiarism comparison for the given student
     */
    public PlagiarismComparison anonymizeComparisonForStudentView(PlagiarismComparison comparison, String userLogin) {
        if (comparison.getSubmissionA().getStudentLogin().equals(userLogin)) {
            comparison.getSubmissionA().setStudentLogin(YOUR_SUBMISSION);
            comparison.getSubmissionB().setStudentLogin(OTHER_SUBMISSION);
            comparison.setInstructorStatementB(null);
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(userLogin)) {
            comparison.getSubmissionA().setStudentLogin(OTHER_SUBMISSION);
            comparison.getSubmissionB().setStudentLogin(YOUR_SUBMISSION);
            comparison.setInstructorStatementA(null);
        }
        else {
            throw new AccessForbiddenException("This plagiarism comparison is not related to the requesting user.");
        }
        return comparison;
    }

    /**
     * Anonymize the submission for the student view.
     * A student should not see sensitive information but be able to retrieve both answers from both students for the comparison
     *
     * @param submission to anonymize.
     * @param userLogin of the student asking to see his plagiarism comparison.
     * @return the anonymized submission for the given student
     */
    public Submission anonymizeSubmissionForStudentView(Submission submission, String userLogin) {
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(submission.getId(), submission.getId());

        // disallow requests from users who are not notified about this case:
        boolean isUserNotifiedByInstructor = false;
        if (comparisonOptional.isPresent()) {
            var comparisons = comparisonOptional.get();
            isUserNotifiedByInstructor = comparisons.stream()
                    .anyMatch(comparison -> (comparison.getInstructorStatementA() != null && (comparison.getSubmissionA().getStudentLogin().equals(userLogin)))
                            || (comparison.getInstructorStatementB() != null && (comparison.getSubmissionB().getStudentLogin().equals(userLogin))));
        }
        if (!isUserNotifiedByInstructor) {
            throw new AccessForbiddenException("This plagiarism submission is not related to the requesting user or the user has not been notified yet.");
        }
        submission.setParticipation(null);
        submission.setResults(null);
        submission.setSubmissionDate(null);
        return submission;
    }
}
