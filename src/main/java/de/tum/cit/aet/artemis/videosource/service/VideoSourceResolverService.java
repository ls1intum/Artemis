package de.tum.cit.aet.artemis.videosource.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.videosource.api.GocastBindingApi;
import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * Resolves a raw video source URL to a {@link ResolvedVideo}, identifying the video source type
 * and (for TUM Live) converting watch-page URLs to HLS playlist URLs.
 * <p>
 * Resolution priority:
 * <ol>
 * <li>If the course is connected to TUM.Live and the connection hands out the lecture → {@link VideoSourceType#TUM_LIVE}, also for a course that is not
 * public</li>
 * <li>If TumLiveApi is present and successfully resolves the URL through the public API → {@link VideoSourceType#TUM_LIVE}</li>
 * <li>If the URL matches a known YouTube pattern → {@link VideoSourceType#YOUTUBE}</li>
 * <li>Otherwise → type is {@code null} (unknown source)</li>
 * </ol>
 * TUM Live is checked first so that TUM Live URLs take precedence even if they technically also
 * contain YouTube-recognizable fragments.
 */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class VideoSourceResolverService {

    private static final Logger log = LoggerFactory.getLogger(VideoSourceResolverService.class);

    private final Optional<TumLiveApi> tumLiveApi;

    private final Optional<GocastBindingApi> gocastBindingApi;

    private final YouTubeUrlService youTubeUrlService;

    public VideoSourceResolverService(Optional<TumLiveApi> tumLiveApi, Optional<GocastBindingApi> gocastBindingApi, YouTubeUrlService youTubeUrlService) {
        this.tumLiveApi = tumLiveApi;
        this.gocastBindingApi = gocastBindingApi;
        this.youTubeUrlService = youTubeUrlService;
    }

    /**
     * Whether a video source is one Iris can transcribe: a YouTube URL, or a TUM Live watch page when the TUM Live integration is configured. Decided from the
     * URL alone, without a network call, so the answer does not change while TUM Live is unreachable; {@link #resolve} is what turns a supported source into a
     * playable URL.
     *
     * @param videoSource the raw video URL; may be {@code null} or blank
     * @return true if the source is supported
     */
    public boolean isSupportedSource(String videoSource) {
        if (videoSource == null || videoSource.isBlank()) {
            return false;
        }
        return (tumLiveApi.isPresent() && tumLiveApi.get().isTumLiveUrl(videoSource)) || youTubeUrlService.isYouTubeUrl(videoSource);
    }

    /**
     * Resolve the video source URL of a lecture unit. The resolved TUM Live playlist is signed and valid for seven hours, so it is resolved again for every use
     * and never cached or logged.
     *
     * @param videoSource the raw video URL; may be {@code null} or blank
     * @param courseId    the Artemis course of the lecture unit, whose TUM.Live connection may hand out a lecture that is not public
     * @return a {@link ResolvedVideo} with the (possibly transformed) URL and its identified type;
     *         type is {@code null} when the source is null/blank or unrecognised
     */
    public ResolvedVideo resolve(String videoSource, long courseId) {
        if (videoSource == null || videoSource.isBlank()) {
            return new ResolvedVideo(videoSource, null, null);
        }
        if (tumLiveApi.isPresent()) {
            try {
                TumLivePlaylistLookup lookup = lookUpTumLivePlaylist(videoSource, courseId);
                if (lookup.outcome() == TumLivePlaylistLookup.Outcome.RESOLVED) {
                    log.info("Resolved TUM Live URL to HLS playlist for video source resolution");
                    return new ResolvedVideo(lookup.playlistUrl(), VideoSourceType.TUM_LIVE, null);
                }
                if (lookup.outcome() != TumLivePlaylistLookup.Outcome.NOT_APPLICABLE) {
                    log.info("TUM Live video source cannot be resolved right now: {}", lookup.outcome());
                }
            }
            catch (RuntimeException e) {
                log.warn("TUM Live resolution failed; falling back to raw URL", e);
                return new ResolvedVideo(videoSource, null, null);
            }
        }
        if (youTubeUrlService.isYouTubeUrl(videoSource)) {
            String youtubeVideoId = youTubeUrlService.extractYouTubeVideoId(videoSource).orElse(null);
            return new ResolvedVideo(videoSource, VideoSourceType.YOUTUBE, youtubeVideoId);
        }
        return new ResolvedVideo(videoSource, null, null);
    }

    private TumLivePlaylistLookup lookUpTumLivePlaylist(String videoSource, long courseId) {
        Optional<TumLivePlaylistLookup> connected = gocastBindingApi.flatMap(api -> api.lookUpConnectedPlaylist(courseId, videoSource));
        return connected.orElseGet(() -> tumLiveApi.orElseThrow().lookUpPlaylist(videoSource));
    }
}
