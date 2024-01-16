package de.tum.in.www1.artemis.localvcci;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIDockerService;

class LocalCIDockerServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private LocalCIDockerService localCIDockerService;

    @Autowired
    private BuildJobRepository buildJobRepository;

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
}
