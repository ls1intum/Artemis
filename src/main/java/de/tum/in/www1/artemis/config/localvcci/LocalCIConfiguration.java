package de.tum.in.www1.artemis.config.localvcci;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLInputFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

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

    private final Logger log = LoggerFactory.getLogger(LocalCIConfiguration.class);

    @Value("${artemis.continuous-integration.thread-pool-size:1}")
    int threadPoolSize;

    @Value("${artemis.continuous-integration.queue-size-limit:30}")
    int queueSizeLimit;

    @Value("${artemis.continuous-integration.build.images.java.default}")
    String dockerImage;

    @Value("${artemis.continuous-integration.docker-connection-uri}")
    String dockerConnectionUri;

    public LocalCIConfiguration(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Creates an executor service that manages the queue of build jobs.
     *
     * @return The executor service bean.
     */
    @Bean
    public ExecutorService localCIBuildExecutorService() {
        log.info("Using ExecutorService with thread pool size {} and a queue size limit of {}.", threadPoolSize, queueSizeLimit);

        ThreadFactory customThreadFactory = new ThreadFactoryBuilder().setNameFormat("local-ci-build-%d")
                .setUncaughtExceptionHandler((thread, exception) -> log.error("Uncaught exception in thread " + thread.getName(), exception)).build();

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

        log.info("Docker client created with connection URI: " + dockerConnectionUri);

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
}
