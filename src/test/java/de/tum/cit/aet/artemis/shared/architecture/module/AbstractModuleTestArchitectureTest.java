package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

public abstract class AbstractModuleTestArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

    protected abstract Set<Class<?>> getAbstractModuleIntegrationTestClasses();

    @Test
    void integrationTestsShouldExtendAbstractModuleIntegrationTest() {
        classesOfThisModuleThat().haveSimpleNameEndingWith("IntegrationTest").should().beAssignableTo(isAssignableToAnyAllowedClass(getAbstractModuleIntegrationTestClasses()))
                .because("All integration tests should extend any of %s".formatted(getAbstractModuleIntegrationTestClasses())).check(testClasses);
    }

    @Test
    void integrationTestsShouldNotAutowireMembers() {
        classes().that().doNotHaveModifier(JavaModifier.ABSTRACT).and().areAssignableTo(isAssignableToAnyAllowedClass(getAbstractModuleIntegrationTestClasses()))
                .should(notHaveAutowiredFieldsOrMethods())
                .because("Integration tests should not autowire members in any class that inherits from any of %s".formatted(getAbstractModuleIntegrationTestClasses()))
                .check(testClasses);
    }

    private static DescribedPredicate<JavaClass> isAssignableToAnyAllowedClass(Iterable<Class<?>> allowedClasses) {
        return new DescribedPredicate<>(stringifyClasses(allowedClasses)) {

            @Override
            public boolean test(JavaClass javaClass) {
                for (Class<?> allowedClass : allowedClasses) {
                    if (javaClass.isAssignableTo(allowedClass)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static ArchCondition<JavaClass> notHaveAutowiredFieldsOrMethods() {
        return new ArchCondition<>("not have @Autowired fields or methods") {

            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                // Check fields for @Autowired
                for (JavaField field : javaClass.getFields()) {
                    if (field.isAnnotatedWith(Autowired.class)) {
                        String message = String.format("%s has a field %s annotated with @Autowired", javaClass.getName(), field.getName());
                        events.add(SimpleConditionEvent.violated(field, message));
                    }
                }

                // Check methods for @Autowired
                javaClass.getMethods().stream().filter(method -> method.isAnnotatedWith(Autowired.class)).forEach(method -> {
                    String message = String.format("%s has a method %s annotated with @Autowired", javaClass.getName(), method.getName());
                    events.add(SimpleConditionEvent.violated(method, message));
                });
            }
        };
    }

    private static String stringifyClasses(Iterable<Class<?>> classes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Class<?> clazz : classes) {
            stringBuilder.append(clazz.getSimpleName()).append(", ");
        }
        return stringBuilder.substring(0, stringBuilder.length() - 2);
    }
}
