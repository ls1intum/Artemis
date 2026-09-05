package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GocastConnectorServiceTest {

    private static final String BASE_URL = "http://localhost:18081/api/v2";

    private static final String REQUEST_ID = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String STATE = "KioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKio";

    private static final String CODE = "Q0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0M";

    private MockRestServiceServer server;

    private GocastAuthenticationService authenticationService;

    private GocastConnectorService connector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        authenticationService = mock(GocastAuthenticationService.class);
        when(authenticationService.getSession()).thenReturn(new GocastAuthenticationService.Session("Bearer integration-token", 17));
        connector = new GocastConnectorService(builder.build(), authenticationService, URI.create("http://localhost:18081"),
                Clock.fixed(Instant.parse("2026-09-05T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsApprovalUsingExactGatewayPayloadAndValidatesApprovalUrl() {
        server.expect(requestTo(BASE_URL + "/integration/approval-requests")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer integration-token"))
                .andExpect(content().json(
                        "{\"state\":\"%s\",\"courseLabel\":\"Introduction to programming\",\"callbackUrl\":\"https://artemis.example/api/videosource/public/gocast/approval/callback\"}"
                                .formatted(STATE)))
                .andRespond(withSuccess("{\"requestId\":\"%s\",\"approvalUrl\":\"http://localhost:18081/integration/approve/%s\",\"expiresAt\":\"2026-09-05T03:15:00Z\"}"
                        .formatted(REQUEST_ID, REQUEST_ID), MediaType.APPLICATION_JSON));

        var result = connector.createApproval(STATE, "Introduction to programming", "https://artemis.example/api/videosource/public/gocast/approval/callback");

        assertThat(result.requestId()).isEqualTo(REQUEST_ID);
        assertThat(result.approvalUrl()).isEqualTo("http://localhost:18081/integration/approve/" + REQUEST_ID);
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-09-05T03:15:00Z"));
        server.verify();
    }

    @Test
    void redeemsAndReadsAndRevokesTheExactGrant() {
        server.expect(requestTo(BASE_URL + "/integration/approval-requests/" + REQUEST_ID + "/redeem")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"state\":\"%s\",\"code\":\"%s\"}".formatted(STATE, CODE)))
                .andRespond(withSuccess(
                        "{\"requestId\":\"%s\",\"state\":\"%s\",\"serviceUserId\":17,\"grantId\":23,\"courseId\":37,\"courseSlug\":\"algorithmen-üben\",\"courseName\":\"Algorithmen & Datenstrukturen\",\"courseVisibility\":\"loggedin\"}"
                                .formatted(REQUEST_ID, STATE),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"active\":true,\"grantId\":23,\"courseId\":37,\"courseSlug\":\"algorithmen-üben\",\"courseName\":\"Algorithmen & Datenstrukturen\",\"courseVisibility\":\"loggedin\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(connector.redeemApproval(REQUEST_ID, STATE, CODE).grantId()).isEqualTo(23);
        assertThat(connector.getGrantStatus(37, 23).active()).isTrue();
        connector.revokeGrant(37, 23);
        server.verify();
    }

    @Test
    void requiresExplicitBooleanAndCompleteActiveMetadata() {
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.getGrantStatus(37, 23)).isInstanceOf(GocastIntegrationException.class);

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andRespond(withSuccess("{\"active\":null}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.getGrantStatus(37, 23)).isInstanceOf(GocastIntegrationException.class);

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));
        assertThat(connector.getGrantStatus(37, 23).active()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidActiveValues")
    void rejectsNonBooleanActiveValuesFromActualHttpJson(String activeValue) {
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23"))
                .andRespond(withSuccess("{\"active\":%s}".formatted(activeValue), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.getGrantStatus(37, 23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("redirectStatuses")
    void rejectsRedirectRevokeAcknowledgements(HttpStatus redirectStatus) {
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(redirectStatus));

        assertThatThrownBy(() -> connector.revokeGrant(37, 23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(redirectStatus));
        server.verify();
    }

    @Test
    void retriesOnceOnUnauthorizedButNeverOnForbiddenOrTransportFailure() {
        when(authenticationService.getSession()).thenReturn(new GocastAuthenticationService.Session("Bearer old", 17), new GocastAuthenticationService.Session("Bearer new", 17));
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer old"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new"))
                .andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));
        assertThat(connector.getGrantStatus(37, 23).active()).isFalse();
        verify(authenticationService).invalidate("Bearer old");

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> connector.getGrantStatus(37, 23)).isInstanceOf(GocastIntegrationException.class).satisfies(error -> {
            assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(error.getCause()).isNull();
        });
        server.verify();

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/approval-requests/" + REQUEST_ID + "/redeem")).andRespond(withException(new SocketTimeoutException("read timed out")));
        assertThatThrownBy(() -> connector.redeemApproval(REQUEST_ID, STATE, CODE)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void rejectsInvalidIdsOpaqueValuesAndExternalApprovalUrls() {
        assertThatThrownBy(() -> connector.getGrantStatus(0, 23)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.redeemApproval("short", STATE, CODE)).isInstanceOf(IllegalArgumentException.class);

        server.expect(requestTo(BASE_URL + "/integration/approval-requests")).andRespond(withSuccess(
                "{\"requestId\":\"%s\",\"approvalUrl\":\"https://evil.example/integration/approve/%s\",\"expiresAt\":\"2026-09-05T03:15:00Z\"}".formatted(REQUEST_ID, REQUEST_ID),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.createApproval(STATE, "Course", "https://artemis.example/api/videosource/public/gocast/approval/callback"))
                .isInstanceOf(GocastIntegrationException.class);
    }

    @Test
    void rejectsApprovalUrlThatDoesNotExactlyMatchReturnedRequestAndUsableExpiry() {
        assertInvalidApproval("http://localhost:18081/integration/approve/%s?next=evil".formatted(REQUEST_ID), REQUEST_ID, "2026-09-05T03:15:00Z");
        assertInvalidApproval("http://localhost:18081/integration/approve/%s/extra".formatted(REQUEST_ID), REQUEST_ID, "2026-09-05T03:15:00Z");
        assertInvalidApproval("http://localhost:18081/integration/approve/%%2e%%2e/%s".formatted(REQUEST_ID), REQUEST_ID, "2026-09-05T03:15:00Z");
        assertInvalidApproval("http://localhost:18081/integration/approve/%s".formatted(STATE), REQUEST_ID, "2026-09-05T03:15:00Z");
        assertInvalidApproval("http://localhost:18081/integration/approve/%s".formatted(REQUEST_ID), REQUEST_ID, "2026-09-05T03:00:00Z");
    }

    @Test
    void mapsMalformedRemoteCourseDataAndJsonToBadGateway() {
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23"))
                .andRespond(withSuccess("{\"active\":true,\"grantId\":0,\"courseId\":37,\"courseSlug\":\"course\",\"courseName\":\"Course\",\"courseVisibility\":\"loggedin\"}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.getGrantStatus(37, 23)).isInstanceOf(GocastIntegrationException.class).satisfies(error -> {
            assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(error.getCause()).isNull();
        });

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andRespond(withSuccess(
                "{\"active\":true,\"grantId\":23,\"courseId\":37,\"courseSlug\":\"course\",\"courseName\":\"Course\",\"courseVisibility\":null}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.getGrantStatus(37, 23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/courses/37/grant?grantId=23")).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.getGrantStatus(37, 23)).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void validatesRedeemServiceUserAgainstTheRetriedSession() {
        when(authenticationService.getSession()).thenReturn(new GocastAuthenticationService.Session("Bearer old", 17), new GocastAuthenticationService.Session("Bearer new", 29));
        server.expect(requestTo(BASE_URL + "/integration/approval-requests/" + REQUEST_ID + "/redeem")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer old"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + "/integration/approval-requests/" + REQUEST_ID + "/redeem")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new"))
                .andRespond(withSuccess(
                        "{\"requestId\":\"%s\",\"state\":\"%s\",\"serviceUserId\":29,\"grantId\":23,\"courseId\":37,\"courseSlug\":\"course\",\"courseName\":\"Course\",\"courseVisibility\":\"loggedin\"}"
                                .formatted(REQUEST_ID, STATE),
                        MediaType.APPLICATION_JSON));

        assertThat(connector.redeemApproval(REQUEST_ID, STATE, CODE).serviceUserId()).isEqualTo(29);
        verify(authenticationService).invalidate("Bearer old");
        server.verify();
    }

    private void assertInvalidApproval(String approvalUrl, String requestId, String expiresAt) {
        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/approval-requests")).andRespond(
                withSuccess("{\"requestId\":\"%s\",\"approvalUrl\":\"%s\",\"expiresAt\":\"%s\"}".formatted(requestId, approvalUrl, expiresAt), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.createApproval(STATE, "Course", "https://artemis.example/api/videosource/public/gocast/approval/callback"))
                .isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    private static Stream<Arguments> invalidActiveValues() {
        return Stream.of(Arguments.of("0"), Arguments.of("\"false\""), Arguments.of("[]"), Arguments.of("{}"));
    }

    private static Stream<Arguments> redirectStatuses() {
        return Stream.of(Arguments.of(HttpStatus.FOUND), Arguments.of(HttpStatus.SEE_OTHER), Arguments.of(HttpStatus.TEMPORARY_REDIRECT));
    }
}
