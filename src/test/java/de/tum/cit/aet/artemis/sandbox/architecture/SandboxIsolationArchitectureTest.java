package de.tum.cit.aet.artemis.sandbox.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.sandbox.config.SandboxEnabled;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Keeps the sandbox module optional: it may use the programming module, but nothing outside the module may depend on it, and every bean of the module is
 * created only when {@code artemis.sandbox.enabled} is on, and only on first use. Switching the module off therefore removes it without touching any other
 * module, and switching it on adds nothing to the startup.
 */
class SandboxIsolationArchitectureTest extends AbstractArchitectureTest {

    private static final String SANDBOX_PACKAGE = ARTEMIS_PACKAGE + ".sandbox..";

    @Test
    void noOtherModuleDependsOnTheSandbox() {
        noClasses().that().resideOutsideOfPackage(SANDBOX_PACKAGE).should().dependOnClassesThat().resideInAPackage(SANDBOX_PACKAGE)
                .because("the sandbox module must be removable by switching it off, so the programming module and every other module must work without it")
                .check(productionClasses);
    }

    @Test
    void everyBeanOfTheSandboxIsConditionalOnTheModuleAndLazy() {
        classes().that().resideInAPackage(SANDBOX_PACKAGE).and(areSpringBeans()).should().beAnnotatedWith(Lazy.class)
                .andShould(new ArchCondition<>("be conditional on SandboxEnabled") {

                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        Optional<Conditional> conditional = javaClass.tryGetAnnotationOfType(Conditional.class);
                        if (conditional.isEmpty() || !List.of(conditional.get().value()).contains(SandboxEnabled.class)) {
                            events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " is not conditional on SandboxEnabled"));
                        }
                    }
                }).because("a disabled sandbox module must create no beans, and an enabled one must not slow down the startup").check(productionClasses);
    }

    private static DescribedPredicate<JavaClass> areSpringBeans() {
        return DescribedPredicate.describe("are Spring beans", javaClass -> javaClass.isMetaAnnotatedWith(Component.class) || javaClass.isAnnotatedWith(Service.class)
                || javaClass.isMetaAnnotatedWith(Controller.class) || javaClass.isMetaAnnotatedWith(Configuration.class));
    }
}
