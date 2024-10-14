package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
    // ToDo: In the future, we might replace this with Spring Modulith Checks
    void shouldOnlyAccessApiDomainDto() {
        noClasses().that().resideOutsideOfPackage(getModuleWithSubpackage()).should()
                .dependOnClassesThat(
                        resideInAPackage(getModuleWithSubpackage()).and(resideOutsideOfPackages(getModuleApiSubpackage(), getModuleDomainSubpackage(), getModuleDtoSubpackage())))
                .check(productionClasses);
    }

    @Test
    void testApiInheritsAbstractApi() {
        classes().that().resideInAPackage(getModuleApiSubpackage()).should().beAssignableTo(AbstractApi.class).check(productionClasses);
    }

    @Test
    void testApiIsController() {
        classes().that().resideInAPackage(getModuleApiSubpackage()).should(beAbstractOrAnnotatedWithController()).check(productionClasses);
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
