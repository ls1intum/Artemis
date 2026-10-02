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

    private static final String CODE = "Q0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0M";

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
