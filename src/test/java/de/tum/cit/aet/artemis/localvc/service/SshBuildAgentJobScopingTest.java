package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.localci.service.BuildJobCloneTokenService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Tests which repositories a build agent authenticated by ssh key may read.
 * <p>
 * The key already establishes which agent is connected, so no token is involved here; the missing constraint was only
 * which repositories that agent legitimately needs at this moment. The processing list answers that, which is why this
 * needs no expiry: a job leaves the list when it finishes, is cancelled, or hits the build timeout.
 * <p>
 * Before this, an agent authenticated by key could read every repository in the installation.
 */
class SshBuildAgentJobScopingTest {

    private static final String BASE_URI = "http://localhost:8000";

    private static final String AGENT_NAME = "artemis-build-agent-1";

    private SshGitLocationResolverService sshGitLocationResolverService;

    private DistributedDataAccessService distributedDataAccessService;

    @BeforeEach
    void setUp() {
        distributedDataAccessService = mock(DistributedDataAccessService.class);
        sshGitLocationResolverService = new SshGitLocationResolverService(null, null, Optional.of(distributedDataAccessService), Optional.of(new BuildJobCloneTokenService()));
    }

    private static LocalVCRepositoryUri uriOf(String path) {
        return new LocalVCRepositoryUri(URI.create(BASE_URI), Path.of(path));
    }

    private static BuildJobQueueItem buildJobFor(String assignmentUri, String testUri) {
        var repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, assignmentUri, testUri, null, new String[0], new String[0]);
        var jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, null, null, null, null);
        return new BuildJobQueueItem("job-1", "name", new BuildAgentDTO(AGENT_NAME, "address", "display"), 1L, 2L, 3L, 0, 1, null, repositoryInfo, jobTimingInfo, buildConfig, null,
                null);
    }

    @SuppressWarnings("unchecked")
    private Optional<BuildJobQueueItem> buildJobFound(String agentName, LocalVCRepositoryUri uri) {
        Optional<BuildJobQueueItem> result = (Optional<BuildJobQueueItem>) ReflectionTestUtils.invokeMethod(sshGitLocationResolverService, "findBuildJobForRepository", agentName,
                uri);
        return result == null ? Optional.empty() : result;
    }

    private boolean mayRead(String agentName, LocalVCRepositoryUri uri) {
        return buildJobFound(agentName, uri).isPresent();
    }

    @Test
    void shouldAllowTheRepositoriesOfARunningJob() {
        LocalVCRepositoryUri assignment = uriOf("/git/TESTEXERCISE/testexercise-student1.git");
        LocalVCRepositoryUri tests = uriOf("/git/TESTEXERCISE/testexercise-tests.git");
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJobFor(assignment.toString(), tests.toString())));

        assertThat(mayRead(AGENT_NAME, assignment)).isTrue();
        assertThat(mayRead(AGENT_NAME, tests)).as("an agent needs the test repository of its job as well").isTrue();
    }

    @Test
    void shouldRefuseARepositoryOutsideTheAgentsJobs() {
        LocalVCRepositoryUri assignment = uriOf("/git/TESTEXERCISE/testexercise-student1.git");
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJobFor(assignment.toString(), null)));

        assertThat(mayRead(AGENT_NAME, uriOf("/git/TESTEXERCISE/testexercise-student2.git"))).as("a key must no longer open every repository in the installation").isFalse();
    }

    @Test
    void shouldRefuseWhenTheAgentIsRunningNothing() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of());

        assertThat(mayRead(AGENT_NAME, uriOf("/git/TESTEXERCISE/testexercise-student1.git"))).isFalse();
    }

    /**
     * The scope belongs to the agent that holds the job, so one agent's running job must not let another agent read.
     */
    @Test
    void shouldRefuseAnotherAgentsJob() {
        LocalVCRepositoryUri assignment = uriOf("/git/TESTEXERCISE/testexercise-student1.git");
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJobFor(assignment.toString(), null)));
        when(distributedDataAccessService.getProcessingJobsForAgentByName("artemis-build-agent-2")).thenReturn(List.of());

        assertThat(mayRead("artemis-build-agent-2", assignment)).isFalse();
    }

    /**
     * A session established before the agent name was recorded carries none, and must be refused rather than treated as
     * unrestricted.
     */
    @Test
    void shouldRefuseASessionWithoutAnAgentName() {
        assertThat(mayRead(null, uriOf("/git/TESTEXERCISE/testexercise-student1.git"))).isFalse();
    }

    @Test
    void shouldRefuseWhenTheNodeHasNoLocalCi() {
        sshGitLocationResolverService = new SshGitLocationResolverService(null, null, Optional.empty(), Optional.empty());

        assertThat(mayRead(AGENT_NAME, uriOf("/git/TESTEXERCISE/testexercise-student1.git"))).isFalse();
    }

    /**
     * The scope check has to yield the matching job, not merely a yes. The ssh read is audited like the https one, and
     * an access log entry that cannot name the build job says no more than "some agent read this" - which is what the
     * entry exists to improve on.
     */
    @Test
    void shouldIdentifyWhichBuildJobAuthorizesTheReadSoItCanBeAudited() {
        LocalVCRepositoryUri assignment = uriOf("/git/TESTEXERCISE/testexercise-student1.git");
        LocalVCRepositoryUri tests = uriOf("/git/TESTEXERCISE/testexercise-tests.git");
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJobFor(assignment.toString(), tests.toString())));

        assertThat(buildJobFound(AGENT_NAME, assignment)).get().extracting(BuildJobQueueItem::id).isEqualTo("job-1");
        assertThat(buildJobFound(AGENT_NAME, tests)).get().extracting(BuildJobQueueItem::id).isEqualTo("job-1");
        assertThat(buildJobFound(AGENT_NAME, uriOf("/git/TESTEXERCISE/testexercise-student2.git"))).isEmpty();
    }
}
