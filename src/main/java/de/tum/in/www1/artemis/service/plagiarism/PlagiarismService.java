package de.tum.in.www1.artemis.service.plagiarism;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class PlagiarismService {

    // correspond to the translation files (suffix) used in the client
    private static final String YOUR_SUBMISSION = "Your submission";

    private static final String OTHER_SUBMISSION = "Other submission";

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    public PlagiarismService(PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismCaseService plagiarismCaseService) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismCaseService = plagiarismCaseService;
    }

    /**
     * Anonymize the comparison for the student view.
     * A student should not have sensitive information (e.g. the userLogin of the other student)
     *
     * @param comparison to anonymize.
     * @param userLogin of the student asking to see his plagiarism comparison.
     * @return the anonymized plagiarism comparison for the given student
     */
    public PlagiarismComparison anonymizeComparisonForStudent(PlagiarismComparison comparison, String userLogin) {
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
        return comparison;
    }

    /**
     * Anonymize the submission for the student view.
     * A student should not see sensitive information but be able to retrieve both answers from both students for the comparison
     *
     * @param submission    the submission to anonymize.
     * @param userLogin     the user login of the student asking to see his plagiarism comparison.
     * @return the anonymized submission for the given student
     */
    public Submission anonymizeSubmissionForStudent(Submission submission, String userLogin) {
        if (!isUserNotifiedByInstructor(submission.getId(), userLogin)) {
            throw new AccessForbiddenException("This plagiarism submission is not related to the requesting user or the user has not been notified yet.");
        }
        submission.setParticipation(null);
        submission.setResults(null);
        submission.setSubmissionDate(null);
        return submission;
    }

    /**
     * Checks whether the student with the given userlogin is involved in a plagiarism case which contains the given submissionId and the student is notified by the instructor.
     *
     * @param submissionId the id of a submissions that will be checked in plagiarism cases
     * @param userLogin the user login of the student
     * @return true if the student with userlogin owns one of the submissions in a PlagiarismComparison which contains the given submissionId and is notified by the instructor, otherwise false
     */
    public boolean isUserNotifiedByInstructor(Long submissionId, String userLogin) {
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
     * @param plagiarismComparisonId    the ID of the plagiarism comparison
     * @param plagiarismStatus          the status to be set
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
}
