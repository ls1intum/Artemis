package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateHealthIndicator;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedCollectionCountDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionCoverageDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.service.CoverageRecomputeService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService;
import de.tum.cit.aet.artemis.iris.api.IrisHealthApi;

class IngestionCoverageResourceTest {

    private final CoverageRecomputeService coverageRecomputeService = mock(CoverageRecomputeService.class);

    private final IngestionCoverageResource resource = new IngestionCoverageResource(Optional.of(mock(WeaviateHealthIndicator.class)),
            mock(IngestionCoverageWeaviateReadService.class), coverageRecomputeService, mock(Environment.class), Optional.<IrisHealthApi>empty());

    @ParameterizedTest
    @ValueSource(strings = { "getStoredCoverage", "getLiveCoveragePage" })
    void coverageEndpointsDeclareTotalCountHeaderAndPaginationParameters(String methodName) {
        var method = Arrays.stream(IngestionCoverageResource.class.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        var response = method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.responseCode()).isEqualTo("200");
        var pageable = Arrays.stream(method.getParameters()).filter(parameter -> parameter.getType() == org.springframework.data.domain.Pageable.class).findFirst().orElseThrow();
        assertThat(pageable.isAnnotationPresent(org.springdoc.core.annotations.ParameterObject.class)).isTrue();
        assertThat(response.headers()).singleElement().satisfies(header -> {
            assertThat(header.name()).isEqualTo("X-Total-Count");
            assertThat(header.schema().type()).isEqualTo("integer");
            assertThat(header.schema().format()).isEqualTo("int64");
        });
    }

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

    @ParameterizedTest
    @EnumSource(value = CoverageRecomputeService.RecomputeOutcome.class, names = { "FAILED", "LOCK_TIMEOUT" })
    void storedCoverageReturns503InsteadOfRowsAfterARecomputeFailure(CoverageRecomputeService.RecomputeOutcome outcome) {
        when(coverageRecomputeService.triggerRecomputeIfStale()).thenReturn(outcome);
        setRequestContext("/api/global-search/admin/coverage");

        var response = resource.getStoredCoverage(null, null, null, PageRequest.of(0, 25));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNull();
        verify(coverageRecomputeService, never()).readStoredCoverage(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(CoverageRecomputeService.RecomputeOutcome.class)
    void refreshIsBodylessAndSucceedsOnlyAfterRecomputation(CoverageRecomputeService.RecomputeOutcome outcome) {
        when(coverageRecomputeService.forceRecompute()).thenReturn(outcome);

        var response = resource.refreshCoverage();

        assertThat(response.getStatusCode()).isEqualTo(outcome == CoverageRecomputeService.RecomputeOutcome.RECOMPUTED ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().isEmpty()).isTrue();
        verify(coverageRecomputeService).forceRecompute();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void coverageReturnsArrayAndPaginationHeaders(boolean live) {
        var pageable = PageRequest.of(0, 1);
        var row = new IngestionCoverageDTO(1, null, null, false, null, IngestionCoverageStatus.EMPTY, 0, ZonedDateTime.parse("2026-10-21T10:00:00Z"), null, List.of());
        var page = new PageImpl<>(List.of(row), pageable, 57);
        String path = live ? "/api/global-search/admin/coverage/page" : "/api/global-search/admin/coverage";
        setRequestContext(path);
        when(coverageRecomputeService.triggerRecomputeIfStale()).thenReturn(CoverageRecomputeService.RecomputeOutcome.RECOMPUTED);
        when(coverageRecomputeService.readStoredCoverage(IngestionCoverageStatus.EMPTY, false, " C++ & Über ", pageable)).thenReturn(page);
        when(coverageRecomputeService.computeLiveCoveragePage(" C++ & Über ", pageable)).thenReturn(page);

        var response = live ? resource.getLiveCoveragePage(" C++ & Über ", pageable) : resource.getStoredCoverage(IngestionCoverageStatus.EMPTY, false, " C++ & Über ", pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(row);
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("57");
        assertThat(response.getHeaders().getFirst("Link")).contains(path, "page=1", "rel=\"next\"", "page=56", "rel=\"last\"");
    }

    @Test
    void liveCoverageAllows200ButRejects201BeforeComputing() {
        setRequestContext("/api/global-search/admin/coverage");
        var largestPage = PageRequest.of(0, 200);
        when(coverageRecomputeService.computeLiveCoveragePage(null, largestPage)).thenReturn(new PageImpl<>(List.of(), largestPage, 0));
        var response = resource.getLiveCoveragePage(null, largestPage);
        assertThat(response.getBody()).isEmpty();
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("0");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resource.getLiveCoveragePage(null, PageRequest.of(0, 201))).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(coverageRecomputeService, never()).computeLiveCoveragePage(null, PageRequest.of(0, 201));
    }

    @Test
    void coverageJsonRetainsNullEmptyFalseZeroAndOffsetMeaning() {
        var mapper = JsonObjectMapper.get();
        var computedAt = ZonedDateTime.parse("2026-10-21T12:00:00+02:00");
        var row = new IngestionCoverageDTO(1, null, null, false, null, IngestionCoverageStatus.EMPTY, 0, computedAt, null, List.of());
        var json = mapper.readTree(mapper.writeValueAsString(row));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("courseId", "courseTitle", "releaseDate", "active", "semester", "status", "coverageGapScore", "computedAt",
                "lastIngestedAt", "typeCounts");
        for (String property : List.of("courseTitle", "releaseDate", "semester", "lastIngestedAt")) {
            assertThat(json.get(property).isNull()).isTrue();
        }
        assertThat(json.get("typeCounts").isArray()).isTrue();
        assertThat(json.get("typeCounts")).isEmpty();
        assertThat(json.get("active").booleanValue()).isFalse();
        assertThat(json.get("coverageGapScore").intValue()).isZero();
        assertThat(ZonedDateTime.parse(json.get("computedAt").stringValue()).toInstant()).isEqualTo(computedAt.toInstant());
        var emptyTitle = new IngestionCoverageDTO(1, "", null, false, "", IngestionCoverageStatus.EMPTY, 0, computedAt, null, List.of());
        var emptyMetadata = mapper.readTree(mapper.writeValueAsString(emptyTitle));
        assertThat(emptyMetadata.get("courseTitle").stringValue()).isEmpty();
        assertThat(emptyMetadata.get("semester").stringValue()).isEmpty();
    }

    @Test
    void persistedTypeCountsRoundTripZeroCountersAndIgnoreLegacyFields() {
        var mapper = JsonObjectMapper.get();
        var persistedMapper = mapper.rebuild().enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        var count = new IngestionTypeCountDTO("exercise", 0, 0, 0, 0);
        var json = mapper.readTree(mapper.writeValueAsString(count));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("type", "expected", "indexed", "missing", "orphaned");
        assertThat(persistedMapper.readValue(mapper.writeValueAsString(count), IngestionTypeCountDTO.class)).isEqualTo(count);
        assertThat(persistedMapper.readValue("{\"type\":\"exercise\",\"expected\":0,\"indexed\":0,\"missing\":0,\"orphaned\":0,\"legacy\":true}", IngestionTypeCountDTO.class))
                .isEqualTo(count);
    }

    private static void setRequestContext(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
