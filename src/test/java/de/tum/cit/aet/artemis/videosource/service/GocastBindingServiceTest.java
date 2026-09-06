package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastApprovalAttemptRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

class GocastBindingServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");

    private static final String STATE = "KioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKio";

    private static final String OTHER_STATE = "QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI";

    private static final String CODE = "Q0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0M";

    private static final String CALLBACK_URL = "https://artemis.example/api/videosource/public/gocast/approval/callback";

    @Autowired
    private GocastConnectionRepository connectionRepository;

    @Autowired
    private GocastCourseBindingRepository bindingRepository;

    @Autowired
    private GocastApprovalAttemptRepository attemptRepository;

    private GocastConnectorService connector;

    private GocastBindingService service;

    private Course course;

    @BeforeEach
    void setUp() {
        connector = mock(GocastConnectorService.class);
        service = new GocastBindingService(connectionRepository, connector, CALLBACK_URL, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        course = courseUtilService.addEmptyCourse();
    }

    @AfterEach
    void cleanUp() {
        attemptRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    void verifiesIdentityBeforeSavingPendingAttemptAndBuildsDirectAuthorizationUrl() {
        when(connector.getIntegration()).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
            return new GocastConnectorService.IntegrationIdentity(17, "Artemis", CALLBACK_URL);
        });
        when(connector.authorizationUrl(anyLong(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();
            return "https://live.example/integration/authorize/17?state=" + invocation.getArgument(1, String.class);
        });

        var approval = service.startApproval(course.getId());

        assertThat(approval.approvalUrl()).startsWith("https://live.example/integration/authorize/17?state=");
        assertThat(approval.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(attemptRepository.findByCourseId(course.getId())).get().satisfies(attempt -> {
            assertThat(attempt.getIntegrationId()).isEqualTo(17);
            assertThat(attempt.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        });
    }

    @Test
    void refusesMismatchedRegisteredReturnUrlBeforeCreatingPendingState() {
        when(connector.getIntegration()).thenReturn(new GocastConnectorService.IntegrationIdentity(17, "Artemis", "https://evil.example/callback"));

        assertThatThrownBy(() -> service.startApproval(course.getId())).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void unknownAndExpiredStateNeverCallGoCast() {
        assertThat(service.completeApproval(STATE, CODE).completed()).isFalse();
        connectionRepository.startAttempt(course.getId(), hash(STATE), 17, NOW);
        assertThat(service.completeApproval(STATE, CODE).completed()).isFalse();

        verifyNoInteractions(connector);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void successfulCallbackBindsTheSavedIntegrationAndIsSingleUse() {
        connectionRepository.startAttempt(course.getId(), hash(STATE), 17, NOW.plusSeconds(900));
        when(connector.redeemApproval(17, STATE, CODE)).thenReturn(verifiedCourse(17, 37, 23));

        assertThat(service.completeApproval(STATE, CODE)).satisfies(result -> {
            assertThat(result.completed()).isTrue();
            assertThat(result.artemisCourseId()).isEqualTo(course.getId());
        });
        clearInvocations(connector);
        assertThat(service.completeApproval(STATE, CODE).completed()).isFalse();
        verifyNoInteractions(connector);

        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(binding -> {
            assertThat(binding.getIntegrationId()).isEqualTo(17);
            assertThat(binding.getGocastCourseId()).isEqualTo(37);
            assertThat(binding.getGocastGrantId()).isEqualTo(23);
        });
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void failedCodeExchangeKeepsAttemptForRetry() {
        connectionRepository.startAttempt(course.getId(), hash(STATE), 17, NOW.plusSeconds(900));
        when(connector.redeemApproval(17, STATE, CODE)).thenThrow(new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(verifiedCourse(17, 37, 23));

        assertThatThrownBy(() -> service.completeApproval(STATE, CODE)).isInstanceOf(GocastIntegrationException.class);
        assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();

        assertThat(service.completeApproval(STATE, CODE).completed()).isTrue();
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void mismatchedVerifiedIntegrationCannotCompleteAttempt() {
        connectionRepository.startAttempt(course.getId(), hash(STATE), 17, NOW.plusSeconds(900));
        when(connector.redeemApproval(17, STATE, CODE)).thenReturn(verifiedCourse(19, 37, 23));

        assertThatThrownBy(() -> service.completeApproval(STATE, CODE)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void denialClearsOnlyItsMatchingPendingAttempt() {
        connectionRepository.startAttempt(course.getId(), hash(STATE), 17, NOW.plusSeconds(900));
        service.cancelApproval(STATE);
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();

        connectionRepository.startAttempt(course.getId(), hash(OTHER_STATE), 17, NOW.plusSeconds(900));
        service.cancelApproval(STATE);
        assertThat(attemptRepository.findByCourseId(course.getId())).get().satisfies(attempt -> assertThat(attempt.getStateHash()).isEqualTo(hash(OTHER_STATE)));
        verifyNoInteractions(connector);
    }

    @Test
    void notFoundMarksSavedGrantRevokedButOutagePreservesActiveConnection() {
        createBinding();
        doThrow(new GocastIntegrationException("not found", HttpStatus.NOT_FOUND)).when(connector).getGrant(23);

        assertThat(service.getBinding(course.getId())).satisfies(result -> {
            assertThat(result.status()).isEqualTo(GocastBindingConnectionStatus.REVOKED);
            assertThat(result.upstreamUnavailable()).isFalse();
        });
        assertThat(bindingRepository.findByCourseId(course.getId())).get().extracting(binding -> binding.getStatus()).isEqualTo(GocastBindingStatus.REVOKED);

        var claim = connectionRepository.prepareUnlink(course.getId()).orElseThrow();
        assertThat(connectionRepository.completeUnlink(claim)).isTrue();
        createBinding();
        doThrow(new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE)).when(connector).getGrant(23);

        assertThat(service.getBinding(course.getId())).satisfies(result -> {
            assertThat(result.status()).isEqualTo(GocastBindingConnectionStatus.ACTIVE);
            assertThat(result.upstreamUnavailable()).isTrue();
        });
        assertThat(bindingRepository.findByCourseId(course.getId())).get().extracting(binding -> binding.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
    }

    @Test
    void refreshedGrantMetadataIsPersisted() {
        createBinding();
        when(connector.getGrant(23)).thenReturn(new GocastConnectorService.GrantDetails(37, "refreshed", "Refreshed course", "public"));

        assertThat(service.getBinding(course.getId())).satisfies(result -> {
            assertThat(result.status()).isEqualTo(GocastBindingConnectionStatus.ACTIVE);
            assertThat(result.courseName()).isEqualTo("Refreshed course");
            assertThat(result.courseSlug()).isEqualTo("refreshed");
            assertThat(result.courseVisibility()).isEqualTo("public");
        });
        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(binding -> assertThat(binding.getCourseName()).isEqualTo("Refreshed course"));
    }

    @Test
    void remoteRevokeRunsOutsideTransactionAndFailurePreservesBindingForRetry() {
        createBinding();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }).when(connector).revokeGrant(23);

        assertThatThrownBy(() -> service.unlink(course.getId())).isInstanceOf(GocastIntegrationException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isPresent();
    }

    @Test
    void exactGrantEndpointsRemainRetryableAcrossAnOutage() {
        createBinding();
        MockRestServiceServer server = useHttpConnector();
        server.expect(requestTo("http://localhost:18081/api/v2/integration/grants/23")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://localhost:18081/api/v2/integration/grants/23")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"courseId\":37,\"name\":\"Refreshed course\",\"slug\":\"refreshed\",\"visibility\":\"public\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:18081/api/v2/integration/grants/23")).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());

        assertThatThrownBy(() -> service.unlink(course.getId())).isInstanceOf(GocastIntegrationException.class);
        assertThat(service.getBinding(course.getId()).courseName()).isEqualTo("Refreshed course");
        service.unlink(course.getId());

        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
        server.verify();
    }

    @Test
    void delayedStatusFailureDoesNotReturnARemovedBinding() {
        createBinding();
        CountDownLatch remoteStarted = new CountDownLatch(1);
        CountDownLatch releaseRemote = new CountDownLatch(1);
        when(connector.getGrant(23)).thenAnswer(invocation -> {
            remoteStarted.countDown();
            await(releaseRemote);
            throw new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        });

        CompletableFuture<de.tum.cit.aet.artemis.videosource.dto.GocastBindingDTO> response = CompletableFuture.supplyAsync(() -> service.getBinding(course.getId()));
        await(remoteStarted);
        try {
            var unlink = connectionRepository.prepareUnlink(course.getId()).orElseThrow();
            assertThat(connectionRepository.completeUnlink(unlink)).isTrue();
        }
        finally {
            releaseRemote.countDown();
        }

        assertThat(response.join().status()).isEqualTo(GocastBindingConnectionStatus.UNLINKED);
    }

    private void createBinding() {
        connectionRepository.startAttempt(course.getId(), "state", 17, NOW.plusSeconds(900));
        connectionRepository.completeAttempt("state", verifiedCourse(17, 37, 23), NOW);
    }

    private MockRestServiceServer useHttpConnector() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18081/api/v2").defaultHeader("Authorization", "Bearer fixture-api-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        connector = new GocastConnectorService(builder.build(), URI.create("http://localhost:18081"));
        service = new GocastBindingService(connectionRepository, connector, CALLBACK_URL, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        return server;
    }

    private static GocastVerifiedCourseDTO verifiedCourse(long integrationId, long gocastCourseId, long grantId) {
        return new GocastVerifiedCourseDTO(integrationId, grantId, gocastCourseId, "algorithms", "Algorithms", "loggedin");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for concurrent status operation");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
