package de.tum.cit.aet.artemis.hyperionworker.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

/** Docker credentials remain on the supervisor and are never added to a sandbox environment. */
@Configuration
public class DockerConfiguration {

    /**
     * Creates a bounded local Docker connection pool with explicit connection and response deadlines.
     *
     * @return the supervisor-only Docker client
     */
    @Bean(destroyMethod = "close")
    public DockerClient dockerClient() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var transport = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).maxConnections(8)
                .connectionTimeout(Duration.ofSeconds(10)).responseTimeout(Duration.ofMinutes(3)).build();
        return DockerClientImpl.getInstance(config, transport);
    }
}
