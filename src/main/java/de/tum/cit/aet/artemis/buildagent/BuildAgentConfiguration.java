package de.tum.cit.aet.artemis.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.util.ArrayList;
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
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import de.tum.cit.aet.artemis.buildagent.service.DockerUtil;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;

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

    @Value("${artemis.continuous-integration.build-container-cache.maven:}")
    private String mavenCacheHostPath;

    @Value("${artemis.continuous-integration.build-container-cache.gradle:}")
    private String gradleCacheHostPath;

    @Value("${artemis.continuous-integration.build-container-cache.read-only:false}")
    private boolean buildContainerCacheReadOnly;

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
     * Returns the host-to-container bind mounts that expose persistent Maven and Gradle dependency caches inside each
     * build container. When configured, dependencies resolved by student submissions are downloaded once per agent
     * (cold miss) instead of once per submission, which drops steady-state traffic to Maven Central by ~99% and
     * avoids HTTP 429 throttling.
     * <p>
     * Both paths are optional and independently configurable. The container-side mount points ({@code /root/.m2} and
     * {@code /root/.gradle}) match the default {@code $HOME} of the build images that currently run as root; the host
     * paths must exist with permissions that allow the build-container user to read and write.
     * <p>
     * Setting {@code build-container-cache.read-only=true} switches both binds to {@link AccessMode#ro}. That is the
     * safe choice for deployments where the cache must not be writable by untrusted student submissions — a malicious
     * build could otherwise seed the shared cache with a manipulated artifact that a future build silently consumes.
     * The read-only variant <em>requires</em> the host cache to be fully populated out of band (e.g. via a trusted
     * warm-up build the operator runs separately) <em>before</em> the flag is enabled. The read-only bind shadows
     * whatever the image's home directory contained, and Maven / Gradle cannot write a freshly fetched artifact into
     * a read-only local repository — so a missing transitive dependency aborts the build at dependency resolution
     * rather than being re-downloaded. The default remains read-write because the typical opt-in flow is "let the
     * first submission warm the cache, all subsequent submissions reuse it", which only works with write access.
     *
     * @return the binds to attach (possibly empty if neither cache path is configured)
     */
    public List<Bind> buildContainerCacheBinds() {
        List<Bind> binds = new ArrayList<>(2);
        AccessMode mode = buildContainerCacheReadOnly ? AccessMode.ro : AccessMode.rw;
        if (mavenCacheHostPath != null && !mavenCacheHostPath.isBlank()) {
            binds.add(new Bind(mavenCacheHostPath, new Volume("/root/.m2"), mode));
        }
        if (gradleCacheHostPath != null && !gradleCacheHostPath.isBlank()) {
            binds.add(new Bind(gradleCacheHostPath, new Volume("/root/.gradle"), mode));
        }
        return binds;
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
        dockerAvailable = false;
        shutdownBuildExecutor();
        closeDockerClient();
    }

    public void openBuildAgentServices() {
        this.buildExecutor = createBuildExecutor();
        this.dockerClient = createDockerClient();
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
