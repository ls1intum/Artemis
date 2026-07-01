package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

public abstract class AbstractModuleAccessArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

    @Test
    void shouldOnlyAccessApiDomainDtoAndAllowedException() {
        ArchCondition<JavaClass> onlyAllowedDependencies = new ArchCondition<>("have only allowed dependencies") {

            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                List<Dependency> targetsInModule = origin.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> resideInAPackage(getModuleWithSubpackage()).test(dependency.getTargetClass())).toList();

                for (Dependency dependency : targetsInModule) {
                    JavaClass target = dependency.getTargetClass();

                    if (resideOutsideOfPackage(getModuleWithSubpackage()).test(origin)) {
                        // target inside default-allowed packages (API, Domain, DTO)
                        boolean inDefaultAllowedPackage = resideInAnyPackage(getModuleApiSubpackage(), getModuleDomainSubpackage(), getModuleDtoSubpackage()).test(target);
                        if (inDefaultAllowedPackage) {
                            continue;
                        }

                        // target explicitly ignored
                        boolean isIgnored = getIgnoredClasses().contains(target.reflect());
                        if (!isIgnored) {
                            String message = "%s depends on %s which is not in an allowed package or explicitly ignored".formatted(origin.getName(), target.getName());
                            events.add(SimpleConditionEvent.violated(origin, message));
                        }
                    }
                }
            }
        };

        classes().that().resideOutsideOfPackage(getModuleWithSubpackage()).should(onlyAllowedDependencies).check(productionClasses);
    }

    /**
     * Safeguard against re-introducing cross-module repository leaks. A module's repository must never be placed on the
     * access ignore list to silence {@link #shouldOnlyAccessApiDomainDtoAndAllowedException()}: a class in another module
     * that depends directly on an (optional) module's repository breaks application startup when that module is disabled,
     * because the repository bean does not exist. This is exactly how {@code ExerciseVersionService} ended up hard-wired to
     * {@code TextExerciseRepository}. Cross-module repository access must go through the module's {@code api} package, which
     * is gated by the same {@code @Conditional} as the repository and can therefore be injected as {@code Optional<...Api>}.
     */
    @Test
    void ignoredClassesShouldNotContainRepositories() {
        for (Class<?> ignoredClass : getIgnoredClasses()) {
            boolean isRepository = ignoredClass.getPackageName().contains(".repository") || Repository.class.isAssignableFrom(ignoredClass);
            assertThat(isRepository).as(
                    "%s must not be on the module access ignore list: cross-module access to a repository must go through the module's api package, otherwise disabling the module breaks application startup",
                    ignoredClass.getName()).isFalse();
        }
    }

    @Test
    void apiClassesShouldInheritFromAbstractApi() {
        classes().that(not(belongToAnyOf(getIgnoredClasses().toArray(Class<?>[]::new)))).and().resideInAPackage(getModuleApiSubpackage()).and()
                .resideOutsideOfPackage(getModuleApiDtoSubpackage()).should().beAssignableTo(AbstractApi.class).check(productionClasses);
    }

    @Test
    void apiClassesShouldBeAbstractOrAnnotatedWithController() {
        classes().that(not(belongToAnyOf(getIgnoredClasses().toArray(Class<?>[]::new)))).and().resideInAPackage(getModuleApiSubpackage()).and()
                .resideOutsideOfPackage(getModuleApiDtoSubpackage()).should(beAbstractOrAnnotatedWithController()).check(productionClasses);
    }

    protected String getModuleApiSubpackage() {
        return getModulePackage() + ".api..";
    }

    protected String getModuleApiDtoSubpackage() {
        return getModulePackage() + ".api.dtos..";
    }

    protected String getModuleDomainSubpackage() {
        return getModulePackage() + ".domain..";
    }

    protected String getModuleDtoSubpackage() {
        return getModulePackage() + ".dto..";
    }

    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of();
    }

    private static ArchCondition<JavaClass> beAbstractOrAnnotatedWithController() {
        return new ArchCondition<>("be abstract or annotated with @Controller") {

            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean isAbstract = javaClass.getModifiers().contains(JavaModifier.ABSTRACT);
                boolean isAnnotatedWithController = javaClass.isAnnotatedWith(Controller.class);

                if (!isAbstract && !isAnnotatedWithController) {
                    String message = "Class %s is neither abstract nor annotated with @Controller".formatted(javaClass.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }
}
