package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.service.BuildContainerIsolationProbe.Result;
import de.tum.cit.aet.artemis.buildagent.service.BuildContainerIsolationProbe.Target;

class BuildContainerIsolationProbeTest {

    private static final String IMAGE = "busybox:test";

    private DockerClient dockerClient;

    private WaitContainerResultCallback waitCallback;

    private RemoveContainerCmd removeCommand;

    @BeforeEach
    void setUp() throws Exception {
        dockerClient = mock(DockerClient.class);
        InspectImageCmd inspectImage = mock(InspectImageCmd.class);
        when(dockerClient.inspectImageCmd(IMAGE)).thenReturn(inspectImage);

        CreateContainerCmd createCommand = mock(CreateContainerCmd.class, Answers.RETURNS_SELF);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("probe-container");
        doReturn(response).when(createCommand).exec();
        when(dockerClient.createContainerCmd(IMAGE)).thenReturn(createCommand);
        when(dockerClient.startContainerCmd("probe-container")).thenReturn(mock(StartContainerCmd.class));

        WaitContainerCmd waitCommand = mock(WaitContainerCmd.class);
        waitCallback = mock(WaitContainerResultCallback.class);
        when(waitCommand.start()).thenReturn(waitCallback);
        when(dockerClient.waitContainerCmd("probe-container")).thenReturn(waitCommand);

        removeCommand = mock(RemoveContainerCmd.class, Answers.RETURNS_SELF);
        when(dockerClient.removeContainerCmd("probe-container")).thenReturn(removeCommand);
    }

    @Test
    void parsesHostsAndPortsAndDropsEntriesThatCannotBeChecked() {
        List<Target> targets = BuildContainerIsolationProbe.parseTargets(Arrays.asList("10.0.0.5:6379", " db.internal:5432 ", "[2001:db8::1]:5701", "localhost:6379",
                "127.0.0.1:6379", "::1:6379", "10.0.0.5:6379", "nonsense", "host:", ":80", "host:notaport", "host:70000", "", null));

        assertThat(targets).containsExactly(new Target("10.0.0.5", 6379), new Target("db.internal", 5432), new Target("2001:db8::1", 5701));
    }

    @Test
    void reportsATargetThatTheContainerCanConnectTo() throws Exception {
        // The first container is the self test of the image, the second one the connection to the target.
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 0);
        Target store = new Target("10.0.0.5", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.reachable()).containsExactly(store);
        assertThat(result.notReachable()).isEmpty();
        assertThat(result.inconclusive()).isEmpty();
        verify(removeCommand, times(2)).exec();
    }

    @Test
    void acceptsATargetThatTheContainerCannotConnectTo() throws Exception {
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 1);
        Target store = new Target("10.0.0.5", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.reachable()).isEmpty();
        assertThat(result.notReachable()).containsExactly(store);
        verify(removeCommand, times(2)).exec();
    }

    @Test
    void doesNotCountAMissingCommandAsIsolation() throws Exception {
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 127);
        Target store = new Target("10.0.0.5", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.notReachable()).isEmpty();
        assertThat(result.inconclusive()).containsExactly(store);
    }

    @Test
    void doesNotCountAContainerThatDidNotFinishAsIsolation() throws Exception {
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0).thenThrow(new RuntimeException("timeout"));
        Target store = new Target("10.0.0.5", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.notReachable()).isEmpty();
        assertThat(result.inconclusive()).containsExactly(store);
        verify(removeCommand, times(2)).exec();
    }

    @Test
    void doesNotCountAnAbnormalExitAsIsolation() throws Exception {
        // 2 is what a command exits with after a usage error, 137 is a container that was killed. Neither says anything about the connection.
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 2, 137);
        Target store = new Target("10.0.0.5", 6379);
        Target database = new Target("10.0.0.6", 5432);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store, database));

        assertThat(result.notReachable()).isEmpty();
        assertThat(result.inconclusive()).containsExactly(store, database);
    }

    @Test
    void doesNotCountTargetsAsIsolatedWhenTheImageFailsItsSelfTest() throws Exception {
        // The nc of the image does not support the options of the probe, so a failed connection would mean nothing.
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(1);
        Target store = new Target("10.0.0.5", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.notReachable()).isEmpty();
        assertThat(result.reachable()).isEmpty();
        assertThat(result.inconclusive()).containsExactly(store);
        // Only the self test ran, no connection to a target was tried.
        verify(dockerClient, times(1)).createContainerCmd(IMAGE);
    }

    @Test
    void skipsTheCheckWhenTheImageIsNeitherPresentNorPullable() {
        InspectImageCmd inspectImage = mock(InspectImageCmd.class);
        doThrow(new NotFoundException("no such image")).when(inspectImage).exec();
        when(dockerClient.inspectImageCmd(IMAGE)).thenReturn(inspectImage);
        PullImageCmd pullCommand = mock(PullImageCmd.class);
        doThrow(new RuntimeException("registry unreachable")).when(pullCommand).start();
        when(dockerClient.pullImageCmd(IMAGE)).thenReturn(pullCommand);
        Target store = new Target("10.0.0.5", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.inconclusive()).containsExactly(store);
        verify(dockerClient, never()).createContainerCmd(IMAGE);
    }

    @Test
    void startsNothingWithoutTargets() {
        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of());

        assertThat(result.reachable()).isEmpty();
        assertThat(result.notReachable()).isEmpty();
        assertThat(result.inconclusive()).isEmpty();
        verify(dockerClient, never()).createContainerCmd(IMAGE);
    }
}
