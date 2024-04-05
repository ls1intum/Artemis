package de.tum.in.www1.artemis.localvcci;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.service.connectors.localci.buildagent.LocalCIDockerService;

class LocalCIDockerServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private LocalCIDockerService localCIDockerService;

    @Autowired
    private BuildJobRepository buildJobRepository;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @AfterEach
    void tearDown() {
        buildJobRepository.deleteAll();
    }

    @Test
    void testDeleteOldDockerImages() {
        // Save build job with outdated image to database
        ZonedDateTime buildStartDate = ZonedDateTime.now().minusDays(3);

        BuildJob buildJob = new BuildJob();
        buildJob.setDockerImage("test-image-name");
        buildJob.setBuildStartDate(buildStartDate);

        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

        dockerImageCleanupInfo.put("test-image-name", buildStartDate);

        buildJobRepository.save(buildJob);

        localCIDockerService.deleteOldDockerImages();

        // Verify that removeImageCmd() was called.
        verify(dockerClient, times(1)).removeImageCmd(anyString());
    }

    @Test
    void testDeleteOldDockerImages_NoOutdatedImages() {
        // Save build job to database
        ZonedDateTime buildStartDate = ZonedDateTime.now();

        BuildJob buildJob = new BuildJob();
        buildJob.setDockerImage("test-image-name");
        buildJob.setBuildStartDate(buildStartDate);

        buildJobRepository.save(buildJob);

        localCIDockerService.deleteOldDockerImages();

        // Verify that removeImageCmd() was not called.
        verify(dockerClient, times(0)).removeImageCmd(anyString());
    }

    @Test
    void testPullDockerImage() {
        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doThrow(new NotFoundException("")).when(inspectImageCmd).exec();

        // Pull image
        localCIDockerService.pullDockerImage("test-image-name");

        // Verify that pullImageCmd() was called.
        verify(dockerClient, times(1)).pullImageCmd("test-image-name");
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
        doReturn(System.currentTimeMillis() - (6 * 60 * 1000)).when(mockContainer).getCreated();
        doReturn("dummy-container-id").when(mockContainer).getId();

        localCIDockerService.cleanUpContainers();

        // Verify that removeContainerCmd() was called
        verify(dockerClient, times(1)).removeContainerCmd(anyString());

        // Mock container creation time to be younger than 5 minutes
        doReturn(System.currentTimeMillis()).when(mockContainer).getCreated();

        localCIDockerService.cleanUpContainers();

        // Verify that removeContainerCmd() was not called a second time
        verify(dockerClient, times(1)).removeContainerCmd(anyString());
    }
}
