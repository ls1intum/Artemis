package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

public abstract class AbstractContinuousIntegrationService implements ContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(AbstractContinuousIntegrationService.class);

    @Value("${artemis.continuous-integration.url}")
    protected URL serverUrl;

    protected final ProgrammingSubmissionRepository programmingSubmissionRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final BuildLogEntryService buildLogService;

    protected final RestTemplate restTemplate;

    protected final RestTemplate shortTimeoutRestTemplate;

    protected final Optional<VersionControlService> versionControlService;

    public AbstractContinuousIntegrationService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository,
            BuildLogEntryService buildLogService, RestTemplate restTemplate, RestTemplate shortTimeoutRestTemplate, Optional<VersionControlService> versionControlService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.buildLogService = buildLogService;
        this.versionControlService = versionControlService;
    }

    /**
     * Retrieves the submission that is assigned to the specified participation and its commit hash matches the one from
     * the build result.
     *
     * @param participationId id of the participation
     * @param buildResult     The build results
     * @return The submission or empty no submissions exist
     */
    protected Optional<ProgrammingSubmission> getSubmissionForBuildResult(Long participationId, AbstractBuildResultNotificationDTO buildResult) {
        var submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId);
        if (submissions.isEmpty()) {
            return Optional.empty();
        }

        return submissions.stream().filter(theSubmission -> {
            var commitHash = getCommitHash(buildResult, theSubmission.getType());
            return commitHash.isPresent() && commitHash.get().equals(theSubmission.getCommitHash());
        }).max(Comparator.comparing(ProgrammingSubmission::getSubmissionDate));
    }

    /**
     * There can be two reasons for the case that there is no programmingSubmission:
     * 1) Manual build triggered from CI (e.g. by the instructor)
     * 2) An unknown error that caused the programming submission not to be created when the code commits have been pushed.
     * we can still get the commit hash from the payload of the CI build result and "reverse engineer" the programming submission object to be consistent
     */
    @NotNull
    protected ProgrammingSubmission createFallbackSubmission(ProgrammingExerciseParticipation participation, ZonedDateTime submissionDate, String commitHash) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation((Participation) participation);
        submission.setSubmitted(true);
        // We set this to manual because all programming submissions should correspond to a student commit in the git history.
        // In case we cannot find the appropriate submission, it means something has not worked before, but there will still be a commit in the student repository
        submission.setType(SubmissionType.MANUAL);
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(submissionDate);
        return submission;
    }

    @NotNull
    protected ProgrammingSubmission createAndSaveFallbackSubmission(ProgrammingExerciseParticipation participation, AbstractBuildResultNotificationDTO buildResult) {
        final var commitHash = getCommitHash(buildResult, SubmissionType.MANUAL);
        if (commitHash.isEmpty()) {
            log.error("Could not find commit hash for participation {}, build plan {}", participation.getId(), participation.getBuildPlanId());
        }
        log.warn("Could not find pending ProgrammingSubmission for Commit-Hash {} (Participation {}, Build-Plan {}). Will create a new one subsequently...", commitHash,
                participation.getId(), participation.getBuildPlanId());
        // We always take the build run date as the fallback solution
        // In general we try to get the actual date.
        ZonedDateTime submissionDate = buildResult.getBuildRunDate();
        if (commitHash.isPresent() && versionControlService.isPresent()) {
            try {
                submissionDate = versionControlService.get().getPushDate(participation, commitHash.get());
            }
            catch (VersionControlException e) {
                log.error("Could not retrieve push date for participation " + participation.getId() + " and build plan " + participation.getBuildPlanId(), e);
            }
        }
        var submission = createFallbackSubmission(participation, submissionDate, commitHash.orElse(null));
        // Save to avoid TransientPropertyValueException.
        return programmingSubmissionRepository.save(submission);
    }

    /**
     * Get the commit hash from the build result, the commit hash will be different for submission types or null.
     *
     * @param buildResult    Build result data provided by build notification.
     * @param submissionType describes why the build was started.
     * @return if the commit hash for the given submission type was found, otherwise empty.
     */
    protected Optional<String> getCommitHash(AbstractBuildResultNotificationDTO buildResult, SubmissionType submissionType) {
        final var isAssignmentSubmission = List.of(SubmissionType.MANUAL, SubmissionType.INSTRUCTOR, SubmissionType.ILLEGAL).contains(submissionType);
        if (isAssignmentSubmission) {
            return buildResult.getCommitHashFromAssignmentRepo();
        }
        else if (submissionType.equals(SubmissionType.TEST)) {
            return buildResult.getCommitHashFromTestsRepo();
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * Generate an Artemis result object from the CI build result. Will use the test case feedback as result feedback.
     *
     * @param buildResult   Build result data provided by build notification.
     * @param participation to attach result to.
     * @return the created result
     */
    protected Result createResultFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingExerciseParticipation participation) {
        final var result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful(buildResult.isBuildSuccessful());
        result.setCompletionDate(buildResult.getBuildRunDate());
        result.setScore(buildResult.getBuildScore());
        result.setParticipation((Participation) participation);
        addFeedbackToResult(result, buildResult);

        // We assume the build has failed if no test case feedback has been sent. Static code analysis feedback might exist even though the build failed
        boolean hasTestCaseFeedback = result.getFeedbacks().stream().anyMatch(feedback -> !feedback.isStaticCodeAnalysisFeedback());
        result.setResultString(hasTestCaseFeedback ? buildResult.getTestsPassedString() : "No tests found");
        return result;
    }

    /**
     * Converts build result details into feedback and stores it in the result object
     *
     * @param result      the result for which the feedback should be added
     * @param buildResult The build result
     */
    protected abstract void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult);

    /**
     * Filter the given list of unfiltered build log entries and return A NEW list only including the filtered build logs.
     *
     * @param buildLogEntries     the original, unfiltered list
     * @param programmingLanguage the programming language for filtering out language-specific logs
     * @return the filtered list
     */
    protected List<BuildLogEntry> removeUnnecessaryLogsForProgrammingLanguage(List<BuildLogEntry> buildLogEntries, ProgrammingLanguage programmingLanguage) {
        List<BuildLogEntry> buildLogs = buildLogService.removeUnnecessaryLogsForProgrammingLanguage(buildLogEntries, programmingLanguage);
        // Replace some unnecessary information and hide complex details to make it easier to read the important information
        return buildLogs.stream().peek(buildLog -> buildLog.setLog(ASSIGNMENT_PATH.matcher(buildLog.getLog()).replaceAll(""))).collect(Collectors.toList());
    }
}
