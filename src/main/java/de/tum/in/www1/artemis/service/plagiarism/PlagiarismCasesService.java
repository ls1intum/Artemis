package de.tum.in.www1.artemis.service.plagiarism;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;
import de.tum.in.www1.artemis.repository.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import org.springframework.stereotype.Service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

@Service
public class PlagiarismCasesService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    public PlagiarismCasesService(PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

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
        // we are a student notified about plagiarism, anonymize:
        submission.setParticipation(null);
        submission.setResults(null);
        submission.setSubmissionDate(null);
    }
}
