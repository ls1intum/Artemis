package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
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
                            String message = String.format("%s depends on %s which is not in an allowed package or explicitly ignored", origin.getName(), target.getName());
                            events.add(SimpleConditionEvent.violated(origin, message));
                        }
                    }
                }
            }
        };

        classes().that().resideOutsideOfPackage(getModuleWithSubpackage()).should(onlyAllowedDependencies).check(productionClasses);
    }

    @Test
    void apiClassesShouldInheritFromAbstractApi() {
        classes().that().resideInAPackage(getModuleApiSubpackage()).should().beAssignableTo(AbstractApi.class).check(productionClasses);
    }

    @Test
    void apiClassesShouldBeAbstractOrAnnotatedWithController() {
        classes().that().resideInAPackage(getModuleApiSubpackage()).should(beAbstractOrAnnotatedWithController()).check(productionClasses);
    }

    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of();
    }

    protected String getModuleApiSubpackage() {
        return getModulePackage() + ".api..";
    }

    protected String getModuleDomainSubpackage() {
        return getModulePackage() + ".domain..";
    }

    protected String getModuleDtoSubpackage() {
        return getModulePackage() + ".dto..";
    }

    private static ArchCondition<JavaClass> beAbstractOrAnnotatedWithController() {
        return new ArchCondition<>("be abstract or annotated with @Controller") {

            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean isAbstract = javaClass.getModifiers().contains(JavaModifier.ABSTRACT);
                boolean isAnnotatedWithController = javaClass.isAnnotatedWith(Controller.class);

                if (!isAbstract && !isAnnotatedWithController) {
                    String message = String.format("Class %s is neither abstract nor annotated with @Controller", javaClass.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }
}
