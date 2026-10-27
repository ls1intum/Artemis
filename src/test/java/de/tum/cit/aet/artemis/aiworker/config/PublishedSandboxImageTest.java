package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

class PublishedSandboxImageTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    private static final String REFERENCE = PublishedSandboxImage.REPOSITORY + ":sha-" + COMMIT;

    private static final String DIGEST = PublishedSandboxImage.REPOSITORY + "@sha256:" + "a".repeat(64);

    private final DockerClient docker = mock();

    private final InspectImageCmd inspect = mock();

    private final PullImageCmd pull = mock();

    @Test
    void usesTheDigestOfAnImageTheDaemonAlreadyHas() {
        inspectReturns(List.of("other/image@sha256:" + "b".repeat(64), DIGEST));

        assertThat(PublishedSandboxImage.resolve(docker, COMMIT)).isEqualTo(DIGEST);
        verify(docker, never()).pullImageCmd(any());
    }

    @Test
    void pullsTheImageOfThisCommitWhenTheDaemonDoesNotHaveIt() {
        InspectImageResponse response = mock();
        when(response.getRepoDigests()).thenReturn(List.of(DIGEST));
        when(docker.inspectImageCmd(REFERENCE)).thenReturn(inspect);
        when(inspect.exec()).thenThrow(new NotFoundException("missing")).thenReturn(response);
        pullCompletes();

        assertThat(PublishedSandboxImage.resolve(docker, COMMIT)).isEqualTo(DIGEST);
        verify(docker).pullImageCmd(REFERENCE);
    }

    @Test
    void namesTheConfigurationWhenNoImageIsPublishedForThisBuild() {
        when(docker.inspectImageCmd(REFERENCE)).thenReturn(inspect);
        when(inspect.exec()).thenThrow(new NotFoundException("missing"));
        when(docker.pullImageCmd(REFERENCE)).thenReturn(pull);
        when(pull.exec(any())).thenThrow(new NotFoundException("manifest unknown"));

        assertThatThrownBy(() -> PublishedSandboxImage.resolve(docker, COMMIT)).isInstanceOf(IllegalStateException.class).hasMessageContaining("AI_WORKER_SANDBOX_IMAGE");
    }

    @Test
    void requiresAFullCommitId() {
        assertThatThrownBy(() -> PublishedSandboxImage.resolve(docker, "0123456")).isInstanceOf(IllegalStateException.class).hasMessageContaining("AI_WORKER_SANDBOX_IMAGE");
        verify(docker, never()).inspectImageCmd(any());
    }

    private void inspectReturns(List<String> digests) {
        InspectImageResponse response = mock();
        when(response.getRepoDigests()).thenReturn(digests);
        when(docker.inspectImageCmd(REFERENCE)).thenReturn(inspect);
        when(inspect.exec()).thenReturn(response);
    }

    private void pullCompletes() {
        when(docker.pullImageCmd(REFERENCE)).thenReturn(pull);
        when(pull.exec(any())).thenAnswer(invocation -> {
            PullImageResultCallback callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });
    }
}
