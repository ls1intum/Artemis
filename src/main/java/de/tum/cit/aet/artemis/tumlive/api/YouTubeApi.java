package de.tum.cit.aet.artemis.tumlive.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.tumlive.service.YouTubeService;

/**
 * API for YouTube operations.
 * This class allows other modules to interact with the YouTube service.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class YouTubeApi implements AbstractApi {

    private final YouTubeService youTubeService;

    public YouTubeApi(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }

    /**
     * Extracts the YouTube video ID from a given URL.
     *
     * @param videoUrl the URL to extract the video ID from
     * @return an optional containing the 11-character video ID, or empty if not a YouTube URL
     */
    public Optional<String> extractYouTubeVideoId(String videoUrl) {
        return youTubeService.extractYouTubeVideoId(videoUrl);
    }

    /**
     * Checks whether the given URL is a recognized YouTube video URL.
     *
     * @param videoUrl the URL to check
     * @return true if the URL is a YouTube video URL
     */
    public boolean isYouTubeUrl(String videoUrl) {
        return youTubeService.isYouTubeUrl(videoUrl);
    }
}
