package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.sun.management.OperatingSystemMXBean;

/**
 * Samples system- and JVM-wide CPU utilization while the server integration tests run and renders a summary.
 * <p>
 * Server integration tests run in a single JVM using JUnit's parallel execution, so the headroom reported here
 * (how many of the available cores stayed idle on average) indicates whether increasing the test parallelism could
 * shorten the wall time. The summary mirrors the CPU-usage section printed by the local Playwright E2E runner.
 * <p>
 * Sampling is started by {@link GlobalCleanupListener#testPlanExecutionStarted} and the summary is rendered (and
 * sampling stopped) by {@link TestBucketTiming#printSummary()}.
 */
final class CpuUsageMonitor {

    private static final long SAMPLE_INTERVAL_MS = 2_000;

    private static final int CORES = Runtime.getRuntime().availableProcessors();

    private static final OperatingSystemMXBean OS_BEAN = resolveOperatingSystemBean();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cpu-usage-monitor");
        thread.setDaemon(true);
        return thread;
    });

    private static final Object LOCK = new Object();

    private static ScheduledFuture<?> samplingTask;

    private static int samples;

    private static double sumSystemLoad;

    private static double maxSystemLoad;

    private static int systemLoadSamples;

    private static double sumProcessLoad;

    private static double maxProcessLoad;

    private static int processLoadSamples;

    private static double sumLoadAverage;

    private static int loadAverageSamples;

    private CpuUsageMonitor() {
    }

    /**
     * Starts periodic CPU sampling. No-op if the JVM does not expose CPU metrics or sampling is already running.
     */
    static synchronized void start() {
        if (OS_BEAN == null || samplingTask != null) {
            return;
        }
        samplingTask = SCHEDULER.scheduleAtFixedRate(CpuUsageMonitor::sample, SAMPLE_INTERVAL_MS, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static void sample() {
        double systemLoad = OS_BEAN.getCpuLoad();
        double processLoad = OS_BEAN.getProcessCpuLoad();
        double loadAverage = OS_BEAN.getSystemLoadAverage();

        synchronized (LOCK) {
            samples++;
            // getCpuLoad()/getProcessCpuLoad() can return -1 (e.g. for the first call or on unsupported platforms); only valid readings
            // may contribute to the sums, so each metric needs its own sample count for a correct average
            if (isValidLoad(systemLoad)) {
                sumSystemLoad += systemLoad;
                maxSystemLoad = Math.max(maxSystemLoad, systemLoad);
                systemLoadSamples++;
            }
            if (isValidLoad(processLoad)) {
                sumProcessLoad += processLoad;
                maxProcessLoad = Math.max(maxProcessLoad, processLoad);
                processLoadSamples++;
            }
            if (loadAverage >= 0) {
                sumLoadAverage += loadAverage;
                loadAverageSamples++;
            }
        }
    }

    /**
     * Stops sampling and returns the rendered CPU usage summary, or {@link Optional#empty()} if no samples were collected.
     */
    static synchronized Optional<String> summaryAndStop() {
        if (samplingTask != null) {
            samplingTask.cancel(false);
            samplingTask = null;
        }

        synchronized (LOCK) {
            // without any valid system-load reading there is nothing meaningful to report (the headroom hint would be wrong)
            if (systemLoadSamples == 0) {
                return Optional.empty();
            }

            double avgSystem = sumSystemLoad / systemLoadSamples;
            double avgIdlePercent = Math.max(0, (1.0 - avgSystem) * 100.0);
            String avgJvm = processLoadSamples > 0 ? String.format(Locale.ROOT, "%.1f%%", sumProcessLoad / processLoadSamples * 100) : "n/a";
            String peakJvm = processLoadSamples > 0 ? String.format(Locale.ROOT, "%.1f%%", maxProcessLoad * 100) : "n/a";

            StringBuilder summary = new StringBuilder();
            summary.append('\n');
            summary.append(String.format(Locale.ROOT, "=== Test CPU Usage Summary (%d cores) ===%n", CORES));
            summary.append(String.format(Locale.ROOT, "Avg CPU used:    %.1f%% system (~%.1f of %d cores), %s this JVM%n", avgSystem * 100, avgSystem * CORES, CORES, avgJvm));
            summary.append(String.format(Locale.ROOT, "Peak CPU used:   %.1f%% system, %s this JVM%n", maxSystemLoad * 100, peakJvm));
            if (loadAverageSamples > 0) {
                summary.append(String.format(Locale.ROOT, "Avg system load: %.2f (1m, %d cores)%n", sumLoadAverage / loadAverageSamples, CORES));
            }
            summary.append(String.format(Locale.ROOT, "Samples:         %d valid of %d (every %ds)%n", systemLoadSamples, samples, SAMPLE_INTERVAL_MS / 1000));
            summary.append("Parallelization: ").append(headroomHint(avgIdlePercent)).append('\n');

            return Optional.of(summary.toString());
        }
    }

    private static String headroomHint(double avgIdlePercent) {
        if (avgIdlePercent > 40) {
            return String.format(Locale.ROOT, "✓ Significant headroom (%.0f%% idle on average) — try more parallel test threads", avgIdlePercent);
        }
        if (avgIdlePercent > 20) {
            return String.format(Locale.ROOT, "~ Moderate headroom (%.0f%% idle on average) — a few more parallel test threads may help", avgIdlePercent);
        }
        return String.format(Locale.ROOT, "✗ CPU mostly saturated (%.0f%% idle on average) — more parallelism is unlikely to help", avgIdlePercent);
    }

    private static boolean isValidLoad(double load) {
        return load >= 0 && load <= 1 && Double.isFinite(load);
    }

    private static OperatingSystemMXBean resolveOperatingSystemBean() {
        // com.sun.management.OperatingSystemMXBean exposes getCpuLoad()/getProcessCpuLoad(); fall back to null on non-HotSpot JVMs.
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean operatingSystemBean) {
            return operatingSystemBean;
        }
        return null;
    }
}
