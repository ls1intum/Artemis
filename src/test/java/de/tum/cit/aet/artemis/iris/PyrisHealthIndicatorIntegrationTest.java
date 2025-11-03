package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisHealthStatusDTO;

public class PyrisHealthIndicatorIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String MODULE = "module_example";

    private static final String GREEN = "\uD83D\uDFE2";   // 🟢

    private static final String YELLOW = "\uD83D\uDFE1";  // 🟡

    private static final String ORANGE = "\uD83D\uDFE0";  // 🟠

    private static final String RED = "\uD83D\uDD34";     // 🔴

    @Autowired
    private PyrisHealthIndicator pyrisHealthIndicator;

    @BeforeEach
    void setUp() {
        irisRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Ensures all expectations were hit and closes open mocks
        irisRequestMockProvider.verify();
        irisRequestMockProvider.reset();
    }

    @Test
    void shouldReturnUpWhenHealthyAndModulesUp() throws Exception {
        irisRequestMockProvider.mockHealthStatusSuccess(true, Map.of(MODULE, PyrisHealthStatusDTO.ServiceStatus.UP));
        Health health = pyrisHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get(MODULE).toString()).contains(GREEN);
    }

    @Test
    void shouldReturnUpWhenOverallHealthyEvenIfModuleDown() throws Exception {
        irisRequestMockProvider.mockHealthStatusSuccess(true, Map.of(MODULE, PyrisHealthStatusDTO.ServiceStatus.DOWN));

        Health health = pyrisHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get(MODULE).toString()).contains(RED);
    }

    @Test
    void shouldReturnDownWhenNullBody() {
        irisRequestMockProvider.mockHealthNullBody();
        Health health = pyrisHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains(RED).contains("Empty response");
    }

    @Test
    void shouldReturnDownWhenServerError() {
        irisRequestMockProvider.mockHealthStatusFailure();
        Health h = pyrisHealthIndicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("error").toString()).contains(RED).contains("failed");
    }

    @Test
    void shouldReturnDownWhenMalformedJson() {
        irisRequestMockProvider.mockHealthMalformedJson();  // triggers JsonProcessingException
        Health health = pyrisHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains(RED).contains("Incorrect format from Pyris");
    }

    @Test
    void shouldReturnDownWhenTimeout() {
        irisRequestMockProvider.mockHealthTimeout();  // triggers ResourceAccessException
        Health health = pyrisHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains(RED).contains("timed out");
    }

    @Test
    void shouldHandleNullModules() throws Exception {
        // modules = null → flattenModulesInto returns early (null-safe)
        irisRequestMockProvider.mockHealthWithModules(true, null);
        Health health = pyrisHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        // no MODULE key present
        assertThat(health.getDetails()).doesNotContainKey(MODULE);
    }

    @Test
    void shouldPreferErrorOverMetadataAndShowYellowForWarn() throws Exception {
        var module = new PyrisHealthStatusDTO.ModuleStatusDTO(PyrisHealthStatusDTO.ServiceStatus.WARN, "Index not ready", "indexing=90%");
        irisRequestMockProvider.mockHealthWithModules(true, Map.of(MODULE, module));

        Health health = pyrisHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        String summary = health.getDetails().get(MODULE).toString();
        assertThat(summary).contains(YELLOW);            // WARN → 🟡
        assertThat(summary).contains("Index not ready"); // error wins; meta should be ignored
        assertThat(summary).doesNotContain("indexing=90%");
    }

    @Test
    void shouldShowMetadataWhenNoErrorAndOrangeForDegraded() throws Exception {
        var module = new PyrisHealthStatusDTO.ModuleStatusDTO(PyrisHealthStatusDTO.ServiceStatus.DEGRADED, null, "indexing=90%");
        irisRequestMockProvider.mockHealthWithModules(true, Map.of(MODULE, module));

        Health health = pyrisHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        String summary = health.getDetails().get(MODULE).toString();
        assertThat(summary).contains(ORANGE);          // DEGRADED → 🟠
        assertThat(summary).contains("indexing=90%");  // meta shows when no error
    }

    @Test
    void shouldReturnCachedHealthWhenUseCacheTrueAndFresh() throws Exception {
        // one expectation only
        irisRequestMockProvider.mockHealthStatusSuccess(true, Map.of(MODULE, PyrisHealthStatusDTO.ServiceStatus.UP));

        // 1) warm the cache
        Health first = pyrisHealthIndicator.health(false);
        assertThat(first.getStatus()).isEqualTo(Status.UP);

        // 2) request with useCache=true should NOT hit HTTP again
        Health cached = pyrisHealthIndicator.health(true);
        assertThat(cached).isSameAs(first); // same object from cache

        // if a second HTTP had been made, MockRestServiceServer would fail verify()
    }

    @Test
    void shouldRefetchWhenCacheExpiredEvenIfUseCacheTrue() throws Exception {
        // queue BOTH expectations up-front (important!)
        irisRequestMockProvider.mockHealthStatusSuccess(true, Map.of(MODULE, PyrisHealthStatusDTO.ServiceStatus.UP));
        irisRequestMockProvider.mockHealthStatusSuccess(true, Map.of(MODULE, PyrisHealthStatusDTO.ServiceStatus.WARN));

        // 1) warm cache
        Health first = pyrisHealthIndicator.health(false);
        assertThat(first.getDetails().get(MODULE).toString()).contains("\uD83D\uDFE2"); // 🟢

        // 2) expire cache
        ReflectionTestUtils.setField(pyrisHealthIndicator, "lastUpdated", 0L);

        // 3) useCache=true must refetch (consumes 2nd expectation)
        Health second = pyrisHealthIndicator.health(true);
        assertThat(second.getDetails().get(MODULE).toString()).contains("\uD83D\uDFE1"); // 🟡
    }

    @Test
    void shouldSummarizeModuleAsIconOnlyWhenNoErrorNoMetadata() throws Exception {
        var mod = new PyrisHealthStatusDTO.ModuleStatusDTO(PyrisHealthStatusDTO.ServiceStatus.UP, null, "   "); // blank meta
        irisRequestMockProvider.mockHealthWithModules(true, Map.of(MODULE, mod));

        Health h = pyrisHealthIndicator.health();
        // summarizeModule returns "🟢 " (icon + space) when error & meta are empty
        assertThat(h.getDetails().get(MODULE)).isEqualTo("\uD83D\uDFE2 "); // 🟢␠
    }

    @Test
    void shouldReturnDownOnMalformedJson() {
        irisRequestMockProvider.mockHealthMalformedJson();
        Health h = pyrisHealthIndicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("error").toString()).contains("\uD83D\uDD34") // 🔴
                .contains("Incorrect format from Pyris");
    }

    @Test
    void shouldReturnDownOnTimeout() {
        irisRequestMockProvider.mockHealthTimeout();
        Health h = pyrisHealthIndicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("error").toString()).contains("\uD83D\uDD34") // 🔴
                .contains("timed out");
    }

}
