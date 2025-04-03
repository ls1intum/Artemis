package de.tum.cit.aet.artemis.plagiarism.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismService;

@Profile(PROFILE_CORE)
@Controller
public class PlagiarismApi extends AbstractPlagiarismApi {

    private final PlagiarismService plagiarismService;

    public PlagiarismApi(PlagiarismService plagiarismService) {
        this.plagiarismService = plagiarismService;
    }

    /**
     * Check if the user has access to the submission.
     * This method also checks if the submission is before due date.
     *
     * @param submissionId  the id of the submission
     * @param userLogin     the login of the user
     * @param participation the participation of the user
     * @return true if the user has access to the submission, false otherwise
     */
    public boolean hasAccessToSubmission(Long submissionId, String userLogin, Participation participation) {
        return plagiarismService.hasAccessToSubmission(submissionId, userLogin, participation);
    }

    /**
     * Check if the user is involved in a plagiarism case and was notified by the instructor about the plagiarism result.
     *
     * @param submission the submission
     * @param userLogin  the login of the user
     * @return true if the user is involved in a plagiarism case and was notified by the instructor, false otherwise
     */
    public boolean wasUserNotifiedByInstructor(Submission submission, String userLogin) {
        return plagiarismService.wasUserNotifiedByInstructor(submission, userLogin);
    }
}
