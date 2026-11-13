package de.tum.cit.aet.artemis.athena;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.athena.config.AthenaAuthorizationInterceptor;
import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.athena.web.internal.AthenaInternalResource;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;

/**
 * Unit tests for the handling of a missing Athena secret on the Artemis side.
 */
class AthenaSecretHandlingTest {

    private final RateLimitService rateLimitService = mock(RateLimitService.class);

    @Test
    void testInternalResourceRefusesEmptyHeaderWhenNoSecretIsConfigured() {
        AthenaRepositoryExportService exportService = mock(AthenaRepositoryExportService.class);
        AthenaInternalResource resource = new AthenaInternalResource(exportService, rateLimitService, "");

        assertThatThrownBy(() -> resource.getTestRepository(1L, "")).isInstanceOf(AccessForbiddenException.class);
        verifyNoInteractions(exportService);
    }

    @Test
    void testInternalResourceRefusesEmptyHeaderWhenSecretIsConfigured() {
        AthenaRepositoryExportService exportService = mock(AthenaRepositoryExportService.class);
        AthenaInternalResource resource = new AthenaInternalResource(exportService, rateLimitService, "configured-secret");

        assertThatThrownBy(() -> resource.getTestRepository(1L, "")).isInstanceOf(AccessForbiddenException.class);
        verifyNoInteractions(exportService);
    }

    @Test
    void testWrongSecretKeepsTheTokenAndAnExhaustedBudgetRefusesTheCheck() {
        AthenaRepositoryExportService exportService = mock(AthenaRepositoryExportService.class);
        AthenaInternalResource resource = new AthenaInternalResource(exportService, rateLimitService, "configured-secret");

        assertThatThrownBy(() -> resource.getTestRepository(1L, "wrong")).isInstanceOf(AccessForbiddenException.class);
        verify(rateLimitService, never()).refundServiceCredentialBudget();

        doThrow(new RateLimitExceededException(60)).when(rateLimitService).requireServiceCredentialBudget();
        assertThatThrownBy(() -> resource.getTestRepository(1L, "configured-secret")).isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(exportService);
    }

    @Test
    void testRequestsToAthenaAreNotSentWithoutSecret() {
        AthenaAuthorizationInterceptor interceptor = new AthenaAuthorizationInterceptor();
        ReflectionTestUtils.setField(interceptor, "secret", " ");
        ReflectionTestUtils.setField(interceptor, "artemisServerUrl", "http://localhost");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        assertThatThrownBy(() -> interceptor.intercept(mock(HttpRequest.class), new byte[0], execution)).isInstanceOf(IOException.class).hasMessageContaining("not configured");
        verifyNoInteractions(execution);
    }
}
