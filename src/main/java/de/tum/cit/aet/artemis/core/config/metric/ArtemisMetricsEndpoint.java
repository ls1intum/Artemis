package de.tum.cit.aet.artemis.core.config.metric;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Inlined replacement for JHipster's {@code JHipsterMetricsEndpoint}.
 * <p>
 * Exposes a custom {@code /management/artemis-metrics} actuator endpoint
 * with JVM thread and memory statistics. This endpoint is extended by
 * {@link de.tum.cit.aet.artemis.core.web.CustomMetricsExtension} which
 * adds active-user counts.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
@Endpoint(id = "artemis-metrics")
public class ArtemisMetricsEndpoint {

    /**
     * Returns all Artemis-specific metrics.
     *
     * @return a map keyed by category (e.g. "jvm") containing metric details
     */
    @ReadOperation
    public Map<String, Map<?, ?>> allMetrics() {
        Map<String, Map<?, ?>> metrics = new LinkedHashMap<>();
        metrics.put("jvm", jvmMetrics());
        return metrics;
    }

    private Map<String, Object> jvmMetrics() {
        Map<String, Object> jvm = new HashMap<>();

        // Thread information
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        jvm.put("threadCount", threadBean.getThreadCount());
        jvm.put("peakThreadCount", threadBean.getPeakThreadCount());
        jvm.put("daemonThreadCount", threadBean.getDaemonThreadCount());

        // Memory information
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        var heap = memoryBean.getHeapMemoryUsage();
        jvm.put("heapUsed", heap.getUsed());
        jvm.put("heapMax", heap.getMax());
        jvm.put("heapCommitted", heap.getCommitted());

        var nonHeap = memoryBean.getNonHeapMemoryUsage();
        jvm.put("nonHeapUsed", nonHeap.getUsed());
        jvm.put("nonHeapCommitted", nonHeap.getCommitted());

        return jvm;
    }
}
