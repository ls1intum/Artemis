package de.tum.in.www1.artemis.service.plagiarism;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.repository.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class PlagiarismCasesService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    public PlagiarismCasesService(PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

    /**
     * Anonymizes a submission for students to review in a plagiarism case.
     *
     * @param submission the submission to be anonymized
     * @param user The user the submission is sent to
     * @throws AccessForbiddenException if the requesting user is not involved in the plagiarism case.
     */
    public void anonymizeSubmissionForStudentOrThrow(Submission submission, User user) {
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(submission.getId(), submission.getId());
        // disallow requests from users who are not notified about this case:
        boolean isUserNotified = false;
        if (comparisonOptional.isPresent()) {
            var comparisons = comparisonOptional.get();
            isUserNotified = comparisons.stream().anyMatch(c -> (c.getNotificationA() != null && ((SingleUserNotification) c.getNotificationA()).getRecipient().equals(user)
                    || c.getNotificationB() != null && ((SingleUserNotification) c.getNotificationB()).getRecipient().equals(user)));
        }
        if (!isUserNotified) {
            throw new AccessForbiddenException("");
        }
        // anonymize:
        submission.setParticipation(null);
        submission.setResults(null);
        submission.setSubmissionDate(null);
    }
}
