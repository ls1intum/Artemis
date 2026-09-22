package de.tum.cit.aet.artemis.aiworker.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SandboxPolicyDTOTest {

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    @Test
    void freezesTheTrustedWritableMountPolicy() {
        var mounts = new HashMap<>(Map.of("/workspace", "rw,nosuid,nodev,size=64m"));
        var policy = policy("documents", IMAGE, mounts);
        mounts.put("/etc", "rw");
        assertThat(policy.writableFilesystems()).containsOnlyKeys("/workspace");
        assertThatThrownBy(() -> policy.writableFilesystems().put("/etc", "rw")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "worker/name", "worker with space" })
    void rejectsUnsafeWorkerIdentity(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> policy(id, IMAGE, Map.of()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "latest", "image:latest", "sha256:123", "bad image@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
    void rejectsMutableOrMalformedImages(String image) {
        assertThatIllegalArgumentException().isThrownBy(() -> policy("documents", image, Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "relative", "/workspace/../etc" })
    void rejectsUnsafeWritableRoots(String path) {
        assertThatIllegalArgumentException().isThrownBy(() -> policy("documents", IMAGE, Map.of(path, "rw")));
    }

    @Test
    void boundsTheNumberOfWritableRoots() {
        Map<String, String> mounts = new HashMap<>();
        for (int i = 0; i < 17; i++) {
            mounts.put("/scratch-" + i, "rw");
        }
        assertThatIllegalArgumentException().isThrownBy(() -> policy("documents", IMAGE, mounts));
    }

    private SandboxPolicyDTO policy(String id, String image, Map<String, String> mounts) {
        return new SandboxPolicyDTO(id, image, "runc", 128 * 1024 * 1024, 100_000, 32, "artemis.ai.worker", "ai-task-", mounts);
    }
}
