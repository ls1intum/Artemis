package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

public abstract class AbstractModuleAccessArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

    @Test
    void shouldOnlyAccessApiDomainDto() {
        noClasses().that(not(belongToAnyOf(getIgnoredClasses().toArray(Class<?>[]::new)))).and().resideOutsideOfPackage(getModuleWithSubpackage()).should()
                .dependOnClassesThat(
                        resideInAPackage(getModuleWithSubpackage()).and(resideOutsideOfPackages(getModuleApiSubpackage(), getModuleDomainSubpackage(), getModuleDtoSubpackage())))
                .check(productionClasses);
    }

    @Test
    void apiClassesShouldInheritFromAbstractApi() {
        classes().that(not(belongToAnyOf(getIgnoredClasses().toArray(Class<?>[]::new)))).and().resideInAPackage(getModuleApiSubpackage()).should().beAssignableTo(AbstractApi.class)
                .check(productionClasses);
    }

    @Test
    void apiClassesShouldBeAbstractOrAnnotatedWithController() {
        classes().that(not(belongToAnyOf(getIgnoredClasses().toArray(Class<?>[]::new)))).and().resideInAPackage(getModuleApiSubpackage())
                .should(beAbstractOrAnnotatedWithController()).check(productionClasses);
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
                    String message = String.format("Class %s is neither abstract nor annotated with @Controller", javaClass.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }
}
