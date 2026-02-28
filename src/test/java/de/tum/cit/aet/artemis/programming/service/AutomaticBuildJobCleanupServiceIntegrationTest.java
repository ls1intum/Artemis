package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.test_repository.BuildJobTestRepository;

// Note: The retention-period property is configured in AbstractSpringIntegrationIndependentTest to avoid creating a separate Spring context
class AutomaticBuildJobCleanupServiceIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    @Autowired
    private AutomaticBuildJobCleanupService cleanupService;

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    @BeforeEach
    void setUp() {
        buildJobRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        buildJobRepository.deleteAll();
    }

    @Test
    void cleanupDeletesOutdatedFinishedJobsOnly() {
        var now = ZonedDateTime.now();

        buildJobRepository.save(createBuildJob("old-finished", now.minusDays(40), BuildStatus.SUCCESSFUL));
        buildJobRepository.save(createBuildJob("recent-finished", now.minusDays(5), BuildStatus.SUCCESSFUL));
        buildJobRepository.save(createBuildJob("old-queued", now.minusDays(40), BuildStatus.QUEUED));

        cleanupService.cleanup();

        Set<String> remainingJobIds = buildJobRepository.findAll().stream().map(BuildJob::getBuildJobId).collect(Collectors.toSet());
        assertThat(remainingJobIds).containsExactly("recent-finished");
    }

    private BuildJob createBuildJob(String buildJobId, ZonedDateTime submissionDate, BuildStatus status) {
        var buildJob = new BuildJob();
        buildJob.setBuildJobId(buildJobId);
        buildJob.setBuildSubmissionDate(submissionDate);
        buildJob.setBuildStatus(status);
        return buildJob;
    }
}
