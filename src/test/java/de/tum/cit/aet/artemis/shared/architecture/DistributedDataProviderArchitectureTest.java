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

/** Restricts direct Redis dependencies to the infrastructure that implements the provider. */
class DistributedDataProviderArchitectureTest extends AbstractArchitectureTest {

    /**
     * Packages of the provider libraries that application code must not reach into.
     */
    private static final String[] PROVIDER_LIBRARY_PACKAGES = { "org.redisson..", "org.springframework.data.redis..", "org.springframework.boot.data.redis.." };

    /** Redis adapters, configuration, rate limit storage and health checks require the provider library. */
    private static final List<String> INFRASTRUCTURE_ALLOWED_TO_USE_A_PROVIDER_LIBRARY_DIRECTLY = List.of("de.tum.cit.aet.artemis.core.service.distributed.redisson.",
            "de.tum.cit.aet.artemis.core.config.RateLimitConfig", "de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration",
            "de.tum.cit.aet.artemis.core.service.connectors.RedisHealthIndicator", "de.tum.cit.aet.artemis.config.DisableRedisAutoConfig");

    private static final String REASON = """
            all cross-node state must go through DistributedDataProvider. Using Redis directly pins the \
            deployment to that provider without any test noticing, because the other provider never sees the state. If a \
            capability is genuinely missing from the abstraction, add it to DistributedDataProvider together with an \
            implementation for every provider and a case in AbstractDistributedDataTest, rather than reaching past it. \
            The infrastructure classes that legitimately adapt a single provider are listed in \
            DistributedDataProviderArchitectureTest.""";

    @Test
    void testNoDirectProviderLibraryUsageOutsideTheProviderImplementations() {
        ArchRule rule = noClasses().that(not(isProviderInfrastructure())).should().dependOnClassesThat(resideInAnyPackage(PROVIDER_LIBRARY_PACKAGES)).because(REASON);

        rule.check(productionClasses);
    }

    /**
     * Guards the allowlist itself: an entry that no longer matches any class is a leftover that would quietly widen the
     * rule the next time somebody creates a class with that name.
     */
    @Test
    void testEveryAllowlistEntryStillMatchesAClass() {
        Set<String> productionClassNames = productionClasses.stream().map(JavaClass::getFullName).collect(java.util.stream.Collectors.toSet());
        for (String allowed : INFRASTRUCTURE_ALLOWED_TO_USE_A_PROVIDER_LIBRARY_DIRECTLY) {
            assertThat(productionClassNames).as("Allowlist entry '%s' matches no production class, remove it", allowed).anyMatch(name -> matches(name, allowed));
        }
    }

    /**
     * Guards against the abstraction being bypassed by widening the allowlist: every allowlisted class must actually
     * still need a provider library, otherwise the entry is dead weight that hides the next real violation.
     */
    @Test
    void testEveryAllowlistEntryStillDependsOnAProviderLibrary() {
        JavaClasses infrastructure = productionClasses.that(isProviderInfrastructure());
        for (String allowed : INFRASTRUCTURE_ALLOWED_TO_USE_A_PROVIDER_LIBRARY_DIRECTLY) {
            boolean anyUsesAProviderLibrary = infrastructure.stream().filter(javaClass -> matches(javaClass.getFullName(), allowed))
                    .anyMatch(javaClass -> javaClass.getDirectDependenciesFromSelf().stream().anyMatch(dependency -> isProviderLibraryClass(dependency.getTargetClass())));
            assertThat(anyUsesAProviderLibrary).as("Allowlist entry '%s' no longer uses Redis directly, remove it", allowed).isTrue();
        }
    }

    private static boolean isProviderLibraryClass(JavaClass javaClass) {
        return resideInAnyPackage(PROVIDER_LIBRARY_PACKAGES).test(javaClass);
    }

    private static DescribedPredicate<JavaClass> isProviderInfrastructure() {
        return new DescribedPredicate<>("infrastructure that adapts a single distributed data provider") {

            @Override
            public boolean test(JavaClass javaClass) {
                return INFRASTRUCTURE_ALLOWED_TO_USE_A_PROVIDER_LIBRARY_DIRECTLY.stream().anyMatch(allowed -> matches(javaClass.getFullName(), allowed));
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
