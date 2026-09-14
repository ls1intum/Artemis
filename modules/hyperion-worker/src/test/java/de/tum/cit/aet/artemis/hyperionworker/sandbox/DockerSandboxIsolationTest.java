package de.tum.cit.aet.artemis.hyperionworker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;

import de.tum.cit.aet.artemis.hyperion.runtime.agent.SandboxUnavailableException;
import de.tum.cit.aet.artemis.hyperionworker.config.DockerConfiguration;
import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;

/** Uses an operator/CI-selected immutable test image; no image pull or shared-server mutation inside tests. */
@EnabledIfEnvironmentVariable(named = "HYPERION_TEST_IMAGE", matches = "sha256:[a-f0-9]{64}")
class DockerSandboxIsolationTest {

    private DockerClient docker;

    private DockerSandbox sandbox;

    @BeforeEach
    void setup() {
        docker = new DockerConfiguration().dockerClient();
        sandbox = sandbox("test-" + UUID.randomUUID());
    }

    @AfterEach
    void cleanup() throws IOException {
        sandbox.destroyActiveSessions();
        docker.close();
    }

    @Test
    void runsUnprivilegedWithoutNetworkOrHostMountsAndResetsAllWritableState() {
        String session = sandbox.createSession();
        var container = docker.inspectContainerCmd(session).exec();
        var host = container.getHostConfig();
        assertThat(container.getConfig().getUser()).isEqualTo("1000:1000");
        assertThat(host.getNetworkMode()).isEqualTo("none");
        assertThat(host.getReadonlyRootfs()).isTrue();
        assertThat(host.getCapDrop()).contains(Capability.ALL);
        assertThat(host.getSecurityOpts()).contains("no-new-privileges");
        assertThat(host.getBinds()).isNullOrEmpty();
        assertThat(host.getPidsLimit()).isEqualTo(32);
        assertThat(host.getMemory()).isEqualTo(host.getMemorySwap()).isEqualTo(128L * 1024 * 1024);
        assertThat(sandbox.exec(session, Duration.ofSeconds(5), "sh", "-c", "printf data > /workspace/probe; printf data > /tmp/probe").isSuccess()).isTrue();
        sandbox.resetSession(session);
        assertThat(sandbox.exec(session, Duration.ofSeconds(5), "sh", "-c", "test ! -e /workspace/probe && test ! -e /tmp/probe").isSuccess()).isTrue();
    }

    @Test
    void copyPreservesBinaryContentAndExecutableMode() throws IOException {
        String session = sandbox.createSession();
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        byte[] content = { 0, -1, 42 };
        try (var tar = new TarArchiveOutputStream(archive)) {
            TarArchiveEntry entry = new TarArchiveEntry("wrapper");
            entry.setSize(content.length);
            entry.setMode(0755);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
        }
        sandbox.copyIn(session, "/workspace", new ByteArrayInputStream(archive.toByteArray()));
        assertThat(sandbox.exec(session, Duration.ofSeconds(5), "sh", "-c", "test -x /workspace/wrapper").isSuccess()).isTrue();
        try (var tar = sandbox.copyOut(session, "/workspace/wrapper")) {
            assertThat(tar.getNextEntry().getName()).isEqualTo("wrapper");
            assertThat(tar.readAllBytes()).containsExactly(content);
        }
    }

    @Test
    void missingReportDoesNotDestroyWorkspace() {
        String session = sandbox.createSession();
        assertThat(sandbox.exec(session, Duration.ofSeconds(5), "sh", "-c", "printf retained > /workspace/probe").isSuccess()).isTrue();

        assertThatExceptionOfType(SandboxUnavailableException.class).isThrownBy(() -> sandbox.copyOut(session, "/workspace/missing-report"))
                .withMessageContaining("Could not archive files");

        assertThat(sandbox.exec(session, Duration.ofSeconds(5), "cat", "/workspace/probe").stdout()).isEqualTo("retained");
    }

    @Test
    void timeoutRemovesTheSessionRatherThanReplayingAnUncertainCommand() {
        String session = sandbox.createSession();
        var result = sandbox.exec(session, Duration.ofMillis(100), "sh", "-c", "sleep 30");
        assertThat(result.timedOut()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> docker.inspectContainerCmd(session).exec());
        sandbox.destroySession(session);
    }

    @Test
    void executionCleanupLeavesOtherSlotsRunning() {
        DockerSandbox first = sandbox.forExecution(UUID.randomUUID());
        DockerSandbox second = sandbox.forExecution(UUID.randomUUID());
        String firstSession = first.createSession();
        String secondSession = second.createSession();
        try {
            first.destroyActiveSessions();
            assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> docker.inspectContainerCmd(firstSession).exec());
            assertThat(second.exec(secondSession, Duration.ofSeconds(5), "true").isSuccess()).isTrue();
        }
        finally {
            second.destroyActiveSessions();
        }
    }

    @Test
    void startupCleanupDoesNotTouchAnotherWorker() {
        String own = sandbox.createSession();
        DockerSandbox other = sandbox("other-" + UUID.randomUUID());
        String theirs = other.createSession();
        try {
            assertThat(sandbox.removePreviousSessions()).isEqualTo(1);
            assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> docker.inspectContainerCmd(own).exec());
            assertThat(other.exec(theirs, Duration.ofSeconds(5), "true").isSuccess()).isTrue();
        }
        finally {
            other.destroyActiveSessions();
        }
    }

    @Test
    void finalCleanupFindsAnUnregisteredContainerFromALostCreateResponse() {
        String id = "lost-" + UUID.randomUUID();
        DockerSandbox creator = sandbox(id);
        String session = creator.createSession();
        try {
            // A distinct registry models CREATE succeeding at Docker while its response never reaches the supervisor.
            sandbox(id).destroyActiveSessions();
            assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> docker.inspectContainerCmd(session).exec());
        }
        finally {
            creator.destroyActiveSessions();
        }
    }

    private DockerSandbox sandbox(String id) {
        return new DockerSandbox(docker, new WorkerSettings(id, System.getenv("HYPERION_TEST_IMAGE"), "runc", 128 * 1024 * 1024, 100_000, 32, Duration.ofSeconds(10),
                Duration.ofSeconds(45), Duration.ofSeconds(5)));
    }
}
