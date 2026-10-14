package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void globalSearchKeepsOnlyTheCitedUnitsOfTheVersionsCapturedBeforeRetrieval() {
        storeEntriesWithTheirOwnLifetime();
        when(repository.findIngestedVersionsByCourseId(5L)).thenReturn(List.of(new LectureUnitIngestedVersionsDTO(42L, 1, null), new LectureUnitIngestedVersionsDTO(43L, 2, null)))
                .thenReturn(List.of(new LectureUnitIngestedVersionsDTO(42L, 9, null)));

        service.captureForGlobalSearch("run", "student", List.of(5L));
        service.keepCitedForGlobalSearch("run", "student", List.of(42L));

        // The versions from before retrieval, reduced to the cited unit; the material is not read again when the answer arrives.
        assertThat(service.getGlobalSearchSnapshot("run", "student")).containsOnlyKeys(42L);
        assertThat(service.getGlobalSearchSnapshot("run", "student").get(42L).attachmentVersion()).isEqualTo(1);
        verify(repository, times(1)).findIngestedVersionsByCourseId(5L);
        verify(snapshots).put(eq("student:run"), anyString(), eq(Duration.ofHours(1)));
        // The capture outlives the job timeout too, because a running answer's job is extended on every status update.
        verify(provider, atLeastOnce()).getExpiringMap("pyris-global-search-material-version-map", Duration.ofHours(1));
        verify(provider, never()).getExpiringMap("pyris-global-search-material-version-map", Duration.ofSeconds(300));
        // Bound to the user who asked, so another user's run id reads nothing.
        assertThat(service.getGlobalSearchSnapshot("run", "other")).isEmpty();
    }

    @Test
    void unscopedGlobalSearchLeavesTheCitationsUnverifiedWhenTheAnswerArrives() {
        storeEntriesWithTheirOwnLifetime();
        when(repository.findIngestedVersionsByCourseId(7L)).thenReturn(List.of(new LectureUnitIngestedVersionsDTO(42L, 3, null)));

        service.keepCitedForGlobalSearch("run", "admin", List.of(42L));

        // Nothing was captured before retrieval, and the material read now may be newer than what the answer was written from.
        assertThat(service.getGlobalSearchSnapshot("run", "admin")).isEmpty();
        verify(repository, never()).findIngestedVersionsByCourseId(anyLong());
        verify(snapshots, never()).put(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void globalSearchAnswerWithoutCitedUnitsDropsItsSnapshot() {
        doAnswer(call -> stored.remove(call.getArgument(0))).when(snapshots).remove(anyString());
        when(repository.findIngestedVersionsByCourseId(5L)).thenReturn(List.of(new LectureUnitIngestedVersionsDTO(42L, 1, null)));
        service.captureForGlobalSearch("run", "student", List.of(5L));

        service.keepCitedForGlobalSearch("run", "student", List.of());

        assertThat(service.getGlobalSearchSnapshot("run", "student")).isEmpty();
    }

    private void storeEntriesWithTheirOwnLifetime() {
        doAnswer(call -> stored.put(call.getArgument(0), call.getArgument(1))).when(snapshots).put(anyString(), anyString(), any(Duration.class));
    }
}
