package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class JhiMetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getMetricsTest() throws Exception {
        var result = request.get("/management/jhimetrics", HttpStatus.OK, String.class);

        JsonNode rootNode = objectMapper.readTree(result);

        assertThat(rootNode.has("jvm")).isTrue();
        assertThat(rootNode.path("jvm").has("G1 Old Gen")).isTrue();
        assertThat(rootNode.path("jvm").path("G1 Old Gen").has("committed")).isTrue();
        assertThat(rootNode.path("jvm").path("G1 Old Gen").has("max")).isTrue();
        assertThat(rootNode.path("jvm").path("G1 Old Gen").has("used")).isTrue();

        assertThat(rootNode.has("http.server.requests")).isTrue();
        assertThat(rootNode.path("http.server.requests").path("all").has("count")).isTrue();

        assertThat(rootNode.has("cache")).isTrue();

        assertThat(rootNode.has("garbageCollector")).isTrue();
        assertThat(rootNode.path("garbageCollector").has("jvm.gc.max.data.size")).isTrue();
        assertThat(rootNode.path("garbageCollector").path("jvm.gc.pause").has("max")).isTrue();

        assertThat(rootNode.has("processMetrics")).isTrue();
        assertThat(rootNode.path("processMetrics").has("process.cpu.usage")).isTrue();

        assertThat(rootNode.has("customMetrics")).isTrue();
    }
}
