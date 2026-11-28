package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.SSLConfig;

class DockerConfigurationTest {

    @Test
    void plaintextTcpIsRejectedEvenForLoopback() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("tcp://127.0.0.1:2375").withDockerTlsVerify(false).build();
        assertThatThrownBy(() -> DockerConfiguration.requireSecureTransport(config)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localSocketRemainsSupported() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("unix:///var/run/docker.sock").withDockerTlsVerify(false).build();
        assertThatCode(() -> DockerConfiguration.requireSecureTransport(config)).doesNotThrowAnyException();
    }

    // docker-java deprecates SSLConfig without a replacement: DockerClientConfig.getSSLConfig() still returns it, and DockerConfiguration reads it from there
    @SuppressWarnings("deprecation")
    @Test
    void remoteDockerWithTlsRemainsSupported() {
        var config = mock(DefaultDockerClientConfig.class);
        when(config.getDockerHost()).thenReturn(URI.create("tcp://docker.example:2376"));
        when(config.getSSLConfig()).thenReturn(mock(SSLConfig.class));
        assertThatCode(() -> DockerConfiguration.requireSecureTransport(config)).doesNotThrowAnyException();
    }
}
