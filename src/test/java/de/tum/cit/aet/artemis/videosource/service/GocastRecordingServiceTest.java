package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.videosource.api.GocastBindingApi;
import de.tum.cit.aet.artemis.videosource.config.GocastConfiguration;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadTargetDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository.BindingSnapshot;

class GocastRecordingServiceTest {

    private static final String WATCH_PAGE = "https://live.rbg.tum.de/w/algo/42";

    private static final String PLAYLIST = "https://edge.example/vod/42/COMB/playlist.m3u8?jwt=signed";

    private final GocastConnectionRepository connectionRepository = mock(GocastConnectionRepository.class);

    private final GocastConnectorService connectorService = mock(GocastConnectorService.class);

    private final GocastRecordingService service = new GocastRecordingService(connectionRepository, connectorService,
            new TumLiveService(RestClient.builder(), "https://live.rbg.tum.de/api/v2"), RestClient.create());

    private static BindingSnapshot binding(GocastBindingStatus status) {
        return new BindingSnapshot(7, 17, 456, 23, 0, status, "algo", "Algorithms", "enrolled");
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = { "NO_CONTENT", "CONFLICT" })
    @Timeout(10)
    void forwardsTheMultipartFileAndHandlesTheUpstreamStatus(HttpStatus upstreamStatus) throws Exception {
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var contentType = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var body = new AtomicReference<byte[]>();
        peer.createContext("/upload", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            body.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(upstreamStatus.value(), -1);
            exchange.close();
        });
        peer.start();
        try {
            var uploadClient = new GocastConfiguration().gocastUploadRestClient(Duration.ofSeconds(5));
            var recordingService = new GocastRecordingService(connectionRepository, connectorService, new TumLiveService(RestClient.builder(), "https://live.rbg.tum.de/api/v2"),
                    uploadClient);
            var api = new GocastBindingApi(mock(GocastBindingService.class), recordingService);
            var target = new GocastUploadTargetDTO(42, "http://127.0.0.1:" + peer.getAddress().getPort() + "/upload");
            byte[] video = { 0, 1, -1, 2 };
            var input = new ByteArrayInputStream(video);
            if (upstreamStatus.is2xxSuccessful()) {
                api.transferUpload(target, input);
            }
            else {
                assertThatThrownBy(() -> api.transferUpload(target, input)).isInstanceOfSatisfying(GocastIntegrationException.class,
                        failure -> assertThat(failure.getUpstreamStatus()).isEqualTo(upstreamStatus));
            }
            assertThat(contentType.get()).startsWith("multipart/form-data;boundary=");
            assertThat(authorization.get()).isNull();
            assertThat(body.get()).containsSequence(video);
            assertThat(new String(body.get(), StandardCharsets.ISO_8859_1)).contains("name=\"file\"; filename=\"video\"").contains("Content-Type: application/octet-stream");
        }
        finally {
            peer.stop(0);
        }
    }

    @Test
    @Timeout(10)
    void outboundUploadTimesOutWhileThePeerDoesNotRead() throws Exception {
        try (var listener = new ServerSocket()) {
            listener.setReceiveBufferSize(1024);
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            listener.setSoTimeout(1000);
            var uploadClient = new GocastConfiguration().gocastUploadRestClient(Duration.ofSeconds(1));
            var recordingService = new GocastRecordingService(connectionRepository, connectorService, new TumLiveService(RestClient.builder(), "https://live.rbg.tum.de/api/v2"),
                    uploadClient);
            var target = new GocastUploadTargetDTO(42, "http://127.0.0.1:" + listener.getLocalPort() + "/upload");
            // 64 MiB is larger than the socket buffers, but the input itself only allocates one block for its contents.
            var block = new byte[64 * 1024];
            var blocks = IntStream.range(0, 1024).mapToObj(_ -> new ByteArrayInputStream(block)).toList();
            try (var video = new SequenceInputStream(Collections.enumeration(blocks))) {
                assertThatThrownBy(() -> recordingService.transferUpload(target, video)).isInstanceOfSatisfying(GocastIntegrationException.class,
                        failure -> assertThat(failure.getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
            }
            // The peer did receive the start of the request. It never drained the body or returned a response.
            try (var peer = listener.accept()) {
                peer.setSoTimeout(1000);
                assertThat(peer.getInputStream().read()).isEqualTo('P');
            }
        }
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
    @EnumSource(value = GocastBindingStatus.class, names = { "PENDING", "REVOKED" })
    void anInactiveCourseBindingDoesNotOfferUploads(GocastBindingStatus status) {
        when(connectionRepository.getBindingSnapshot(7)).thenReturn(Optional.of(binding(status)));

        assertThat(service.findUploadConnection(7)).isEmpty();
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
