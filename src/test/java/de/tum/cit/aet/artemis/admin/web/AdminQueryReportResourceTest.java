package de.tum.cit.aet.artemis.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.config.performance.IndexCatalog;
import de.tum.cit.aet.artemis.core.config.performance.QueryCollector;
import de.tum.cit.aet.artemis.core.config.performance.QueryProperties;
import de.tum.cit.aet.artemis.core.config.performance.TableIndex;

/**
 * Covers what the admin endpoint returns.
 */
class AdminQueryReportResourceTest {

    private QueryCollector collector;

    private AdminQueryReportResource resource;

    @BeforeEach
    void setUp() {
        collector = new QueryCollector(new QueryProperties());
        IndexCatalog indexCatalog = mock(IndexCatalog.class);
        when(indexCatalog.indexes()).thenReturn(Map.of("participation", List.of(new TableIndex("pk", List.of("id")))));
        resource = new AdminQueryReportResource(collector, indexCatalog);
    }

    private void recordRepeatedQuery() {
        collector.record("select 1", 1, () -> null);
        collector.record("select 1", 2, () -> null);
        collector.finishRequest("GET", "/api/x", null, null);
    }

    @Test
    void theReportHoldsTheCollectedFindingsAndTheIndexesOfTheDatabase() {
        recordRepeatedQuery();

        var response = resource.getReport();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().repeatedQueries()).hasSize(1);
        assertThat(response.getBody().indexes()).containsKey("participation");
        collector.resetRequestState();
    }
}
