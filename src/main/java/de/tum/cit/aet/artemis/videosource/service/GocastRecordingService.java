package de.tum.cit.aet.artemis.videosource.service;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastRecordingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadConnectionDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadTargetDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;

/**
 * Reads recordings of a connected TUM.Live course through the course's grant, so lectures of courses that are not public can be played and processed.
 */
@Service
@Lazy
@Conditional(GocastEnabled.class)
public class GocastRecordingService {

    private static final Logger log = LoggerFactory.getLogger(GocastRecordingService.class);

    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;

    private final GocastConnectionRepository connectionRepository;

    private final GocastConnectorService connectorService;

    private final TumLiveService tumLiveService;

    private final RestClient uploadRestClient;

    public GocastRecordingService(GocastConnectionRepository connectionRepository, GocastConnectorService connectorService, TumLiveService tumLiveService,
            @Qualifier("gocastUploadRestClient") RestClient uploadRestClient) {
        this.connectionRepository = connectionRepository;
        this.connectorService = connectorService;
        this.tumLiveService = tumLiveService;
        this.uploadRestClient = uploadRestClient;
    }

    /**
     * Looks up the playlist of a TUM Live watch page through the grant of the Artemis course the video belongs to. The grant comes from the course, never from
     * the URL; TUM.Live hands out only lectures of the granted course that students may see.
     *
     * @param courseId    the Artemis course of the lecture unit
     * @param videoSource the unit's video URL
     * @return the lookup, or empty when the course is not connected, the URL is not a watch page, or TUM.Live does not hand out this lecture through the grant
     *         (another course, a private or deleted lecture, a revoked grant); the caller then uses the public lookup
     */
    public Optional<TumLivePlaylistLookup> lookUpConnectedPlaylist(long courseId, String videoSource) {
        Optional<Long> streamId = findStreamId(videoSource);
        if (streamId.isEmpty()) {
            return Optional.empty();
        }
        var binding = connectionRepository.getBindingSnapshot(courseId).filter(snapshot -> snapshot.status() == GocastBindingStatus.ACTIVE);
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(connectorService.getRecordingPlaylist(binding.get().grantId(), streamId.get()).map(TumLivePlaylistLookup::resolved)
                    .orElseGet(() -> TumLivePlaylistLookup.failed(TumLivePlaylistLookup.Outcome.NOT_AVAILABLE)));
        }
        catch (GocastIntegrationException exception) {
            HttpStatus status = HttpStatus.resolve(exception.getUpstreamStatus().value());
            if (status == HttpStatus.NOT_FOUND || status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                // Not this grant's lecture, or the grant or key is no longer valid: the connection page reports the latter
                log.info("TUM.Live does not hand out stream {} through the grant of course {}: {}", streamId.get(), courseId, status);
                return Optional.empty();
            }
            log.warn("Could not read stream {} through the grant of course {}: {}", streamId.get(), courseId, exception.getUpstreamStatus());
            return Optional.of(TumLivePlaylistLookup.failed(TumLivePlaylistLookup.Outcome.TEMPORARILY_UNAVAILABLE));
        }
    }

    /**
     * The active connection of a course, as uploads need it.
     *
     * @param courseId the Artemis course
     * @return the connection, or empty if the course is not connected
     */
    public Optional<GocastUploadConnectionDTO> findUploadConnection(long courseId) {
        return connectionRepository.getBindingSnapshot(courseId).filter(snapshot -> snapshot.status() == GocastBindingStatus.ACTIVE)
                .map(snapshot -> new GocastUploadConnectionDTO(snapshot.integrationId(), snapshot.grantId(), snapshot.courseSlug()));
    }

    public GocastUploadTargetDTO createUpload(long grantId, String externalRef, String title) {
        return connectorService.createUpload(grantId, externalRef, title);
    }

    /**
     * Streams the resource in GoCast's multipart format. The one-use target was issued and validated by the connector.
     *
     * @param target the server-issued TUM.Live target
     * @param file   the incoming video stream
     */
    public void transferUpload(GocastUploadTargetDTO target, InputStream file) {
        var body = new MultipartBodyBuilder();
        // The runner probes the content. A fixed filename also keeps browser data out of multipart headers.
        body.part("file", new InputStreamResource(file), MediaType.APPLICATION_OCTET_STREAM).filename("video");
        try {
            uploadRestClient.post().uri(URI.create(target.uploadUrl())).contentType(MediaType.MULTIPART_FORM_DATA).body(body.build()).exchange((request, response) -> {
                var status = response.getStatusCode();
                if (!status.is2xxSuccessful()) {
                    throw new GocastIntegrationException("TUM.Live refused the upload (HTTP " + status.value() + ").", status);
                }
                return null;
            });
        }
        catch (RestClientException exception) {
            // The HTTP client's exception can contain the one-use ticket or a response from an intermediary.
            throw new GocastIntegrationException("Could not transfer the video to TUM.Live.", HttpStatus.BAD_GATEWAY);
        }
    }

    public GocastRecordingDTO getRecording(long grantId, long streamId) {
        return connectorService.getRecording(grantId, streamId);
    }

    public void deleteUpload(long grantId, String externalRef) {
        connectorService.deleteUpload(grantId, externalRef);
    }

    public String watchUrl(String courseSlug, long streamId) {
        return connectorService.watchUrl(courseSlug, streamId);
    }

    /**
     * The TUM.Live lecture a video URL shows, without calling TUM.Live.
     *
     * @param videoSource the video URL
     * @return the lecture, or empty if the URL is not a TUM Live watch page
     */
    public Optional<Long> findStreamId(String videoSource) {
        return videoSource == null ? Optional.empty() : tumLiveService.findWatchPage(videoSource).flatMap(page -> parseId(page.streamId()));
    }

    private static Optional<Long> parseId(String digits) {
        try {
            long id = Long.parseLong(digits);
            return id > 0 && id <= MAX_UNSIGNED_INT ? Optional.of(id) : Optional.empty();
        }
        catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
