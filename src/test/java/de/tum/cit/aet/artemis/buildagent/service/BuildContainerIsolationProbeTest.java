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
import org.mockito.Mockito;

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
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.buildagent.service.BuildContainerIsolationProbe.Result;
import de.tum.cit.aet.artemis.buildagent.service.BuildContainerIsolationProbe.Target;

class BuildContainerIsolationProbeTest {

    private static final String IMAGE = "busybox:test";

    private DockerClient dockerClient;

    private WaitContainerResultCallback waitCallback;

    private RemoveContainerCmd removeCommand;

    private CreateContainerCmd createCommand;

    @BeforeEach
    void setUp() throws Exception {
        dockerClient = mock(DockerClient.class);
        InspectImageCmd inspectImage = mock(InspectImageCmd.class);
        when(dockerClient.inspectImageCmd(IMAGE)).thenReturn(inspectImage);

        createCommand = mock(CreateContainerCmd.class, Answers.RETURNS_SELF);
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
    void parsesHostsAndPortsAndDropsMalformedEntries() {
        List<Target> targets = BuildContainerIsolationProbe.parseTargets(Arrays.asList("10.0.0.5:6379", " db.internal:5432 ", "[2001:db8::1]:5701", "localhost:6379",
                "127.0.0.1:6379", "::1:6379", "10.0.0.5:6379", "nonsense", "host:", ":80", "host:notaport", "host:70000", "", null));

        assertThat(targets).containsExactly(new Target("10.0.0.5", 6379), new Target("db.internal", 5432), new Target("2001:db8::1", 5701), new Target("localhost", 6379),
                new Target("127.0.0.1", 6379), new Target("::1", 6379));
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

    @Test
    void doesNotCountAnUnresolvableNameAsIsolation() throws Exception {
        // The nc of the container exits with the code of a refused connection when it cannot resolve a name, so the names are resolved before a container is started.
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 1);
        Target unresolvable = new Target("no-such-host.invalid", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(unresolvable));

        assertThat(result.notReachable()).isEmpty();
        assertThat(result.reachable()).isEmpty();
        assertThat(result.inconclusive()).containsExactly(unresolvable);
        // The self test and the attempt by name in the container ran, and no connection to an address
        verify(dockerClient, times(2)).createContainerCmd(IMAGE);
    }

    @Test
    void reportsANameThatTheContainerReachesThroughItsOwnResolution() throws Exception {
        // The agent cannot resolve the name, but the container reaches it, as it would through an alias of the network.
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenAnswer(invocation -> {
            // The command of the container that was created last
            var invocations = Mockito.mockingDetails(createCommand).getInvocations().stream().filter(call -> call.getMethod().getName().equals("withCmd")).toList();
            String[] command = (String[]) invocations.getLast().getRawArguments()[0];
            boolean selfTest = command[0].equals("sh") && !command[2].contains("nslookup");
            boolean byName = command[0].equals("sh") && command[2].contains("nslookup alias.internal");
            return selfTest || byName ? 0 : 1;
        });
        Target aliased = new Target("alias.internal", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(aliased));

        assertThat(result.reachable()).containsExactly(aliased);
        assertThat(result.notReachable()).isEmpty();
    }

    @Test
    void asksTheContainerToTryEveryAddressOfAName() throws Exception {
        // nc alone connects to one address of a name. The test container resolves the name itself and tries each address, so that a refusal on one of them hides no other.
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 1);
        Target named = new Target("alias.internal", 6379);

        new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(named));

        var commands = Mockito.mockingDetails(createCommand).getInvocations().stream().filter(call -> call.getMethod().getName().equals("withCmd")).toList();
        String script = ((String[]) commands.getLast().getRawArguments()[0])[2];
        assertThat(script).contains("nslookup alias.internal").contains("for address in $addresses").contains("exit 4");
    }

    @Test
    void probesALoopbackTargetThroughTheNameOfTheHost() throws Exception {
        // A service that the agent reaches as localhost is a service of the host. A container reaches the host by another name, and a connection to it succeeds when the
        // service listens on every interface.
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 0);
        Target store = new Target("localhost", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.reachable()).containsExactly(store);
        var invocations = Mockito.mockingDetails(createCommand).getInvocations();
        String[] lastCommand = (String[]) invocations.stream().filter(call -> call.getMethod().getName().equals("withCmd")).toList().getLast().getRawArguments()[0];
        assertThat(lastCommand[2]).as("the address of the host is read from the hosts file of the container").contains("/etc/hosts");
        HostConfig lastHostConfig = (HostConfig) invocations.stream().filter(call -> call.getMethod().getName().equals("withHostConfig")).toList().getLast().getRawArguments()[0];
        assertThat(lastHostConfig.getExtraHosts()).contains("host.docker.internal:host-gateway");
    }

    @Test
    void acceptsALoopbackTargetThatTheHostDoesNotExposeToContainers() throws Exception {
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 1);
        Target store = new Target("127.0.0.1", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.notReachable()).containsExactly(store);
    }

    @Test
    void doesNotCountAMissingEntryForTheHostAsIsolation() throws Exception {
        // The container has no entry for the name of the host, which the probe reports with its own exit code
        when(waitCallback.awaitStatusCode(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(0, 3);
        Target store = new Target("localhost", 6379);

        Result result = new BuildContainerIsolationProbe(dockerClient, IMAGE).run(null, List.of(store));

        assertThat(result.notReachable()).isEmpty();
        assertThat(result.inconclusive()).containsExactly(store);
    }
}
