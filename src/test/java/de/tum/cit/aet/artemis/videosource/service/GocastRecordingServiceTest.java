package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository.BindingSnapshot;

class GocastRecordingServiceTest {

    private static final String WATCH_PAGE = "https://live.rbg.tum.de/w/algo/42";

    private static final String PLAYLIST = "https://edge.example/vod/42/COMB/playlist.m3u8?jwt=signed";

    private final GocastConnectionRepository connectionRepository = mock(GocastConnectionRepository.class);

    private final GocastConnectorService connectorService = mock(GocastConnectorService.class);

    private final GocastRecordingService service = new GocastRecordingService(connectionRepository, connectorService,
            new TumLiveService(RestClient.builder(), "https://live.rbg.tum.de/api/v2"));

    private static BindingSnapshot binding(GocastBindingStatus status) {
        return new BindingSnapshot(7, 17, 456, 23, 0, status, "algo", "Algorithms", "enrolled");
    }

    @Test
    void readsTheLectureThroughTheGrantOfTheUnitsCourse() {
        when(connectionRepository.getBindingSnapshot(7)).thenReturn(Optional.of(binding(GocastBindingStatus.ACTIVE)));
        when(connectorService.getRecordingPlaylist(23, 42)).thenReturn(Optional.of(PLAYLIST));

        assertThat(service.lookUpConnectedPlaylist(7, WATCH_PAGE)).contains(new TumLivePlaylistLookup(PLAYLIST, TumLivePlaylistLookup.Outcome.RESOLVED));
    }

    @Test
    void aLectureWithoutRecordingIsNotAvailableYet() {
        when(connectionRepository.getBindingSnapshot(7)).thenReturn(Optional.of(binding(GocastBindingStatus.ACTIVE)));
        when(connectorService.getRecordingPlaylist(23, 42)).thenReturn(Optional.empty());

        assertThat(service.lookUpConnectedPlaylist(7, WATCH_PAGE)).contains(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_AVAILABLE));
    }

    @Test
    void anUnconnectedOrRevokedCourseUsesThePublicLookup() {
        when(connectionRepository.getBindingSnapshot(7)).thenReturn(Optional.empty());
        assertThat(service.lookUpConnectedPlaylist(7, WATCH_PAGE)).isEmpty();

        when(connectionRepository.getBindingSnapshot(7)).thenReturn(Optional.of(binding(GocastBindingStatus.REVOKED)));
        assertThat(service.lookUpConnectedPlaylist(7, WATCH_PAGE)).isEmpty();
        verifyNoInteractions(connectorService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://example.com/w/algo/42", "https://live.rbg.tum.de/w/algo/4294967296",
            "https://live.rbg.tum.de/w/algo/0" })
    void aUrlThatIsNotAGrantableWatchPageUsesThePublicLookup(String videoSource) {
        assertThat(service.lookUpConnectedPlaylist(7, videoSource)).isEmpty();
        verifyNoInteractions(connectionRepository, connectorService);
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = { "NOT_FOUND", "UNAUTHORIZED", "FORBIDDEN" })
    void aLectureTheGrantDoesNotCoverUsesThePublicLookup(HttpStatus status) {
        when(connectionRepository.getBindingSnapshot(7)).thenReturn(Optional.of(binding(GocastBindingStatus.ACTIVE)));
        when(connectorService.getRecordingPlaylist(23, 42)).thenThrow(new GocastIntegrationException("refused", status));

        assertThat(service.lookUpConnectedPlaylist(7, WATCH_PAGE)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = { "SERVICE_UNAVAILABLE", "BAD_GATEWAY", "TOO_MANY_REQUESTS" })
    void anUnreachableTumLiveIsRetriedLater(HttpStatus status) {
        when(connectionRepository.getBindingSnapshot(7)).thenReturn(Optional.of(binding(GocastBindingStatus.ACTIVE)));
        when(connectorService.getRecordingPlaylist(23, 42)).thenThrow(new GocastIntegrationException("unavailable", status));

        assertThat(service.lookUpConnectedPlaylist(7, WATCH_PAGE)).contains(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.TEMPORARILY_UNAVAILABLE));
    }
}
