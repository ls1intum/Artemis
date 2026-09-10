package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.REDIS;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns Eureka off on every node that reaches the cluster through Redis.
 *
 * <p>
 * Such a deployment has nothing to discover. Eureka exists in Artemis for one purpose: finding the addresses of
 * Hazelcast members so a member or client can join the cluster. Every reader of the registry -
 * {@link HazelcastConfiguration}, {@link HazelcastClusterManager}, {@code EurekaHazelcastDiscoveryStrategy} and the
 * {@link EurekaInstanceHelper} they share - is conditional on Hazelcast, so under Redis a registration has no consumer
 * at all. Nodes find each other through the distributed node registry instead, and build agents reach the queue
 * through Redis.
 *
 * <p>
 * Leaving discovery on is not merely a wasted registration. On a build agent the context fails to start outright,
 * because {@code JHipsterRegistryHealthIndicator} asks for a {@code Registration} and the Eureka client it pulls up has
 * no {@code TransportClientFactories} to build itself from. On a core node it opens a client, registers, heartbeats and
 * reports health for a service nothing reads - surface a Redis deployment should not carry, and one more thing that has
 * to be running for Artemis to come up.
 *
 * <p>
 * Properties do this rather than {@code @EnableAutoConfiguration(exclude = ...)}, which is what an earlier version
 * used. Excluding the auto-configurations leaves {@code eureka.client.enabled} at the {@code true} that
 * {@code application-buildagent.yml} sets, so the surrounding beans still believe discovery is active and ask for parts
 * that no longer exist; a hand-maintained list of Spring Cloud class names also drifts on every upgrade, and Boot fails
 * startup outright once one of those names stops being an auto-configuration class. With these two properties set,
 * Spring Cloud's own conditions keep every discovery and Eureka auto-configuration out of the context, so no client,
 * registration, heartbeat thread or health indicator is created in the first place.
 *
 * <p>
 * The property source is added first on purpose: {@code application-buildagent.yml} and the deployment's environment
 * would otherwise win, and neither can express "except on Redis".
 */
public class RedisDiscoveryEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "redisDiscovery";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!DistributedDataProviderResolver.isProvider(environment, REDIS)) {
            return;
        }
        // Two switches, because they remove different halves. Turning the Eureka client off stops the registration,
        // the heartbeat and the registry health indicator; it leaves Spring Cloud's own discovery health indicators
        // behind, reporting "Discovery Client not initialized" for ever, which drags the whole health endpoint down.
        // Turning discovery off removes those, and keeps every DiscoveryClient auto-configuration out of the context.
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of("eureka.client.enabled", "false", "spring.cloud.discovery.enabled", "false")));
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
