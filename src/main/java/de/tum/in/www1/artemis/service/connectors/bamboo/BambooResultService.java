package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry.BuildJobPartDuration;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationResultService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;

@Service
@Profile("bamboo")
public class BambooResultService extends AbstractContinuousIntegrationResultService {

    private final ObjectMapper mapper;

    public BambooResultService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            TestwiseCoverageService testwiseCoverageService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, ObjectMapper mapper,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService, feedbackCreationService);
        this.mapper = mapper;
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        final var buildResult = mapper.convertValue(requestBody, BambooBuildResultNotificationDTO.class);
        // Filter the first build plan in case it was automatically executed when the build plan was created.
        if (isFirstBuildForThisPlan(buildResult)) {
            return null;
        }
        return buildResult;
    }

    /**
     * Check if the build result received is the initial build of the plan.
     * Note: this is an edge case and means it was not created with a commit+push by the user
     *
     * @param buildResult Build result data provided by build notification.
     * @return true if build is the first build.
     */
    private boolean isFirstBuildForThisPlan(BambooBuildResultNotificationDTO buildResult) {
        final var reason = buildResult.getBuild().reason();
        return reason != null && reason.contains("First build for this plan");
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        if (buildLogEntries.isEmpty()) {
            // No logs received -> Do nothing
            return;
        }

        if (programmingLanguage != ProgrammingLanguage.JAVA) {
            // Not supported -> Do nothing
            return;
        }

        ZonedDateTime jobStarted = getTimestampForLogEntry(buildLogEntries, "started building on agent");
        ZonedDateTime agentSetupCompleted = getTimestampForLogEntry(buildLogEntries, "Executing build");
        ZonedDateTime testsStarted = getTimestampForLogEntry(buildLogEntries, "Starting task 'Tests'");
        ZonedDateTime testsFinished = getTimestampForLogEntry(buildLogEntries, "Finished task 'Tests' with result");
        ZonedDateTime scaStarted = getTimestampForLogEntry(buildLogEntries, "Starting task 'Static Code Analysis'");
        ZonedDateTime scaFinished = getTimestampForLogEntry(buildLogEntries, "Finished task 'Static Code Analysis'");
        ZonedDateTime jobFinished = getTimestampForLogEntry(buildLogEntries, "Finished building");

        Integer dependenciesDownloadedCount = null;

        if (ProjectType.isMavenProject(projectType)) {
            // Not supported for GRADLE projects
            dependenciesDownloadedCount = countMatchingLogs(buildLogEntries, "Downloaded from");
        }

        var agentSetupDuration = new BuildJobPartDuration(jobStarted, agentSetupCompleted);
        var testDuration = new BuildJobPartDuration(testsStarted, testsFinished);
        var scaDuration = new BuildJobPartDuration(scaStarted, scaFinished);
        var totalJobDuration = new BuildJobPartDuration(jobStarted, jobFinished);

        buildLogStatisticsEntryRepository.saveBuildLogStatisticsEntry(programmingSubmission, agentSetupDuration, testDuration, scaDuration, totalJobDuration,
                dependenciesDownloadedCount);
    }

}
