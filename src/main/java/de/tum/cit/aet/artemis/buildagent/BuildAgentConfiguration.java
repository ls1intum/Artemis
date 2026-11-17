package de.tum.cit.aet.artemis.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.github.dockerjava.api.model.Capability;
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

    private ThreadPoolExecutor buildResultExecutor;

    private int threadPoolSize = 0;

    private DockerClient dockerClient;

    private volatile boolean dockerAvailable = false;

    private static final Logger log = LoggerFactory.getLogger(BuildAgentConfiguration.class);

    /** A size such as {@code 10g}, {@code 512MB} or {@code 2048}: a number, an optional unit k, m or g, and an optional b. */
    private static final Pattern STORAGE_LIMIT = Pattern.compile("(\\d{1,12})([kKmMgG]?)[bB]?");

    /** Everything that is not a digit, removed to read the number out of a Docker flag value such as {@code 2g}. */
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

    @Value("${artemis.continuous-integration.docker-connection-uri}")
    String dockerConnectionUri;

    @Value("${artemis.continuous-integration.build-runner:docker}")
    String buildRunner;

    @Value("${artemis.continuous-integration.concurrent-build-size:1}")
    int concurrentBuildSize;

    @Value("${artemis.continuous-integration.specify-concurrent-builds:false}")
    boolean specifyConcurrentBuilds;

    @Value("${artemis.continuous-integration.allow-privilege-escalation:true}")
    boolean allowPrivilegeEscalation = true;

    /**
     * The size that the writable layer of a build container may reach, for example {@code 10g}, or blank for no limit. Docker applies it only on storage drivers and file systems
     * that support quotas, such as overlay2 on xfs mounted with {@code pquota}, and refuses to create the container on others.
     */
    @Value("${artemis.continuous-integration.container-storage-limit:}")
    String containerStorageLimit = "";

    /** The limit in bytes, read once at startup from {@link #containerStorageLimit}, or null for no limit. */
    Long storageLimitBytes;

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
        // An invalid value stops the start of the agent, instead of failing every build later
        storageLimitBytes = parseStorageLimit(containerStorageLimit);
        if (storageLimitBytes != null) {
            log.info("The writable layer of a build container is limited to {} bytes", storageLimitBytes);
        }
        buildExecutor = createBuildExecutor();
        buildResultExecutor = createBuildResultExecutor();
        openDockerServicesIfSelected();
    }

    public ThreadPoolExecutor getBuildExecutor() {
        return buildExecutor;
    }

    public ThreadPoolExecutor getBuildResultExecutor() {
        return buildResultExecutor;
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
                case "--cpus" -> cpuCount = Long.parseLong(NON_DIGIT.matcher(value).replaceAll(""));
                case "--memory" -> memory = parseMemoryString(value);
                case "--memory-swap" -> memorySwap = parseMemoryString(value);
                case "--pids-limit" -> pidsLimit = Long.parseLong(NON_DIGIT.matcher(value).replaceAll(""));
                default -> throw new LocalCIException("Unknown docker flag: " + flag);
            }
        }

        log.info("Using build job container docker host config with CPU(s): {}, memory: {}, memory swap: {}, pids limit: {}.", cpuCount, formatMemory(memory),
                formatMemory(memorySwap), pidsLimit);

        return applyStorageLimit(restrictPrivileges(HostConfig.newHostConfig().withCpuQuota(cpuCount * cpuPeriod).withCpuPeriod(cpuPeriod).withMemory(memory)
                .withMemorySwap(memorySwap).withPidsLimit(pidsLimit).withAutoRemove(true), allowPrivilegeEscalation));
    }

    /**
     * Limits the writable layer of a container to the configured size, see {@code artemis.continuous-integration.container-storage-limit}. Nothing changes if no limit is
     * configured.
     *
     * @param hostConfig the configuration of the container
     * @return the same configuration
     */
    public HostConfig applyStorageLimit(HostConfig hostConfig) {
        if (storageLimitBytes == null) {
            return hostConfig;
        }
        return hostConfig.withStorageOpt(Map.of("size", Long.toString(storageLimitBytes)));
    }

    /**
     * Reads a size such as {@code 10g}.
     *
     * @param limit the configured value, may be blank
     * @return the size in bytes, or null if the value is blank
     * @throws LocalCIException if the value is not a size
     */
    static Long parseStorageLimit(String limit) {
        if (limit == null || limit.isBlank()) {
            return null;
        }
        Matcher matcher = STORAGE_LIMIT.matcher(limit.trim());
        if (!matcher.matches()) {
            throw new LocalCIException("The container storage limit '" + limit + "' is not a size such as 10g");
        }
        long factor = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "k" -> 1024L;
            case "m" -> 1024L * 1024L;
            case "g" -> 1024L * 1024L * 1024L;
            default -> 1L;
        };
        try {
            return Math.multiplyExact(Long.parseLong(matcher.group(1)), factor);
        }
        catch (ArithmeticException e) {
            throw new LocalCIException("The container storage limit '" + limit + "' is too large", e);
        }
    }

    /**
     * Whether processes in build containers may gain privileges through setuid programs, see
     * {@code artemis.continuous-integration.allow-privilege-escalation}.
     *
     * @return true if the restriction is lifted by the administrator
     */
    public boolean isPrivilegeEscalationAllowed() {
        return allowPrivilegeEscalation;
    }

    /**
     * Limits what processes of a build container may do. The capabilities that the container runtime grants by default but that no build needs are removed. Unless the
     * administrator allows it with {@code artemis.continuous-integration.allow-privilege-escalation}, processes also cannot gain privileges through setuid programs such as
     * {@code sudo}. Network access, file ownership and the ordinary use of package managers are not affected.
     *
     * @param hostConfig               the configuration of the container
     * @param allowPrivilegeEscalation whether setuid programs may still raise privileges, for example {@code sudo} for a non-root user
     * @return the same configuration
     */
    public static HostConfig restrictPrivileges(HostConfig hostConfig, boolean allowPrivilegeEscalation) {
        if (allowPrivilegeEscalation) {
            return hostConfig.withCapDrop(Capability.MKNOD, Capability.SYS_CHROOT, Capability.SETFCAP);
        }
        return hostConfig.withCapDrop(Capability.MKNOD, Capability.SYS_CHROOT, Capability.SETFCAP, Capability.AUDIT_WRITE).withSecurityOpts(List.of("no-new-privileges"));
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

        log.debug("Using build executor with thread pool size {}.", threadPoolSize);
        return createExecutor(threadPoolSize, 1, "local-ci-build-%d");
    }

    /**
     * Creates a separate executor for the blocking adapters that turn build {@link java.util.concurrent.Future Futures} into completable futures.
     * Keeping these waits off the JVM common pool is important on single-core build agents because libraries used by a build, including JGit, may need that pool themselves.
     *
     * @return the executor used for build result waits
     */
    private ThreadPoolExecutor createBuildResultExecutor() {
        log.debug("Using build result executor with thread pool size {}.", threadPoolSize);
        return createExecutor(threadPoolSize, threadPoolSize, "local-ci-build-result-%d");
    }

    private ThreadPoolExecutor createExecutor(int poolSize, int queueCapacity, String threadNamePattern) {
        ThreadFactory customThreadFactory = BasicThreadFactory.builder().namingPattern(threadNamePattern)
                .uncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in thread {}", t.getName(), e)).build();

        RejectedExecutionHandler customRejectedExecutionHandler = (runnable, executor) -> {
            throw new RejectedExecutionException("Task " + runnable.toString() + " rejected from " + executor.toString());
        };

        return new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(queueCapacity), customThreadFactory, customRejectedExecutionHandler);
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
            return Long.parseLong(NON_DIGIT.matcher(memoryString).replaceAll("")) * 1024L * 1024L * 1024L;
        }
        else if (memoryString.endsWith("m\"")) {
            return Long.parseLong(NON_DIGIT.matcher(memoryString).replaceAll("")) * 1024L * 1024L;
        }
        else if (memoryString.endsWith("k\"")) {
            return Long.parseLong(NON_DIGIT.matcher(memoryString).replaceAll("")) * 1024L;
        }
        else {
            return Long.parseLong(memoryString);
        }
    }

    private void shutdownBuildExecutors() {
        shutdownExecutor(buildExecutor, "build");
        shutdownExecutor(buildResultExecutor, "build result");
        buildExecutor = null;
        buildResultExecutor = null;
    }

    private void shutdownExecutor(ThreadPoolExecutor executor, String executorName) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("{} executor termination interrupted", executorName, e);
            }
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

    public void closeBuildAgentServices() {
        dockerAvailable = false;
        shutdownBuildExecutors();
        closeDockerClient();
    }

    public void openBuildAgentServices() {
        this.buildExecutor = createBuildExecutor();
        this.buildResultExecutor = createBuildResultExecutor();
        openDockerServicesIfSelected();
    }

    private void openDockerServicesIfSelected() {
        if (!"docker".equalsIgnoreCase(buildRunner)) {
            dockerClient = null;
            dockerAvailable = false;
            return;
        }
        dockerClient = createDockerClient();
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
