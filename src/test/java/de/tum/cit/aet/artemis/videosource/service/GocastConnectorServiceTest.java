package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GocastConnectorServiceTest {

    private static final String BASE_URL = "http://localhost:18081/api/v2";

    private static final String STATE = "KioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKio";

    private static final String CODE = "685aebd3-45ba-4d4c-91a5-b181932f2ff5";

    private static final String CALLBACK_URL = "https://artemis.example/api/videosource/public/gocast/approval/callback";

    private static final String AUTHORIZATION = "Bearer fixture-api-key";

    private MockRestServiceServer server;

    private GocastConnectorService connector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL).defaultHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION);
        server = MockRestServiceServer.bindTo(builder).build();
        connector = new GocastConnectorService(builder.build(), URI.create("http://localhost:18081"));
    }

    @Test
    void readsIntegrationIdentityAndBuildsTheTrustedAuthorizationUrl() {
        server.expect(requestTo(BASE_URL + "/integration")).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess("{\"id\":17,\"name\":\"Artemis\",\"returnUrl\":\"%s\"}".formatted(CALLBACK_URL), MediaType.APPLICATION_JSON));

        var identity = connector.getIntegration();

        assertThat(identity).satisfies(value -> {
            assertThat(value.id()).isEqualTo(17);
            assertThat(value.name()).isEqualTo("Artemis");
            assertThat(value.returnUrl()).isEqualTo(CALLBACK_URL);
        });
        assertThat(connector.authorizationUrl(identity.id(), STATE)).isEqualTo("http://localhost:18081/integration/authorize/17?state=" + STATE);
        server.verify();
    }

    @Test
    void redeemsAuthorizationReadsMatchingGrantAndRevokesTheExactGrant() {
        server.expect(requestTo(BASE_URL + "/integration/authorizations/redeem")).andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(content().json("{\"code\":\"%s\",\"state\":\"%s\"}".formatted(CODE, STATE), JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"grantId\":23,\"courseId\":37}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23")).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)).andRespond(
                withSuccess("{\"courseId\":37,\"name\":\"Algorithmen & Datenstrukturen\",\"slug\":\"algorithmen-üben\",\"visibility\":\"loggedin\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23")).andExpect(method(HttpMethod.DELETE)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess());

        var verified = connector.redeemApproval(17, STATE, CODE);

        assertThat(verified).satisfies(value -> {
            assertThat(value.integrationId()).isEqualTo(17);
            assertThat(value.grantId()).isEqualTo(23);
            assertThat(value.courseId()).isEqualTo(37);
            assertThat(value.courseName()).isEqualTo("Algorithmen & Datenstrukturen");
            assertThat(value.courseSlug()).isEqualTo("algorithmen-üben");
            assertThat(value.courseVisibility()).isEqualTo("loggedin");
        });
        connector.revokeGrant(23);
        server.verify();
    }

    @Test
    void readsCurrentGrantMetadataWithoutAnActiveFlag() {
        server.expect(requestTo(BASE_URL + "/integration/grants/23")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"courseId\":37,\"name\":\"Course\",\"slug\":\"course\",\"visibility\":\"public\"}", MediaType.APPLICATION_JSON));

        assertThat(connector.getGrant(23)).satisfies(grant -> {
            assertThat(grant.courseId()).isEqualTo(37);
            assertThat(grant.courseName()).isEqualTo("Course");
            assertThat(grant.courseSlug()).isEqualTo("course");
            assertThat(grant.courseVisibility()).isEqualTo("public");
        });
        server.verify();
    }

    @Test
    void rejectsGrantWhoseCourseDoesNotMatchTheRedeemedCourse() {
        server.expect(requestTo(BASE_URL + "/integration/authorizations/redeem")).andRespond(withSuccess("{\"grantId\":23,\"courseId\":37}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23"))
                .andRespond(withSuccess("{\"courseId\":41,\"name\":\"Other\",\"slug\":\"other\",\"visibility\":\"public\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.redeemApproval(17, STATE, CODE)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void preservesNotFoundAndTransportStatusForTheBindingPolicy() {
        server.expect(requestTo(BASE_URL + "/integration/grants/23")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> connector.getGrant(23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        server.verify();

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/grants/23")).andRespond(withException(new SocketTimeoutException("read timed out")));
        assertThatThrownBy(() -> connector.getGrant(23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void readsTheSignedRecordingPlaylistOfTheExactGrant() {
        String playlist = "https://edge.example/vod/42/COMB/playlist.m3u8?jwt=signed";
        server.expect(requestTo(BASE_URL + "/integration/grants/23/recordings/42")).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess("{\"playlistUrl\":\"%s\",\"converting\":true,\"progress\":10}".formatted(playlist), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23/recordings/43")).andRespond(withSuccess("{\"converting\":true,\"progress\":40}", MediaType.APPLICATION_JSON));

        assertThat(connector.getRecordingPlaylist(23, 42)).contains(playlist);
        assertThat(connector.getRecordingPlaylist(23, 43)).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"playlistUrl\":\"javascript:alert(1)\"}", "{\"playlistUrl\":\"/relative.m3u8\"}", "{\"playlistUrl\":\"https://user:pass@edge.example/p.m3u8\"}",
            "null" })
    void rejectsRecordingPlaylistsThatAreNotHttpUrls(String responseBody) {
        server.expect(requestTo(BASE_URL + "/integration/grants/23/recordings/42")).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.getRecordingPlaylist(23, 42)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void preservesNotFoundForRecordingsTheGrantDoesNotCover() {
        server.expect(requestTo(BASE_URL + "/integration/grants/23/recordings/42")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> connector.getRecordingPlaylist(23, 42)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> connector.getRecordingPlaylist(23, 0)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void createsUploadsWithAnAbsoluteUrlOnTheWebHostAndDeletesThem() {
        String ticket = "BQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQU";
        server.expect(requestTo(BASE_URL + "/integration/grants/23/uploads")).andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(content().json("{\"externalRef\":\"reference-a-0000\",\"title\":\"Lecture 3\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"streamId\":55,\"uploadPath\":\"/api/v2/integration/uploads/%s\"}".formatted(ticket), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23/uploads/reference-a-0000")).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());

        var target = connector.createUpload(23, "reference-a-0000", "Lecture 3");
        connector.deleteUpload(23, "reference-a-0000");

        assertThat(target.streamId()).isEqualTo(55);
        assertThat(target.uploadUrl()).isEqualTo("http://localhost:18081/api/v2/integration/uploads/" + ticket);
        assertThat(target.toString()).doesNotContain(ticket);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"streamId\":55,\"uploadPath\":\"https://evil.example/api/v2/integration/uploads/BQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQU\"}",
            "{\"streamId\":55,\"uploadPath\":\"/api/v2/other\"}", "{\"streamId\":0,\"uploadPath\":\"/api/v2/integration/uploads/BQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQU\"}" })
    void rejectsUploadTargetsOutsideTheTicketRoute(String responseBody) {
        server.expect(requestTo(BASE_URL + "/integration/grants/23/uploads")).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.createUpload(23, "reference-a-0000", "Lecture")).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> connector.createUpload(23, "short", "Lecture")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void onlyASuccessConfirmsTheDeletionOfAnUpload() {
        server.expect(requestTo(BASE_URL + "/integration/grants/23/uploads/reference-a-0000")).andRespond(withStatus(HttpStatus.FOUND));

        assertThatThrownBy(() -> connector.deleteUpload(23, "reference-a-0000")).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.FOUND));
        server.verify();
    }

    @Test
    void readsTheConversionStateOfAnUpload() {
        server.expect(requestTo(BASE_URL + "/integration/grants/23/recordings/55"))
                .andRespond(withSuccess("{\"failed\":true,\"error\":\"The file is not a video.\",\"progress\":70}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23/recordings/55")).andRespond(withSuccess("{\"converting\":true,\"progress\":40}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23/recordings/55"))
                .andRespond(withSuccess("{\"playlistUrl\":\"https://edge.example/vod/55/COMB/playlist.m3u8?jwt=x\"}", MediaType.APPLICATION_JSON));

        var failed = connector.getRecording(23, 55);
        var converting = connector.getRecording(23, 55);
        var ready = connector.getRecording(23, 55);

        assertThat(failed.failed()).isTrue();
        assertThat(failed.error()).isEqualTo("The file is not a video.");
        assertThat(failed.ready()).isFalse();
        assertThat(converting.converting()).isTrue();
        assertThat(converting.progress()).isEqualTo(40);
        assertThat(converting.ready()).isFalse();
        assertThat(ready.ready()).isTrue();
        assertThat(ready.toString()).doesNotContain("jwt");
        server.verify();
    }

    @Test
    void buildsWatchPagesThatArtemisResolvesAsTumLiveVideos() {
        var tumLiveService = new TumLiveService(RestClient.builder(), BASE_URL);

        String watchUrl = connector.watchUrl("algorithmen-üben", 55);

        // Only the video, like a TUM.Live link an editor enters, so the edit form accepts it.
        assertThat(watchUrl).isEqualTo("http://localhost:18081/w/algorithmen-%C3%BCben/55?video_only=1");
        assertThat(tumLiveService.findWatchPage(watchUrl)).hasValueSatisfying(page -> assertThat(page.streamId()).isEqualTo("55"));
    }

    @ParameterizedTest
    @MethodSource("invalidIntegrationResponses")
    void rejectsInvalidIntegrationIdentity(String responseBody) {
        server.expect(requestTo(BASE_URL + "/integration")).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(connector::getIntegration).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("invalidGrantResponses")
    void rejectsMalformedGrantMetadata(String responseBody) {
        server.expect(requestTo(BASE_URL + "/integration/grants/23")).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.getGrant(23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void rejectsInvalidIdsAndOpaqueValuesBeforeCallingGoCast() {
        assertThatThrownBy(() -> connector.authorizationUrl(0, STATE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.authorizationUrl(17, "short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.redeemApproval(17, STATE, "short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.getGrant(4_294_967_296L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.revokeGrant(0)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Q0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0M", "685AEBD3-45BA-4D4C-91A5-B181932F2FF5", "{685aebd3-45ba-4d4c-91a5-b181932f2ff5}",
            "urn:uuid:685aebd3-45ba-4d4c-91a5-b181932f2ff5", "685aebd3-45ba-1d4c-91a5-b181932f2ff5", "685aebd3-45ba-4d4c-c1a5-b181932f2ff5", "1-2-4000-8000-16" })
    void rejectsApprovalCodesThatAreNotCanonicalVersion4Uuids(String code) {
        assertThatThrownBy(() -> connector.redeemApproval(17, STATE, code)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 4_294_967_295L })
    void acceptsIntegrationIdsInThePositiveUint32Range(long integrationId) {
        assertThat(connector.authorizationUrl(integrationId, STATE)).contains("/integration/authorize/" + integrationId + "?state=");
    }

    @ParameterizedTest
    @MethodSource("redirectStatuses")
    void rejectsRedirectRevokeAcknowledgements(HttpStatus redirectStatus) {
        server.expect(requestTo(BASE_URL + "/integration/grants/23")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(redirectStatus));

        assertThatThrownBy(() -> connector.revokeGrant(23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(redirectStatus));
        server.verify();
    }

    private static Stream<Arguments> invalidIntegrationResponses() {
        return Stream.of(Arguments.of("{}"), Arguments.of("{\"id\":0,\"name\":\"Artemis\",\"returnUrl\":\"%s\"}".formatted(CALLBACK_URL)),
                Arguments.of("{\"id\":17,\"name\":\" \",\"returnUrl\":\"%s\"}".formatted(CALLBACK_URL)), Arguments.of("{\"id\":17,\"name\":\"Artemis\",\"returnUrl\":\" \"}"));
    }

    private static Stream<Arguments> invalidGrantResponses() {
        return Stream.of(Arguments.of("{}"), Arguments.of("{\"courseId\":0,\"name\":\"Course\",\"slug\":\"course\",\"visibility\":\"public\"}"),
                Arguments.of("{\"courseId\":37,\"name\":\" \",\"slug\":\"course\",\"visibility\":\"public\"}"),
                Arguments.of("{\"courseId\":37,\"name\":\"Course\",\"slug\":\" \",\"visibility\":\"public\"}"),
                Arguments.of("{\"courseId\":37,\"name\":\"Course\",\"slug\":\"course\",\"visibility\":\"private\"}"));
    }

    private static Stream<Arguments> redirectStatuses() {
        return Stream.of(Arguments.of(HttpStatus.FOUND), Arguments.of(HttpStatus.SEE_OTHER), Arguments.of(HttpStatus.TEMPORARY_REDIRECT));
    }
}
