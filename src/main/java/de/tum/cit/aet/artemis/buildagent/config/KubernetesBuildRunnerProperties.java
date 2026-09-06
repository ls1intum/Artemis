package de.tum.cit.aet.artemis.buildagent.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for LocalCI build execution through Kubernetes Jobs.
 */
@ConfigurationProperties(prefix = "artemis.continuous-integration.kubernetes")
public record KubernetesBuildRunnerProperties(@DefaultValue("artemis-builds") String namespace, @DefaultValue("artemis-localci-helper:local") String helperImage,
        @DefaultValue("IfNotPresent") String imagePullPolicy, @DefaultValue("artemis-localci-workload") String workloadServiceAccount,
        @DefaultValue("120") int podStartTimeoutSeconds, @DefaultValue("60") int resultCollectionTimeoutSeconds, @DefaultValue("300") int jobTtlSeconds,
        @DefaultValue("60") int activeDeadlineGraceSeconds, @DefaultValue("30") int orphanCleanupIntervalSeconds, @DefaultValue("4Gi") String workspaceSizeLimit,
        @DefaultValue Resources defaultResources, @DefaultValue Map<String, String> nodeSelector, @DefaultValue List<Toleration> tolerations,
        @DefaultValue List<String> imagePullSecrets) {

    public record Resources(@DefaultValue("2") String cpu, @DefaultValue("2Gi") String memory, @DefaultValue("4Gi") String ephemeralStorage) {
    }

    public record Toleration(String key, String operator, String value, String effect, Long tolerationSeconds) {
    }
}
