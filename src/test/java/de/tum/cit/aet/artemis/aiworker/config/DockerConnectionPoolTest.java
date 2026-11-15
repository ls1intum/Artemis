package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DefaultDockerClientConfig;

import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;

/** Exercises the real pooled Docker transport; mocked Docker commands cannot expose connection starvation. */
@EnabledOnOs({ OS.LINUX, OS.MAC })
class DockerConnectionPoolTest {

    @TempDir
    Path directory;

    @Test
    void fullExecutionCapacityLeavesAConnectionForExactContainerCancellation() throws Exception {
        var streamsAttached = new CountDownLatch(WorkerCapacityDTO.MAX_SLOTS);
        var releaseStreams = new CountDownLatch(1);
        var deleted = new CountDownLatch(1);
        List<String> deletions = new CopyOnWriteArrayList<>();
        List<SocketChannel> connections = new CopyOnWriteArrayList<>();
        List<ResultCallback.Adapter<Frame>> callbacks = new ArrayList<>();
        try (var requests = Executors.newVirtualThreadPerTaskExecutor(); var server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            Path socket = directory.resolve("docker.sock");
            server.bind(UnixDomainSocketAddress.of(socket));
            var acceptor = requests.submit(() -> {
                while (server.isOpen()) {
                    try {
                        SocketChannel connection = server.accept();
                        connections.add(connection);
                        requests.submit(() -> respond(connection, streamsAttached, releaseStreams, deleted, deletions));
                    }
                    catch (IOException closed) {
                        if (server.isOpen()) {
                            throw new java.io.UncheckedIOException(closed);
                        }
                    }
                }
            });
            var config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("unix://" + socket).withDockerTlsVerify(false).build();
            try (var docker = DockerConfiguration.createDockerClient(config)) {
                try {
                    for (int slot = 0; slot < WorkerCapacityDTO.MAX_SLOTS; slot++) {
                        var callback = new ResultCallback.Adapter<Frame>();
                        callbacks.add(callback);
                        docker.execStartCmd("execution-" + slot).withDetach(false).exec(callback);
                    }
                    assertThat(streamsAttached.await(10, TimeUnit.SECONDS)).as("every supported execution holds an attached connection").isTrue();

                    var cancellation = requests.submit(() -> docker.removeContainerCmd("execution-0").withForce(true).exec());

                    assertThat(deleted.await(5, TimeUnit.SECONDS)).as("cancellation reaches Docker before any execution stream is released").isTrue();
                    cancellation.get(5, TimeUnit.SECONDS);
                    assertThat(releaseStreams.getCount()).isEqualTo(1);
                    assertThat(deletions).singleElement().asString().contains("/containers/execution-0?").doesNotContain("execution-1");
                }
                finally {
                    server.close();
                    releaseStreams.countDown();
                    for (var callback : callbacks) {
                        callback.close();
                    }
                    acceptor.get(5, TimeUnit.SECONDS);
                    for (var connection : connections) {
                        connection.close();
                    }
                }
            }
        }
    }

    private static void respond(SocketChannel connection, CountDownLatch streamsAttached, CountDownLatch releaseStreams, CountDownLatch deleted, List<String> deletions) {
        try (connection) {
            var reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(connection), StandardCharsets.US_ASCII));
            String request = reader.readLine();
            int contentLength = 0;
            for (String header; (header = reader.readLine()) != null && !header.isEmpty();) {
                if (header.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                    contentLength = Integer.parseInt(header.substring("Content-Length:".length()).trim());
                }
            }
            for (int consumed = 0; consumed < contentLength; consumed++) {
                if (reader.read() < 0) {
                    throw new java.io.EOFException("Incomplete Docker request body");
                }
            }
            var response = Channels.newOutputStream(connection);
            if (request != null && request.startsWith("POST ") && request.contains("/exec/") && request.contains("/start")) {
                response.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.docker.raw-stream\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                response.write(new byte[] { 1, 0, 0, 0, 0, 0, 0, 1, 'x' });
                response.flush();
                streamsAttached.countDown();
                releaseStreams.await();
            }
            else if (request != null && request.startsWith("DELETE ")) {
                deletions.add(request);
                response.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                response.flush();
                deleted.countDown();
            }
            else {
                response.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            }
        }
        catch (IOException closed) {
            // The test closes every retained stream during cleanup.
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
