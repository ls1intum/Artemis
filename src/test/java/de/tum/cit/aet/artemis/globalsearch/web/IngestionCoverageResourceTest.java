package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateHealthIndicator;
import de.tum.cit.aet.artemis.globalsearch.service.CoverageRecomputeService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService;
import de.tum.cit.aet.artemis.iris.api.IrisHealthApi;

class IngestionCoverageResourceTest {

    private final CoverageRecomputeService coverageRecomputeService = mock(CoverageRecomputeService.class);

    private final IngestionCoverageResource resource = new IngestionCoverageResource(mock(WeaviateHealthIndicator.class), mock(IngestionCoverageWeaviateReadService.class),
            coverageRecomputeService, mock(Environment.class), Optional.<IrisHealthApi>empty());

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void storedCoverageReturns503InsteadOfRowsAfterARecomputeFailure() {
        when(coverageRecomputeService.triggerRecomputeIfStale()).thenReturn(CoverageRecomputeService.RecomputeOutcome.FAILED);
        setRequestContext();

        var response = resource.getStoredCoverage(null, null, null, PageRequest.of(0, 25));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(coverageRecomputeService, never()).readStoredCoverage(any(), any(), any(), any());
    }

    @Test
    void refreshReturns503ForAnExplicitFailedAttempt() {
        when(coverageRecomputeService.forceRecompute()).thenReturn(CoverageRecomputeService.RecomputeOutcome.FAILED);

        assertThat(resource.refreshCoverage().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static void setRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/global-search/admin/coverage");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
