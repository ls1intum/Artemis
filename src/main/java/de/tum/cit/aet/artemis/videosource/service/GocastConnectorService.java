package de.tum.cit.aet.artemis.videosource.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
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

    private final GocastAuthenticationService authenticationService;

    private final URI webBaseUri;

    private final Clock clock;

    public GocastConnectorService(@Qualifier("gocastIntegrationRestClient") RestClient restClient, GocastAuthenticationService authenticationService, GocastSettings settings) {
        this(restClient, authenticationService, settings.webBaseUri(), Clock.systemUTC());
    }

    GocastConnectorService(RestClient restClient, GocastAuthenticationService authenticationService, URI webBaseUri, Clock clock) {
        this.restClient = restClient;
        this.authenticationService = authenticationService;
        this.webBaseUri = webBaseUri;
        this.clock = clock;
    }

    /**
     * Creates an approval and validates its exact trusted web URL.
     *
     * @param state       the opaque browser state
     * @param courseLabel the Artemis course label shown for approval
     * @param callbackUrl the exact Artemis callback URL
     * @return the verified approval response
     */
    public CreatedApproval createApproval(String state, String courseLabel, String callbackUrl) {
        requireOpaque(state, "state");
        if (!StringUtils.hasText(courseLabel) || courseLabel.length() > 255) {
            throw new IllegalArgumentException("courseLabel must contain at most 255 characters");
        }
        CreatedApproval response = executeAuthenticated("Could not start TUM.Live approval", authorization -> restClient.post().uri("/integration/approval-requests")
                .header(HttpHeaders.AUTHORIZATION, authorization).body(new CreateApprovalRequest(state, courseLabel, callbackUrl)).retrieve().body(CreatedApproval.class)).value();
        if (response == null || !isOpaque(response.requestId()) || response.expiresAt() == null || !response.expiresAt().isAfter(clock.instant())
                || !isAllowedApprovalUrl(response.approvalUrl(), response.requestId())) {
            throw invalidResponse("TUM.Live approval response is invalid");
        }
        return response;
    }

    /**
     * Redeems an approval and verifies the returned service account and course grant.
     *
     * @param requestId the remote request identifier
     * @param state     the opaque browser state
     * @param code      the single-use redeem code
     * @return the verified course grant
     */
    public GocastVerifiedCourseDTO redeemApproval(String requestId, String state, String code) {
        requireOpaque(requestId, "requestId");
        requireOpaque(state, "state");
        requireOpaque(code, "code");
        AuthenticatedResult<RedeemResponse> authenticated = executeAuthenticated("Could not complete TUM.Live approval",
                authorization -> restClient.post().uri("/integration/approval-requests/{requestId}/redeem", requestId).header(HttpHeaders.AUTHORIZATION, authorization)
                        .body(new RedeemRequest(state, code)).retrieve().body(RedeemResponse.class));
        RedeemResponse response = authenticated.value();
        if (response == null || !requestId.equals(response.requestId()) || !state.equals(response.state()) || response.serviceUserId() != authenticated.session().userId()) {
            throw invalidResponse("TUM.Live approval verification failed");
        }
        validateCourse(response.grantId(), response.courseId(), response.courseSlug(), response.courseName(), response.courseVisibility());
        return new GocastVerifiedCourseDTO(response.serviceUserId(), response.grantId(), response.courseId(), response.courseSlug(), response.courseName(),
                response.courseVisibility());
    }

    /**
     * Reads the status of the exact saved course grant.
     *
     * @param courseId the GoCast course identifier
     * @param grantId  the exact saved grant identifier
     * @return the verified grant status
     */
    public GrantStatus getGrantStatus(long courseId, long grantId) {
        requireId(courseId, "courseId");
        requireId(grantId, "grantId");
        GrantStatus response = executeAuthenticated("Could not check the TUM.Live connection",
                authorization -> restClient.get().uri(builder -> builder.path("/integration/courses/{courseId}/grant").queryParam("grantId", grantId).build(courseId))
                        .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(GrantStatus.class))
                .value();
        if (response == null || response.active() == null) {
            throw invalidResponse("TUM.Live connection status is invalid");
        }
        if (response.active()) {
            validateCourse(response.grantId(), response.courseId(), response.courseSlug(), response.courseName(), response.courseVisibility());
            if (response.grantId() != grantId || response.courseId() != courseId) {
                throw invalidResponse("TUM.Live connection status does not match the saved grant");
            }
        }
        return response;
    }

    /**
     * Revokes the exact saved course grant.
     *
     * @param courseId the GoCast course identifier
     * @param grantId  the exact saved grant identifier
     */
    public void revokeGrant(long courseId, long grantId) {
        requireId(courseId, "courseId");
        requireId(grantId, "grantId");
        executeAuthenticated("Could not revoke the TUM.Live connection", authorization -> {
            restClient.delete().uri(builder -> builder.path("/integration/courses/{courseId}/grant").queryParam("grantId", grantId).build(courseId))
                    .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().toBodilessEntity();
            return Boolean.TRUE;
        });
    }

    private <T> AuthenticatedResult<T> executeAuthenticated(String message, Function<String, T> operation) {
        return executeAuthenticated(authenticationService.getSession(), message, operation);
    }

    private <T> AuthenticatedResult<T> executeAuthenticated(GocastAuthenticationService.Session firstSession, String message, Function<String, T> operation) {
        try {
            return new AuthenticatedResult<>(operation.apply(firstSession.authorizationHeader()), firstSession);
        }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                throw translate(message, exception);
            }
            authenticationService.invalidate(firstSession.authorizationHeader());
            var retrySession = authenticationService.getSession();
            try {
                return new AuthenticatedResult<>(operation.apply(retrySession.authorizationHeader()), retrySession);
            }
            catch (RestClientException retryException) {
                throw translate(message, retryException);
            }
        }
        catch (RestClientException exception) {
            throw translate(message, exception);
        }
    }

    private boolean isAllowedApprovalUrl(String value, String requestId) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            URI expected = webBaseUri.resolve("/integration/approve/" + requestId);
            return uri.isAbsolute() && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null && uri.getRawPath() != null
                    && !uri.getRawPath().contains("%") && sameOrigin(webBaseUri, uri) && expected.equals(uri);
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sameOrigin(URI expected, URI actual) {
        int expectedPort = expected.getPort() >= 0 ? expected.getPort() : defaultPort(expected.getScheme());
        int actualPort = actual.getPort() >= 0 ? actual.getPort() : defaultPort(actual.getScheme());
        return expected.getScheme().equalsIgnoreCase(actual.getScheme()) && expected.getHost().equalsIgnoreCase(actual.getHost()) && expectedPort == actualPort;
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static void validateCourse(long grantId, long courseId, String slug, String name, String visibility) {
        if (!isId(grantId) || !isId(courseId) || !StringUtils.hasText(slug) || slug.length() > 255 || !StringUtils.hasText(name) || name.length() > 255 || visibility == null
                || !VISIBILITIES.contains(visibility)) {
            throw invalidResponse("TUM.Live course data is invalid");
        }
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

    private static boolean isOpaque(String value) {
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

    private record CreateApprovalRequest(String state, String courseLabel, String callbackUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatedApproval(String requestId, String approvalUrl, Instant expiresAt) {
    }

    private record RedeemRequest(String state, String code) {
    }

    private record AuthenticatedResult<T>(T value, GocastAuthenticationService.Session session) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RedeemResponse(String requestId, String state, long serviceUserId, long grantId, long courseId, String courseSlug, String courseName, String courseVisibility) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GrantStatus(Boolean active, long grantId, long courseId, String courseSlug, String courseName, String courseVisibility) {
    }
}
