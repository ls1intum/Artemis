package de.tum.cit.aet.artemis.buildagent.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

/**
 * Creates the Kubernetes API client only for build agents configured with the Kubernetes runner.
 */
@Configuration
@Lazy
@Profile(PROFILE_BUILDAGENT)
@EnableConfigurationProperties(KubernetesBuildRunnerProperties.class)
@ConditionalOnProperty(prefix = "artemis.continuous-integration", name = "build-runner", havingValue = "kubernetes")
public class KubernetesBuildRunnerConfiguration {

    @Bean
    KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
