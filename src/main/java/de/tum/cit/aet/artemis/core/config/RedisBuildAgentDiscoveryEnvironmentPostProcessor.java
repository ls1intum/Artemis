package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.REDIS;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns Eureka off on a build agent that reaches the cluster through Redis.
 *
 * <p>
 * Such a node has nothing to discover: with Redis there is no Hazelcast member list to join, the build queue is reached
 * through Redis, and Artemis only contributes the Eureka client plumbing on core nodes and Hazelcast build agents (see
 * {@link EurekaClientConfiguration}). Leaving discovery on therefore does not merely waste a registration — the context
 * fails to start, because {@code JHipsterRegistryHealthIndicator} asks for a {@code Registration} and the Eureka client
 * it pulls up has no {@code TransportClientFactories} to build itself from.
 *
 * <p>
 * This is done here rather than with {@code @EnableAutoConfiguration(exclude = ...)} on a conditional configuration
 * class, which is what it used to be. Excluding the auto-configurations leaves {@code eureka.client.enabled} at the
 * {@code true} that {@code application-buildagent.yml} sets for every build agent, so the surrounding beans still
 * believe discovery is active and ask for parts that no longer exist. One property switches all of them off coherently,
 * and it cannot drift out of date the way a hand-maintained list of Spring Cloud class names does.
 *
 * <p>
 * The property source is added first on purpose: {@code application-buildagent.yml} would otherwise win, and a build
 * agent has no way to express "except on Redis" in YAML.
 */
public class RedisBuildAgentDiscoveryEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "redisBuildAgentDiscovery";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (!activeProfiles.contains(PROFILE_BUILDAGENT) || activeProfiles.contains(PROFILE_CORE)) {
            return;
        }
        if (!DistributedDataProviderResolver.isProvider(environment, REDIS)) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of("eureka.client.enabled", "false")));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Runs last, so that the configuration files and the active profiles it reads are already in place.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
