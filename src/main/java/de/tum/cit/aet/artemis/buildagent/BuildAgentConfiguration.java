package de.tum.cit.aet.artemis.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import de.tum.cit.aet.artemis.buildagent.service.DockerUtil;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

/**
 * Creates beans needed for the local CI system.
 * This includes a Docker client and an executor service that manages the queue of build jobs.
 */
@Configuration
@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
public class BuildAgentConfiguration {

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private ThreadPoolExecutor buildExecutor;

    private int threadPoolSize = 0;

    private DockerClient dockerClient;

    private volatile boolean dockerAvailable = false;

    private static final Logger log = LoggerFactory.getLogger(BuildAgentConfiguration.class);

    @Value("${artemis.continuous-integration.docker-connection-uri}")
    String dockerConnectionUri;

    @Value("${artemis.continuous-integration.concurrent-build-size:1}")
    int concurrentBuildSize;

    @Value("${artemis.continuous-integration.specify-concurrent-builds:false}")
    boolean specifyConcurrentBuilds;

    @Value("${artemis.continuous-integration.pause-after-consecutive-failed-jobs:100}")
    int pauseAfterConsecutiveFailedJobs;

    public BuildAgentConfiguration(ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    /**
     * Initializes docker client and build executor when bean is created
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void onApplicationReady() {
        buildExecutor = createBuildExecutor();
        dockerClient = createDockerClient();
        probeDockerAvailability();
    }

    public ThreadPoolExecutor getBuildExecutor() {
        return buildExecutor;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public int getPauseAfterConsecutiveFailedJobs() {
        return pauseAfterConsecutiveFailedJobs;
    }

    public boolean isDockerAvailable() {
        return dockerAvailable;
    }

    public void setDockerAvailable(boolean dockerAvailable) {
        this.dockerAvailable = dockerAvailable;
    }

    /**
     * Creates a HostConfig object that is used to configure the Docker container for build jobs.
     * The configuration is based on the default Docker flags for build jobs as specified in artemis.continuous-integration.build.
     *
     * @return The HostConfig bean.
     */
    @NonNull
    public HostConfig hostConfig() {
        BigDecimal cpuCount = null;
        long cpuPeriod = 100000L;
        long memory = 0;
        long memorySwap = 0;
        long pidsLimit = 0;

        List<String> defaultDockerFlags = programmingLanguageConfiguration.getDefaultDockerFlags();

        for (int i = 0; i < defaultDockerFlags.size(); i += 2) {
            String flag = defaultDockerFlags.get(i);
            String value = defaultDockerFlags.get(i + 1);

            switch (flag) {
                case "--cpus" -> cpuCount = new BigDecimal(value.replace("\"", "").trim());
                case "--memory" -> memory = parseMemoryString(value);
                case "--memory-swap" -> memorySwap = parseMemoryString(value);
                case "--pids-limit" -> pidsLimit = Long.parseLong(value.replace("\"", "").trim());
                default -> throw new LocalCIException("Unknown docker flag: " + flag);
            }
        }

        log.info("Using build job container docker host config with CPU(s): {}, memory: {}, memory swap: {}, pids limit: {}.", cpuCount != null ? cpuCount : "unlimited",
                formatMemory(memory), formatMemory(memorySwap), pidsLimit);

        long cpuQuota = 0;
        if (cpuCount != null) {
            if (cpuCount.signum() <= 0) {
                throw new IllegalArgumentException("Docker --cpus must be greater than zero");
            }
            cpuQuota = cpuCount.multiply(BigDecimal.valueOf(cpuPeriod)).setScale(0, RoundingMode.DOWN).longValueExact();
            if (cpuQuota == 0) {
                throw new IllegalArgumentException("Docker --cpus is below the supported CPU quota precision");
            }
        }
        return HostConfig.newHostConfig().withCpuQuota(cpuQuota).withCpuPeriod(cpuPeriod).withMemory(memory).withMemorySwap(memorySwap).withPidsLimit(pidsLimit)
                .withAutoRemove(true);
    }

    /**
     * Converts bytes into a human-readable format (KB, MB, or GB).
     *
     * @param bytes The number of bytes.
     * @return A string representing the memory size in KB, MB, or GB.
     */
    public static String formatMemory(long bytes) {
        if (bytes < 1024) {
            return bytes + " Bytes";
        }
        else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        else if (bytes < 1024 * 1024 * 1024) {
            return (bytes / (1024 * 1024)) + " MB";
        }
        else {
            return "%.1f GB".formatted(bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Creates an thread pool executor that manages the queue of build jobs.
     *
     * @return The executor service.
     */
    private ThreadPoolExecutor createBuildExecutor() {
        int threadPoolSize;

        if (specifyConcurrentBuilds) {
            threadPoolSize = concurrentBuildSize;
        }
        else {
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            threadPoolSize = Math.max(1, (availableProcessors - 2) / 2);
        }
        this.threadPoolSize = threadPoolSize;

        ThreadFactory customThreadFactory = BasicThreadFactory.builder().namingPattern("local-ci-build-%d")
                .uncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in thread {}", t.getName(), e)).build();

        RejectedExecutionHandler customRejectedExecutionHandler = (runnable, executor) -> {
            throw new RejectedExecutionException("Task " + runnable.toString() + " rejected from " + executor.toString());
        };

        log.debug("Using ExecutorService with thread pool size {}.", threadPoolSize);
        return new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1), customThreadFactory, customRejectedExecutionHandler);
    }

    /**
     * Creates a Docker client that is used to communicate with the Docker daemon.
     * Configures connection and response timeouts to prevent hanging on unresponsive Docker daemons.
     * <p>
     * The response timeout (45s) applies to each chunk of data received, not the total operation time.
     * For streaming operations like image pulls, Docker sends progress updates regularly, so this
     * timeout only fires if the daemon becomes completely unresponsive.
     *
     * @return The DockerClient.
     */
    public DockerClient createDockerClient() {
        log.debug("Create bean dockerClient");
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerConnectionUri).build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig())
                .connectionTimeout(java.time.Duration.ofSeconds(10)).responseTimeout(java.time.Duration.ofSeconds(45)).build();
        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

        log.debug("Docker client created with connection URI: {}", dockerConnectionUri);

        return dockerClient;
    }

    /*-------------Helper methods-----------------*/

    private static long parseMemoryString(String memoryString) {
        if (memoryString.endsWith("g\"")) {
            return Long.parseLong(memoryString.replaceAll("[^0-9]", "")) * 1024L * 1024L * 1024L;
        }
        else if (memoryString.endsWith("m\"")) {
            return Long.parseLong(memoryString.replaceAll("[^0-9]", "")) * 1024L * 1024L;
        }
        else if (memoryString.endsWith("k\"")) {
            return Long.parseLong(memoryString.replaceAll("[^0-9]", "")) * 1024L;
        }
        else {
            return Long.parseLong(memoryString);
        }
    }

    private synchronized void shutdownBuildExecutor() {
        ThreadPoolExecutor executor = buildExecutor;
        if (executor != null) {
            if (!executor.isShutdown()) {
                executor.shutdown();
            }
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("Build executor did not stop after forced cancellation; refusing to replace it with another executor");
                        return;
                    }
                }
            }
            catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("Executor termination interrupted", e);
                return;
            }
        }
        if (buildExecutor == executor) {
            buildExecutor = null;
        }
    }

    private void closeDockerClient() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            }
            catch (IOException e) {
                log.error("Error closing Docker client", e);
            }
            dockerClient = null;
        }
    }

    public synchronized void closeBuildAgentServices() {
        dockerAvailable = false;
        shutdownBuildExecutor();
        closeDockerClient();
    }

    /** Stops normal LocalCI build execution while leaving the Docker client available for generation sandboxes. */
    public synchronized void pauseBuildJobs() {
        shutdownBuildExecutor();
    }

    /** Opens any missing LocalCI executor and Docker client, then probes Docker availability. */
    public synchronized void openBuildAgentServices() {
        if (buildExecutor != null && buildExecutor.isShutdown() && !buildExecutor.isTerminated()) {
            throw new LocalCIException("The previous build executor is still stopping; the build agent cannot resume yet");
        }
        if (buildExecutor == null || buildExecutor.isTerminated()) {
            this.buildExecutor = createBuildExecutor();
        }
        if (dockerClient == null) {
            this.dockerClient = createDockerClient();
        }
        probeDockerAvailability();
    }

    /**
     * Synchronously probes Docker availability by executing a lightweight version command.
     * Sets {@link #dockerAvailable} based on whether Docker responds successfully.
     */
    private void probeDockerAvailability() {
        try {
            dockerClient.versionCmd().exec();
            dockerAvailable = true;
        }
        catch (Exception e) {
            dockerAvailable = false;
            if (DockerUtil.isDockerNotAvailable(e)) {
                log.warn("Docker is not available: {}", e.getMessage());
            }
            else {
                log.warn("Failed to probe Docker availability", e);
            }
        }
    }
}
