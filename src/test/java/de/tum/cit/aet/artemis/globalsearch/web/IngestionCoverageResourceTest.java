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
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

    private final IngestionCoverageResource resource = new IngestionCoverageResource(Optional.of(mock(WeaviateHealthIndicator.class)),
            mock(IngestionCoverageWeaviateReadService.class), coverageRecomputeService, mock(Environment.class), Optional.<IrisHealthApi>empty());

    @Test
    void startsForOpenApiDocsWithoutAWeaviateHealthIndicator() {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("core"))
                .withPropertyValues("artemis.weaviate.enabled=true", "artemis.openapi-docs-generation=true")
                .withBean(IngestionCoverageWeaviateReadService.class, () -> mock(IngestionCoverageWeaviateReadService.class))
                .withBean(CoverageRecomputeService.class, () -> coverageRecomputeService).withUserConfiguration(IngestionCoverageResource.class).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IngestionCoverageResource.class);
                    assertThat(context.getBean(IngestionCoverageResource.class)).isNotNull();
                    assertThat(context).doesNotHaveBean(WeaviateHealthIndicator.class);
                });
    }

    @Test
    void readsTheLiveWeaviateHealthWhenAvailable() {
        var healthIndicator = mock(WeaviateHealthIndicator.class);
        when(healthIndicator.health()).thenReturn(Health.up().withDetail("Address", "http://weaviate:8080").build());
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("core"))
                .withPropertyValues("artemis.weaviate.enabled=true", "artemis.iris.enabled=false").withBean(WeaviateHealthIndicator.class, () -> healthIndicator)
                .withBean(IngestionCoverageWeaviateReadService.class, () -> mock(IngestionCoverageWeaviateReadService.class))
                .withBean(CoverageRecomputeService.class, () -> coverageRecomputeService).withUserConfiguration(IngestionCoverageResource.class).run(context -> {
                    var overview = context.getBean(IngestionCoverageResource.class).getIndexOverview().getBody();
                    assertThat(overview).isNotNull();
                    assertThat(overview.weaviateReachable()).isTrue();
                    assertThat(overview.weaviateAddress()).isEqualTo("http://weaviate:8080");
                    verify(healthIndicator).health();
                });
    }

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
