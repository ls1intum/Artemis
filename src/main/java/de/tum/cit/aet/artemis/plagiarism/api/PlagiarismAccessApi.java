package de.tum.cit.aet.artemis.plagiarism.api;

import java.time.Instant;
import java.util.Optional;

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
     * Check if the user has access to the submission.
     * This method also checks if the submission is before due date.
     *
     * @param exerciseId    the id of the exercise the submission belongs to
     * @param submissionId  the id of the submission
     * @param userLogin     the login of the user
     * @param participation the participation of the user
     * @return true if the user has access to the submission, false otherwise
     */
    public boolean hasAccessToSubmission(Long exerciseId, Long submissionId, String userLogin, Participation participation) {
        return plagiarismAccessService.hasAccessToSubmission(exerciseId, submissionId, userLogin, participation);
    }

    /**
     * Finds the commit that was compared for a participation of a programming exercise.
     *
     * @param exerciseId      the id of the programming exercise
     * @param participationId the id of the participation
     * @return the hash of the most recently compared commit, empty if none was stored
     */
    public Optional<String> findComparedCommitHash(long exerciseId, long participationId) {
        return plagiarismAccessService.findComparedCommitHash(exerciseId, participationId);
    }

    /**
     * Finds when the most recent plagiarism check that compared a participation of a programming exercise was created.
     *
     * @param exerciseId      the id of the programming exercise
     * @param participationId the id of the participation
     * @return the creation date of the most recent result, empty if the participation was not compared
     */
    public Optional<Instant> findComparisonCreatedDate(long exerciseId, long participationId) {
        return plagiarismAccessService.findComparisonCreatedDate(exerciseId, participationId);
    }

    /**
     * Check if the user is involved in a plagiarism case and was notified by the instructor about the plagiarism result.
     *
     * @param exerciseId the id of the exercise the submission belongs to
     * @param submission the submission
     * @param userLogin  the login of the user
     * @return true if the user is involved in a plagiarism case and was notified by the instructor, false otherwise
     */
    public boolean wasUserNotifiedByInstructor(Long exerciseId, Submission submission, String userLogin) {
        return plagiarismAccessService.wasUserNotifiedByInstructor(exerciseId, submission, userLogin);
    }
}
