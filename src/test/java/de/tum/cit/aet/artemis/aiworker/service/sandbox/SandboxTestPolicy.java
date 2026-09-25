package de.tum.cit.aet.artemis.aiworker.service.sandbox;

import java.util.Map;

import de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO;

final class SandboxTestPolicy {

    private SandboxTestPolicy() {
    }

    static SandboxPolicyDTO sandboxPolicy(String id, String image, String runtime, long memory, long cpu, long pids) {
        return new SandboxPolicyDTO(id, image, runtime, memory, cpu, pids, "artemis.aiworker", "aiworker-",
                Map.of("/workspace", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m", "/tmp", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m",
                        "/opt/aiworker", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=256m", "/opt/aiworker-readiness-fixture",
                        "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=64m"));
    }
}
