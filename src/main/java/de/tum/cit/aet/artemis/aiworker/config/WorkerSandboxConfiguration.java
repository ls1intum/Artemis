package de.tum.cit.aet.artemis.aiworker.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO;

/** Trusted sandbox policy; the sandbox implementation knows neither exercises nor toolchains. */
@Configuration(proxyBeanMethods = false)
@Profile(PROFILE_AIWORKER)
@Lazy
public class WorkerSandboxConfiguration {

    /**
     * Preserves the workload's ownership namespace and bounded scratch mounts across worker restarts.
     *
     * @param settings      validated local deployment configuration
     * @param dockerClient  the worker's Docker client, used to pull the published sandbox image when none is configured
     * @param gitProperties the running build's commit, which selects the published sandbox image; only needed when no image is configured
     * @return the immutable execution policy
     */
    @Bean
    public SandboxPolicyDTO sandboxPolicy(WorkerSettings settings, DockerClient dockerClient, ObjectProvider<GitProperties> gitProperties) {
        String image = settings.image();
        if (image == null || image.isBlank()) {
            GitProperties git = gitProperties.getIfAvailable();
            image = PublishedSandboxImage.resolve(dockerClient, git == null ? null : git.getCommitId());
        }
        return new SandboxPolicyDTO(settings.id(), image, settings.runtime(), settings.memoryBytes(), settings.cpuQuota(), settings.pids(), "artemis.aiworker", "aiworker-",
                Map.of("/workspace", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m", "/tmp", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m",
                        "/opt/aiworker", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=256m", "/opt/aiworker-readiness-fixture",
                        "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=64m"));
    }
}
