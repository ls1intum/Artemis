package de.tum.cit.aet.artemis.videosource.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
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

    public GocastRecordingService(GocastConnectionRepository connectionRepository, GocastConnectorService connectorService, TumLiveService tumLiveService) {
        this.connectionRepository = connectionRepository;
        this.connectorService = connectorService;
        this.tumLiveService = tumLiveService;
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
        Optional<Long> streamId = tumLiveService.findWatchPage(videoSource).flatMap(page -> parseId(page.streamId()));
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
