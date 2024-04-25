package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceRoleInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceRoleInExercise;

/**
 * This class contains architecture tests for endpoints with EnforceRoleInResource annotations.
 */
class EnforceRoleInResourceArchitectureTest extends AbstractArchitectureTest {

    @Test
    void testEnforceRoleInCourseEndpointHasCourseIdParameter() {
        ArchCondition<JavaMethod> haveParameterWithAnnotation = new ArchCondition<>("have a parameter with EnforceRoleInCourse annotation") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // Get annotation
                var enforceRoleInCourseAnnotation = getAnnotation(EnforceRoleInCourse.class, method);
                var courseIdFieldName = enforceRoleInCourseAnnotation.resourceIdFieldName();
                if (hasNoParameterWithName(method, courseIdFieldName)) {
                    events.add(violated(method, String.format("Method %s does not have a parameter named %s", method.getFullName(), courseIdFieldName)));
                }
            }
        };

        var enforceRoleInCourse = methods().that().areAnnotatedWith(EnforceRoleInCourse.class).or().areMetaAnnotatedWith(EnforceRoleInCourse.class).or().areDeclaredInClassesThat()
                .areAnnotatedWith(EnforceRoleInCourse.class).and().areDeclaredInClassesThat().areNotAnnotations().should(haveParameterWithAnnotation);

        enforceRoleInCourse.check(productionClasses);
    }

    @Test
    void testEnforceRoleInExerciseEndpointHasExerciseIdParameter() {
        ArchCondition<JavaMethod> haveParameterWithAnnotation = new ArchCondition<>("have a parameter with EnforceRoleInExercise annotation") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // Get annotation
                var enforceRoleInExerciseAnnotation = getAnnotation(EnforceRoleInExercise.class, method);
                var exerciseIdFieldName = enforceRoleInExerciseAnnotation.resourceIdFieldName();
                if (hasNoParameterWithName(method, exerciseIdFieldName)) {
                    events.add(violated(method, String.format("Method %s does not have a parameter named %s", method.getFullName(), exerciseIdFieldName)));
                }
            }
        };

        var enforceRoleInExercise = methods().that().areAnnotatedWith(EnforceRoleInExercise.class).or().areMetaAnnotatedWith(EnforceRoleInExercise.class).or()
                .areDeclaredInClassesThat().areAnnotatedWith(EnforceRoleInExercise.class).and().areDeclaredInClassesThat().areNotAnnotations().should(haveParameterWithAnnotation);

        enforceRoleInExercise.check(productionClasses);
    }
}
