package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JhiMetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ObjectMapper objectMapper;  // Jackson ObjectMapper to parse JSON

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getMetricsTest() throws Exception {
        var result = request.get("/management/jhimetrics", HttpStatus.OK, String.class);

        JsonNode rootNode = objectMapper.readTree(result);
        // Perform assertions on the parsed JSON data
        assertThat(rootNode.has("jvm")).isTrue();
        assertThat(rootNode.path("jvm").has("G1 Old Gen")).isTrue();
        assertThat(rootNode.path("jvm").path("G1 Old Gen").has("committed")).isTrue();
        assertThat(rootNode.path("jvm").path("G1 Old Gen").has("max")).isTrue();
        assertThat(rootNode.path("jvm").path("G1 Old Gen").has("used")).isTrue();

        assertThat(rootNode.has("http.server.requests")).isTrue();
        assertThat(rootNode.path("http.server.requests").path("all").has("count")).isTrue();

        assertThat(rootNode.has("cache")).isTrue();
        assertThat(rootNode.path("cache").path("features").path("cache.size").asDouble()).isEqualTo(8.0);

        assertThat(rootNode.has("garbageCollector")).isTrue();
        assertThat(rootNode.path("garbageCollector").has("jvm.gc.max.data.size")).isTrue();
        assertThat(rootNode.path("garbageCollector").path("jvm.gc.pause").has("max")).isTrue();

        assertThat(rootNode.has("processMetrics")).isTrue();
        assertThat(rootNode.path("processMetrics").path("system.cpu.count").asInt()).isEqualTo(16);
        assertThat(rootNode.path("processMetrics").has("process.cpu.usage")).isTrue();

        assertThat(rootNode.has("customMetrics")).isTrue();
        assertThat(rootNode.path("customMetrics").path("activeUsers").asInt()).isEqualTo(0);
    }
}
