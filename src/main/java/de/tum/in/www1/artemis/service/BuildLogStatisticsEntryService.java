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
     * @param agentSetupDuration the BuildJobPartDuration between the start of the build on the CI server and the completion of the agent setup. This includes e.g. pulling the docker images
     * @param testDuration the BuildJobPartDuration of the test execution
     * @param scaDuration the BuildJobPartDuration of the SCA execution
     * @param totalJobDuration the BuildJobPartDuration of the complete job
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
