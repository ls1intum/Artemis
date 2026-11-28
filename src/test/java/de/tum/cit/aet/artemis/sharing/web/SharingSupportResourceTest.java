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
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.ServiceCredentialBudget;
import de.tum.cit.aet.artemis.sharing.service.SharingConnectorService;

/**
 * Unit tests for the throttling of failed API key checks on the sharing configuration endpoint.
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
    void testExhaustedBudgetRefusesWithoutComparingTheKey() {
        doThrow(new RateLimitExceededException(60)).when(credentialBudget).requireServiceCredentialBudget();

        assertThatThrownBy(() -> resource.getConfig(Optional.of("any"), "https://sharing.example.org", Optional.empty())).isInstanceOf(RateLimitExceededException.class);

        verify(connectorService, never()).validateApiKey("any");
    }
}
