package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

class WorkerSandboxImageTest {

    private static final String REFERENCE = "ghcr.io/ls1intum/artemis-hyperion-sandbox:java25-1";

    private static final String IMAGE_ID = "sha256:" + "a".repeat(64);

    private final DockerClient docker = mock();

    private final InspectImageCmd inspect = mock();

    private final PullImageCmd pull = mock();

    @ParameterizedTest
    @ValueSource(strings = { REFERENCE, "registry.example/sandbox@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
    void reusesACachedImageWithoutRegistryAccess(String reference) {
        when(docker.inspectImageCmd(reference)).thenReturn(inspect);
        InspectImageResponse response = image(IMAGE_ID);
        when(inspect.exec()).thenReturn(response);

        assertThat(WorkerSandboxImage.resolve(docker, reference)).isEqualTo(IMAGE_ID);
        verify(docker, never()).pullImageCmd(any());
    }

    @Test
    void pullsAMissingVersionAndResolvesItsLocalId() {
        when(docker.inspectImageCmd(REFERENCE)).thenReturn(inspect);
        when(docker.pullImageCmd(REFERENCE)).thenReturn(pull);
        InspectImageResponse response = image(IMAGE_ID);
        when(inspect.exec()).thenThrow(new NotFoundException("missing")).thenReturn(response);
        when(pull.exec(any())).thenAnswer(invocation -> {
            PullImageResultCallback callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });

        assertThat(WorkerSandboxImage.resolve(docker, REFERENCE)).isEqualTo(IMAGE_ID);
        verify(docker).pullImageCmd(REFERENCE);
    }

    @Test
    void reportsRegistryFailureWithTheImageAndConfiguration() {
        missingImage();
        when(pull.exec(any())).thenThrow(new NotFoundException("manifest unknown"));

        assertThatThrownBy(() -> WorkerSandboxImage.resolve(docker, REFERENCE)).isInstanceOf(IllegalStateException.class).hasMessageContaining(REFERENCE, "AI_WORKER_SANDBOX_IMAGE")
                .hasCauseInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "mutable:tag", "sha256:short" })
    void refusesAnInvalidLocalImageId(String id) {
        when(docker.inspectImageCmd(REFERENCE)).thenReturn(inspect);
        InspectImageResponse response = image(id);
        when(inspect.exec()).thenReturn(response);

        assertThatThrownBy(() -> WorkerSandboxImage.resolve(docker, REFERENCE)).isInstanceOf(IllegalStateException.class).hasMessageContaining("immutable image ID");
        verify(docker, never()).pullImageCmd(any());
    }

    @Test
    void reportsAPullTimeoutAndClosesThePull() throws Exception {
        missingImage();
        PullImageResultCallback callback = mock();
        when(pull.exec(any())).thenReturn(callback);
        when(callback.awaitCompletion(30, TimeUnit.MINUTES)).thenReturn(false);

        assertThatThrownBy(() -> WorkerSandboxImage.resolve(docker, REFERENCE)).isInstanceOf(IllegalStateException.class).hasMessageContaining("did not finish within 30 minutes");
        verify(pull).close();
    }

    @Test
    void preservesInterruptionWhenAPullIsStopped() throws InterruptedException {
        missingImage();
        PullImageResultCallback callback = mock();
        when(pull.exec(any())).thenReturn(callback);
        InterruptedException interrupted = new InterruptedException("stopping");
        when(callback.awaitCompletion(30, TimeUnit.MINUTES)).thenThrow(interrupted);

        try {
            assertThatThrownBy(() -> WorkerSandboxImage.resolve(docker, REFERENCE)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Interrupted while pulling")
                    .hasCause(interrupted);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

    private void missingImage() {
        when(docker.inspectImageCmd(REFERENCE)).thenReturn(inspect);
        when(inspect.exec()).thenThrow(new NotFoundException("missing"));
        when(docker.pullImageCmd(REFERENCE)).thenReturn(pull);
    }

    private InspectImageResponse image(String id) {
        InspectImageResponse response = mock();
        when(response.getId()).thenReturn(id);
        return response;
    }
}
