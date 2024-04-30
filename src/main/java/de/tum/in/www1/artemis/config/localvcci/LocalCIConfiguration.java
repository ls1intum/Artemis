package de.tum.in.www1.artemis.config.localvcci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
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
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.exception.LocalCIException;

/**
 * Creates beans needed for the local CI system.
 * This includes a Docker client and an executor service that manages the queue of build jobs.
 */
@Configuration
@Profile({ "localci", PROFILE_BUILDAGENT })
public class LocalCIConfiguration {

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private static final Logger log = LoggerFactory.getLogger(LocalCIConfiguration.class);

    @Value("${artemis.continuous-integration.docker-connection-uri}")
    String dockerConnectionUri;

    @Value("${artemis.continuous-integration.concurrent-build-size:1}")
    int concurrentBuildSize;

    @Value("${artemis.continuous-integration.specify-concurrent-builds:false}")
    boolean specifyConcurrentBuilds;

    public LocalCIConfiguration(ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
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
    public ExecutorService localCIBuildExecutorService() {
        int threadPoolSize;

        if (specifyConcurrentBuilds) {
            threadPoolSize = concurrentBuildSize;
        }
        else {
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            threadPoolSize = Math.max(1, (availableProcessors - 2) / 2);
        }

        ThreadFactory customThreadFactory = new ThreadFactoryBuilder().setNameFormat("local-ci-build-%d")
                .setUncaughtExceptionHandler((thread, exception) -> log.error("Uncaught exception in thread {}", thread.getName(), exception)).build();

        RejectedExecutionHandler customRejectedExecutionHandler = (runnable, executor) -> {
            throw new RejectedExecutionException("Task " + runnable.toString() + " rejected from " + executor.toString());
        };

        log.info("Using ExecutorService with thread pool size {}.", threadPoolSize);
        return new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1), customThreadFactory, customRejectedExecutionHandler);
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
        log.debug("Create bean dockerClient");
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerConnectionUri).build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build();
        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

        log.info("Docker client created with connection URI: {}", dockerConnectionUri);

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
}
