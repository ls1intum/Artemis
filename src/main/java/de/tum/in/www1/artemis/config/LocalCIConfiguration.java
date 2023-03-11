package de.tum.in.www1.artemis.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

@Configuration
@Profile("localci")
public class LocalCIConfiguration {

    private final Logger log = LoggerFactory.getLogger(LocalCIConfiguration.class);

    @Value("${artemis.continuous-integration.thread-pool-size}")
    int threadPoolSize;

    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(threadPoolSize);
    }

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
