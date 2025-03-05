package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.BuildJobGitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.icl.DockerClientTestService;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("AbstractArtemisBuildAgentTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ PROFILE_TEST_BUILDAGENT, PROFILE_BUILDAGENT })

// Note: the server.port property must correspond to the port used in the artemis.version-control.url property.
@TestPropertySource(properties = { "server.port=49152", "artemis.version-control.url=http://localhost:49152", "artemis.user-management.use-external=false",
        "artemis.version-control.local-vcs-repo-path=${java.io.tmpdir}", "artemis.build-logs-path=${java.io.tmpdir}/build-logs",
        "artemis.continuous-integration.specify-concurrent-builds=true", "artemis.continuous-integration.concurrent-build-size=2",
        "artemis.continuous-integration.asynchronous=true", "artemis.continuous-integration.build.images.java.default=dummy-docker-image",
        "artemis.continuous-integration.image-cleanup.enabled=true", "artemis.continuous-integration.image-cleanup.disk-space-threshold-mb=1000000000",
        "spring.liquibase.enabled=false", "artemis.version-control.ssh-private-key-folder-path=${java.io.tmpdir}", "artemis.version-control.build-agent-use-ssh=false",
        "artemis.version-control.ssh-template-clone-url=ssh://git@locaFlhost:7921/", "artemis.continuous-integration.pause-grace-period-seconds=2" })
public abstract class AbstractArtemisBuildAgentTest {

    @Autowired
    protected DockerClientTestService dockerClientTestService;

    @Autowired
    @Qualifier("hazelcastInstance")
    protected HazelcastInstance hazelcastInstance;

    @MockitoSpyBean
    protected BuildAgentConfiguration buildAgentConfiguration;

    @MockitoSpyBean
    protected BuildJobGitService buildJobGitService;

    protected DockerClient dockerClient;

    private static DockerClient dockerClientMock;

    private static final Path TEST_RESULTS_PATH = Path.of("src", "test", "resources", "test-data", "test-results");

    private static final Path GRADLE_TEST_RESULTS_PATH = TEST_RESULTS_PATH.resolve("java-gradle");

    protected static final Path PARTLY_SUCCESSFUL_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("partly-successful");

    @BeforeAll
    protected static void mockDockerClient() throws InterruptedException {
        dockerClientMock = DockerClientTestService.mockDockerClient();
    }

    @BeforeEach
    protected void mockServices() throws IOException {
        mockBuildJobGitService();
        dockerClientTestService.mockTestResults(dockerClientMock, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);

        // Mock the startContainerCmd to sleep for 100ms. This is necessary to appropriately test the build agent's behavior when a build job is started.
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClientMock.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        doAnswer(invocation -> {
            Thread.sleep(100);
            return null;
        }).when(startContainerCmd).exec();

        when(buildAgentConfiguration.getDockerClient()).thenReturn(dockerClientMock);
        dockerClient = dockerClientMock;
    }

    protected void mockBuildJobGitService() {
        var objectId = mock(ObjectId.class);
        doReturn("dummy-commit-hash").when(objectId).getName();
        doReturn(objectId).when(buildJobGitService).getLastCommitHash(any());

        var repository = mock(Repository.class);
        doNothing().when(repository).closeBeforeDelete();
        doReturn(Path.of("src", "test", "resources", "templates", "java", "java.txt")).when(repository).getLocalPath();
        try {
            doReturn(repository).when(buildJobGitService).cloneRepository(any(), any());
        }
        catch (Exception ignored) {
        }

        doNothing().when(buildJobGitService).checkoutRepositoryAtCommit(any(), any());

        doReturn(repository).when(buildJobGitService).getExistingCheckedOutRepositoryByLocalPath(any(), any(), any());
        try {
            doNothing().when(buildJobGitService).deleteLocalRepository(any());
        }
        catch (Exception ignored) {
        }
    }

    protected static BuildJobQueueItem createBaseBuildJobQueueItemForTrigger() {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        BuildConfig buildConfig = new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", "assignment-commit-hash", "test-commit-hash", "main",
                ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 15, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", null);

        long millisNow = System.currentTimeMillis();
        return new BuildJobQueueItem("dummy-id-" + millisNow, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    protected static BuildJobQueueItem createBuildJobQueueItemWithNoCommitHash() {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        BuildConfig buildConfig = new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", null, null, "main", ProgrammingLanguage.JAVA,
                ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 15, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", null);

        long millisNow = System.currentTimeMillis();
        return new BuildJobQueueItem("dummy-id-" + millisNow, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    protected static BuildJobQueueItem createBuildJobQueueItemForTimeout() {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        BuildConfig buildConfig = new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", "assignment-commit-hash", "test-commit-hash", "main",
                ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 1, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", null);

        long millisNow = System.currentTimeMillis();
        return new BuildJobQueueItem("dummy-id-" + millisNow, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    protected static BuildJobQueueItem createBuildJobQueueItemWithNetworkDisabled() {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        DockerRunConfig dockerRunConfig = new DockerRunConfig(true, List.of("dummy-env", "dummy-env-value"));
        BuildConfig buildConfig = new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", "assignment-commit-hash", "test-commit-hash", "main",
                ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 1, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", dockerRunConfig);

        long millisNow = System.currentTimeMillis();
        return new BuildJobQueueItem("dummy-id-" + millisNow, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }
}
