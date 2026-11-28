package de.tum.cit.aet.artemis.sharing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.ServiceCredentialBudget;
import de.tum.cit.aet.artemis.sharing.service.SharingConnectorService;

/**
 * Unit tests for credential checks and callback URL validation on the sharing configuration endpoint.
 */
class SharingSupportResourceTest {

    private SharingConnectorService connectorService;

    private ServiceCredentialBudget credentialBudget;

    private SharingSupportResource resource;

    @BeforeEach
    void setUp() {
        connectorService = mock(SharingConnectorService.class);
        credentialBudget = mock(ServiceCredentialBudget.class);
        resource = new SharingSupportResource(connectorService, credentialBudget);
    }

    @Test
    void testWrongKeyKeepsTheReservedToken() {
        when(connectorService.validateApiKey("wrong")).thenReturn(false);

        var response = resource.getConfig(Optional.of("Bearer wrong"), "https://sharing.example.org", Optional.empty());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(credentialBudget, never()).refundServiceCredentialBudget();
    }

    @Test
    void testMissingKeyKeepsTheReservedToken() {
        var response = resource.getConfig(Optional.empty(), "https://sharing.example.org", Optional.empty());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(credentialBudget, never()).refundServiceCredentialBudget();
    }

    @Test
    void malformedCallbackUrlKeepsCredentialsOutOfLogs() {
        when(connectorService.validateApiKey("valid")).thenReturn(true);
        String dummyCredential = "dummy-callback-credential";
        var logger = (Logger) LoggerFactory.getLogger(SharingSupportResource.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = resource.getConfig(Optional.of("valid"), "https://sharing.example/invalid path?token=" + dummyCredential, Optional.empty());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("malformed apiBaseUrl")).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(dummyCredential);
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testExhaustedBudgetRefusesWithoutComparingTheKey() {
        doThrow(new RateLimitExceededException(60)).when(credentialBudget).requireServiceCredentialBudget();

        assertThatThrownBy(() -> resource.getConfig(Optional.of("any"), "https://sharing.example.org", Optional.empty())).isInstanceOf(RateLimitExceededException.class);

        verify(connectorService, never()).validateApiKey("any");
    }
}
