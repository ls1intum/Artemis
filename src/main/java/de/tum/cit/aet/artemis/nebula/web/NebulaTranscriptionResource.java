package de.tum.cit.aet.artemis.nebula.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.nebula.service.TumLiveService;

/**
 * REST controller for managing Nebula-powered lecture transcriptions and related utilities.
 */
@Conditional(NebulaEnabled.class)
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/nebula/")
public class NebulaTranscriptionResource {

    private static final Logger log = LoggerFactory.getLogger(NebulaTranscriptionResource.class);

    private final LectureTranscriptionService lectureTranscriptionService;

    private final TumLiveService tumLiveService;

    public NebulaTranscriptionResource(LectureTranscriptionService lectureTranscriptionService, TumLiveService tumLiveService) {
        this.lectureTranscriptionService = lectureTranscriptionService;
        this.tumLiveService = tumLiveService;
    }

    /**
     * POST /lecture/{lectureId}/lecture-unit/{lectureUnitId}/transcriber :
     * Start a transcription job with Nebula and create a placeholder transcription entry.
     *
     * @param lectureId     the ID of the lecture
     * @param lectureUnitId the ID of the lecture unit
     * @param request       the request containing the video URL and any additional options
     * @return the ResponseEntity with status 200 (OK) if transcription started successfully
     */
    @PostMapping("{lectureId}/lecture-unit/{lectureUnitId}/transcriber")
    @EnforceAtLeastEditorInLectureUnit
    public ResponseEntity<Void> startNebulaTranscription(@PathVariable Long lectureId, @PathVariable Long lectureUnitId,
            @RequestBody @Valid NebulaTranscriptionRequestDTO request) {

        lectureTranscriptionService.startNebulaTranscription(lectureId, lectureUnitId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /video-utils/tum-live-playlist : Fetch TUM Live playlist URL for transcription.
     *
     * @param url the TUM Live video URL to fetch the playlist for
     * @return the ResponseEntity with status 200 (OK) and playlist URL if found, 404 (Not Found) if no playlist exists, or 400 (Bad Request) for invalid input
     */
    @GetMapping("video-utils/tum-live-playlist")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getTumLivePlaylist(@RequestParam @NotBlank String url) {
        log.info("Received request to fetch playlist for TUM Live URL");

        // Basic URL validation to match Javadoc promise of 400 for invalid input
        if (url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Optional<String> playlistUrl = tumLiveService.getTumLivePlaylistLink(url);

        if (playlistUrl.isPresent()) {
            // Log only the host for security, avoid exposing full URLs
            try {
                String host = URI.create(url).getHost();
                log.debug("Playlist URL found for host: {}", host);
            }
            catch (Exception e) {
                log.debug("Playlist URL found for request");
            }
            return ResponseEntity.ok(playlistUrl.get());
        }
        else {
            log.warn("No playlist URL found for request");
            return ResponseEntity.notFound().build();
        }
    }
}
