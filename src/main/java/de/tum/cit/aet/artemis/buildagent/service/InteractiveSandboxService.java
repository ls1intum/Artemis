package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

/**
 * Build-agent-side {@link InteractiveSandbox}: a warm, resource-limited Docker container an exercise-generation session drives through many cheap operations.
 * <p>
 * Reuses the build-agent Docker client and {@link BuildAgentConfiguration#hostConfig()}, so isolation matches a CI build container (same CPU/memory/PID limits; runs untrusted
 * code but holds no credentials or database access); unlike the regular build path it captures and returns each command's stdout/stderr as agent observations. Containers carry
 * the {@value #SANDBOX_CONTAINER_PREFIX} prefix so {@link InteractiveSandboxReaperService} never reaps a live session as if it were a CI build container.
 */
@Lazy
@Service
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxService implements InteractiveSandbox {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxService.class);

    /** Name prefix for sandbox containers, distinct from the CI {@code local-ci-} prefix so each reaper matches only its own containers. */
    public static final String SANDBOX_CONTAINER_PREFIX = "hyperion-gen-";

    private static final String WORKING_DIRECTORY = "/workspace";

    private static final String STOP_SENTINEL = WORKING_DIRECTORY + "/.stop_sandbox";

    /** Cap on captured stdout/stderr returned to the caller; longer output is truncated to the tail (where compiler/test failures appear) to bound the agent's context. */
    private static final int MAX_CAPTURED_OUTPUT_CHARS = 50_000;

    private final BuildAgentConfiguration buildAgentConfiguration;

    public InteractiveSandboxService(BuildAgentConfiguration buildAgentConfiguration) {
        this.buildAgentConfiguration = buildAgentConfiguration;
    }

    @Override
    public String createSession(SandboxSessionSpec spec) {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            throw new LocalCIException("Docker is not available. Cannot create interactive sandbox session.");
        }
        String containerName = SANDBOX_CONTAINER_PREFIX + UUID.randomUUID();
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        HostConfig hostConfig = buildAgentConfiguration.hostConfig(); // fresh per call, safe to mutate
        // No auto-remove: the container is torn down explicitly by destroySession; auto-remove would race that and could delete it under an in-flight exec.
        hostConfig.withAutoRemove(false);
        if (spec.runConfig() != null && spec.runConfig().network() != null && !spec.runConfig().network().isBlank()) {
            hostConfig.withNetworkMode(spec.runConfig().network());
        }
        try (final var createCommand = dockerClient.createContainerCmd(spec.image())) {
            // Main process is an idle wait-loop keeping the container warm until the stop sentinel appears; the session is driven entirely by separate `docker exec` calls.
            var response = createCommand.withName(containerName).withHostConfig(hostConfig).withEntrypoint()
                    .withCmd("sh", "-c", "mkdir -p " + WORKING_DIRECTORY + "; while [ ! -f " + STOP_SENTINEL + " ]; do sleep 0.5; done").exec();
            String containerId = response.getId();
            try (final var startCommand = dockerClient.startContainerCmd(containerId)) {
                startCommand.exec();
            }
            log.info("Started interactive sandbox session {} (container {})", containerName, containerId);
            return containerId;
        }
    }

    @Override
    public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var execCreateCommand = dockerClient.execCreateCmd(sessionId).withAttachStdout(true).withAttachStderr(true).withCmd(command)) {
            ExecCreateCmdResponse execCreateResponse = execCreateCommand.exec();
            String execId = execCreateResponse.getId();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            // withDetach(false) keeps the stream open until the command finishes, so onComplete fires only after the command completed.
            dockerClient.execStartCmd(execId).withDetach(false).exec(new ResultCallback.Adapter<>() {

                @Override
                public void onNext(Frame item) {
                    String payload = new String(item.getPayload(), StandardCharsets.UTF_8);
                    if (item.getStreamType() == StreamType.STDERR) {
                        appendBounded(stderr, payload);
                    }
                    else {
                        appendBounded(stdout, payload);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Error while executing sandbox command: {} in session {}", String.join(" ", command), sessionId, throwable);
                    errorRef.set(throwable);
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });

            boolean completed;
            try {
                completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LocalCIException("Interrupted while executing sandbox command: " + String.join(" ", command), e);
            }

            if (!completed) {
                // Budget exceeded: return partial output with the timeout flag so the agent can react rather than block.
                return new SandboxExecResult(-1, truncateTail(stdout.toString()), truncateTail(stderr.toString()), true);
            }

            Throwable execError = errorRef.get();
            if (execError != null) {
                throw new LocalCIException("Sandbox command failed: " + String.join(" ", command), execError);
            }

            int exitCode;
            try (final var inspectCommand = dockerClient.inspectExecCmd(execId)) {
                InspectExecResponse inspectResponse = inspectCommand.exec();
                Long exitCodeLong = inspectResponse.getExitCodeLong();
                exitCode = exitCodeLong != null ? exitCodeLong.intValue() : -1;
            }
            return new SandboxExecResult(exitCode, truncateTail(stdout.toString()), truncateTail(stderr.toString()), false);
        }
    }

    @Override
    public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var copyCommand = dockerClient.copyArchiveToContainerCmd(sessionId).withTarInputStream(tarArchive).withRemotePath(destinationPath)) {
            copyCommand.exec();
        }
    }

    @Override
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var copyCommand = dockerClient.copyArchiveFromContainerCmd(sessionId, path)) {
            InputStream archiveStream = copyCommand.exec();
            try {
                return new TarArchiveInputStream(archiveStream);
            }
            catch (RuntimeException e) {
                closeQuietly(archiveStream); // do not leak the Docker response stream if the wrapper cannot be constructed
                throw e;
            }
        }
    }

    @Override
    public void destroySession(String sessionId) {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            return;
        }
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        // Signal the idle-loop entrypoint to exit gracefully, then force-remove in case the container is unresponsive.
        try {
            exec(sessionId, Duration.ofSeconds(5), "touch", STOP_SENTINEL);
        }
        catch (RuntimeException e) {
            log.debug("Could not write stop sentinel for sandbox session {} (will force-remove): {}", sessionId, e.getMessage());
        }
        try (final var removeCommand = dockerClient.removeContainerCmd(sessionId).withForce(true)) {
            removeCommand.exec();
        }
        catch (NotFoundException e) {
            // Already gone.
        }
        catch (RuntimeException e) {
            log.warn("Failed to remove interactive sandbox session {}: {}", sessionId, e.getMessage());
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        }
        catch (IOException e) {
            log.debug("Failed to close sandbox archive stream: {}", e.getMessage());
        }
    }

    private static void appendBounded(StringBuilder builder, String payload) {
        // Cap memory at 2x the limit; only the tail is kept at the end anyway.
        if (builder.length() < MAX_CAPTURED_OUTPUT_CHARS * 2) {
            builder.append(payload);
        }
    }

    private static String truncateTail(String value) {
        if (value.length() <= MAX_CAPTURED_OUTPUT_CHARS) {
            return value;
        }
        String tail = value.substring(value.length() - MAX_CAPTURED_OUTPUT_CHARS);
        return "[... output truncated, showing last " + MAX_CAPTURED_OUTPUT_CHARS + " characters ...]\n" + tail;
    }
}
