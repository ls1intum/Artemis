package de.tum.cit.aet.artemis.localvcci;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;

import de.tum.cit.aet.artemis.config.icl.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.service.connectors.localci.buildagent.BuildAgentDockerService;
import de.tum.cit.aet.artemis.util.FixMissingServletPathProcessor;

/**
 * This class is used to overwrite the configuration of the local CI system ({@link BuildAgentConfiguration}).
 * In particular, it provides a DockerClient Bean that has all methods used in the tests mocked.
 */
@TestConfiguration
@Import(BuildAgentConfiguration.class) // Fall back to the default configuration if no overwrite is provided here.
public class TestBuildAgentConfiguration {

    /**
     * Provide a mocked DockerClient Bean that returns a mock value for all methods used.
     *
     * @return a mocked DockerClient Bean
     */
    @Bean
    public DockerClient dockerClient() throws InterruptedException {
        DockerClient dockerClient = mock(DockerClient.class);

        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        InspectImageResponse inspectImageResponse = new InspectImageResponse();
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doReturn(inspectImageResponse).when(inspectImageCmd).exec();

        // Mock PullImageCmd
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        doReturn(pullImageCmd).when(dockerClient).pullImageCmd(anyString());
        doReturn(pullImageCmd).when(pullImageCmd).withPlatform(anyString());
        BuildAgentDockerService.MyPullImageResultCallback callback1 = mock(BuildAgentDockerService.MyPullImageResultCallback.class);
        doReturn(callback1).when(pullImageCmd).exec(any(BuildAgentDockerService.MyPullImageResultCallback.class));
        doReturn(null).when(callback1).awaitCompletion();

        String dummyContainerId = "1234567890";

        // Mock dockerClient.createContainerCmd(String dockerImage).withHostConfig(HostConfig hostConfig).withEnv(String... env).withCmd(String... cmd).exec()
        CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createContainerResponse = new CreateContainerResponse();
        createContainerResponse.setId(dummyContainerId);
        doReturn(createContainerCmd).when(dockerClient).createContainerCmd(anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withName(anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withHostConfig(any());
        doReturn(createContainerCmd).when(createContainerCmd).withEnv(anyList());
        doReturn(createContainerCmd).when(createContainerCmd).withUser(anyString());
        doReturn(createContainerCmd).when(createContainerCmd).withCmd(anyString(), anyString(), anyString());
        doReturn(createContainerResponse).when(createContainerCmd).exec();

        // Mock dockerClient.startContainerCmd(String containerId)
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        doReturn(startContainerCmd).when(dockerClient).startContainerCmd(anyString());

        // Mock dockerClient.copyArchiveToContainer(String containerId).withRemotePath(String path).withTarInputStream(InputStream uploadStream).exec()
        CopyArchiveToContainerCmd copyArchiveToContainerCmd = mock(CopyArchiveToContainerCmd.class);
        doReturn(copyArchiveToContainerCmd).when(dockerClient).copyArchiveToContainerCmd(anyString());
        doReturn(copyArchiveToContainerCmd).when(copyArchiveToContainerCmd).withRemotePath(anyString());
        doReturn(copyArchiveToContainerCmd).when(copyArchiveToContainerCmd).withTarInputStream(any());
        doNothing().when(copyArchiveToContainerCmd).exec();

        // Mock dockerClient.execCreateCmd(String containerId).withAttachStdout(Boolean attachStdout).withAttachStderr(Boolean attachStderr).withCmd(String... cmd).exec()
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        doReturn(execCreateCmd).when(dockerClient).execCreateCmd(anyString());
        doReturn(execCreateCmd).when(execCreateCmd).withCmd(any(String[].class));
        doReturn(execCreateCmd).when(execCreateCmd).withUser(anyString());
        doReturn(execCreateCmd).when(execCreateCmd).withAttachStdout(anyBoolean());
        doReturn(execCreateCmd).when(execCreateCmd).withAttachStderr(anyBoolean());
        doReturn(execCreateCmd).when(execCreateCmd).withCmd(anyString(), anyString());
        doReturn(execCreateCmdResponse).when(execCreateCmd).exec();
        doReturn("1234").when(execCreateCmdResponse).getId();

        // Mock dockerClient.execStartCmd(String execId).exec(T resultCallback)
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        doReturn(execStartCmd).when(dockerClient).execStartCmd(anyString());
        doReturn(execStartCmd).when(execStartCmd).withDetach(anyBoolean());
        doAnswer(invocation -> {
            // Stub the 'exec' method of the 'ExecStartCmd' to call the 'onComplete' method of the provided 'ResultCallback.Adapter', which simulates the command completing
            // immediately.
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return null;
        }).when(execStartCmd).exec(any());

        // Mock listContainerCmd() method.
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(listContainersCmd).when(listContainersCmd).withShowAll(anyBoolean());

        // Mock container class
        Container container = mock(Container.class);
        doReturn(new String[] { "dummy-container-name" }).when(container).getNames();
        doReturn("dummy-image-id").when(container).getImageId();
        doReturn(List.of(container)).when(listContainersCmd).exec();

        // Mock listImagesCmd() method.
        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
        doReturn(listImagesCmd).when(dockerClient).listImagesCmd();
        Image image = mock(Image.class);
        doReturn("test-image-id").when(image).getId();
        doReturn(new String[] { "test-image-name" }).when(image).getRepoTags();
        doReturn(List.of(image)).when(listImagesCmd).exec();

        // Mock removeImageCmd method.
        RemoveImageCmd removeImageCmd = mock(RemoveImageCmd.class);
        doReturn(removeImageCmd).when(dockerClient).removeImageCmd(anyString());
        doNothing().when(removeImageCmd).exec();

        // Mock removeContainerCmd
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        doReturn(removeContainerCmd).when(dockerClient).removeContainerCmd(anyString());
        doReturn(removeContainerCmd).when(removeContainerCmd).withForce(true);

        // Mock stopContainerCmd
        StopContainerCmd stopContainerCmd = mock(StopContainerCmd.class);
        doReturn(stopContainerCmd).when(dockerClient).stopContainerCmd(anyString());
        doReturn(stopContainerCmd).when(stopContainerCmd).withTimeout(any());

        // Mock killContainerCmd
        KillContainerCmd killContainerCmd = mock(KillContainerCmd.class);
        doReturn(killContainerCmd).when(dockerClient).killContainerCmd(anyString());

        return dockerClient;
    }

    @Bean
    public FixMissingServletPathProcessor fixMissingServletPathProcessor() {
        return new FixMissingServletPathProcessor();
    }
}
