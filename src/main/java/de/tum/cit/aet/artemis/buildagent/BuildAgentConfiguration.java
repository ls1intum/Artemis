package de.tum.cit.aet.artemis.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import de.tum.cit.aet.artemis.core.config.FullStartupEvent;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;

/**
 * Creates beans needed for the local CI system.
 * This includes a Docker client and an executor service that manages the queue of build jobs.
 */
@Configuration
@Profile(PROFILE_BUILDAGENT)
@Lazy
public class BuildAgentConfiguration {

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private ThreadPoolExecutor buildExecutor;

    private final AtomicInteger threadPoolSize = new AtomicInteger(0);

    private DockerClient dockerClient;

    private static final Logger log = LoggerFactory.getLogger(BuildAgentConfiguration.class);

    @Value("${artemis.continuous-integration.docker-connection-uri}")
    String dockerConnectionUri;

    @Value("${artemis.continuous-integration.concurrent-builds.default:1}")
    int concurrentBuildsDefault;

    @Value("${artemis.continuous-integration.concurrent-builds.maximum:0}")
    int concurrentBuildsMaximum;

    @Value("${artemis.continuous-integration.specify-concurrent-builds:false}")
    boolean specifyConcurrentBuilds;

    @Value("${artemis.continuous-integration.pause-after-consecutive-failed-jobs:100}")
    int pauseAfterConsecutiveFailedJobs;

    public BuildAgentConfiguration(ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @EventListener(FullStartupEvent.class)
    public void onApplicationReady() {
        buildExecutor = createBuildExecutor();
        dockerClient = createDockerClient();
        if (concurrentBuildsMaximum <= 0) {
            concurrentBuildsMaximum = Runtime.getRuntime().availableProcessors();
        }
    }

    public ThreadPoolExecutor getBuildExecutor() {
        return buildExecutor;
    }

    public int getThreadPoolSize() {
        return threadPoolSize.get();
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public int getPauseAfterConsecutiveFailedJobs() {
        return pauseAfterConsecutiveFailedJobs;
    }

    public int getConcurrentBuildsMaximum() {
        return concurrentBuildsMaximum;
    }

    /**
     * Dynamically adjusts the thread pool size for concurrent build jobs.
     *
     * @param newConcurrentBuildSize the new number of concurrent builds
     * @return true if the adjustment was successful, false otherwise
     */
    public synchronized boolean adjustConcurrentBuildSize(int newConcurrentBuildSize) {
        if (newConcurrentBuildSize <= 0) {
            log.error("Invalid concurrent build size: {}. Must be greater than 0.", newConcurrentBuildSize);
            return false;
        }

        if (newConcurrentBuildSize > concurrentBuildsMaximum) {
            log.error("Invalid concurrent build size: {}. Must not exceed maximum of {}.", newConcurrentBuildSize, concurrentBuildsMaximum);
            return false;
        }

        if (buildExecutor == null) {
            log.error("Build executor is not initialized yet");
            return false;
        }

        int currentSize = threadPoolSize.get();

        // We need this check since maximumPoolSize >= corePoolSize should hold at all times.
        if (newConcurrentBuildSize > currentSize) {
            buildExecutor.setMaximumPoolSize(newConcurrentBuildSize);
            buildExecutor.setCorePoolSize(newConcurrentBuildSize);
        }
        else {
            buildExecutor.setCorePoolSize(newConcurrentBuildSize);
            buildExecutor.setMaximumPoolSize(newConcurrentBuildSize);
        }

        threadPoolSize.set(newConcurrentBuildSize);
        return true;
    }

    /**
     * Creates a HostConfig object that is used to configure the Docker container for build jobs.
     * The configuration is based on the default Docker flags for build jobs as specified in artemis.continuous-integration.build.
     *
     * @return The HostConfig bean.
     */
    @NotNull
    public HostConfig hostConfig() {
        long cpuCount = 0;
        long cpuPeriod = 100000L;
        long memory = 0;
        long memorySwap = 0;
        long pidsLimit = 0;

        List<String> defaultDockerFlags = programmingLanguageConfiguration.getDefaultDockerFlags();

        for (int i = 0; i < defaultDockerFlags.size(); i += 2) {
            String flag = defaultDockerFlags.get(i);
            String value = defaultDockerFlags.get(i + 1);

            switch (flag) {
                case "--cpus" -> cpuCount = Long.parseLong(value.replaceAll("[^0-9]", ""));
                case "--memory" -> memory = parseMemoryString(value);
                case "--memory-swap" -> memorySwap = parseMemoryString(value);
                case "--pids-limit" -> pidsLimit = Long.parseLong(value.replaceAll("[^0-9]", ""));
                default -> throw new LocalCIException("Unknown docker flag: " + flag);
            }
        }

        log.info("Using build job container docker host config with CPU(s): {}, memory: {}, memory swap: {}, pids limit: {}.", cpuCount, formatMemory(memory),
                formatMemory(memorySwap), pidsLimit);

        return HostConfig.newHostConfig().withCpuQuota(cpuCount * cpuPeriod).withCpuPeriod(cpuPeriod).withMemory(memory).withMemorySwap(memorySwap).withPidsLimit(pidsLimit)
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
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Creates a thread pool executor that manages the queue of build jobs.
     *
     * @return The executor service.
     */
    private ThreadPoolExecutor createBuildExecutor() {
        // Use preserved size if available, otherwise calculate
        int poolSize = threadPoolSize.get();
        if (poolSize == 0) {
            poolSize = specifyConcurrentBuilds ? concurrentBuildsDefault : Math.max(1, (Runtime.getRuntime().availableProcessors() - 2) / 2);
            threadPoolSize.set(poolSize);
        }

        ThreadFactory customThreadFactory = new ThreadFactoryBuilder().setNameFormat("local-ci-build-%d")
                .setUncaughtExceptionHandler((thread, exception) -> log.error("Uncaught exception in thread {}", thread.getName(), exception)).build();

        RejectedExecutionHandler customRejectedExecutionHandler = (runnable, executor) -> {
            throw new RejectedExecutionException("Task " + runnable.toString() + " rejected from " + executor.toString());
        };

        return new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1), customThreadFactory, customRejectedExecutionHandler);
    }

    /**
     * Creates a Docker client that is used to communicate with the Docker daemon.
     *
     * @return The DockerClient.
     */
    public DockerClient createDockerClient() {
        log.debug("Create bean dockerClient");
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerConnectionUri).build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build();
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

    private void shutdownBuildExecutor() {
        // Shut down the current executor gracefully
        if (buildExecutor != null && !buildExecutor.isShutdown()) {
            buildExecutor.shutdown();
            try {
                buildExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                log.warn("Executor termination interrupted", e);
            }
        }
        buildExecutor = null;
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

    public void closeBuildAgentServices() {
        shutdownBuildExecutor();
        closeDockerClient();
    }

    public void openBuildAgentServices() {
        this.buildExecutor = createBuildExecutor();
        this.dockerClient = createDockerClient();
    }
}
