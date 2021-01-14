package de.tum.in.www1.artemis.service.connectors;

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

public abstract class AbstractContinuousIntegrationService implements ContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(AbstractContinuousIntegrationService.class);

    protected ProgrammingSubmissionRepository programmingSubmissionRepository;

    public AbstractContinuousIntegrationService(ProgrammingSubmissionRepository programmingSubmissionRepository) {
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

    @NotNull
    protected ProgrammingSubmission createAndSaveFallbackSubmission(ProgrammingExerciseParticipation participation, AbstractBuildResultNotificationDTO buildResult) {
        final var commitHash = getCommitHash(buildResult, SubmissionType.MANUAL);
        log.warn("Could not find pending ProgrammingSubmission for Commit-Hash {} (Participation {}, Build-Plan {}). Will create a new one subsequently...", commitHash,
                participation.getId(), participation.getBuildPlanId());
        // In this case we don't know the submission time, so we use the result completion time as a fallback.
        // TODO: we should actually ask the git service to retrieve the actual commit date in the git repository here and only use result.getCompletionDate() as fallback
        var submission = createFallbackSubmission(participation, buildResult.getBuildRunDate(), commitHash.orElse(null));
        // Save to avoid TransientPropertyValueException.
        programmingSubmissionRepository.save(submission);
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
