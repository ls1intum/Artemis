package de.tum.cit.aet.artemis.hyperionworker.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.core.DefaultDockerClientConfig;

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

    @Test
    void remoteDockerWithTlsRemainsSupported() {
        var config = org.mockito.Mockito.mock(DefaultDockerClientConfig.class);
        org.mockito.Mockito.when(config.getDockerHost()).thenReturn(java.net.URI.create("tcp://docker.example:2376"));
        org.mockito.Mockito.when(config.getSSLConfig()).thenReturn(org.mockito.Mockito.mock(com.github.dockerjava.core.SSLConfig.class));
        assertThatCode(() -> DockerConfiguration.requireSecureTransport(config)).doesNotThrowAnyException();
    }
}
