package de.tum.cit.aet.artemis.buildagent.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class BuildAgentDockerCleanupTest {

    private final BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);

    private final DistributedDataAccessService distributed = mock(DistributedDataAccessService.class);

    private final BuildJobContainerService containers = mock(BuildJobContainerService.class);

    private final DockerClient docker = mock(DockerClient.class);

    private final ListContainersCmd listContainers = mock(ListContainersCmd.class, RETURNS_SELF);

    private final ListImagesCmd listImages = mock(ListImagesCmd.class);

    private final RemoveImageCmd removeImage = mock(RemoveImageCmd.class);

    private final BuildAgentDockerService service = new BuildAgentDockerService(configuration, distributed, containers, mock(TaskScheduler.class));

    @BeforeEach
    void setUp() {
        when(configuration.getDockerClient()).thenReturn(docker);
        when(configuration.isDockerAvailable()).thenReturn(true);
        when(docker.listContainersCmd()).thenReturn(listContainers);
        when(docker.listImagesCmd()).thenReturn(listImages);
        when(docker.removeImageCmd(any())).thenReturn(removeImage);
        ReflectionTestUtils.setField(service, "imageCleanupEnabled", true);
        ReflectionTestUtils.setField(service, "imageExpiryDays", 2);
        ReflectionTestUtils.setField(service, "containerExpiryMinutes", 5);
        ReflectionTestUtils.setField(service, "buildContainerPrefix", "local-ci-");
        ReflectionTestUtils.setField(service, "buildAgentShortName", "agent");
        ReflectionTestUtils.setField(service, "imageCleanupDiskSpaceThresholdMb", 100);
    }

    @Test
    void initialCleanupRemovesAllOwnContainersAndLaterOnlyExpiredOnes() {
        Container old = container("old", new String[] { "/local-ci-old" }, Instant.now().minusSeconds(600).getEpochSecond());
        Container young = container("young", new String[] { "/local-ci-young" }, Instant.now().getEpochSecond());
        Container other = container("other", new String[] { "/other-service" }, 0);
        Container unnamed = container("unnamed", null, 0);
        Container emptyNames = container("empty", new String[0], 0);
        when(listContainers.exec()).thenReturn(List.of(old, young, other, unnamed, emptyNames));
        service.cleanUpContainers();
        service.cleanUpContainers();
        verify(containers, times(2)).stopUnresponsiveContainer("old");
        verify(containers).stopUnresponsiveContainer("young");
        verify(containers, never()).stopUnresponsiveContainer("other");
        verify(containers, never()).stopUnresponsiveContainer("unnamed");
        verify(containers, never()).stopUnresponsiveContainer("empty");
    }

    @Test
    void failedInitialListingStillAdvancesToAgeFilteredCleanup() {
        Container young = container("young", new String[] { "/local-ci-young" }, Instant.now().getEpochSecond());
        when(listContainers.exec()).thenThrow(new IllegalStateException("daemon error")).thenReturn(List.of(young)).thenThrow(new RuntimeException("Connection refused"));
        service.cleanUpContainers();
        service.cleanUpContainers();
        service.cleanUpContainers();
        verify(containers, never()).stopUnresponsiveContainer(any());
    }

    @ParameterizedTest
    @EnumSource(BuildAgentStatus.class)
    void missingDockerClientSkipsCleanupForEveryAgentState(BuildAgentStatus status) {
        when(configuration.getDockerClient()).thenReturn(null);
        when(distributed.getBuildAgentStatus("agent")).thenReturn(status);
        service.cleanUpContainers();
        service.deleteOldDockerImages();
        service.checkUsableDiskSpaceThenCleanUp();
        verify(docker, never()).listContainersCmd();
        verify(containers, never()).stopUnresponsiveContainer(any());
    }

    @Test
    void unavailableDockerAndDisabledCleanupDoNotQueryDaemon() {
        when(configuration.isDockerAvailable()).thenReturn(false);
        service.cleanUpContainers();
        service.deleteOldDockerImages();
        service.checkUsableDiskSpaceThenCleanUp();
        when(configuration.isDockerAvailable()).thenReturn(true);
        ReflectionTestUtils.setField(service, "imageCleanupEnabled", false);
        service.deleteOldDockerImages();
        service.checkUsableDiskSpaceThenCleanUp();
        verify(docker, never()).listContainersCmd();
        verify(docker, never()).infoCmd();
    }

    @Test
    void deletesOnlyUnusedExpiredImageTagsAndToleratesAlreadyRemovedImage() {
        Image unused = image("unused-id", new String[] { "expired", "recent", "already-gone" });
        Image inUse = image("used-id", new String[] { "used" });
        Image dangling = image("dangling-id", null);
        Container running = mock(Container.class);
        when(running.getImageId()).thenReturn("used-id");
        when(listContainers.exec()).thenReturn(List.of(running));
        when(listImages.exec()).thenReturn(List.of(unused, inUse, dangling));
        ZonedDateTime now = ZonedDateTime.now();
        when(distributed.getDockerImageCleanupInfoMap())
                .thenReturn(Map.of("expired", now.minusDays(3), "recent", now, "used", now.minusDays(4), "unlisted", now.minusDays(5), "already-gone", now.minusDays(6)));
        RemoveImageCmd missing = mock(RemoveImageCmd.class);
        when(docker.removeImageCmd("already-gone")).thenReturn(missing);
        when(missing.exec()).thenThrow(new NotFoundException("already removed"));
        service.deleteOldDockerImages();
        verify(docker).removeImageCmd("expired");
        verify(docker).removeImageCmd("already-gone");
        verify(removeImage).exec();
        verify(missing).exec();
        verify(docker, never()).removeImageCmd("recent");
        verify(docker, never()).removeImageCmd("used");
        verify(docker, never()).removeImageCmd("unlisted");
    }

    @Test
    void diskCleanupSortsByLastUseSkipsUsedImagesAndStopsWhenSpaceRecovered() throws IOException {
        InfoCmd infoCommand = mock(InfoCmd.class);
        Info info = mock(Info.class);
        when(docker.infoCmd()).thenReturn(infoCommand);
        when(infoCommand.exec()).thenReturn(info);
        when(info.getDockerRootDir()).thenReturn(null);
        when(listContainers.exec()).thenReturn(List.of());
        Image image = image("id", new String[] { "older", "newer" });
        when(listImages.exec()).thenReturn(List.of(image));
        Map<String, ZonedDateTime> lastUsed = new LinkedHashMap<>();
        lastUsed.put("newer", ZonedDateTime.now().minusDays(1));
        lastUsed.put("in-use", ZonedDateTime.now().minusDays(3));
        lastUsed.put("older", ZonedDateTime.now().minusDays(2));
        when(distributed.getDockerImageCleanupInfoMap()).thenReturn(lastUsed);
        FileStore store = mock(FileStore.class);
        when(store.getUsableSpace()).thenReturn(0L, Long.MAX_VALUE);
        try (var files = org.mockito.Mockito.mockStatic(Files.class)) {
            files.when(() -> Files.getFileStore(Path.of("/"))).thenReturn(store);
            service.checkUsableDiskSpaceThenCleanUp();
        }
        verify(docker).removeImageCmd("older");
        verify(removeImage).exec();
        verify(docker, never()).removeImageCmd("in-use");
        verify(docker, never()).removeImageCmd("newer");
    }

    @Test
    void diskCleanupHandlesEmptyMetadataAndFailedFilesystemLookup() throws IOException {
        InfoCmd infoCommand = mock(InfoCmd.class);
        Info info = mock(Info.class);
        when(docker.infoCmd()).thenReturn(infoCommand);
        when(infoCommand.exec()).thenReturn(info);
        when(info.getDockerRootDir()).thenReturn("/");
        when(listContainers.exec()).thenReturn(List.of());
        when(listImages.exec()).thenReturn(List.of());
        when(distributed.getDockerImageCleanupInfoMap()).thenReturn(Map.of());
        FileStore store = mock(FileStore.class);
        when(store.getUsableSpace()).thenReturn(0L);
        try (var files = org.mockito.Mockito.mockStatic(Files.class)) {
            files.when(() -> Files.getFileStore(Path.of("/"))).thenReturn(store).thenThrow(new IOException("unavailable"));
            service.checkUsableDiskSpaceThenCleanUp();
            service.checkUsableDiskSpaceThenCleanUp();
        }
        verify(docker, never()).removeImageCmd(any());
    }

    private Container container(String id, String[] names, long created) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(id);
        when(container.getNames()).thenReturn(names);
        when(container.getCreated()).thenReturn(created);
        return container;
    }

    private Image image(String id, String[] tags) {
        Image image = mock(Image.class);
        when(image.getId()).thenReturn(id);
        when(image.getRepoTags()).thenReturn(tags);
        return image;
    }
}
