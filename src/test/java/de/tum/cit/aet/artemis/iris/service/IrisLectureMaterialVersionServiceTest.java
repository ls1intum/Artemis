package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitIngestedVersionsDTO;

class IrisLectureMaterialVersionServiceTest {

    private LectureUnitRepositoryApi repository;

    private DistributedDataProvider provider;

    private DistributedMap<String, String> snapshots;

    private Map<String, String> stored;

    private IrisLectureMaterialVersionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(LectureUnitRepositoryApi.class);
        provider = mock(DistributedDataProvider.class);
        snapshots = mock(DistributedMap.class);
        stored = new HashMap<>();
        when(provider.<String, String>getExpiringMap(anyString(), any())).thenReturn(snapshots);
        when(snapshots.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        doAnswer(call -> stored.put(call.getArgument(0), call.getArgument(1))).when(snapshots).put(anyString(), anyString());
        service = newService();
    }

    private IrisLectureMaterialVersionService newService() {
        var result = new IrisLectureMaterialVersionService(Optional.of(repository), provider, JsonMapper.builder().build());
        ReflectionTestUtils.setField(result, "jobTimeout", 300);
        return result;
    }

    @Test
    void answerReturningAfterNewIngestionKeepsTheLaunchRevisionAcrossNodes() {
        when(repository.findIngestedVersionsByCourseId(5L)).thenReturn(List.of(new LectureUnitIngestedVersionsDTO(42L, 1, 3)))
                .thenReturn(List.of(new LectureUnitIngestedVersionsDTO(42L, 2, 4)));
        service.capture("run", 5L);
        var otherNode = newService();
        var citations = new IrisCitationService(Optional.of(repository), mock(IrisSessionRepository.class), otherNode);

        service.refresh("run");

        assertThat(citations.stampCitationVersions("[cite:L:42:7:::Key:Summary]", "run")).endsWith(":va1]");
        assertThat(citations.stampCitationVersions("[cite:L:42::30:60:Key:Summary]", "run")).endsWith(":vt3]");
        verify(repository, times(1)).findIngestedVersionsByCourseId(5L);
        verify(snapshots).put(eq("run"), eq(stored.get("run")), eq(Duration.ofSeconds(300)));
    }

    @Test
    void missingExpiredCorruptAndForeignUnitSnapshotsCannotUseCurrentVersions() {
        var citations = new IrisCitationService(Optional.of(repository), mock(IrisSessionRepository.class), service);
        assertThat(citations.stampCitationVersions("[cite:L:42:7:::Key:Summary]", "missing")).endsWith(":va0]");
        stored.put("corrupt", "not json");
        assertThat(service.getSnapshot("corrupt")).isEmpty();
        when(repository.findIngestedVersionsByCourseId(5L)).thenReturn(List.of(new LectureUnitIngestedVersionsDTO(42L, 1, null)));
        service.capture("run", 5L);
        assertThat(citations.stampCitationVersions("[cite:L:99:7:::Key:Summary]", "run")).endsWith(":va0]");
        stored.remove("run");
        service.refresh("run");
        assertThat(service.getSnapshot("run")).isEmpty();
        service.remove("run");
        verify(snapshots).remove("run");
    }

    @Test
    void failedCaptureLeavesTheChatUnverified() {
        when(repository.findIngestedVersionsByCourseId(5L)).thenThrow(new IllegalStateException("unavailable"));
        service.capture("run", 5L);
        assertThat(service.getSnapshot("run")).isEmpty();
    }
}
