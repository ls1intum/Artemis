package de.tum.in.www1.artemis.config.localvcci;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import javax.xml.stream.XMLInputFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.ResourceLoaderService;

/**
 * Creates beans needed for the local CI system.
 * This includes a Docker client and an executor service that manages the queue of build jobs.
 */
@Configuration
@Profile("localci")
public class LocalCIConfiguration {

    private final ResourceLoaderService resourceLoaderService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final Logger log = LoggerFactory.getLogger(LocalCIConfiguration.class);

    @Value("${artemis.continuous-integration.queue-size-limit:30}")
    int queueSizeLimit;

    @Value("${artemis.continuous-integration.build.images.java.default}")
    String dockerImage;

    @Value("${artemis.continuous-integration.docker-connection-uri}")
    String dockerConnectionUri;

    public LocalCIConfiguration(ResourceLoaderService resourceLoaderService, ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.resourceLoaderService = resourceLoaderService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    /**
     * Defines the thread pool size for the local CI ExecutorService based on system resources.
     *
     * @return The thread pool size bean.
     */
    @Bean
    public int threadPoolSize() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return Math.max(1, (availableProcessors - 2) / 2);
    }

    /**
     * Creates a HostConfig object that is used to configure the Docker container for build jobs.
     * The configuration is based on the default Docker flags for build jobs as specified in artemis.continuous-integration.build.
     *
     * @return The HostConfig bean.
     */
    @Bean
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

        log.info("Using Build Job Container HostConfig with cpus {}, memory {}, memorySwap {}, pidsLimit {}.", cpuCount, memory, memorySwap, pidsLimit);

        return HostConfig.newHostConfig().withCpuQuota(cpuCount * cpuPeriod).withCpuPeriod(cpuPeriod).withMemory(memory).withMemorySwap(memorySwap).withPidsLimit(pidsLimit)
                .withAutoRemove(true);
    }

    /**
     * Creates an executor service that manages the queue of build jobs.
     *
     * @return The executor service bean.
     */
    @Bean
    public ExecutorService localCIBuildExecutorService(int threadPoolSize) {

        log.info("Using ExecutorService with thread pool size {} and a queue size limit of {}.", threadPoolSize, queueSizeLimit);

        ThreadFactory customThreadFactory = new ThreadFactoryBuilder().setNameFormat("local-ci-build-%d")
                .setUncaughtExceptionHandler((thread, exception) -> log.error("Uncaught exception in thread {}", thread.getName(), exception)).build();

        RejectedExecutionHandler customRejectedExecutionHandler = (runnable, executor) -> {
            throw new RejectedExecutionException("Task " + runnable.toString() + " rejected from " + executor.toString());
        };

        return new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(queueSizeLimit), customThreadFactory,
                customRejectedExecutionHandler);
    }

    /**
     * Creates a scheduled executor service that logs the current state of the local CI ExecutorService queue.
     *
     * @param localCIBuildExecutorService The local CI ExecutorService bean.
     * @return The scheduled executor service bean.
     */
    @Bean
    public ScheduledExecutorService buildQueueLogger(ExecutorService localCIBuildExecutorService) {
        ScheduledExecutorService buildQueueLogger = Executors.newSingleThreadScheduledExecutor();
        buildQueueLogger.scheduleAtFixedRate(() -> {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) localCIBuildExecutorService;
            // Report on the current state of the local CI ExecutorService queue every 30 seconds.
            log.info("Current queue size of local CI ExecutorService: {}", threadPoolExecutor.getQueue().size());
            log.info("Number of jobs currently building on this node: {}", threadPoolExecutor.getActiveCount());
        }, 0, 30, TimeUnit.SECONDS);
        return buildQueueLogger;
    }

    /**
     * Creates an XMLInputFactory that is used to parse the test results during execution of the local CI build jobs.
     *
     * @return The XMLInputFactory bean.
     */
    @Bean
    public XMLInputFactory localCIXMLInputFactory() {
        return XMLInputFactory.newInstance();
    }

    // TODO: the Artemis server should start even if docker is not running. Also, pulling the image should be done after the start has finished or only on demand
    /**
     * Creates a Docker client that is used to communicate with the Docker daemon.
     *
     * @return The DockerClient bean.
     */
    @Bean
    public DockerClient dockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerConnectionUri).build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build();
        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

        log.info("Docker client created with connection URI: {}", dockerConnectionUri);

        return dockerClient;
    }

    /**
     * Provides the path to the build script used for local CI build jobs.
     * To bind the build script into the Docker container running the build job, we need to get a File or Path object directly pointing to the resource.
     * However, if the application is packaged (like it is in production), the Java runtime does not provide direct access to the file system for embedded resources.
     * To make the path available, the resource is retrieved as an InputStream and written to a temporary file.
     * This is a rather costly operation, so we only do it once and then provide the Path object via this Bean.
     * TODO LOCALVC_CI: Find a better way to provide the build script to the Docker container.
     * To implement additional features like Sequential Test Runs, Static Code Analysis, and Testwise Coverage Analysis, the build script needs to be configurable when creating the
     * exercise.
     *
     * @return the Path to the build script.
     */
    @Bean
    public Path buildScriptFilePath() {
        Path resourcePath = Path.of("templates", "localci", "java", "build_and_run_tests.sh");
        Path scriptPath;
        try {
            scriptPath = resourceLoaderService.getResourceFilePath(resourcePath);
            log.info("Providing build script at {}", scriptPath);
        }
        catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new LocalCIException("Could not retrieve build script.", e);
        }

        return scriptPath;
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
}
