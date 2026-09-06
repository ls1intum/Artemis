package de.tum.cit.aet.artemis.localci.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Unit tests for turning a finished build job into the row that is kept in the build history.
 * <p>
 * The conversion copies twenty-one fields out of four nested records, and several of them have the same type: three
 * timestamps, two repository types, and a handful of ids and hashes. Two of those swapped still compiles, still stores,
 * and still renders - the build history simply shows a build that started after it finished, or attributes a push to the
 * wrong repository. Giving every field a value that could only have come from one place is what makes such a swap
 * visible.
 */
class BuildJobTest {

    private static final ZonedDateTime SUBMITTED_AT = ZonedDateTime.parse("2026-03-01T10:00:00Z");

    private static final ZonedDateTime STARTED_AT = ZonedDateTime.parse("2026-03-01T10:05:00Z");

    private static final ZonedDateTime COMPLETED_AT = ZonedDateTime.parse("2026-03-01T10:07:30Z");

    private static BuildJobQueueItem finishedQueueItem() {
        return new BuildJobQueueItem("job-id", "job-name", new BuildAgentDTO("agent-name", "10.0.0.5:5701", "agent-display-name"), 10L, 1L, 3L, 2, 4, BuildStatus.BUILDING,
                new RepositoryInfo("abc-student", RepositoryType.USER, RepositoryType.TESTS, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(SUBMITTED_AT, STARTED_AT, COMPLETED_AT, null, 150), new BuildConfig("script", "ghcr.io/example/image:1", "commit-to-build", "assignment-commit",
                        "test-commit", "main", null, null, false, false, List.of(), 0, null, null, null, null),
                null, "clone-token");
    }

    @Test
    void aFinishedBuildJobKeepsEveryFieldOfTheQueueItemItCameFrom() {
        Result result = new Result();
        result.setId(99L);

        BuildJob buildJob = new BuildJob(finishedQueueItem(), BuildStatus.SUCCESSFUL, result);

        assertThat(buildJob.getBuildJobId()).isEqualTo("job-id");
        assertThat(buildJob.getName()).isEqualTo("job-name");
        assertThat(buildJob.getExerciseId()).isEqualTo(3L);
        assertThat(buildJob.getCourseId()).isEqualTo(1L);
        assertThat(buildJob.getParticipationId()).isEqualTo(10L);
        assertThat(buildJob.getResult()).isSameAs(result);
        // The address identifies the agent that ran the build; the agent's display name would not let anyone find it again.
        assertThat(buildJob.getBuildAgentAddress()).isEqualTo("10.0.0.5:5701");
        assertThat(buildJob.getBuildSubmissionDate()).isEqualTo(SUBMITTED_AT);
        assertThat(buildJob.getBuildStartDate()).isEqualTo(STARTED_AT);
        assertThat(buildJob.getBuildCompletionDate()).isEqualTo(COMPLETED_AT);
        assertThat(buildJob.getRepositoryType()).isEqualTo(RepositoryType.USER);
        assertThat(buildJob.getRepositoryName()).isEqualTo("abc-student");
        // The build history records the commit that was built, which is not the same as the assignment commit of the job.
        assertThat(buildJob.getCommitHash()).isEqualTo("commit-to-build");
        assertThat(buildJob.getRetryCount()).isEqualTo(2);
        assertThat(buildJob.getPriority()).isEqualTo(4);
        assertThat(buildJob.getTriggeredByPushTo()).isEqualTo(RepositoryType.TESTS);
        // The status is the one the build ended with, not the one it carried while it was still queued.
        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(buildJob.getDockerImage()).isEqualTo("ghcr.io/example/image:1");
    }

    @Test
    void aBuildJobThatProducedNoResultIsStillRecorded() {
        // A build that failed before it could produce a result still belongs in the history, otherwise the failure is invisible.
        BuildJob buildJob = new BuildJob(finishedQueueItem(), BuildStatus.FAILED, null);

        assertThat(buildJob.getResult()).isNull();
        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.FAILED);
        assertThat(buildJob.getBuildJobId()).isEqualTo("job-id");
    }
}
