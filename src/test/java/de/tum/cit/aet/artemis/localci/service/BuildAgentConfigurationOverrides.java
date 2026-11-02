package de.tum.cit.aet.artemis.localci.service;

import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

/**
 * Lets a test replace what the shared {@link BuildAgentConfiguration} spy hands out without stubbing the spy while the application is running.
 * <p>
 * Stubbing and resetting a Mockito spy is not thread-safe: the pending stubbing is kept in the mock, not in the calling thread. The build agent calls this spy from
 * scheduled tasks (availability check, agent information, runner status) and from build threads while a test is still setting up. A call that lands between the two
 * halves of {@code doReturn(x).when(spy).method()} is validated against the stubbing and fails with {@code WrongTypeOfReturnValue} in the test's {@code @BeforeEach}.
 * <p>
 * Therefore the spy is stubbed exactly once, when the context is built and no task has started yet (see {@link #installOn(BuildAgentConfiguration)}). The stubbed answers
 * read the values held here, and tests change those values. An {@link AtomicReference} is safe to change at any time. Without an override, the real method runs.
 */
public class BuildAgentConfigurationOverrides {

    private final AtomicReference<DockerClient> dockerClient = new AtomicReference<>();

    private final AtomicReference<ThreadPoolExecutor> buildExecutor = new AtomicReference<>();

    /**
     * Makes the configuration hand out the given Docker client and report Docker as available, until {@link #reset()}.
     *
     * @param client the client to hand out
     */
    public void useDockerClient(DockerClient client) {
        dockerClient.set(client);
    }

    /**
     * Makes the configuration hand out the given build executor until {@link #reset()}.
     *
     * @param executor the executor to hand out
     */
    public void useBuildExecutor(ThreadPoolExecutor executor) {
        buildExecutor.set(executor);
    }

    /**
     * Removes every override, so that the configuration behaves as the real one again.
     */
    public void reset() {
        dockerClient.set(null);
        buildExecutor.set(null);
    }

    /**
     * Stubs the spy once. This must run while nothing else uses the spy, which is the case while the context is built.
     *
     * @param configuration the spy bean of the build agent configuration; anything else is left alone
     */
    void installOn(BuildAgentConfiguration configuration) {
        if (!Mockito.mockingDetails(configuration).isSpy()) {
            return;
        }
        doAnswer(invocation -> overrideOrReal(dockerClient.get(), invocation)).when(configuration).getDockerClient();
        doAnswer(invocation -> dockerClient.get() != null || (boolean) invocation.callRealMethod()).when(configuration).isDockerAvailable();
        doAnswer(invocation -> overrideOrReal(buildExecutor.get(), invocation)).when(configuration).getBuildExecutor();
    }

    private static Object overrideOrReal(Object override, InvocationOnMock invocation) throws Throwable {
        return override != null ? override : invocation.callRealMethod();
    }
}
