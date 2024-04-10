package de.tum.in.www1.artemis.domain.notification;

import static com.tngtech.archunit.base.DescribedPredicate.allElements;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.in.www1.artemis.architecture.AbstractArchitectureTest;

class NotificationPlaceholderArchitectureTest extends AbstractArchitectureTest {

    private static DescribedPredicate<? super List<JavaClass>> allStringParameters() {
        return allElements(type(String.class)).as("All parameters should be of type String.");
    }

    /**
     * Test that all NotificationPlaceholderCreator methods return a String array and only have Strings as arguments.
     */
    @Test
    void testPlaceholderCreatorMethodSignature() {
        methods().that().areAnnotatedWith(NotificationPlaceholderCreator.class).should().haveRawReturnType(String[].class).andShould().haveRawParameterTypes(allStringParameters())
                .check(productionClasses);
    }

    /**
     * Test that all NotificationPlaceholderCreator methods return a String array exactly contains its arguments in the specified order.
     */
    @Test
    void testNotificationPlaceholderCreatorStringArrayCreation() {
        methods().that().areAnnotatedWith(NotificationPlaceholderCreator.class).should(new ArchCondition<>("returns an array of its input parameters in the order of declaration") {

            @Override
            public void check(JavaMethod javaMethod, ConditionEvents conditionEvents) {
                var method = javaMethod.reflect();
                var paramCount = javaMethod.getParameters().size();

                var description = "NotificationPlaceholderCreator" + method.getName() + " in class " + method.getDeclaringClass().getName();

                String[] params = IntStream.range(0, paramCount).mapToObj(i -> "param" + i).toArray(String[]::new);

                try {
                    var result = method.invoke(null, (Object[]) params);
                    if (!(result instanceof String[])) {
                        throw new RuntimeException("Method " + description + " does not return a string array.");
                    }

                    if (!Arrays.equals(params, (String[]) result)) {
                        conditionEvents.add(violated(javaMethod,
                                String.format("Method %s does not return an array of its input parameters in the order of declaration", javaMethod.getFullName())));
                    }
                }
                catch (IllegalAccessException e) {
                    throw new RuntimeException("Could not access " + description, e);
                }
                catch (InvocationTargetException e) {
                    throw new RuntimeException("Could not invoke " + description + " with a string array.", e);
                }
            }
        }).check(productionClasses);
    }
}
