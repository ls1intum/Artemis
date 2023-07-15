package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationResultService;
import de.tum.in.www1.artemis.service.connectors.ci.notification.dto.TestResultsDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;

@Profile("gitlabci")
@Service
public class GitLabCIResultService extends AbstractContinuousIntegrationResultService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIResultService.class);

    public GitLabCIResultService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, TestwiseCoverageService testwiseCoverageService,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService, feedbackCreationService);
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        return TestResultsDTO.convert(requestBody);
    }

    // TODO: this seems to be duplicated code
    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        // In GitLab CI we get the logs from the maven command. Therefore, we cannot extract any information about the setup of the runner.
        // In addition, static code analysis is not yet available.

        if (buildLogEntries.isEmpty() || programmingLanguage != ProgrammingLanguage.JAVA) {
            log.debug("No build logs statistics extracted for submission {}", programmingSubmission.getId());
            // No logs received -> Do nothing
            return;
        }

        if (!ProjectType.isMavenProject(projectType)) {
            // A new, unsupported project type was used -> Log it but don't store it since it would only contain null-values
            log.warn("Received unsupported project type {} for GitLabCIService.extractAndPersistBuildLogStatistics, will not store any build log statistics.", projectType);
            return;
        }
        ZonedDateTime jobStarted = getTimestampForLogEntry(buildLogEntries, ""); // First entry;
        ZonedDateTime jobFinished = buildLogEntries.get(buildLogEntries.size() - 1).getTime(); // Last entry
        ZonedDateTime testsStarted = getTimestampForLogEntry(buildLogEntries, "Scanning for projects...");
        ZonedDateTime testsFinished = getTimestampForLogEntry(buildLogEntries, "Total time:");
        Integer dependenciesDownloadedCount = countMatchingLogs(buildLogEntries, "Downloaded from");

        var testDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(testsStarted, testsFinished);
        var totalJobDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(jobStarted, jobFinished);

        // Set the duration to 0 for the durations, we cannot extract.
        var time = ZonedDateTime.now(); // TODO: this needs to be properly implemented
        var agentSetupDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(time, time);
        var scaDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(time, time);

        buildLogStatisticsEntryRepository.saveBuildLogStatisticsEntry(programmingSubmission, agentSetupDuration, testDuration, scaDuration, totalJobDuration,
                dependenciesDownloadedCount);
    }
}
