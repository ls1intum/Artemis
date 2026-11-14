package de.tum.cit.aet.artemis.plagiarism.api;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismAccessService;

@Controller
@Conditional(PlagiarismEnabled.class)
@Lazy
public class PlagiarismAccessApi extends AbstractPlagiarismApi {

    private final PlagiarismAccessService plagiarismAccessService;

    public PlagiarismAccessApi(PlagiarismAccessService plagiarismAccessService) {
        this.plagiarismAccessService = plagiarismAccessService;
    }

    /**
     * Check if the user has access to the participation in the plagiarism view.
     * This method also checks if the participation is before due date.
     *
     * @param participation the participation whose content the user wants to see
     * @param userLogin     the login of the user
     * @return true if the user has access to the participation, false otherwise
     */
    public boolean hasAccessToParticipation(Participation participation, String userLogin) {
        return plagiarismAccessService.hasAccessToParticipation(participation, userLogin);
    }

    /**
     * Finds the submission of the participation that was compared in a plagiarism check.
     *
     * @param participationId the id of the participation
     * @param userLogin       the login of the student asking to see the comparison, or null for staff
     * @return the id of the compared submission, or empty if no submission of the participation was compared in a comparison the user may see
     */
    public Optional<Long> findComparedSubmissionId(long participationId, @Nullable String userLogin) {
        return plagiarismAccessService.findComparedSubmissionId(participationId, userLogin);
    }

    /**
     * Check if the user is involved in a plagiarism case and was notified by the instructor about the plagiarism result.
     *
     * @param submission the submission
     * @param userLogin  the login of the user
     * @return true if the user is involved in a plagiarism case and was notified by the instructor, false otherwise
     */
    public boolean wasUserNotifiedByInstructor(Submission submission, String userLogin) {
        return plagiarismAccessService.wasUserNotifiedByInstructor(submission, userLogin);
    }
}
