package de.tum.cit.aet.artemis.service.connectors.jenkins;

import static de.tum.cit.aet.artemis.domain.statistics.BuildLogStatisticsEntry.BuildJobPartDuration;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.BuildLogEntry;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.enumeration.ProjectType;
import de.tum.cit.aet.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.cit.aet.artemis.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.service.connectors.ci.AbstractContinuousIntegrationResultService;
import de.tum.cit.aet.artemis.service.connectors.ci.notification.dto.TestResultsDTO;
import de.tum.cit.aet.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.cit.aet.artemis.service.hestia.TestwiseCoverageService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;

@Profile("jenkins")
@Service
public class JenkinsResultService extends AbstractContinuousIntegrationResultService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsResultService.class);

    public JenkinsResultService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, TestwiseCoverageService testwiseCoverageService,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        super(testCaseRepository, buildLogStatisticsEntryRepository, testwiseCoverageService, feedbackCreationService, programmingExerciseBuildConfigRepository);
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        return TestResultsDTO.convert(requestBody);
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

        ZonedDateTime jobStarted = getTimestampForLogEntry(buildLogEntries, ""); // First entry;
        ZonedDateTime agentSetupCompleted = null;
        ZonedDateTime testsStarted;
        ZonedDateTime testsFinished;
        ZonedDateTime scaStarted;
        ZonedDateTime scaFinished;
        ZonedDateTime jobFinished = buildLogEntries.getLast().getTime(); // Last entry
        Integer dependenciesDownloadedCount = null;

        if (ProjectType.isMavenProject(projectType)) {
            agentSetupCompleted = getTimestampForLogEntry(buildLogEntries, "docker exec");
            testsStarted = getTimestampForLogEntry(buildLogEntries, "Scanning for projects...");
            testsFinished = getTimestampForLogEntry(buildLogEntries, "Total time:");
            scaStarted = getTimestampForLogEntry(buildLogEntries, "Scanning for projects...", 1);
            scaFinished = getTimestampForLogEntry(buildLogEntries, "Total time:", 1);
            dependenciesDownloadedCount = countMatchingLogs(buildLogEntries, "Downloaded from");
        }
        else if (projectType.isGradle()) {
            // agentSetupCompleted is not supported
            testsStarted = getTimestampForLogEntry(buildLogEntries, "Starting a Gradle Daemon");
            testsFinished = getTimestampForLogEntry(buildLogEntries,
                    buildLogEntry -> buildLogEntry.getLog().contains("BUILD SUCCESSFUL in") || buildLogEntry.getLog().contains("BUILD FAILED in"));
            scaStarted = getTimestampForLogEntry(buildLogEntries, "Task :checkstyleMain");
            scaFinished = getTimestampForLogEntry(buildLogEntries,
                    buildLogEntry -> buildLogEntry.getLog().contains("BUILD SUCCESSFUL in") || buildLogEntry.getLog().contains("BUILD FAILED in"), 1);
            // dependenciesDownloadedCount is not supported
        }
        else {
            // A new, unsupported project type was used -> Log it but don't store it since it would only contain null-values
            log.warn("Received unsupported project type {} for JenkinsService.extractAndPersistBuildLogStatistics, will not store any build log statistics.", projectType);
            return;
        }

        var agentSetupDuration = new BuildJobPartDuration(jobStarted, agentSetupCompleted);
        var testDuration = new BuildJobPartDuration(testsStarted, testsFinished);
        var scaDuration = new BuildJobPartDuration(scaStarted, scaFinished);
        var totalJobDuration = new BuildJobPartDuration(jobStarted, jobFinished);

        buildLogStatisticsEntryRepository.saveBuildLogStatisticsEntry(programmingSubmission, agentSetupDuration, testDuration, scaDuration, totalJobDuration,
                dependenciesDownloadedCount);
    }
}
