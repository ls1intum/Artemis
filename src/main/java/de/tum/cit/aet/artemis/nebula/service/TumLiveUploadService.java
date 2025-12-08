package de.tum.cit.aet.artemis.nebula.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveAuthResponseDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveSSORequestDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadResponseDTO;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;

/**
 * Service for uploading videos to TUM Live.
 * Handles SSO-based authentication and video upload to the TUM Live platform.
 */
@Conditional(NebulaEnabled.class)
@Service
@Lazy
@Profile(PROFILE_CORE)
public class TumLiveUploadService {

    private static final Logger log = LoggerFactory.getLogger(TumLiveUploadService.class);

    private static final String AUTH_SSO_PATH = "/api/artemis/auth/sso";

    private static final String UPLOAD_PATH_TEMPLATE = "/api/course/artemis/%d/upload";

    private final RestTemplate restTemplate;

    private final String tumLiveBaseUrl;

    private final String artemisApiKey;

    /**
     * Constructs a new TumLiveUploadService.
     *
     * @param restTemplate   the RestTemplate for making HTTP requests
     * @param tumLiveBaseUrl the base URL of the TUM Live API
     * @param artemisApiKey  the shared secret for authenticating with TUM Live
     */
    public TumLiveUploadService(RestTemplate restTemplate, @Value("${artemis.tum-live.upload-base-url:#{null}}") String tumLiveBaseUrl,
            @Value("${artemis.tum-live.api-key:#{null}}") String artemisApiKey) {
        this.restTemplate = restTemplate;
        this.artemisApiKey = artemisApiKey;

        if (tumLiveBaseUrl == null || tumLiveBaseUrl.isBlank()) {
            log.warn("TUM Live upload base URL is not configured. Video upload to TUM Live will be disabled. "
                    + "Please set 'artemis.tum-live.upload-base-url' in your configuration.");
            this.tumLiveBaseUrl = null;
        }
        else if (artemisApiKey == null || artemisApiKey.isBlank()) {
            log.warn("TUM Live API key is not configured. Video upload to TUM Live will be disabled. " + "Please set 'artemis.tum-live.api-key' in your configuration.");
            this.tumLiveBaseUrl = null;
        }
        else {
            log.info("TUM Live upload base URL is set to '{}'", tumLiveBaseUrl);
            this.tumLiveBaseUrl = tumLiveBaseUrl;
        }
    }

    /**
     * Checks if the TUM Live upload service is configured and available.
     *
     * @return true if the service is configured, false otherwise
     */
    public boolean isConfigured() {
        return tumLiveBaseUrl != null && !tumLiveBaseUrl.isBlank();
    }

    /**
     * Authenticates a user with TUM Live using SSO (Single Sign-On).
     * This method is called after the user has been authenticated via SAML in Artemis.
     *
     * @param user the authenticated Artemis user
     * @return the authentication response containing token and available courses
     * @throws BadRequestAlertException if TUM Live upload is not configured
     */
    public TumLiveAuthResponseDTO authenticateWithSSO(User user) {
        if (!isConfigured()) {
            throw new BadRequestAlertException("TUM Live upload is not configured", "TumLiveUpload", "notConfigured");
        }

        String url = tumLiveBaseUrl + AUTH_SSO_PATH;
        log.debug("Authenticating user '{}' with TUM Live via SSO at {}", user.getLogin(), url);

        // Build SSO request with user information from Artemis
        TumLiveSSORequestDTO request = new TumLiveSSORequestDTO(user.getLogin(), // LRZ ID
                user.getRegistrationNumber(), // Matriculation number
                user.getFirstName(), user.getLastName(), user.getEmail(), artemisApiKey // Shared secret
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<TumLiveSSORequestDTO> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<TumLiveAuthResponseDTO> response = restTemplate.exchange(url, HttpMethod.POST, entity, TumLiveAuthResponseDTO.class);

            TumLiveAuthResponseDTO authResponse = response.getBody();
            if (authResponse == null) {
                log.error("TUM Live SSO authentication returned null response for user '{}'", user.getLogin());
                return new TumLiveAuthResponseDTO(false, null, null, null, null, "Authentication failed: empty response");
            }

            if (authResponse.success()) {
                log.info("Successfully authenticated user '{}' with TUM Live via SSO. Available courses: {}", user.getLogin(),
                        authResponse.courses() != null ? authResponse.courses().size() : 0);
            }
            else {
                log.warn("TUM Live SSO authentication failed for user '{}': {}", user.getLogin(), authResponse.error());
            }

            return authResponse;
        }
        catch (HttpClientErrorException.Unauthorized e) {
            log.warn("TUM Live SSO authentication failed for user '{}': Invalid API key", user.getLogin());
            return new TumLiveAuthResponseDTO(false, null, null, null, null, "Invalid Artemis API key");
        }
        catch (HttpClientErrorException.Forbidden e) {
            log.warn("TUM Live SSO authentication failed for user '{}': User not authorized", user.getLogin());
            return new TumLiveAuthResponseDTO(false, null, null, null, null, "User not authorized to upload videos");
        }
        catch (RestClientException e) {
            log.error("TUM Live SSO authentication failed for user '{}': {}", user.getLogin(), e.getMessage(), e);
            return new TumLiveAuthResponseDTO(false, null, null, null, null, "Authentication failed: " + e.getMessage());
        }
    }

    /**
     * Uploads a video file to TUM Live.
     *
     * @param token       the authentication token from a successful login
     * @param courseId    the TUM Live course ID to upload to
     * @param videoFile   the video file to upload
     * @param title       the title for the video
     * @param description the description for the video (optional)
     * @return the upload response
     * @throws BadRequestAlertException if TUM Live upload is not configured
     */
    public TumLiveUploadResponseDTO uploadVideo(String token, Long courseId, File videoFile, String title, String description) {
        if (!isConfigured()) {
            throw new BadRequestAlertException("TUM Live upload is not configured", "TumLiveUpload", "notConfigured");
        }

        String url = buildUploadUrl(courseId, title, description);
        log.info("Uploading video '{}' to TUM Live course {} at {}", title, courseId, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(videoFile));

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully uploaded video '{}' to TUM Live course {}", title, courseId);
                return TumLiveUploadResponseDTO.success("Video uploaded successfully to TUM Live");
            }
            else {
                log.error("TUM Live upload failed with status {}: {}", response.getStatusCode(), response.getBody());
                return TumLiveUploadResponseDTO.failure("Upload failed with status: " + response.getStatusCode());
            }
        }
        catch (HttpClientErrorException.Unauthorized e) {
            log.warn("TUM Live upload failed: Token expired or invalid");
            return TumLiveUploadResponseDTO.failure("Authentication token expired. Please re-authenticate.");
        }
        catch (HttpClientErrorException.Forbidden e) {
            log.warn("TUM Live upload failed: User not authorized for course {}", courseId);
            return TumLiveUploadResponseDTO.failure("User not authorized to upload to this course");
        }
        catch (RestClientException e) {
            log.error("TUM Live upload failed: {}", e.getMessage(), e);
            return TumLiveUploadResponseDTO.failure("Upload failed: " + e.getMessage());
        }
    }

    /**
     * Uploads a video from a MultipartFile to TUM Live.
     * This method handles the temporary file creation and cleanup.
     *
     * @param token       the authentication token from a successful login
     * @param courseId    the TUM Live course ID to upload to
     * @param videoFile   the multipart video file to upload
     * @param title       the title for the video
     * @param description the description for the video (optional)
     * @return the upload response
     * @throws IOException if temporary file creation fails
     */
    public TumLiveUploadResponseDTO uploadVideo(String token, Long courseId, MultipartFile videoFile, String title, String description) throws IOException {
        File tempFile = null;
        try {
            // Create temporary file with appropriate extension
            String originalFilename = videoFile.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".mp4";

            tempFile = File.createTempFile("artemis-tumlive-upload-", extension);
            videoFile.transferTo(tempFile);

            return uploadVideo(token, courseId, tempFile, title, description);
        }
        finally {
            // Clean up temporary file
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Builds the upload URL with query parameters.
     *
     * @param courseId    the course ID
     * @param title       the video title
     * @param description the video description (optional)
     * @return the complete upload URL
     */
    private String buildUploadUrl(Long courseId, String title, String description) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(tumLiveBaseUrl);
        urlBuilder.append(String.format(UPLOAD_PATH_TEMPLATE, courseId));
        urlBuilder.append("?title=").append(urlEncode(title));

        if (description != null && !description.isBlank()) {
            urlBuilder.append("&description=").append(urlEncode(description));
        }

        return urlBuilder.toString();
    }

    /**
     * URL-encodes a string value.
     *
     * @param value the value to encode
     * @return the URL-encoded value
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        }
        catch (UnsupportedEncodingException e) {
            // UTF-8 is always supported
            return value;
        }
    }
}
