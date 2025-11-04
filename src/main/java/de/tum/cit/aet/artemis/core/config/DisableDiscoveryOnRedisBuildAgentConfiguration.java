package de.tum.cit.aet.artemis.core.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Disables Discovery/Eureka auto-configuration on build agent nodes when Redis is used for CI data storage
 * and the CORE profile is not present. This avoids starting a DiscoveryClient where it is not needed.
 */
@Lazy
@Conditional(RedisNotCoreCondition.class)
@Configuration
@EnableAutoConfiguration(excludeName = { "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.loadbalancer.LoadBalancerEurekaAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.config.DiscoveryClientOptionalArgsConfiguration", "org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration",
        "org.springframework.cloud.netflix.eureka.EurekaClientConfigServerAutoConfiguration", "org.springframework.cloud.netflix.eureka.EurekaHealthIndicatorAutoConfiguration", })
public class DisableDiscoveryOnRedisBuildAgentConfiguration {
}
