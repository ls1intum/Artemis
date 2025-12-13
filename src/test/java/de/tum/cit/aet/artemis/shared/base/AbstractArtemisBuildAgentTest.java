package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
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
import java.util.UUID;

import org.eclipse.jgit.lib.ObjectId;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.StartContainerCmd;

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
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("AbstractArtemisBuildAgentTest")
// NOTE: Do not use SPRING_PROFILE_TEST as it will cause the test to fail due to missing beans. This is because SPRING_PROFILE_TEST will cause some
// test services, which dependencies are not provided since we are not running the full application context, to be created.
@ActiveProfiles({ PROFILE_TEST_BUILDAGENT, PROFILE_BUILDAGENT, "local" })
@TestPropertySource(properties = { "artemis.continuous-integration.specify-concurrent-builds=true", "artemis.continuous-integration.concurrent-build-size=2",
        "artemis.continuous-integration.pause-grace-period-seconds=2", "artemis.continuous-integration.pause-after-consecutive-failed-jobs=5" })
public abstract class AbstractArtemisBuildAgentTest {

    @Autowired
    protected DockerClientTestService dockerClientTestService;

    @Autowired
    protected DistributedDataAccessService distributedDataAccessService;

    @MockitoSpyBean
    protected BuildAgentConfiguration buildAgentConfiguration;

    @MockitoSpyBean
    protected BuildJobGitService buildJobGitService;

    protected DockerClient dockerClient;

    private static DockerClient dockerClientMock;

    private static final Path TEST_RESULTS_PATH = Path.of("src", "test", "resources", "test-data", "test-results");

    private static final Path GRADLE_TEST_RESULTS_PATH = TEST_RESULTS_PATH.resolve("java-gradle");

    protected static final Path PARTLY_SUCCESSFUL_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("partly-successful");

    protected static final Path ALL_SUCCEED_TEST_RESULTS_PATH = GRADLE_TEST_RESULTS_PATH.resolve("all-succeed");

    @BeforeAll
    protected static void mockDockerClient() throws InterruptedException {
        dockerClientMock = DockerClientTestService.mockDockerClient();
    }

    @BeforeEach
    protected void mockServices() throws IOException {
        mockBuildJobGitService();
        dockerClientTestService.mockTestResults(dockerClientMock, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);

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
            // Exception is ignored as we are mocking the cloneRepository method
        }

        doNothing().when(buildJobGitService).checkoutRepositoryAtCommit(any(), any());

        doReturn(repository).when(buildJobGitService).getExistingCheckedOutRepositoryByLocalPath(any(), any(), any());
        try {
            doNothing().when(buildJobGitService).deleteLocalRepository(any());
        }
        catch (Exception ignored) {
            // Exception is ignored as we are mocking the deleteLocalRepository method
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

        String randomString = UUID.randomUUID().toString();
        return new BuildJobQueueItem("dummy-id-" + randomString, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    protected static BuildJobQueueItem createBaseBuildJobQueueItemForTriggerWithImage(String image) {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        BuildConfig buildConfig = new BuildConfig("dummy-build-script", image, "dummy-commit-hash", "assignment-commit-hash", "test-commit-hash", "main", ProgrammingLanguage.JAVA,
                ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 15, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", null);

        String randomString = UUID.randomUUID().toString();
        return new BuildJobQueueItem("dummy-id-" + randomString, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    protected static BuildJobQueueItem createBuildJobQueueItemWithNoCommitHash() {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        BuildConfig buildConfig = new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", null, null, "main", ProgrammingLanguage.JAVA,
                ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 15, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", null);

        String randomString = UUID.randomUUID().toString();
        return new BuildJobQueueItem("dummy-id-" + randomString, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    protected static BuildJobQueueItem createBuildJobQueueItemForTimeout() {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        BuildConfig buildConfig = new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", "assignment-commit-hash", "test-commit-hash", "main",
                ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 1, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", null);

        String randomString = UUID.randomUUID().toString();
        return new BuildJobQueueItem("dummy-id-" + randomString, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    protected static BuildJobQueueItem createBuildJobQueueItemWithNetworkDisabled() {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});

        final var buildConfig = getBuildConfig();

        String randomString = UUID.randomUUID().toString();
        return new BuildJobQueueItem("dummy-id-" + randomString, "dummy-name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    private static @NonNull BuildConfig getBuildConfig() {
        DockerRunConfig dockerRunConfig = new DockerRunConfig(List.of("dummy-env", "dummy-env-value"), "none", 0, 0, 0);
        return new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", "assignment-commit-hash", "test-commit-hash", "main", ProgrammingLanguage.JAVA,
                ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 1, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", dockerRunConfig);
    }
}
