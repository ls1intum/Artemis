package de.tum.cit.aet.artemis.videosource.service;

import java.net.URI;
import java.util.Base64;
import java.util.Set;
import java.util.function.Supplier;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.videosource.config.GocastConfiguration.GocastSettings;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;

@Service
@Lazy
@Conditional(GocastEnabled.class)
public class GocastConnectorService {

    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;

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
     * @param code          the single-use redeem code
     * @return the verified integration, grant, and course data
     */
    public GocastVerifiedCourseDTO redeemApproval(long integrationId, String state, String code) {
        requireId(integrationId, "integrationId");
        requireOpaque(state, "state");
        requireOpaque(code, "code");
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
}
