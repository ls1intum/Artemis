package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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

    public BuildLogStatisticsEntry saveBuildLogStatisticsEntry(ProgrammingSubmission programmingSubmission, ZonedDateTime jobStarted, ZonedDateTime agentSetupCompleted,
            ZonedDateTime testsStarted, ZonedDateTime testsFinished, ZonedDateTime scaStarted, ZonedDateTime scaFinished, ZonedDateTime jobFinished,
            Long dependenciesDownloadedCount) {
        Long agentSetupDuration = null;
        if (jobStarted != null && agentSetupCompleted != null) {
            agentSetupDuration = ChronoUnit.SECONDS.between(jobStarted, agentSetupCompleted);
        }
        Long testDuration = null;
        if (testsStarted != null && testsFinished != null) {
            testDuration = ChronoUnit.SECONDS.between(testsStarted, testsFinished);
        }
        Long scaDuration = null;
        if (scaStarted != null && scaFinished != null) {
            scaDuration = ChronoUnit.SECONDS.between(scaStarted, scaFinished);
        }
        Long totalJobDuration = null;
        if (jobStarted != null && jobFinished != null) {
            totalJobDuration = ChronoUnit.SECONDS.between(jobStarted, jobFinished);
        }

        BuildLogStatisticsEntry buildLogStatisticsEntry = new BuildLogStatisticsEntry(programmingSubmission, agentSetupDuration, testDuration, scaDuration, totalJobDuration,
                dependenciesDownloadedCount);
        return buildLogStatisticsEntryRepository.save(buildLogStatisticsEntry);
    }
}
