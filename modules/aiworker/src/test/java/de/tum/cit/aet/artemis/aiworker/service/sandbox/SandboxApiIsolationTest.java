package de.tum.cit.aet.artemis.aiworker.service.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;

import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO;

class SandboxApiIsolationTest {

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    @Test
    void bootsAndReconcilesWithoutHyperionBrokerOrModelConfiguration() {
        DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        when(docker.listContainersCmd().withShowAll(true).exec()).thenReturn(List.of());
        when(docker.inspectImageCmd(IMAGE).exec().getId()).thenReturn(IMAGE);
        var policy = new SandboxPolicyDTO("documents", IMAGE, "runc", 128 * 1024 * 1024, 100_000, 32, "artemis.ai.worker", "ai-task-",
                Map.of("/workspace", "rw,nosuid,nodev,size=64m", "/tmp", "rw,nosuid,nodev,size=16m"));

        new ApplicationContextRunner().withClassLoader(new FilteredClassLoader("de.tum.cit.aet.artemis.hyperion")).withUserConfiguration(DockerSandboxService.class)
                .withBean(DockerClient.class, () -> docker).withBean(SandboxPolicyDTO.class, () -> policy).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SandboxApi.class);
                    assertThat(ClassUtils.isPresent("de.tum.cit.aet.artemis.hyperion.service.worker.HyperionWorkloadService", context.getClassLoader())).isFalse();
                    SandboxApi api = context.getBean(SandboxApi.class);
                    assertThat(api.prepare()).isEqualTo(IMAGE);
                    UUID execution = UUID.randomUUID();
                    assertThat(api.forExecution(execution)).isNotNull();
                    api.destroyExecution(execution);
                });
    }

    @Test
    void cleanupUsesTheConfiguredNamespaceAndExactExecutionNotAnotherWorkload() {
        DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        UUID execution = UUID.randomUUID();
        Container own = container("own", "artemis.ai.worker", "documents", "/ai-task-documents-" + execution + "-" + UUID.randomUUID());
        Container otherExecution = container("other-execution", "artemis.ai.worker", "documents", "/ai-task-documents-" + UUID.randomUUID() + "-" + UUID.randomUUID());
        Container otherOwner = container("other-owner", "artemis.other.worker", "documents", own.getNames()[0]);
        when(docker.listContainersCmd().withShowAll(true).exec()).thenReturn(List.of(own, otherExecution, otherOwner));
        var policy = new SandboxPolicyDTO("documents", IMAGE, "runc", 128 * 1024 * 1024, 100_000, 32, "artemis.ai.worker", "ai-task-", Map.of());

        SandboxApi api = new DockerSandboxService(docker, policy);
        api.destroyExecution(execution);

        verify(docker.removeContainerCmd("own").withForce(true)).exec();
        verify(docker, never()).removeContainerCmd("other-execution");
        verify(docker, never()).removeContainerCmd("other-owner");
    }

    private Container container(String id, String label, String owner, String name) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(id);
        when(container.getLabels()).thenReturn(Map.of(label, owner));
        when(container.getNames()).thenReturn(new String[] { name });
        return container;
    }
}
