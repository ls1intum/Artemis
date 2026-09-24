package de.tum.cit.aet.artemis.aiworker.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO;

/** Trusted sandbox policy; the sandbox implementation knows neither exercises nor toolchains. */
@Configuration(proxyBeanMethods = false)
@Profile("aiworker")
@Lazy
public class WorkerSandboxConfiguration {

    /**
     * Preserves the workload's ownership namespace and bounded scratch mounts across worker restarts.
     *
     * @param settings validated local deployment configuration
     * @return the immutable execution policy
     */
    @Bean
    public SandboxPolicyDTO sandboxPolicy(WorkerSettings settings) {
        return new SandboxPolicyDTO(settings.id(), settings.image(), settings.runtime(), settings.memoryBytes(), settings.cpuQuota(), settings.pids(), "artemis.aiworker",
                "aiworker-",
                Map.of("/workspace", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m", "/tmp", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m",
                        "/opt/aiworker", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=256m", "/opt/aiworker-readiness-fixture",
                        "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=64m"));
    }
}
