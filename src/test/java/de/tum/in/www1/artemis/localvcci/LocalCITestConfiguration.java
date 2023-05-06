package de.tum.in.www1.artemis.localvcci;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Container;

import de.tum.in.www1.artemis.config.localvcci.LocalCIConfiguration;

/**
 * This class is used to overwrite the configuration of the local CI system ({@link LocalCIConfiguration}).
 * In particular, it provides a DockerClient Bean that has all methods used in the tests mocked.
 */
@TestConfiguration
@Import(LocalCIConfiguration.class) // Fall back to the default configuration if no overwrite is provided here.
public class LocalCITestConfiguration {

    /**
     * Provide a mocked DockerClient Bean that returns a mock value for all methods used.
     *
     * @return a mocked DockerClient Bean
     */
    @Bean
    public DockerClient dockerClient() {
        DockerClient dockerClient = mock(DockerClient.class);

        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        InspectImageResponse inspectImageResponse = new InspectImageResponse();
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doReturn(inspectImageResponse).when(inspectImageCmd).exec();

        String dummyContainerId = "1234567890";

        // Mock dockerClient.createContainerCmd(String dockerImage).withHostConfig(HostConfig hostConfig).withEnv(String... env).withCmd(String... cmd).exec()
        CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createContainerResponse = new CreateContainerResponse();
        createContainerResponse.setId(dummyContainerId);
        doReturn(createContainerCmd).when(dockerClient).createContainerCmd(anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withName(anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withHostConfig(any());
        doReturn(createContainerCmd).when(createContainerCmd).withEnv(anyString(), anyString(), anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withCmd(anyString(), anyString(), anyString());
        doReturn(createContainerResponse).when(createContainerCmd).exec();

        // Mock dockerClient.startContainerCmd(String containerId)
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        doReturn(startContainerCmd).when(dockerClient).startContainerCmd(anyString());

        // Mock dockerClient.execCreateCmd(String containerId).withAttachStdout(Boolean attachStdout).withAttachStderr(Boolean attachStderr).withCmd(String... cmd).exec()
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        doReturn(execCreateCmd).when(dockerClient).execCreateCmd(anyString());
        doReturn(execCreateCmd).when(execCreateCmd).withAttachStdout(anyBoolean());
        doReturn(execCreateCmd).when(execCreateCmd).withAttachStderr(anyBoolean());
        doReturn(execCreateCmd).when(execCreateCmd).withCmd(anyString(), anyString());
        doReturn(execCreateCmdResponse).when(execCreateCmd).exec();
        doReturn("1234").when(execCreateCmdResponse).getId();

        // Mock dockerClient.execStartCmd(String execId).exec(T resultCallback)
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        doReturn(execStartCmd).when(dockerClient).execStartCmd(anyString());
        doAnswer(invocation -> {
            // Stub the 'exec' method of the 'ExecStartCmd' to call the 'onComplete' method of the provided 'ResultCallback.Adapter', which simulates the command completing
            // immediately.
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return null;
        }).when(execStartCmd).exec(any());

        // Mock stopContainer() method.
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(listContainersCmd).when(listContainersCmd).withShowAll(anyBoolean());
        Container container = mock(Container.class);
        doReturn(new String[] { "dummy-container-name" }).when(container).getNames();
        doReturn(List.of(container)).when(listContainersCmd).exec();

        return dockerClient;
    }
}
