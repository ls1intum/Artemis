package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;

@Service
public class BuildLogStatisticsEntryService {

    private final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    public BuildLogStatisticsEntryService(BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository) {
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
    }

    /**
     * Generate a BuildLogStatisticsEntry from the given ZonedDateTime (and other parameters) and persist it.
     *
     * @param programmingSubmission the submission for which the BuildLogStatisticsEntry should be generated
     * @param jobStarted the ZonedDateTime when the CI server started the build (does not include queueing time), or null
     * @param agentSetupCompleted the ZonedDateTime when the CI server completed the setup of the build agent (e.g. pulling the docker images is completed), or null
     * @param testsStarted the ZonedDateTime when the tests have been started, or null
     * @param testsFinished the ZonedDateTime when the tests have been finished, or null
     * @param scaStarted the ZonedDateTime when the static code analysis has been started, or null
     * @param scaFinished the ZonedDateTime when the static code analysis has been finished, or null
     * @param jobFinished the ZonedDateTime when the CI server completed the build, or null
     * @param dependenciesDownloadedCount the number of dependencies downloaded during the build, or null (if it is not exposed through the logs)
     * @return the already persisted BuildLogStatisticsEntry
     */
    public BuildLogStatisticsEntry saveBuildLogStatisticsEntry(ProgrammingSubmission programmingSubmission, BuildLogStatisticsEntry.BuildJobPartDuration agentSetupDuration,
            BuildLogStatisticsEntry.BuildJobPartDuration testDuration, BuildLogStatisticsEntry.BuildJobPartDuration scaDuration,
            BuildLogStatisticsEntry.BuildJobPartDuration totalJobDuration, Long dependenciesDownloadedCount) {

        BuildLogStatisticsEntry buildLogStatisticsEntry = new BuildLogStatisticsEntry(programmingSubmission, agentSetupDuration.durationInSeconds(),
                testDuration.durationInSeconds(), scaDuration.durationInSeconds(), totalJobDuration.durationInSeconds(), dependenciesDownloadedCount);
        return buildLogStatisticsEntryRepository.save(buildLogStatisticsEntry);
    }
}
