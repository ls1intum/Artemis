package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps the distributed data provider abstraction the only way into Hazelcast and Redis.
 *
 * <p>
 * Artemis supports Hazelcast and Redis as interchangeable backends for all cross-node state, and intends to be able to
 * switch between them. That only holds as long as application code talks to
 * {@link de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider} rather than to a backend directly:
 * a single {@code hazelcastInstance.getMap(...)} in a service silently pins the whole deployment to Hazelcast, and it
 * does so without failing any test, because the other backend simply never sees that state. That is exactly how the
 * abstraction drifted the first time, when 21 services across 8 modules had grown their own Hazelcast usage.
 *
 * <p>
 * The rules below therefore forbid every dependency on a backend library outside a small, explicitly named set of
 * infrastructure classes. Adding a class to {@link #INFRASTRUCTURE_ALLOWED_TO_USE_A_BACKEND_DIRECTLY} is a deliberate
 * decision that needs a reason recorded next to the entry, not a way to make a failing build green.
 */
class DistributedDataProviderArchitectureTest extends AbstractArchitectureTest {

    /**
     * Packages of the backend libraries that application code must not reach into.
     */
    private static final String[] BACKEND_PACKAGES = { "com.hazelcast..", "org.redisson..", "org.springframework.data.redis..", "org.springframework.boot.data.redis.." };

    /**
     * The only production classes that may depend on a backend library directly.
     *
     * <p>
     * Each entry is infrastructure that exists precisely to adapt one backend, and therefore cannot be written against
     * the abstraction:
     * <ul>
     * <li>{@code core.service.distributed.hazelcast} / {@code core.service.distributed.redisson} — the two provider
     * implementations the abstraction dispatches to.</li>
     * <li>{@code HazelcastConfiguration}, {@code HazelcastClusterManager}, {@code HazelcastPathSerializer},
     * {@code EurekaHazelcastDiscoveryStrategy(Factory)} — bootstrap of the Hazelcast instance and its cluster
     * discovery, which has to exist before any provider can be built on top of it.</li>
     * <li>{@code DisableRedisAutoConfig} — switches Spring Boot's Redis auto-configuration off when Redis is not the
     * configured provider, so it necessarily names the auto-configuration classes.</li>
     * <li>{@code RedissonCodecConfiguration} — aligns Redis serialization with Hazelcast's, which means naming the
     * Redisson codec.</li>
     * <li>{@code RateLimitConfig} — Bucket4j ships one storage module per backend and offers no common abstraction, so
     * the bean that selects the storage has to name both.</li>
     * <li>{@code HazelcastHealthIndicator}, {@code RedisHealthIndicator}, {@code ArtemisMetricsEndpoint} — report
     * backend-specific health and statistics that have no equivalent on the other backend, and degrade to nothing when
     * their backend is not the configured one.</li>
     * </ul>
     */
    private static final List<String> INFRASTRUCTURE_ALLOWED_TO_USE_A_BACKEND_DIRECTLY = List.of("de.tum.cit.aet.artemis.core.service.distributed.hazelcast.",
            "de.tum.cit.aet.artemis.core.service.distributed.redisson.", "de.tum.cit.aet.artemis.core.config.HazelcastConfiguration",
            "de.tum.cit.aet.artemis.core.config.HazelcastClusterManager", "de.tum.cit.aet.artemis.core.config.HazelcastPathSerializer",
            "de.tum.cit.aet.artemis.core.config.EurekaHazelcastDiscoveryStrategy", "de.tum.cit.aet.artemis.core.config.EurekaHazelcastDiscoveryStrategyFactory",
            "de.tum.cit.aet.artemis.core.config.RateLimitConfig", "de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration",
            "de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint", "de.tum.cit.aet.artemis.core.service.connectors.HazelcastHealthIndicator",
            "de.tum.cit.aet.artemis.core.service.connectors.RedisHealthIndicator", "de.tum.cit.aet.artemis.config.DisableRedisAutoConfig");

    private static final String REASON = """
            all cross-node state must go through DistributedDataProvider. Using Hazelcast or Redis directly pins the \
            deployment to that backend without any test noticing, because the other backend never sees the state. If a \
            capability is genuinely missing from the abstraction, add it to DistributedDataProvider together with an \
            implementation for every backend and a case in AbstractDistributedDataTest, rather than reaching past it. \
            The infrastructure classes that legitimately adapt a single backend are listed in \
            DistributedDataProviderArchitectureTest.""";

    @Test
    void testNoDirectBackendUsageOutsideTheProviderImplementations() {
        ArchRule rule = noClasses().that(not(isBackendInfrastructure())).should().dependOnClassesThat(resideInAnyPackage(BACKEND_PACKAGES)).because(REASON);

        rule.check(productionClasses);
    }

    /**
     * Guards the allowlist itself: an entry that no longer matches any class is a leftover that would quietly widen the
     * rule the next time somebody creates a class with that name.
     */
    @Test
    void testEveryAllowlistEntryStillMatchesAClass() {
        Set<String> productionClassNames = productionClasses.stream().map(JavaClass::getFullName).collect(java.util.stream.Collectors.toSet());
        for (String allowed : INFRASTRUCTURE_ALLOWED_TO_USE_A_BACKEND_DIRECTLY) {
            assertThat(productionClassNames).as("Allowlist entry '%s' matches no production class, remove it", allowed).anyMatch(name -> matches(name, allowed));
        }
    }

    /**
     * Guards against the abstraction being bypassed by widening the allowlist: every allowlisted class must actually
     * still need a backend, otherwise the entry is dead weight that hides the next real violation.
     */
    @Test
    void testEveryAllowlistEntryStillDependsOnABackend() {
        JavaClasses infrastructure = productionClasses.that(isBackendInfrastructure());
        for (String allowed : INFRASTRUCTURE_ALLOWED_TO_USE_A_BACKEND_DIRECTLY) {
            boolean anyUsesABackend = infrastructure.stream().filter(javaClass -> matches(javaClass.getFullName(), allowed))
                    .anyMatch(javaClass -> javaClass.getDirectDependenciesFromSelf().stream().anyMatch(dependency -> isBackendClass(dependency.getTargetClass())));
            assertThat(anyUsesABackend).as("Allowlist entry '%s' no longer uses Hazelcast or Redis directly, remove it", allowed).isTrue();
        }
    }

    private static boolean isBackendClass(JavaClass javaClass) {
        return resideInAnyPackage(BACKEND_PACKAGES).test(javaClass);
    }

    private static DescribedPredicate<JavaClass> isBackendInfrastructure() {
        return new DescribedPredicate<>("infrastructure that adapts a single distributed data backend") {

            @Override
            public boolean test(JavaClass javaClass) {
                return INFRASTRUCTURE_ALLOWED_TO_USE_A_BACKEND_DIRECTLY.stream().anyMatch(allowed -> matches(javaClass.getFullName(), allowed));
            }
        };
    }

    /**
     * @param className the fully qualified name of a class, where a nested class is separated by {@code $}
     * @param allowed   an allowlist entry: a package prefix when it ends in a dot, otherwise a class name
     * @return true if the class is covered by the entry
     */
    private static boolean matches(String className, String allowed) {
        if (allowed.endsWith(".")) {
            return className.startsWith(allowed);
        }
        return className.equals(allowed) || className.startsWith(allowed + "$");
    }
}
