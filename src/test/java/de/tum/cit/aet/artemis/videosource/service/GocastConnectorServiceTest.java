package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.videosource.dto.GocastCourseDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastPlaybackTokenDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastStreamDTO;

/**
 * Unit tests for {@link GocastConnectorService}.
 * <p>
 * Uses {@link MockRestServiceServer} bound to a {@link RestClient.Builder} to spin up an in-memory HTTP
 * server that verifies:
 * <ul>
 * <li>Each method targets the exact URL defined in the API contract (spec §4).</li>
 * <li>{@code Authorization: Bearer <token>} is present on every request.</li>
 * <li>{@code X-On-Behalf-Of: <lrzId>} is present where required (EP1, EP2) and absent where not (EP8, EP7).</li>
 * <li>Response JSON is correctly deserialized into the DTO types.</li>
 * <li>HTTP error responses are wrapped in {@link GocastIntegrationException} carrying the upstream status.</li>
 * </ul>
 */
class GocastConnectorServiceTest {

    private static final String BASE_URL = "https://tum.live";

    private static final String TOKEN = "test-secret-token";

    private static final String LRZ_ID = "ab12cde";

    private static final String BEARER = "Bearer " + TOKEN;

    private MockRestServiceServer mockServer;

    private GocastConnectorService connector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        connector = new GocastConnectorService(builder.build(), TOKEN);
    }

    // ── EP1: listAdministeredCourses ──────────────────────────────────────────

    @Test
    void listAdministeredCourses_sendsCorrectUrlAndHeaders_andMapsDtoFields() {
        mockServer.expect(requestTo(BASE_URL + "/integration/users/" + LRZ_ID + "/administered-courses?year=2026&term=W")).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(header("X-On-Behalf-Of", LRZ_ID)).andRespond(withSuccess("""
                        [
                          {"id":42,"name":"Eidi","slug":"eidi","year":2026,"teachingTerm":"W","vodEnabled":true,"visibility":"loggedin"},
                          {"id":99,"name":"TGI","slug":"tgi","year":2026,"teachingTerm":"W","vodEnabled":false,"visibility":"public"}
                        ]""", MediaType.APPLICATION_JSON));

        List<GocastCourseDTO> result = connector.listAdministeredCourses(LRZ_ID, 2026, "W");

        mockServer.verify();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(42L);
        assertThat(result.get(0).name()).isEqualTo("Eidi");
        assertThat(result.get(0).slug()).isEqualTo("eidi");
        assertThat(result.get(0).year()).isEqualTo(2026);
        assertThat(result.get(0).teachingTerm()).isEqualTo("W");
        assertThat(result.get(0).vodEnabled()).isTrue();
        assertThat(result.get(0).visibility()).isEqualTo("loggedin");
        assertThat(result.get(1).id()).isEqualTo(99L);
    }

    @Test
    void listAdministeredCourses_throwsGocastIntegrationException_on404_oboUserNotFound() {
        mockServer.expect(requestTo(BASE_URL + "/integration/users/" + LRZ_ID + "/administered-courses?year=2026&term=W")).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> connector.listAdministeredCourses(LRZ_ID, 2026, "W")).isInstanceOf(GocastIntegrationException.class)
                .satisfies(ex -> assertThat(((GocastIntegrationException) ex).getUpstreamStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listAdministeredCourses_throwsGocastIntegrationException_on5xx() {
        mockServer.expect(requestTo(BASE_URL + "/integration/users/" + LRZ_ID + "/administered-courses?year=2026&term=W")).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> connector.listAdministeredCourses(LRZ_ID, 2026, "W")).isInstanceOf(GocastIntegrationException.class)
                .satisfies(ex -> assertThat(((GocastIntegrationException) ex).getUpstreamStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // ── EP8: listCourseStreams ─────────────────────────────────────────────────

    @Test
    void listCourseStreams_sendsCorrectUrlAndBearerOnly_noOboHeader() {
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/streams")).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(headerDoesNotExist("X-On-Behalf-Of")).andRespond(withSuccess("""
                        [
                          {"streamId":1234,"name":"Lecture 1","private":false}
                        ]""", MediaType.APPLICATION_JSON));

        List<GocastStreamDTO> result = connector.listCourseStreams(42L);

        mockServer.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).streamId()).isEqualTo(1234L);
        assertThat(result.get(0).name()).isEqualTo("Lecture 1");
        assertThat(result.get(0).isPrivate()).isFalse();
    }

    @Test
    void listCourseStreams_throwsGocastIntegrationException_on403() {
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/streams")).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> connector.listCourseStreams(42L)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(ex -> assertThat(((GocastIntegrationException) ex).getUpstreamStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── EP2: getPlaybackToken ─────────────────────────────────────────────────

    @Test
    void getPlaybackToken_postsToCorrectUrlWithBearerAndObo_andMapsDtoFields() {
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/streams/1234/playback-token")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(header("X-On-Behalf-Of", LRZ_ID)).andRespond(withSuccess("""
                        {
                          "playlistUrl":"https://cdn.tum.live/pl.m3u8?jwt=abc",
                          "playlistUrlPres":"https://cdn.tum.live/pl_pres.m3u8?jwt=abc",
                          "playlistUrlCam":"https://cdn.tum.live/pl_cam.m3u8?jwt=abc",
                          "expiresIn":7200
                        }""", MediaType.APPLICATION_JSON));

        GocastPlaybackTokenDTO result = connector.getPlaybackToken(42L, 1234L, 7200, LRZ_ID);

        mockServer.verify();
        assertThat(result.playlistUrl()).isEqualTo("https://cdn.tum.live/pl.m3u8?jwt=abc");
        assertThat(result.playlistUrlPres()).isEqualTo("https://cdn.tum.live/pl_pres.m3u8?jwt=abc");
        assertThat(result.playlistUrlCam()).isEqualTo("https://cdn.tum.live/pl_cam.m3u8?jwt=abc");
        assertThat(result.expiresIn()).isEqualTo(7200);
    }

    @Test
    void getPlaybackToken_throwsGocastIntegrationException_on403() {
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/streams/1234/playback-token")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BEARER)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> connector.getPlaybackToken(42L, 1234L, 7200, LRZ_ID)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(ex -> assertThat(((GocastIntegrationException) ex).getUpstreamStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── EP7: getBindingStatus ─────────────────────────────────────────────────

    @Test
    void getBindingStatus_returnsTrueWhenBound() {
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/binding-status")).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(headerDoesNotExist("X-On-Behalf-Of")).andRespond(withSuccess("{\"bound\":true}", MediaType.APPLICATION_JSON));

        boolean result = connector.getBindingStatus(42L);

        mockServer.verify();
        assertThat(result).isTrue();
    }

    @Test
    void getBindingStatus_returnsFalseWhenNotBound() {
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/binding-status")).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andRespond(withSuccess("{\"bound\":false}", MediaType.APPLICATION_JSON));

        boolean result = connector.getBindingStatus(42L);

        mockServer.verify();
        assertThat(result).isFalse();
    }

    @Test
    void getBindingStatus_throwsGocastIntegrationException_on401() {
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/binding-status")).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> connector.getBindingStatus(42L)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(ex -> assertThat(((GocastIntegrationException) ex).getUpstreamStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ── Transport / I-O errors (no HTTP response) → synthetic 503 ─────────────

    @Test
    void networkError_isWrappedAsServiceUnavailable() {
        // A transport failure (e.g. connection refused / read timeout) surfaces as a RestClientException
        // WITHOUT a RestClientResponseException, so there is no upstream HTTP status. The connector must
        // still wrap it into a typed GocastIntegrationException with a synthetic 503 rather than let the
        // raw RestClientException escape.
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/streams")).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> connector.listCourseStreams(42L)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(ex -> assertThat(((GocastIntegrationException) ex).getUpstreamStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    // ── EP2 empty-body guard → synthetic 502 (never returns null) ────────────

    @Test
    void getPlaybackToken_emptyBody_isWrappedAsBadGateway() {
        // A 2xx with no body would deserialize to null; the connector must throw rather than return null
        // for its @NonNull signature.
        mockServer.expect(requestTo(BASE_URL + "/integration/courses/42/streams/1234/playback-token")).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));

        assertThatThrownBy(() -> connector.getPlaybackToken(42L, 1234L, 7200, LRZ_ID)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(ex -> assertThat(((GocastIntegrationException) ex).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
