package de.tum.cit.aet.artemis.buildagent.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Info;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BuildAgentDockerServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private BuildAgentDockerService buildAgentDockerService;

    @Autowired
    @Qualifier("hazelcastInstance")
    private HazelcastInstance hazelcastInstance;

    @Test
    @Order(2)
    void testDeleteOldDockerImages() {
        // Save build job with outdated image to database
        ZonedDateTime buildStartDate = ZonedDateTime.now().minusDays(3);

        BuildJob buildJob = new BuildJob();
        buildJob.setDockerImage("test-image-name");
        buildJob.setBuildStartDate(buildStartDate);

        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

        dockerImageCleanupInfo.put("test-image-name", buildStartDate);

        buildJobRepository.save(buildJob);

        buildAgentDockerService.deleteOldDockerImages();

        // Verify that removeImageCmd() was called.
        verify(dockerClient, times(1)).removeImageCmd(anyString());
    }

    @Test
    @Order(1)
    void testDeleteOldDockerImages_NoOutdatedImages() {
        // Save build job to database
        ZonedDateTime buildStartDate = ZonedDateTime.now();

        BuildJob buildJob = new BuildJob();
        buildJob.setDockerImage("test-image-name");
        buildJob.setBuildStartDate(buildStartDate);

        buildJobRepository.save(buildJob);

        buildAgentDockerService.deleteOldDockerImages();

        // Verify that removeImageCmd() was not called.
        verify(dockerClient, times(0)).removeImageCmd(anyString());
    }

    @Test
    void testPullDockerImage() {
        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doThrow(new NotFoundException("")).when(inspectImageCmd).exec();
        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test-image-name", "test", "test", "test", "test", null, null, false, false, false, null, 0, null, null, null,
                null);
        BuildAgentDTO buildAgent = new BuildAgentDTO("buildagent1", "address1", "buildagent1");
        var build = new BuildJobQueueItem("1", "job1", buildAgent, 1, 1, 1, 1, 1, BuildStatus.SUCCESSFUL, null, null, buildConfig, null);
        // Pull image
        try {
            buildAgentDockerService.pullDockerImage(build, new BuildLogsMap());
        }
        catch (LocalCIException e) {
            // Expected exception
            if (!(e.getCause() instanceof NotFoundException)) {
                throw e;
            }
        }

        // Verify that pullImageCmd() was called.
        verify(dockerClient, times(1)).pullImageCmd("test-image-name");
    }

    @Test
    @Order(3)
    void testCheckUsableDiskSpaceThenCleanUp() {
        // Mock dockerClient.infoCmd().exec()
        InfoCmd infoCmd = mock(InfoCmd.class);
        Info info = mock(Info.class);
        doReturn(infoCmd).when(dockerClient).infoCmd();
        doReturn(info).when(infoCmd).exec();
        doReturn("/").when(info).getDockerRootDir();

        ZonedDateTime buildStartDate = ZonedDateTime.now();

        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

        dockerImageCleanupInfo.put("test-image-name", buildStartDate);

        buildAgentDockerService.checkUsableDiskSpaceThenCleanUp();

        // Verify that removeImageCmd() was called.
        verify(dockerClient, times(2)).removeImageCmd("test-image-name");
    }

    @Test
    void testRemoveStrandedContainers() {

        // Mocks
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(listContainersCmd).when(listContainersCmd).withShowAll(true);

        Container mockContainer = mock(Container.class);
        doReturn(List.of(mockContainer)).when(listContainersCmd).exec();
        doReturn(new String[] { "/local-ci-dummycontainer" }).when(mockContainer).getNames();
        // Mock container creation time to be older than 5 minutes
        doReturn(Instant.now().getEpochSecond() - (6 * 60)).when(mockContainer).getCreated();
        doReturn("dummy-container-id").when(mockContainer).getId();

        buildAgentDockerService.cleanUpContainers();

        // Verify that removeContainerCmd() was called
        verify(dockerClient, times(1)).stopContainerCmd(anyString());

        // Mock container creation time to be younger than 5 minutes
        doReturn(Instant.now().getEpochSecond()).when(mockContainer).getCreated();

        buildAgentDockerService.cleanUpContainers();

        // Verify that removeContainerCmd() was not called a second time
        verify(dockerClient, times(1)).stopContainerCmd(anyString());

        // Mock container creation time to be older than 5 minutes
        doReturn(Instant.now().getEpochSecond() - (6 * 60)).when(mockContainer).getCreated();

        // Mock exception when stopping container
        StopContainerCmd stopContainerCmd = mock(StopContainerCmd.class);
        doReturn(stopContainerCmd).when(dockerClient).stopContainerCmd(anyString());
        doThrow(new RuntimeException("Container stopping failed")).when(stopContainerCmd).exec();

        buildAgentDockerService.cleanUpContainers();

        // Verify that killContainerCmd() was called
        verify(dockerClient, times(1)).killContainerCmd(anyString());
    }
}
