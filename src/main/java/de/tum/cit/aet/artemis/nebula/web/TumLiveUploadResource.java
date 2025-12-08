package de.tum.cit.aet.artemis.nebula.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveAuthResponseDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadResponseDTO;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.service.TumLiveUploadService;

/**
 * REST controller for uploading videos to TUM Live.
 * Provides endpoints for SSO-based authentication and video upload to the TUM Live platform.
 */
@Conditional(NebulaEnabled.class)
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/tumlive/")
public class TumLiveUploadResource {

    private static final Logger log = LoggerFactory.getLogger(TumLiveUploadResource.class);

    private final TumLiveUploadService tumLiveUploadService;

    private final UserRepository userRepository;

    public TumLiveUploadResource(TumLiveUploadService tumLiveUploadService, UserRepository userRepository) {
        this.tumLiveUploadService = tumLiveUploadService;
        this.userRepository = userRepository;
    }

    /**
     * GET /status : Check if TUM Live upload is configured and available.
     *
     * @return ResponseEntity with status 200 (OK) if configured, 503 (Service Unavailable) if not
     */
    @GetMapping("status")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> getStatus() {
        if (tumLiveUploadService.isConfigured()) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /**
     * POST /auth : Authenticate with TUM Live using SSO and get available courses.
     * The user must already be authenticated in Artemis (via SAML or other means).
     *
     * @return ResponseEntity with authentication response including token and courses
     */
    @PostMapping("auth")
    @EnforceAtLeastInstructor
    public ResponseEntity<TumLiveAuthResponseDTO> authenticate() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.info("Received TUM Live SSO authentication request for user '{}'", user.getLogin());

        TumLiveAuthResponseDTO response = tumLiveUploadService.authenticateWithSSO(user);

        if (response.success()) {
            return ResponseEntity.ok(response);
        }
        else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * POST /upload : Upload a video to TUM Live.
     *
     * @param token       the authentication token from a successful login
     * @param courseId    the TUM Live course ID to upload to
     * @param title       the title for the video
     * @param description the description for the video (optional)
     * @param video       the video file to upload
     * @return ResponseEntity with upload result
     */
    @PostMapping("upload")
    @EnforceAtLeastInstructor
    public ResponseEntity<TumLiveUploadResponseDTO> uploadVideo(@RequestParam String token, @RequestParam Long courseId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam MultipartFile video) {

        log.info("Received TUM Live upload request for course {} with title '{}'", courseId, title);

        if (video.isEmpty()) {
            return ResponseEntity.badRequest().body(TumLiveUploadResponseDTO.failure("Video file is empty"));
        }

        // Validate file type
        String contentType = video.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().body(TumLiveUploadResponseDTO.failure("Invalid file type. Only video files are accepted."));
        }

        try {
            TumLiveUploadResponseDTO response = tumLiveUploadService.uploadVideo(token, courseId, video, title, description);

            if (response.success()) {
                return ResponseEntity.ok(response);
            }
            else {
                // Determine appropriate HTTP status based on error
                if (response.error() != null && response.error().contains("expired")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                }
                else if (response.error() != null && response.error().contains("not authorized")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }
        catch (IOException e) {
            log.error("Failed to process video file for TUM Live upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(TumLiveUploadResponseDTO.failure("Failed to process video file: " + e.getMessage()));
        }
    }
}
