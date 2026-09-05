package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastApprovalAttemptRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

class GocastBindingServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");

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
        service = new GocastBindingService(connectionRepository, courseRepository, connector, "https://artemis.example/api/videosource/public/gocast/approval/callback",
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        course = courseUtilService.addEmptyCourse();
    }

    @AfterEach
    void cleanUp() {
        attemptRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    void remoteCreateRunsAfterPendingCommitAndOutsideTransaction() {
        when(connector.createApproval(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();
            return new GocastConnectorService.CreatedApproval("request", "https://live.example/integration/approve/request", NOW.plusSeconds(900));
        });

        var approval = service.startApproval(course.getId());

        assertThat(approval.approvalUrl()).isEqualTo("https://live.example/integration/approve/request");
        assertThat(attemptRepository.findByCourseId(course.getId())).get().satisfies(attempt -> assertThat(attempt.getRequestId()).isEqualTo("request"));
    }

    @Test
    void remoteRevokeRunsOutsideTransactionAndFailurePreservesBindingForRetry() {
        createBinding();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }).when(connector).revokeGrant(37, 23);

        try {
            service.unlink(course.getId());
        }
        catch (GocastIntegrationException ignored) {
            // The endpoint exposes this as a retryable 503.
        }

        assertThat(bindingRepository.findByCourseId(course.getId())).isPresent();
    }

    @Test
    void redirectRevokeResponseRetainsTheExactLocalBindingForRetry() {
        createBinding();
        MockRestServiceServer server = useHttpConnector();
        server.expect(requestTo("http://localhost:18081/api/v2/integration/courses/37/grant?grantId=23")).andRespond(withStatus(HttpStatus.FOUND));

        assertThatThrownBy(() -> service.unlink(course.getId())).isInstanceOf(GocastIntegrationException.class);

        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(binding -> {
            assertThat(binding.getGocastCourseId()).isEqualTo(37);
            assertThat(binding.getGocastGrantId()).isEqualTo(23);
            assertThat(binding.getStatus()).isEqualTo(GocastBindingStatus.UNLINKING);
        });
        server.verify();
    }

    @Test
    void statusFailureKeepsSavedConnectionVisible() {
        createBinding();
        when(connector.getGrantStatus(37, 23)).thenThrow(new GocastIntegrationException("forbidden", HttpStatus.FORBIDDEN));

        GocastBindingDTO result = service.getBinding(course.getId());

        assertThat(result.status()).isEqualTo(GocastBindingConnectionStatus.ACTIVE);
        assertThat(result.courseName()).isEqualTo("Algorithms");
        assertThat(result.upstreamUnavailable()).isTrue();
        assertThat(bindingRepository.findByCourseId(course.getId())).isPresent();
    }

    @ParameterizedTest
    @MethodSource("strictActiveResponses")
    void actualHttpStatusJsonOnlyPersistsRevocationForABooleanFalse(String responseBody, boolean expectedUnavailable, GocastBindingStatus expectedStoredStatus) {
        createBinding();
        MockRestServiceServer server = useHttpConnector();
        server.expect(requestTo("http://localhost:18081/api/v2/integration/courses/37/grant?grantId=23")).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        GocastBindingDTO result = service.getBinding(course.getId());

        assertThat(result.upstreamUnavailable()).isEqualTo(expectedUnavailable);
        assertThat(bindingRepository.findByCourseId(course.getId())).get().extracting(binding -> binding.getStatus()).isEqualTo(expectedStoredStatus);
        server.verify();
    }

    @Test
    void delayedStatusFailureDoesNotReturnARemovedBinding() {
        createBinding();
        CountDownLatch remoteStarted = new CountDownLatch(1);
        CountDownLatch releaseRemote = new CountDownLatch(1);
        when(connector.getGrantStatus(37, 23)).thenAnswer(invocation -> {
            remoteStarted.countDown();
            await(releaseRemote);
            throw new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        });

        CompletableFuture<GocastBindingDTO> response = CompletableFuture.supplyAsync(() -> service.getBinding(course.getId()));
        await(remoteStarted);
        try {
            var unlink = connectionRepository.claimUnlink(course.getId()).orElseThrow();
            assertThat(connectionRepository.completeUnlink(unlink)).isTrue();
        }
        finally {
            releaseRemote.countDown();
        }

        assertThat(response.join().status()).isEqualTo(GocastBindingConnectionStatus.UNLINKED);
    }

    @Test
    void delayedStatusFailureReturnsAReplacementBindingWithoutMarkingItUnavailable() {
        createBinding();
        CountDownLatch remoteStarted = new CountDownLatch(1);
        CountDownLatch releaseRemote = new CountDownLatch(1);
        when(connector.getGrantStatus(37, 23)).thenAnswer(invocation -> {
            remoteStarted.countDown();
            await(releaseRemote);
            throw new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        });

        CompletableFuture<GocastBindingDTO> response = CompletableFuture.supplyAsync(() -> service.getBinding(course.getId()));
        await(remoteStarted);
        try {
            var unlink = connectionRepository.claimUnlink(course.getId()).orElseThrow();
            assertThat(connectionRepository.completeUnlink(unlink)).isTrue();
            createBinding("replacement-state", "replacement-request", 41, 29, "Replacement course");
        }
        finally {
            releaseRemote.countDown();
        }

        assertThat(response.join()).satisfies(result -> {
            assertThat(result.status()).isEqualTo(GocastBindingConnectionStatus.ACTIVE);
            assertThat(result.courseId()).isEqualTo(41);
            assertThat(result.courseName()).isEqualTo("Replacement course");
            assertThat(result.upstreamUnavailable()).isFalse();
        });
    }

    @Test
    void delayedStatusFailureReturnsANewPendingAttempt() {
        createBinding();
        CountDownLatch remoteStarted = new CountDownLatch(1);
        CountDownLatch releaseRemote = new CountDownLatch(1);
        when(connector.getGrantStatus(37, 23)).thenAnswer(invocation -> {
            remoteStarted.countDown();
            await(releaseRemote);
            throw new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        });

        CompletableFuture<GocastBindingDTO> response = CompletableFuture.supplyAsync(() -> service.getBinding(course.getId()));
        await(remoteStarted);
        try {
            var unlink = connectionRepository.claimUnlink(course.getId()).orElseThrow();
            assertThat(connectionRepository.completeUnlink(unlink)).isTrue();
            connectionRepository.startAttempt(course.getId(), "new-state", NOW.plusSeconds(900));
        }
        finally {
            releaseRemote.countDown();
        }

        assertThat(response.join().status()).isEqualTo(GocastBindingConnectionStatus.PENDING);
    }

    @Test
    void lostConditionalStatusUpdateReturnsANewPendingAttempt() {
        createBinding();
        CountDownLatch remoteStarted = new CountDownLatch(1);
        CountDownLatch releaseRemote = new CountDownLatch(1);
        when(connector.getGrantStatus(37, 23)).thenAnswer(invocation -> {
            remoteStarted.countDown();
            await(releaseRemote);
            return new GocastConnectorService.GrantStatus(true, 23, 37, "algorithms", "Algorithms", "loggedin");
        });

        CompletableFuture<GocastBindingDTO> response = CompletableFuture.supplyAsync(() -> service.getBinding(course.getId()));
        await(remoteStarted);
        try {
            var unlink = connectionRepository.claimUnlink(course.getId()).orElseThrow();
            assertThat(connectionRepository.completeUnlink(unlink)).isTrue();
            connectionRepository.startAttempt(course.getId(), "new-state", NOW.plusSeconds(900));
        }
        finally {
            releaseRemote.countDown();
        }

        assertThat(response.join().status()).isEqualTo(GocastBindingConnectionStatus.PENDING);
    }

    private void createBinding() {
        createBinding("state", "request", 37, 23, "Algorithms");
    }

    private void createBinding(String state, String requestId, long gocastCourseId, long grantId, String courseName) {
        connectionRepository.startAttempt(course.getId(), state, NOW.plusSeconds(900));
        connectionRepository.attachRemoteRequest(course.getId(), state, requestId, NOW.plusSeconds(900));
        connectionRepository.claimAttempt(state, requestId, NOW);
        connectionRepository.completeAttempt(state, requestId, new GocastVerifiedCourseDTO(17, grantId, gocastCourseId, "algorithms", courseName, "loggedin"), NOW);
    }

    private MockRestServiceServer useHttpConnector() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18081/api/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GocastAuthenticationService authentication = mock(GocastAuthenticationService.class);
        when(authentication.getSession()).thenReturn(new GocastAuthenticationService.Session("Bearer integration-token", 17));
        connector = new GocastConnectorService(builder.build(), authentication, URI.create("http://localhost:18081"), Clock.fixed(NOW, ZoneOffset.UTC));
        service = new GocastBindingService(connectionRepository, courseRepository, connector, "https://artemis.example/api/videosource/public/gocast/approval/callback",
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        return server;
    }

    private static Stream<Arguments> strictActiveResponses() {
        return Stream.of(Arguments.of("{\"active\":0}", true, GocastBindingStatus.ACTIVE), Arguments.of("{\"active\":\"false\"}", true, GocastBindingStatus.ACTIVE),
                Arguments.of("{\"active\":false}", false, GocastBindingStatus.REVOKED));
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
