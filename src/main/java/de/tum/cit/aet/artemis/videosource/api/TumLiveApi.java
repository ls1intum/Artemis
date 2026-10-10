package de.tum.cit.aet.artemis.videosource.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.videosource.config.TumLiveEnabled;
import de.tum.cit.aet.artemis.videosource.service.TumLiveService;

/**
 * API for TUM Live operations.
 * This class allows other modules to interact with the TUM Live service.
 */
@Controller
@Lazy
@Conditional(TumLiveEnabled.class)
public class TumLiveApi implements AbstractApi {

    private final TumLiveService tumLiveService;

    public TumLiveApi(TumLiveService tumLiveService) {
        this.tumLiveService = tumLiveService;
    }

    /**
     * Given a TUM Live public video URL, extracts courseSlug and streamId,
     * then fetches the playlist URL from the TUM Live API.
     *
     * @param videoUrl the public TUM Live video URL to resolve
     * @return an optional playlist URL if found from the TUM Live API, or empty if not found or the URL is invalid
     */
    public Optional<String> getTumLivePlaylistLink(String videoUrl) {
        return tumLiveService.getTumLivePlaylistLink(videoUrl);
    }

    /**
     * Whether the URL has the shape of a TUM Live watch page. No call to the TUM Live API.
     *
     * @param videoUrl the video URL to check
     * @return true if the URL names a TUM Live stream
     */
    public boolean isTumLiveUrl(String videoUrl) {
        return tumLiveService.isTumLiveUrl(videoUrl);
    }
}
