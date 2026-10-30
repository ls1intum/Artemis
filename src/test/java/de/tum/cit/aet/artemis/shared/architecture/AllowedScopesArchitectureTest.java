package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.cit.aet.artemis.core.security.allowedScopes.AllowedScopes;

/**
 * Keeps {@link AllowedScopes} a narrowing of access and never a replacement for the role check.
 * <p>
 * A personal access token authenticates as its owner, so an endpoint that opts in to tokens still has to decide, like any
 * other, which roles may call it. An {@code @AllowedScopes} endpoint without an {@code @EnforceAtLeast...} annotation would
 * be reachable by every authenticated user who holds the scope, whatever their role in the course.
 */
class AllowedScopesArchitectureTest extends AbstractArchitectureTest {

    private static final String ENFORCE_ROLE_ANNOTATION_PREFIX = "EnforceAtLeast";

    @Test
    void endpointsOptedInToTokensEnforceARole() {
        methods().that().areAnnotatedWith(AllowedScopes.class).should(haveAnEnforceAtLeastAnnotation()).check(productionClasses);
    }

    private static ArchCondition<JavaMethod> haveAnEnforceAtLeastAnnotation() {
        return new ArchCondition<>("be annotated with an @EnforceAtLeast... annotation") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean enforcesRole = method.getAnnotations().stream().map(JavaAnnotation::getRawType)
                        .anyMatch(annotationType -> annotationType.getSimpleName().startsWith(ENFORCE_ROLE_ANNOTATION_PREFIX));
                if (!enforcesRole) {
                    events.add(violated(method, method.getFullName() + " is annotated with @AllowedScopes but not with an @EnforceAtLeast... annotation"));
                }
            }
        };
    }
}
