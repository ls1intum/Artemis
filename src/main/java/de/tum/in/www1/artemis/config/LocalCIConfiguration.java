package de.tum.in.www1.artemis.config;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
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

/**
 * Creates beans needed for the local CI system.
 * This includes a Docker client and an executor service that manages the queue of build jobs.
 */
@Configuration
@Profile("localci")
public class LocalCIConfiguration {

    private final Logger log = LoggerFactory.getLogger(LocalCIConfiguration.class);

    @Value("${artemis.continuous-integration.thread-pool-size:2}")
    int threadPoolSize;

    /**
     * Creates an executor service that manages the queue of build jobs.
     *
     * @return The executor service bean.
     */
    @Bean
    public ExecutorService localCIBuildExecutorService() {
        if (threadPoolSize > 0) {
            log.info("Using ExecutorService with thread pool size: " + threadPoolSize);
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(threadPoolSize);
            executorService.scheduleAtFixedRate(() -> {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executorService;
                // Report on the current state of the executor service every 10 seconds.
                // Subtract 1 from the active count because we only want to report on the additional threads that are spun up for the build jobs.
                log.info("Current active tasks in the local CI executor service: {}, Queue size: {}", threadPoolExecutor.getActiveCount() - 1,
                        threadPoolExecutor.getQueue().size());
            }, 0, 10, TimeUnit.SECONDS);
            return executorService;
        }
        else {
            log.info("Using SynchronousExecutorService");
            // Return a synchronous ExecutorService.
            return new SynchronousExecutorService();
        }
    }

    /**
     * Creates a scheduler that is used to cancel stuck build jobs after a timeout.
     *
     * @return The scheduler bean.
     */
    @Bean
    public ScheduledExecutorService localCIBuildTimeoutExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * This class can be used instead of the regular ExecutorService for testing purposes.
     * The regular ExecutorService maintains a thread pool and new build runs are executed in a separate thread.
     * When testing or debugging, it is helpful to run all tasks synchronously.
     */
    static class SynchronousExecutorService extends AbstractExecutorService {

        private boolean isShutdown = false;

        @Override
        public void execute(@NotNull Runnable command) {
            if (isShutdown) {
                throw new IllegalStateException("ExecutorService is already shut down.");
            }
            command.run();
        }

        @Override
        public void shutdown() {
            isShutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            isShutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return isShutdown;
        }

        @Override
        public boolean isTerminated() {
            return isShutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
            return true;
        }
    }

    /**
     * Creates a Docker client that is used to communicate with the Docker daemon.
     *
     * @return The Docker client bean.
     */
    @Bean
    public DockerClient dockerClient() {
        String dockerConnectionUri;

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            dockerConnectionUri = "tcp://localhost:2375";
        }
        else {
            dockerConnectionUri = "unix:///var/run/docker.sock";
        }

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerConnectionUri).build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build();
        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

        log.info("Docker client created with connection URI: " + dockerConnectionUri);

        return dockerClient;
    }
}
