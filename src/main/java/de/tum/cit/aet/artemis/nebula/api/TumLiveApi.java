package de.tum.cit.aet.artemis.nebula.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.service.TumLiveService;

/**
 * API for TUM Live operations.
 * This class allows other modules to interact with the TUM Live service.
 */
@Conditional(NebulaEnabled.class)
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class TumLiveApi extends AbstractNebulaApi {

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
}
