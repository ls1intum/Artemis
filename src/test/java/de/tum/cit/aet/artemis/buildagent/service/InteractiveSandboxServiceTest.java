package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;

/**
 * Pure unit test (no Docker) for {@link InteractiveSandboxService}'s capture-exec logic: stdout/stderr captured separately by stream type, exit code read back, and a command that
 * never completes reported as timed out.
 */
class InteractiveSandboxServiceTest {

    private BuildAgentConfiguration buildAgentConfiguration;

    private DockerClient dockerClient;

    private InteractiveSandboxService service;

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        dockerClient = mock(DockerClient.class);
        when(buildAgentConfiguration.getDockerClient()).thenReturn(dockerClient);
        when(buildAgentConfiguration.isDockerAvailable()).thenReturn(true);
        service = new InteractiveSandboxService(buildAgentConfiguration);
    }

    private void stubExec(int exitCode, boolean complete, Frame... frames) {
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        when(dockerClient.execCreateCmd(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(execCreateResponse.getId()).thenReturn("exec-1");

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(anyString())).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(any(Boolean.class))).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
            for (Frame frame : frames) {
                callback.onNext(frame);
            }
            if (complete) {
                callback.onComplete();
            }
            return null;
        });

        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(dockerClient.inspectExecCmd(anyString())).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn((long) exitCode);
    }

    private static Frame frame(StreamType type, String text) {
        return new Frame(type, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void exec_capturesStdoutAndStderrSeparately_andExitCode() {
        stubExec(0, true, frame(StreamType.STDOUT, "hello out\n"), frame(StreamType.STDERR, "warn err\n"));

        SandboxExecResult result = service.exec("container-1", Duration.ofSeconds(5), "echo", "hi");

        assertThat(result.exitCode()).isZero();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.stdout()).contains("hello out");
        assertThat(result.stderr()).contains("warn err");
    }

    @Test
    void exec_nonZeroExitCode_isNotSuccess() {
        stubExec(1, true, frame(StreamType.STDOUT, "boom\n"));

        SandboxExecResult result = service.exec("container-1", Duration.ofSeconds(5), "false");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void exec_commandThatNeverCompletes_reportsTimeout() {
        // onComplete is never called, so the latch never trips and the short timeout elapses.
        stubExec(0, false, frame(StreamType.STDOUT, "partial\n"));

        SandboxExecResult result = service.exec("container-1", Duration.ofMillis(200), "sleep", "100");

        assertThat(result.timedOut()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.stdout()).contains("partial");
    }
}
