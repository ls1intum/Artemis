package de.tum.cit.aet.artemis.videosource.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.videosource.config.GocastConfiguration.GocastSettings;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.dto.GocastRecordingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadTargetDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;

@Service
@Lazy
@Conditional(GocastEnabled.class)
public class GocastConnectorService {

    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;

    private static final Pattern EXTERNAL_REF = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    private static final Pattern UPLOAD_PATH = Pattern.compile("/api/v2/integration/uploads/[A-Za-z0-9_-]{43}");

    private static final Set<String> VISIBILITIES = Set.of("public", "hidden", "loggedin", "enrolled");

    private final RestClient restClient;

    private final URI webBaseUri;

    @Autowired
    public GocastConnectorService(@Qualifier("gocastIntegrationRestClient") RestClient restClient, GocastSettings settings) {
        this(restClient, settings.webBaseUri());
    }

    GocastConnectorService(RestClient restClient, URI webBaseUri) {
        this.restClient = restClient;
        this.webBaseUri = webBaseUri;
    }

    /**
     * Reads the integration identity authenticated by the configured API key.
     *
     * @return the registered integration identity
     */
    public IntegrationIdentity getIntegration() {
        IntegrationIdentity response = execute("Could not verify the TUM.Live integration", () -> restClient.get().uri("/integration").retrieve().body(IntegrationIdentity.class));
        if (response == null || !isId(response.id()) || !validText(response.name()) || response.name().length() > 255 || !validText(response.returnUrl())
                || response.returnUrl().length() > 2_048) {
            throw invalidResponse("TUM.Live integration identity is invalid");
        }
        return response;
    }

    /**
     * Builds the trusted GoCast authorization URL for locally generated state.
     *
     * @param integrationId the registered integration identifier
     * @param state         the local opaque state
     * @return the trusted browser URL
     */
    public String authorizationUrl(long integrationId, String state) {
        requireId(integrationId, "integrationId");
        requireOpaque(state, "state");
        return webBaseUri.resolve("/integration/authorize/" + integrationId + "?state=" + state).toString();
    }

    /**
     * Redeems an authorization and verifies the exact grant metadata.
     *
     * @param integrationId the integration identity saved with the local attempt
     * @param state         the local opaque state
     * @param code          the single-use redeem code, a canonical version 4 UUID
     * @return the verified integration, grant, and course data
     */
    public GocastVerifiedCourseDTO redeemApproval(long integrationId, String state, String code) {
        requireId(integrationId, "integrationId");
        requireOpaque(state, "state");
        if (!isApprovalCode(code)) {
            throw new IllegalArgumentException("code must be a canonical version 4 UUID");
        }
        RedeemResponse redeemed = execute("Could not complete TUM.Live approval",
                () -> restClient.post().uri("/integration/authorizations/redeem").body(new RedeemRequest(code, state)).retrieve().body(RedeemResponse.class));
        if (redeemed == null || !isId(redeemed.grantId()) || !isId(redeemed.courseId())) {
            throw invalidResponse("TUM.Live approval verification failed");
        }
        GrantDetails grant = getGrant(redeemed.grantId());
        if (grant.courseId() != redeemed.courseId()) {
            throw invalidResponse("TUM.Live grant does not match the approved course");
        }
        return new GocastVerifiedCourseDTO(integrationId, redeemed.grantId(), grant.courseId(), grant.courseSlug(), grant.courseName(), grant.courseVisibility());
    }

    /**
     * Reads metadata for the exact saved grant.
     *
     * @param grantId the GoCast grant identifier
     * @return verified course metadata for the grant
     */
    public GrantDetails getGrant(long grantId) {
        requireId(grantId, "grantId");
        WireGrantDetails response = execute("Could not check the TUM.Live connection",
                () -> restClient.get().uri("/integration/grants/{grantId}", grantId).retrieve().body(WireGrantDetails.class));
        if (response == null) {
            throw invalidResponse("TUM.Live grant data is invalid");
        }
        validateCourse(response.courseId(), response.slug(), response.name(), response.visibility());
        return new GrantDetails(response.courseId(), response.slug(), response.name(), response.visibility());
    }

    /**
     * Revokes the exact saved grant for the authenticated integration.
     *
     * @param grantId the GoCast grant identifier
     */
    public void revokeGrant(long grantId) {
        requireId(grantId, "grantId");
        execute("Could not revoke the TUM.Live connection", () -> {
            var response = restClient.delete().uri("/integration/grants/{grantId}", grantId).retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new GocastIntegrationException("Could not revoke the TUM.Live connection", response.getStatusCode());
            }
            return Boolean.TRUE;
        });
    }

    /**
     * Reads the conversion state and the signed combined playlist of one lecture of the course behind the exact saved grant. GoCast refuses lectures outside that
     * course, private ones it did not upload, and deleted ones with 404.
     *
     * @param grantId  the GoCast grant identifier
     * @param streamId the GoCast lecture identifier
     * @return the recording state
     */
    public GocastRecordingDTO getRecording(long grantId, long streamId) {
        requireId(grantId, "grantId");
        requireId(streamId, "streamId");
        WireRecording response = execute("Could not read the TUM.Live recording",
                () -> restClient.get().uri("/integration/grants/{grantId}/recordings/{streamId}", grantId, streamId).retrieve().body(WireRecording.class));
        if (response == null) {
            throw invalidResponse("TUM.Live recording data is invalid");
        }
        String playlistUrl = StringUtils.hasText(response.playlistUrl()) ? response.playlistUrl() : null;
        if (playlistUrl != null && !isHttpUrl(playlistUrl)) {
            throw invalidResponse("TUM.Live recording data is invalid");
        }
        return new GocastRecordingDTO(playlistUrl, response.converting(), response.progress(), response.failed(), response.failed() ? response.error() : null);
    }

    /**
     * Reads the signed combined playlist of one lecture of the course behind the exact saved grant.
     *
     * @param grantId  the GoCast grant identifier
     * @param streamId the GoCast lecture identifier
     * @return the playlist URL, valid for seven hours, or empty while the lecture has no recording. A bearer credential: never log it.
     */
    public Optional<String> getRecordingPlaylist(long grantId, long streamId) {
        return Optional.ofNullable(getRecording(grantId, streamId).playlistUrl());
    }

    /**
     * Creates the TUM.Live lecture for one upload, or renews the upload URL of a reference whose upload has not started.
     *
     * @param grantId     the GoCast grant identifier
     * @param externalRef the reference Artemis chose for this upload
     * @param title       the lecture's title in TUM.Live
     * @return the lecture and the absolute upload URL on TUM.Live
     */
    public GocastUploadTargetDTO createUpload(long grantId, String externalRef, String title) {
        requireId(grantId, "grantId");
        requireReference(externalRef);
        WireUpload response = execute("Could not create the TUM.Live upload",
                () -> restClient.post().uri("/integration/grants/{grantId}/uploads", grantId).body(new CreateUploadRequest(externalRef, title)).retrieve().body(WireUpload.class));
        if (response == null || !isId(response.streamId()) || response.uploadPath() == null || !UPLOAD_PATH.matcher(response.uploadPath()).matches()) {
            throw invalidResponse("TUM.Live upload data is invalid");
        }
        return new GocastUploadTargetDTO(response.streamId(), webBaseUri.resolve(response.uploadPath()).toString());
    }

    /**
     * Deletes the TUM.Live lecture of an upload and closes its reference. Repeating it succeeds, also for a reference TUM.Live never saw.
     *
     * @param grantId     the GoCast grant identifier
     * @param externalRef the upload's reference
     */
    public void deleteUpload(long grantId, String externalRef) {
        requireId(grantId, "grantId");
        requireReference(externalRef);
        execute("Could not delete the TUM.Live recording", () -> {
            var response = restClient.delete().uri("/integration/grants/{grantId}/uploads/{externalRef}", grantId, externalRef).retrieve().toBodilessEntity();
            // Only a success confirms the deletion; a redirect, which is not followed, would otherwise drop the cleanup unconfirmed.
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new GocastIntegrationException("Could not delete the TUM.Live recording", response.getStatusCode());
            }
            return Boolean.TRUE;
        });
    }

    /**
     * The TUM.Live watch page of a lecture, in the form Artemis resolves as a TUM Live video.
     *
     * @param courseSlug the TUM.Live course slug
     * @param streamId   the TUM.Live lecture
     * @return the watch page URL
     */
    public String watchUrl(String courseSlug, long streamId) {
        requireId(streamId, "streamId");
        return webBaseUri.resolve("/w/" + UriUtils.encodePathSegment(courseSlug, StandardCharsets.UTF_8) + "/" + streamId).toString();
    }

    private static void requireReference(String externalRef) {
        if (externalRef == null || !EXTERNAL_REF.matcher(externalRef).matches()) {
            throw new IllegalArgumentException("externalRef must be 16 to 64 characters of A-Z, a-z, 0-9, - and _");
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("https".equals(scheme) || "http".equals(scheme)) && uri.getHost() != null && uri.getUserInfo() == null;
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static <T> T execute(String message, Supplier<T> operation) {
        try {
            return operation.get();
        }
        catch (RestClientException exception) {
            throw translate(message, exception);
        }
    }

    private static void validateCourse(long courseId, String slug, String name, String visibility) {
        if (!isId(courseId) || !validText(slug) || slug.length() > 255 || !validText(name) || name.length() > 255 || visibility == null || !VISIBILITIES.contains(visibility)) {
            throw invalidResponse("TUM.Live course data is invalid");
        }
    }

    private static boolean validText(String value) {
        return StringUtils.hasText(value);
    }

    private static void requireId(long value, String name) {
        if (!isId(value)) {
            throw new IllegalArgumentException(name + " must be a positive uint32");
        }
    }

    private static boolean isId(long value) {
        return value > 0 && value <= MAX_UNSIGNED_INT;
    }

    private static void requireOpaque(String value, String name) {
        if (!isOpaque(value)) {
            throw new IllegalArgumentException(name + " must be a 32-byte base64url value");
        }
    }

    static boolean isOpaque(String value) {
        if (!StringUtils.hasText(value) || value.contains("=")) {
            return false;
        }
        try {
            return Base64.getUrlDecoder().decode(value).length == 32;
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isApprovalCode(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID code = UUID.fromString(value);
            return code.version() == 4 && code.variant() == 2 && code.toString().equals(value);
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static GocastIntegrationException translate(String message, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return new GocastIntegrationException(message, responseException.getStatusCode(), exception);
        }
        return new GocastIntegrationException(message, exception instanceof ResourceAccessException ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY, exception);
    }

    private static GocastIntegrationException invalidResponse(String message) {
        return new GocastIntegrationException(message, HttpStatus.BAD_GATEWAY);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntegrationIdentity(long id, String name, String returnUrl) {
    }

    private record RedeemRequest(String code, String state) {

        @Override
        public String toString() {
            return "RedeemRequest[code=[REDACTED], state=[REDACTED]]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RedeemResponse(long grantId, long courseId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireGrantDetails(long courseId, String name, String slug, String visibility) {
    }

    public record GrantDetails(long courseId, String courseSlug, String courseName, String courseVisibility) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireRecording(String playlistUrl, boolean converting, int progress, boolean failed, String error) {

        @Override
        public String toString() {
            return "WireRecording[playlistUrl=[REDACTED], converting=" + converting + ", progress=" + progress + ", failed=" + failed + "]";
        }
    }

    private record CreateUploadRequest(String externalRef, String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireUpload(long streamId, String uploadPath) {

        @Override
        public String toString() {
            return "WireUpload[streamId=" + streamId + ", uploadPath=[REDACTED]]";
        }
    }
}
