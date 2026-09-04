package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;

/**
 * Pins {@link InteractiveSandboxService#resetSession} against a real Docker daemon and the production sandbox image.
 * <p>
 * The reset is the guarantee the differential verifier rests on: each of its two pristine builds must start from exactly the captured candidate, so a reset that stopped wiping
 * the writable tmpfs or stopped killing processes the agent left behind would let the verifier approve a tree different from the one persistence receives. Every collaborator is
 * real here (Docker, the image, the hardened host config) because a mocked Docker client can only pin the call shape, not the wipe.
 */
@EnabledIf("dockerGateEnabled")
class InteractiveSandboxResetDockerIntegrationTest {

    private static final String SANDBOX_IMAGE = System.getenv().getOrDefault("HYPERION_TEST_JAVA_BUILD_IMAGE", "ls1tum/artemis-maven-template:java17-25");

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    /** Passed as {@code $0} to the stray shell so it is greppable in {@code ps} output without the probe itself matching. */
    private static final String STRAY_PROCESS_MARKER = "hyperion-stray-process";

    /** Paths the session may write to: {@code /workspace} holds the candidate, and the build writes reports and scratch files to the other two. */
    private static final List<String> WRITABLE_PATHS = List.of("/workspace", "/tmp", "/opt/hyperion");

    private DockerClient dockerClient;

    private InteractiveSandboxService service;

    private String sessionId;

    /**
     * Locally an absent Docker daemon skips this test as a developer convenience; in CI Docker is always present (Testcontainers already requires it), so a broken daemon must
     * fail loudly rather than turn the run green by skipping.
     *
     * @return whether the Docker-backed reset test should run
     */
    static boolean dockerGateEnabled() {
        return System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @BeforeEach
    void createRealSandboxSession() {
        dockerClient = DockerClientFactory.instance().client();
        new RemoteDockerImage(DockerImageName.parse(SANDBOX_IMAGE)).get();
        String imageId;
        try (var inspectImage = dockerClient.inspectImageCmd(SANDBOX_IMAGE)) {
            imageId = inspectImage.exec().getId();
        }

        BuildAgentConfiguration buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        doReturn(dockerClient).when(buildAgentConfiguration).getDockerClient();
        // Mirrors the production defaults from application.yml (--cpus 2, --memory 2g, --memory-swap 2g, --pids-limit 1000).
        doReturn(HostConfig.newHostConfig().withCpuQuota(200_000L).withCpuPeriod(100_000L).withMemory(2L * 1024 * 1024 * 1024).withMemorySwap(2L * 1024 * 1024 * 1024)
                .withPidsLimit(1000L)).when(buildAgentConfiguration).hostConfig();
        BuildAgentDockerService buildAgentDockerService = mock(BuildAgentDockerService.class);
        when(buildAgentDockerService.ensureDockerImageAvailable(SANDBOX_IMAGE)).thenReturn(imageId);

        service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);
        sessionId = service.createSession(new SandboxSessionSpecDTO(SANDBOX_IMAGE, new DockerRunConfig(List.of(), "none", 0, 0, 0)));
    }

    @AfterEach
    void destroyRealSandboxSession() {
        if (service != null && sessionId != null) {
            service.destroySession(sessionId);
        }
    }

    @Test
    void resetSessionWipesEveryWritablePathAndKillsProcessesTheAgentLeftBehind() {
        seedCandidateFilesAndStrayProcess();

        service.resetSession(sessionId);

        assertThat(runInSession("find", "/workspace", "/tmp", "/opt/hyperion", "-mindepth", "1").stdout()).as("every writable path is empty after the reset").isBlank();
        assertThat(runInSession("ps", "-eo", "args").stdout()).as("the detached process the agent left behind is gone after the reset").doesNotContain(STRAY_PROCESS_MARKER);
        SandboxExecResultDTO writeBack = runInSession("sh", "-c", "echo reusable > /workspace/probe && cat /workspace/probe");
        assertThat(writeBack.isSuccess()).as("the session is still usable after the reset").isTrue();
        assertThat(writeBack.stdout()).contains("reusable");
    }

    /** The verifier resets twice per pass, so a reset that only works the first time would surface here as the second seed surviving. */
    @Test
    void resetSessionKeepsWipingWhenRepeatedWithinTheSameSession() {
        seedCandidateFilesAndStrayProcess();
        service.resetSession(sessionId);
        seedCandidateFilesAndStrayProcess();

        service.resetSession(sessionId);

        assertThat(runInSession("find", "/workspace", "-mindepth", "1").stdout()).as("the second reset wipes the re-seeded candidate too").isBlank();
        assertThat(runInSession("ps", "-eo", "args").stdout()).as("the second reset kills the re-spawned detached process too").doesNotContain(STRAY_PROCESS_MARKER);
    }

    /**
     * PID 1 must handle SIGTERM, so the container stops on signal instead of waiting out the stop grace and being SIGKILLed. Without Docker's init forwarding the signal, the
     * shell that keeps the container warm ignores it and every reset costs the full grace — two of them per verification pass, on the critical path of every generation.
     */
    @Test
    void resetSessionStopsTheContainerOnSignalRatherThanWaitingOutTheStopGrace() {
        // Warm up so the measurement covers the stop and start alone, not a first-restart cost of the freshly created container.
        service.resetSession(sessionId);
        seedCandidateFilesAndStrayProcess();

        long startedAt = System.nanoTime();
        service.resetSession(sessionId);
        Duration reset = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(reset).as("the reset stops the container on SIGTERM instead of burning the %ds stop grace", InteractiveSandboxService.SESSION_RESET_STOP_GRACE_SECONDS)
                .isLessThan(Duration.ofSeconds(InteractiveSandboxService.SESSION_RESET_STOP_GRACE_SECONDS));
    }

    /** Leaves the session in the state a finished agent turn leaves it in: candidate files across every writable path, plus a detached process that ignores SIGTERM. */
    private void seedCandidateFilesAndStrayProcess() {
        SandboxExecResultDTO seed = runInSession("sh", "-c",
                "mkdir -p /workspace/solution/src /opt/hyperion/reports && echo candidate > /workspace/solution/src/Solution.java && echo scratch > /tmp/build-scratch "
                        + "&& echo report > /opt/hyperion/reports/results.xml");
        assertThat(seed.isSuccess()).as("the sandbox accepts the seeded candidate").isTrue();
        runInSession("sh", "-c", "nohup sh -c 'trap \"\" TERM HUP; while :; do sleep 1; done' " + STRAY_PROCESS_MARKER + " >/dev/null 2>&1 &");
        // The post-reset assertions are only meaningful once the stray is actually running.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(runInSession("ps", "-eo", "args").stdout()).contains(STRAY_PROCESS_MARKER));
        for (String path : WRITABLE_PATHS) {
            assertThat(runInSession("find", path, "-mindepth", "1").stdout()).as("%s holds seeded state before the reset", path).isNotBlank();
        }
    }

    private SandboxExecResultDTO runInSession(String... command) {
        return service.exec(sessionId, COMMAND_TIMEOUT, command);
    }
}
