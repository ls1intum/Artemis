package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateHealthIndicator;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedCollectionCountDTO;
import de.tum.cit.aet.artemis.globalsearch.service.CoverageRecomputeService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService;
import de.tum.cit.aet.artemis.iris.api.IrisHealthApi;

class IngestionCoverageResourceTest {

    private final CoverageRecomputeService coverageRecomputeService = mock(CoverageRecomputeService.class);

    private final IngestionCoverageResource resource = new IngestionCoverageResource(Optional.of(mock(WeaviateHealthIndicator.class)),
            mock(IngestionCoverageWeaviateReadService.class), coverageRecomputeService, mock(Environment.class), Optional.<IrisHealthApi>empty());

    @Test
    void overviewPreservesCollectionOrderZeroAndUnavailableCounts() {
        var health = mock(WeaviateHealthIndicator.class);
        when(health.health()).thenReturn(Health.up().withDetail("Address", "http://weaviate:8080").build());
        var reads = mock(IngestionCoverageWeaviateReadService.class);
        when(reads.countPrefixedCollection(SearchableEntitySchema.COLLECTION_NAME)).thenReturn(OptionalLong.of(0));
        when(reads.countExternalCollection("Lectures")).thenReturn(OptionalLong.of(42));
        when(reads.countExternalCollection("LectureTranscriptions")).thenReturn(OptionalLong.empty());
        when(reads.countExternalCollection("LectureUnitSegments")).thenReturn(OptionalLong.of(7));
        when(reads.countExternalCollection("LectureUnits")).thenReturn(OptionalLong.empty());
        var iris = mock(IrisHealthApi.class);
        when(iris.isReachable()).thenReturn(true);
        var environment = new MockEnvironment().withProperty("artemis.iris.enabled", "true");
        var controller = new IngestionCoverageResource(Optional.of(health), reads, coverageRecomputeService, environment, Optional.of(iris));

        var response = controller.getIndexOverview();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new IndexOverviewDTO(true, "http://weaviate:8080", true, true,
                List.of(IndexedCollectionCountDTO.of(SearchableEntitySchema.COLLECTION_NAME, 0), IndexedCollectionCountDTO.of("Lectures", 42),
                        IndexedCollectionCountDTO.unavailable("LectureTranscriptions"), IndexedCollectionCountDTO.of("LectureUnitSegments", 7),
                        IndexedCollectionCountDTO.unavailable("LectureUnits"))));
    }

    @Test
    void overviewDistinguishesDisabledIrisFromEnabledButUnreachableIris() {
        var health = mock(WeaviateHealthIndicator.class);
        when(health.health()).thenReturn(Health.down().withDetail("Address", "http://weaviate:8080").build());
        var reads = mock(IngestionCoverageWeaviateReadService.class);
        when(reads.countPrefixedCollection(any())).thenReturn(OptionalLong.empty());
        when(reads.countExternalCollection(any())).thenReturn(OptionalLong.empty());
        var environment = new MockEnvironment().withProperty("artemis.iris.enabled", "false");
        var disabled = new IngestionCoverageResource(Optional.of(health), reads, coverageRecomputeService, environment, Optional.empty()).getIndexOverview().getBody();

        assertThat(disabled).isNotNull();
        assertThat(disabled.weaviateReachable()).isFalse();
        assertThat(disabled.weaviateAddress()).isEqualTo("http://weaviate:8080");
        assertThat(disabled.irisEnabled()).isFalse();
        assertThat(disabled.irisReachable()).isFalse();
        assertThat(disabled.collections()).hasSize(5).allSatisfy(collection -> {
            assertThat(collection.readable()).isFalse();
            assertThat(collection.count()).isNull();
        });

        environment.setProperty("artemis.iris.enabled", "true");
        var iris = mock(IrisHealthApi.class);
        when(iris.isReachable()).thenReturn(false);
        var unreachable = new IngestionCoverageResource(Optional.of(health), reads, coverageRecomputeService, environment, Optional.of(iris)).getIndexOverview().getBody();
        assertThat(unreachable).isNotNull();
        assertThat(unreachable.irisEnabled()).isTrue();
        assertThat(unreachable.irisReachable()).isFalse();
    }

    @Test
    void overviewJsonRetainsFalseAndZeroButOmitsUnavailableCounts() {
        var overview = new IndexOverviewDTO(false, "http://weaviate:8080", false, false,
                List.of(IndexedCollectionCountDTO.of("Lectures", 0), IndexedCollectionCountDTO.unavailable("LectureUnits")));
        var mapper = JsonObjectMapper.get();
        var json = mapper.readTree(mapper.writeValueAsString(overview));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("weaviateReachable", "weaviateAddress", "irisEnabled", "irisReachable", "collections");
        assertThat(json.get("weaviateReachable").isBoolean()).isTrue();
        assertThat(json.get("weaviateReachable").booleanValue()).isFalse();
        assertThat(json.get("irisEnabled").isBoolean()).isTrue();
        assertThat(json.get("irisEnabled").booleanValue()).isFalse();
        assertThat(json.get("irisReachable").isBoolean()).isTrue();
        assertThat(json.get("irisReachable").booleanValue()).isFalse();
        var zero = json.get("collections").get(0);
        assertThat(zero.get("collection").stringValue()).isEqualTo("Lectures");
        assertThat(zero.get("count").isIntegralNumber()).isTrue();
        assertThat(zero.get("count").longValue()).isZero();
        assertThat(zero.get("readable").booleanValue()).isTrue();
        var unavailable = json.get("collections").get(1);
        assertThat(unavailable.get("collection").stringValue()).isEqualTo("LectureUnits");
        assertThat(unavailable.has("count")).isFalse();
        assertThat(unavailable.get("readable").isBoolean()).isTrue();
        assertThat(unavailable.get("readable").booleanValue()).isFalse();
    }

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
