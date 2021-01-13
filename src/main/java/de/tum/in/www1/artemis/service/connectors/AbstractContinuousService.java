package de.tum.in.www1.artemis.service.connectors;

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

public abstract class AbstractContinuousService implements ContinuousIntegrationService {

    protected ProgrammingSubmissionRepository programmingSubmissionRepository;

    public AbstractContinuousService(ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
    }

    /**
     * Retrieves the submission that is assigned to the specified particiation and its commit hash matches the one from
     * the build result.
     * @param participationId id of the participation
     * @param buildResult The build results
     * @return The submission or empty no submissions exist
     */
    protected Optional<ProgrammingSubmission> getSubmissionForBuildResult(Long participationId, AbstractBuildResultNotificationDTO buildResult) {
        var optionalSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participationId);
        if (optionalSubmission.isEmpty()) {
            return Optional.empty();
        }

        var submission = optionalSubmission.get();
        var commitHash = getCommitHash(buildResult, optionalSubmission.get().getType());
        if (commitHash.isPresent() && submission.getCommitHash().equals(commitHash.get())) {
            return Optional.of(submission);
        }

        return Optional.empty();
    }

    /**
     * There can be two reasons for the case that there is no programmingSubmission:
     * 1) Manual build triggered from CI (e.g. by the instructor)
     * 2) An unknown error that caused the programming submission not to be created when the code commits have been pushed.
     * we can still get the commit hash from the payload of the CI build result and "reverse engineer" the programming submission object to be consistent
     *
     */
    @NotNull
    protected ProgrammingSubmission createFallbackSubmission(ProgrammingExerciseParticipation participation, ZonedDateTime submissionDate, String commitHash) {
        ProgrammingSubmission submission;
        submission = new ProgrammingSubmission();
        submission.setParticipation((Participation) participation);
        submission.setSubmitted(true);
        submission.setType(SubmissionType.OTHER);
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(submissionDate);
        return submission;
    }

    /**
     * Get the commit hash from the build result, the commit hash will be different for submission types or null.
     *
     * @param buildResult    Build result data provided by build notification.
     * @param submissionType describes why the build was started.
     * @return if the commit hash for the given submission type was found, otherwise empty.
     */
    protected abstract Optional<String> getCommitHash(AbstractBuildResultNotificationDTO buildResult, SubmissionType submissionType);
}
