package de.tum.cit.aet.artemis.videosource.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * Resolves a raw video source URL to a {@link ResolvedVideo}, identifying the video source type
 * and (for TUM Live) converting watch-page URLs to HLS playlist URLs.
 * <p>
 * Resolution priority:
 * <ol>
 * <li>If TumLiveApi is present and successfully resolves the URL → {@link VideoSourceType#TUM_LIVE}</li>
 * <li>If the URL matches a known YouTube pattern → {@link VideoSourceType#YOUTUBE}</li>
 * <li>Otherwise → type is {@code null} (unknown source)</li>
 * </ol>
 * TUM Live is checked first so that TUM Live URLs take precedence even if they technically also
 * contain YouTube-recognizable fragments.
 */
@Service
@Lazy
public class VideoSourceResolver {

    private static final Logger log = LoggerFactory.getLogger(VideoSourceResolver.class);

    private final Optional<TumLiveApi> tumLiveApi;

    private final YouTubeUrlService youTubeUrlService;

    public VideoSourceResolver(Optional<TumLiveApi> tumLiveApi, YouTubeUrlService youTubeUrlService) {
        this.tumLiveApi = tumLiveApi;
        this.youTubeUrlService = youTubeUrlService;
    }

    /**
     * Resolve a video source URL.
     *
     * @param videoSource the raw video URL; may be {@code null} or blank
     * @return a {@link ResolvedVideo} with the (possibly transformed) URL and its identified type;
     *         type is {@code null} when the source is null/blank or unrecognised
     */
    public ResolvedVideo resolve(String videoSource) {
        if (videoSource == null || videoSource.isBlank()) {
            return new ResolvedVideo(videoSource, null, null);
        }
        if (tumLiveApi.isPresent()) {
            try {
                Optional<String> resolved = tumLiveApi.get().getTumLivePlaylistLink(videoSource);
                if (resolved.isPresent()) {
                    log.info("Resolved TUM Live URL to HLS playlist for video source resolution");
                    return new ResolvedVideo(resolved.get(), VideoSourceType.TUM_LIVE, null);
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
}
