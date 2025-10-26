package de.tum.cit.aet.artemis.nebula.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.dto.VideoUploadResponseDTO;
import de.tum.cit.aet.artemis.nebula.exception.NebulaException;

/**
 * Service for managing video storage through the Nebula Video Storage Service.
 * Provides functionality for uploading, deleting, and streaming videos in HLS format.
 */
@Conditional(NebulaEnabled.class)
@Service
@Lazy
@Profile(PROFILE_CORE)
public class VideoStorageService {

    private static final Logger log = LoggerFactory.getLogger(VideoStorageService.class);

    private final RestTemplate restTemplate;

    private final NebulaConnectionService nebulaConnectionService;

    private final String nebulaUrl;

    private final String nebulaSecretToken;

    private static final String VIDEO_STORAGE_BASE_PATH = "/video-storage";

    private static final long MAX_VIDEO_SIZE = 524288000L; // 500 MB in bytes

    private static final String ALLOWED_VIDEO_FORMAT = ".mp4";

    private static final String ALLOWED_MIME_TYPE = "video/mp4";

    public VideoStorageService(@Qualifier("nebulaRestTemplate") RestTemplate restTemplate, NebulaConnectionService nebulaConnectionService,
            @Value("${artemis.nebula.url}") String nebulaUrl, @Value("${artemis.nebula.secret-token}") String nebulaSecretToken) {
        this.restTemplate = restTemplate;
        this.nebulaConnectionService = nebulaConnectionService;
        this.nebulaUrl = nebulaUrl;
        this.nebulaSecretToken = nebulaSecretToken;
    }

    /**
     * Uploads a video file to the Nebula Video Storage Service.
     * The service will automatically convert the video to HLS format with multiple quality levels.
     *
     * @param file the video file to upload
     * @return VideoUploadResponseDTO containing video metadata and streaming URL
     * @throws ResponseStatusException if the upload fails or file is invalid
     */
    public VideoUploadResponseDTO uploadVideo(MultipartFile file) {
        log.info("=== VIDEO UPLOAD START ===");
        log.info("Uploading video file: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());
        log.info("Nebula URL: {}, Upload endpoint: {}{}/upload", nebulaUrl, nebulaUrl, VIDEO_STORAGE_BASE_PATH);

        // Validate file
        try {
            validateVideoFile(file);
            log.info("Video file validation passed");
        }
        catch (Exception e) {
            log.error("Video file validation failed: {}", e.getMessage(), e);
            throw e;
        }

        try {
            // Prepare multipart request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", nebulaSecretToken);
            log.info("Request headers prepared. Authorization token length: {}", nebulaSecretToken != null ? nebulaSecretToken.length() : 0);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {

                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            log.info("Request body prepared with file: {}", file.getOriginalFilename());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String url = nebulaUrl + VIDEO_STORAGE_BASE_PATH + "/upload";
            log.info("Sending POST request to: {}", url);

            ResponseEntity<VideoUploadResponseDTO> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, VideoUploadResponseDTO.class);
            log.info("Received response from Nebula. Status: {}", response.getStatusCode());

            VideoUploadResponseDTO responseBody = response.getBody();
            if (responseBody == null) {
                log.error("Nebula Video Storage returned null response body for file: {}", file.getOriginalFilename());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Video storage service did not return a valid response");
            }

            log.info("Video uploaded successfully. Video ID: {}, Duration: {} seconds, Playlist URL: {}", responseBody.videoId(), responseBody.durationSeconds(),
                    responseBody.playlistUrl());
            log.info("=== VIDEO UPLOAD SUCCESS ===");
            return responseBody;
        }
        catch (HttpStatusCodeException e) {
            log.error("=== VIDEO UPLOAD FAILED (HTTP ERROR) ===");
            log.error("HTTP Status: {}", e.getStatusCode());
            log.error("Response Body: {}", e.getResponseBodyAsString());
            log.error("Exception: ", e);
            NebulaException nebulaException = nebulaConnectionService.toNebulaException(e);
            throw new ResponseStatusException(e.getStatusCode(), "Failed to upload video: " + nebulaException.getMessage(), nebulaException);
        }
        catch (IOException e) {
            log.error("=== VIDEO UPLOAD FAILED (IO ERROR) ===");
            log.error("IO Error while reading file: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read video file: " + e.getMessage(), e);
        }
        catch (Exception e) {
            log.error("=== VIDEO UPLOAD FAILED (UNEXPECTED ERROR) ===");
            log.error("Error type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            log.error("Full stack trace: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload video: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a video from the Nebula Video Storage Service.
     *
     * @param videoId the ID of the video to delete
     * @throws ResponseStatusException if the deletion fails
     */
    public void deleteVideo(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            log.warn("Attempted to delete video with null or empty videoId");
            return;
        }

        log.info("Deleting video with ID: {}", videoId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", nebulaSecretToken);
            HttpEntity<?> requestEntity = new HttpEntity<>(headers);

            String url = nebulaUrl + VIDEO_STORAGE_BASE_PATH + "/delete/" + videoId;
            restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);

            log.info("Video deleted successfully. Video ID: {}", videoId);
        }
        catch (HttpStatusCodeException e) {
            // Log but don't throw exception for 404 - video might already be deleted
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Video not found in storage service (might already be deleted). Video ID: {}", videoId);
            }
            else {
                log.error("HTTP error while deleting video {}: {} - {}", videoId, e.getStatusCode(), e.getResponseBodyAsString());
                NebulaException nebulaException = nebulaConnectionService.toNebulaException(e);
                throw new ResponseStatusException(e.getStatusCode(), "Failed to delete video: " + nebulaException.getMessage(), nebulaException);
            }
        }
        catch (Exception e) {
            log.error("Unexpected error while deleting video {}: {}", videoId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete video: " + e.getMessage(), e);
        }
    }

    /**
     * Generates the HLS streaming URL for a video.
     *
     * @param videoId the ID of the video
     * @return the URL to the HLS playlist file
     */
    public String getVideoStreamingUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("Video ID cannot be null or empty");
        }
        return nebulaUrl + VIDEO_STORAGE_BASE_PATH + "/playlist/" + videoId + "/playlist.m3u8";
    }

    /**
     * Validates the uploaded video file.
     *
     * @param file the file to validate
     * @throws ResponseStatusException if validation fails
     */
    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is required and cannot be empty");
        }

        // Check file size (500 MB)
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file size exceeds maximum allowed size of 500 MB");
        }

        // Check content type - only MP4 allowed
        String contentType = file.getContentType();
        if (!ALLOWED_MIME_TYPE.equals(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only MP4 video files are allowed. Received content type: " + contentType);
        }

        // Check file extension - only .mp4 allowed
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(ALLOWED_VIDEO_FORMAT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .mp4 video files are allowed");
        }
    }
}
