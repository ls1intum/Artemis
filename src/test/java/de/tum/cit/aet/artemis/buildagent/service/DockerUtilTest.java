package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketException;

import org.junit.jupiter.api.Test;

class DockerUtilTest {

    @Test
    void shouldDetectSocketException() {
        var ex = new SocketException("No such file or directory");
        assertThat(DockerUtil.isDockerNotAvailable(ex)).isTrue();
    }

    @Test
    void shouldDetectConnectException() {
        var ex = new ConnectException("Connection refused");
        assertThat(DockerUtil.isDockerNotAvailable(ex)).isTrue();
    }

    @Test
    void shouldDetectNestedDockerUnavailability() {
        var socket = new SocketException("No such file or directory");
        var mid = new RuntimeException("mid", socket);
        var top = new RuntimeException("top", mid);
        assertThat(DockerUtil.isDockerNotAvailable(top)).isTrue();
    }

    @Test
    void shouldReturnFalseForUnrelatedRuntimeException() {
        var ex = new RuntimeException("something else");
        assertThat(DockerUtil.isDockerNotAvailable(ex)).isFalse();
    }

    @Test
    void shouldHandleCauseChainCycle() {
        var ex1 = new RuntimeException("cycle1");
        var ex2 = new RuntimeException("cycle2", ex1);
        ex1.initCause(ex2);
        assertThat(DockerUtil.isDockerNotAvailable(ex1)).isFalse();
    }

    @Test
    void shouldReturnFalseForNull() {
        assertThat(DockerUtil.isDockerNotAvailable(null)).isFalse();
    }
}
