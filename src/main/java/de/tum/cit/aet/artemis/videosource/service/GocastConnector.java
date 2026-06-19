package de.tum.cit.aet.artemis.videosource.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.dto.GocastCourseDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastPlaybackTokenDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastStreamDTO;

/**
 * Connector for the gocast (TUM Live) service-account integration API.
 * <p>
 * Implements the four integration endpoints required by Phase 1:
 * <ul>
 * <li>EP1 — {@code GET /integration/users/{lrzId}/administered-courses} (with OBO)</li>
 * <li>EP8 — {@code GET /integration/courses/{courseId}/streams} (bearer only)</li>
 * <li>EP2 — {@code POST /integration/courses/{courseId}/streams/{streamId}/playback-token} (with OBO)</li>
 * <li>EP7 — {@code GET /integration/courses/{courseId}/binding-status} (bearer only)</li>
 * </ul>
 * <p>
 * The service-account bearer token is the same for every call and injected once at construction time.
 * The {@code X-On-Behalf-Of} header (carrying the acting user's LRZ ID) varies per call and is set
 * per-request where required.
 * <p>
 * Errors from the upstream gocast server are translated into typed {@link GocastIntegrationException}:
 * <ul>
 * <li>HTTP-status errors ({@link RestClientResponseException}) carry the real upstream status, so callers
 * can distinguish e.g. a {@code 404} (unknown OBO user → fall back to public path) from a {@code 403}
 * (binding revoked → mark binding as REVOKED).</li>
 * <li>Transport/I-O failures ({@link RestClientException} without a response, e.g. connection refused, DNS
 * failure, read timeout) are mapped to a synthetic {@link HttpStatus#SERVICE_UNAVAILABLE} with the cause
 * preserved, so a network outage never escapes as a raw {@link RestClientException}.</li>
 * </ul>
 * <p>
 * This bean is only created when the gocast integration is fully configured; see {@link GocastEnabled}.
 */
@Service
@Lazy
@Conditional(GocastEnabled.class)
public class GocastConnector {

    private static final Logger log = LoggerFactory.getLogger(GocastConnector.class);

    private static final String HEADER_ON_BEHALF_OF = "X-On-Behalf-Of";

    private final RestClient restClient;

    private final String bearerToken;

    /**
     * Constructs a {@link GocastConnector} using the provided pre-configured {@link RestClient} bean
     * and the service-account bearer token.
     * <p>
     * The {@link RestClient} bean is qualified as {@code gocastIntegrationRestClient} and is
     * pre-configured with the gocast API base URL by {@code GocastConfiguration}. The bearer token
     * is the same for every call, so the full {@code Authorization} header value is precomputed once here.
     *
     * @param restClient the gocast integration {@link RestClient} (pre-configured with the base URL)
     * @param token      the service-account bearer token from {@code artemis.tum-live.service-account-token}
     */
    public GocastConnector(@Qualifier("gocastIntegrationRestClient") RestClient restClient, @Value("${artemis.tum-live.service-account-token}") String token) {
        this.restClient = restClient;
        this.bearerToken = "Bearer " + token;
    }

    /**
     * EP1 — Lists the TUM Live courses directly administered by the given user.
     * <p>
     * Sends {@code GET /integration/users/{lrzId}/administered-courses?year=&term=} with
     * {@code Authorization: Bearer <token>} and {@code X-On-Behalf-Of: <lrzId>}.
     *
     * @param lrzId the TUM LRZ ID of the user whose administered courses are requested
     * @param year  the academic year (e.g. {@code 2026})
     * @param term  the teaching term (e.g. {@code "W"} or {@code "S"})
     * @return list of administered courses; empty list if the user has none
     * @throws GocastIntegrationException if gocast returns an error (carries upstream HTTP status)
     */
    public List<GocastCourseDTO> listAdministeredCourses(String lrzId, int year, String term) {
        log.debug("EP1 listAdministeredCourses: lrzId={}, year={}, term={}", lrzId, year, term);
        try {
            GocastCourseDTO[] result = restClient.get()
                    .uri(b -> b.path("/integration/users/{lrzId}/administered-courses").queryParam("year", year).queryParam("term", term).build(lrzId))
                    .header(HttpHeaders.AUTHORIZATION, bearerToken).header(HEADER_ON_BEHALF_OF, lrzId).retrieve().body(GocastCourseDTO[].class);
            return result != null ? List.of(result) : List.of();
        }
        catch (RestClientException ex) {
            throw translate("EP1 listAdministeredCourses failed for lrzId=" + lrzId, ex);
        }
    }

    /**
     * EP8 — Lists the streams of a gocast course.
     * <p>
     * Sends {@code GET /integration/courses/{courseId}/streams} with bearer auth only (no OBO).
     * The service account must be a course admin for the given course.
     *
     * @param gocastCourseId the gocast course ID
     * @return list of streams; empty list if the course has none
     * @throws GocastIntegrationException if gocast returns an error (carries upstream HTTP status)
     */
    public List<GocastStreamDTO> listCourseStreams(long gocastCourseId) {
        log.debug("EP8 listCourseStreams: courseId={}", gocastCourseId);
        try {
            GocastStreamDTO[] result = restClient.get().uri("/integration/courses/{courseId}/streams", gocastCourseId).header(HttpHeaders.AUTHORIZATION, bearerToken).retrieve()
                    .body(GocastStreamDTO[].class);
            return result != null ? List.of(result) : List.of();
        }
        catch (RestClientException ex) {
            throw translate("EP8 listCourseStreams failed for courseId=" + gocastCourseId, ex);
        }
    }

    /**
     * EP2 — Obtains a signed playback token for a specific stream on behalf of the given user.
     * <p>
     * Sends {@code POST /integration/courses/{courseId}/streams/{streamId}/playback-token} with
     * {@code Authorization: Bearer <token>} and {@code X-On-Behalf-Of: <oboLrzId>}.
     * <p>
     * gocast independently verifies that (a) the service account is a course admin of the given course,
     * and (b) the on-behalf-of user is eligible to watch the stream. Only then does gocast sign the
     * playlist URLs in the OBO user's context.
     *
     * @param gocastCourseId the gocast course ID (course-scoped so EP2 auth can check admin binding)
     * @param streamId       the gocast stream ID
     * @param ttlSeconds     requested token TTL in seconds (gocast clamps to its configured bounds)
     * @param oboLrzId       the LRZ ID of the user on whose behalf the token is requested
     * @return signed playlist URLs and the effective {@code expiresIn} value
     * @throws GocastIntegrationException if gocast returns an error (carries upstream HTTP status)
     */
    public GocastPlaybackTokenDTO getPlaybackToken(long gocastCourseId, long streamId, int ttlSeconds, String oboLrzId) {
        log.debug("EP2 getPlaybackToken: courseId={}, streamId={}, ttl={}, obo={}", gocastCourseId, streamId, ttlSeconds, oboLrzId);
        try {
            GocastPlaybackTokenDTO result = restClient.post().uri("/integration/courses/{courseId}/streams/{streamId}/playback-token", gocastCourseId, streamId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken).header(HEADER_ON_BEHALF_OF, oboLrzId).body(Map.of("ttlSeconds", ttlSeconds)).retrieve()
                    .body(GocastPlaybackTokenDTO.class);
            if (result == null) {
                // A 2xx with an empty body would otherwise return null for a @NonNull signature.
                throw new GocastIntegrationException("EP2 getPlaybackToken returned an empty body for courseId=" + gocastCourseId + ", streamId=" + streamId,
                        HttpStatus.BAD_GATEWAY);
            }
            return result;
        }
        catch (RestClientException ex) {
            throw translate("EP2 getPlaybackToken failed for courseId=" + gocastCourseId + ", streamId=" + streamId, ex);
        }
    }

    /**
     * EP7 — Verifies server-to-server whether the service account is currently bound to the given course.
     * <p>
     * Sends {@code GET /integration/courses/{courseId}/binding-status} with bearer auth only.
     * Returns {@code true} iff gocast reports that the service account is a course admin.
     * <p>
     * Artemis must call this endpoint after a binding approval redirect (rather than trusting the
     * redirect alone) to flip a {@code PENDING} binding to {@code ACTIVE}.
     *
     * @param gocastCourseId the gocast course ID to check
     * @return {@code true} if the service account is an admin of the course (binding is active)
     * @throws GocastIntegrationException if gocast returns an error (carries upstream HTTP status)
     */
    public boolean getBindingStatus(long gocastCourseId) {
        log.debug("EP7 getBindingStatus: courseId={}", gocastCourseId);
        try {
            BindingStatusResponse response = restClient.get().uri("/integration/courses/{courseId}/binding-status", gocastCourseId).header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve().body(BindingStatusResponse.class);
            return response != null && response.bound();
        }
        catch (RestClientException ex) {
            throw translate("EP7 getBindingStatus failed for courseId=" + gocastCourseId, ex);
        }
    }

    /**
     * Translates a Spring {@link RestClientException} into a typed {@link GocastIntegrationException}.
     * <p>
     * HTTP-status errors ({@link RestClientResponseException}) preserve the real upstream status code so
     * callers can branch on it. All other transport/I-O failures (connection refused, DNS, read timeout,
     * etc.) are reported as {@link HttpStatus#SERVICE_UNAVAILABLE}. In both cases the original exception is
     * preserved as the cause.
     *
     * @param context a short human-readable description of the failed operation
     * @param ex      the underlying Spring REST client exception
     * @return a typed {@link GocastIntegrationException} (never returns normally to the caller's logic; the
     *         return type lets callers write {@code throw translate(...)})
     */
    private GocastIntegrationException translate(String context, RestClientException ex) {
        if (ex instanceof RestClientResponseException responseException) {
            return new GocastIntegrationException(context + ": " + responseException.getMessage(), responseException.getStatusCode(), responseException);
        }
        return new GocastIntegrationException(context + " (transport error): " + ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, ex);
    }

    /**
     * Internal projection of the {@code GetBindingStatusResponse} proto message.
     * Only the {@code bound} field is needed on the Artemis side; the record component name already matches
     * the JSON property, so no {@code @JsonProperty} is required.
     */
    private record BindingStatusResponse(boolean bound) {
    }
}
