package de.tum.cit.aet.artemis.nebula.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.service.YouTubeService;

/**
 * API for YouTube operations.
 * This class allows other modules to interact with the YouTube service.
 */
@Conditional(NebulaEnabled.class)
@Controller
@Lazy
public class YouTubeApi extends AbstractNebulaApi {

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
