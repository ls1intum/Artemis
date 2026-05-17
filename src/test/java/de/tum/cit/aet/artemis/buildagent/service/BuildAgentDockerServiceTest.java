package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BuildAgentDockerServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "badoservtst";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

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
        verify(dockerClient, never()).removeImageCmd(anyString());
    }

    @Test
    void testPullDockerImage() {
        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doThrow(new NotFoundException("")).when(inspectImageCmd).exec();
        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test-image-name", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
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

        // Verify that stopContainerCmd() was called
        verify(dockerClient, times(1)).stopContainerCmd(anyString());

        // Mock container creation time to be younger than 5 minutes
        doReturn(Instant.now().getEpochSecond()).when(mockContainer).getCreated();

        buildAgentDockerService.cleanUpContainers();

        // Verify that stopContainerCmd() was not called a second time
        verify(dockerClient, times(1)).stopContainerCmd(anyString());

        // Mock container creation time to be older than 5 minutes
        doReturn(Instant.now().getEpochSecond() - (6 * 60)).when(mockContainer).getCreated();

        // Mock exception when stopping container
        StopContainerCmd stopContainerCmd = mock(StopContainerCmd.class);
        doReturn(stopContainerCmd).when(dockerClient).stopContainerCmd(anyString());
        doReturn(stopContainerCmd).when(stopContainerCmd).withTimeout(anyInt());
        doThrow(new RuntimeException("Container stopping failed")).when(stopContainerCmd).exec();

        buildAgentDockerService.cleanUpContainers();

        // Verify that killContainerCmd() was called
        verify(dockerClient, times(1)).killContainerCmd(anyString());

        // Mock NotModified exception when stopping container
        doThrow(new NotModifiedException("Container not running")).when(stopContainerCmd).exec();
        buildAgentDockerService.cleanUpContainers();

        // Verify that removeContainerCmd() was called
        verify(dockerClient, times(1)).removeContainerCmd(anyString());
    }

    // --- clearAllUnusedDockerImages + getUnusedDockerImageStats (admin Reclaim disk) ---------------------------

    @Test
    void testClearAllUnusedDockerImagesRemovesEveryUnusedImage() {
        // Three images: two unused (one with a repo tag, one dangling — i.e. no tags), one bound to a running
        // container. The id-based clear must hit both unused images regardless of whether they carry tags, and
        // must skip the bound one.
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        Container runningContainer = mock(Container.class);
        doReturn("in-use-image-id").when(runningContainer).getImageId();
        doReturn(List.of(runningContainer)).when(listContainersCmd).exec();

        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
        doReturn(listImagesCmd).when(dockerClient).listImagesCmd();
        Image taggedUnused = mock(Image.class);
        doReturn("unused-tagged-id").when(taggedUnused).getId();
        doReturn(new String[] { "ls1tum/artemis-maven-template:java17-25" }).when(taggedUnused).getRepoTags();
        Image danglingUnused = mock(Image.class);
        doReturn("unused-dangling-id").when(danglingUnused).getId();
        doReturn(new String[] { "<none>:<none>" }).when(danglingUnused).getRepoTags();
        Image inUse = mock(Image.class);
        doReturn("in-use-image-id").when(inUse).getId();
        doReturn(new String[] { "in-use:latest" }).when(inUse).getRepoTags();
        doReturn(List.of(taggedUnused, danglingUnused, inUse)).when(listImagesCmd).exec();

        RemoveImageCmd removeImageCmd = mock(RemoveImageCmd.class);
        doReturn(removeImageCmd).when(dockerClient).removeImageCmd(anyString());

        int removed = buildAgentDockerService.clearAllUnusedDockerImages();

        assertThat(removed).isEqualTo(2);
        // Both unused images are removed by their ID, not their tag.
        verify(dockerClient).removeImageCmd("unused-tagged-id");
        verify(dockerClient).removeImageCmd("unused-dangling-id");
        verify(dockerClient, never()).removeImageCmd("in-use-image-id");
    }

    @Test
    void testClearAllUnusedDockerImagesToleratesNotFoundPerImage() {
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(List.<Container>of()).when(listContainersCmd).exec();

        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
        doReturn(listImagesCmd).when(dockerClient).listImagesCmd();
        Image one = mock(Image.class);
        doReturn("one-id").when(one).getId();
        Image two = mock(Image.class);
        doReturn("two-id").when(two).getId();
        doReturn(List.of(one, two)).when(listImagesCmd).exec();

        RemoveImageCmd okCmd = mock(RemoveImageCmd.class);
        RemoveImageCmd notFoundCmd = mock(RemoveImageCmd.class);
        doThrow(new NotFoundException("gone")).when(notFoundCmd).exec();
        doReturn(notFoundCmd).when(dockerClient).removeImageCmd("one-id");
        doReturn(okCmd).when(dockerClient).removeImageCmd("two-id");

        int removed = buildAgentDockerService.clearAllUnusedDockerImages();

        assertThat(removed).isEqualTo(1);
        verify(dockerClient).removeImageCmd("one-id");
        verify(dockerClient).removeImageCmd("two-id");
    }

    @Test
    void testGetUnusedDockerImageStatsSumsSizesOfImagesNotInUse() {
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        Container running = mock(Container.class);
        doReturn("bound-id").when(running).getImageId();
        doReturn(List.of(running)).when(listContainersCmd).exec();

        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
        doReturn(listImagesCmd).when(dockerClient).listImagesCmd();
        Image free1 = mock(Image.class);
        doReturn("free1-id").when(free1).getId();
        doReturn(123_456_789L).when(free1).getSize();
        Image free2 = mock(Image.class);
        doReturn("free2-id").when(free2).getId();
        doReturn(1_000L).when(free2).getSize();
        Image bound = mock(Image.class);
        doReturn("bound-id").when(bound).getId();
        doReturn(999_999_999L).when(bound).getSize();
        doReturn(List.of(free1, free2, bound)).when(listImagesCmd).exec();

        var stats = buildAgentDockerService.getUnusedDockerImageStats();

        assertThat(stats.count()).isEqualTo(2);
        assertThat(stats.totalBytes()).isEqualTo(123_456_789L + 1_000L);
    }
}
