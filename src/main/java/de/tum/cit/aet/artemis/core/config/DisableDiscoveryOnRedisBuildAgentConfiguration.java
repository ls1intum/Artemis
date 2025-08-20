package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Disables Discovery/Eureka auto-configuration on build agent nodes when the "redis" profile is active
 * and the CORE profile is not present. This avoids starting a DiscoveryClient where it is not needed,
 * regardless of eureka.client.enabled or discovery properties.
 */
@Profile("redis & !" + PROFILE_CORE)
@Configuration
@EnableAutoConfiguration(excludeName = { "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.loadbalancer.LoadBalancerEurekaAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.config.DiscoveryClientOptionalArgsConfiguration", "org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration",
        "org.springframework.cloud.netflix.eureka.EurekaClientConfigServerAutoConfiguration", "org.springframework.cloud.netflix.eureka.EurekaHealthIndicatorAutoConfiguration", })
public class DisableDiscoveryOnRedisBuildAgentConfiguration {
}
