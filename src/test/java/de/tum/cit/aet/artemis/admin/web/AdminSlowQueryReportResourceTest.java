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
import de.tum.cit.aet.artemis.core.config.performance.SlowQueryCollector;
import de.tum.cit.aet.artemis.core.config.performance.SlowQueryProperties;
import de.tum.cit.aet.artemis.core.config.performance.TableIndex;

/**
 * Covers what the admin endpoint returns and clears.
 */
class AdminSlowQueryReportResourceTest {

    private SlowQueryCollector collector;

    private AdminSlowQueryReportResource resource;

    @BeforeEach
    void setUp() {
        collector = new SlowQueryCollector(new SlowQueryProperties());
        IndexCatalog indexCatalog = mock(IndexCatalog.class);
        when(indexCatalog.indexes()).thenReturn(Map.of("participation", List.of(new TableIndex("pk", List.of("id"), true))));
        resource = new AdminSlowQueryReportResource(collector, indexCatalog);
    }

    private void recordSlowQuery() {
        collector.record("select 1", 1, 500, 0, () -> null, "GET", "/api/x", null, null, "tomcat-1");
    }

    @Test
    void theReportHoldsTheCollectedFindingsAndTheIndexesOfTheDatabase() {
        recordSlowQuery();

        var response = resource.getReport();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().slowQueryCount()).isEqualTo(1);
        assertThat(response.getBody().indexes()).containsKey("participation");
        collector.resetRequestState();
    }

    @Test
    void resetStartsANewCollectionWindow() {
        recordSlowQuery();

        assertThat(resource.reset().getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(resource.getReport().getBody().slowQueryCount()).isZero();
        collector.resetRequestState();
    }
}
