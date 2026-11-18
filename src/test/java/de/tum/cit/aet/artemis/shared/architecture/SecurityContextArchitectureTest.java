package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Keeps background work from depending on whatever a pooled thread happens to hold.
 *
 * <p>
 * {@link SecurityUtils#setAuthorizationObject()} deliberately keeps an existing principal, so that a path which may
 * carry a real user does not lose that user's identity. On a scheduled job the same behaviour is wrong: the thread is
 * pooled, nothing carries a caller's context to it, and whatever the previous task left behind is leftover state
 * rather than an identity. Such an entry point has to install the system principal deliberately, with
 * {@link SecurityUtils#runAsSystem(Runnable)} or {@link SecurityUtils#setSystemAuthorizationObject()}.
 *
 * <p>
 * Without this rule the distinction is a convention, and conventions of this kind drift back: the difference is
 * invisible at the call site and only shows up as a job that silently acted as the wrong principal.
 */
class SecurityContextArchitectureTest extends AbstractArchitectureTest {

    @Test
    void testScheduledMethodsInstallTheSystemPrincipalDeliberately() {
        ArchRule rule = noMethods().that().areAnnotatedWith(Scheduled.class).should(callSetAuthorizationObject())
                .because("a scheduled method runs on a pooled thread with no caller to inherit from, so it must use SecurityUtils.runAsSystem or "
                        + "SecurityUtils.setSystemAuthorizationObject rather than setAuthorizationObject, which would keep a leftover principal");
        rule.check(productionClasses);
    }

    /**
     * @return a condition satisfied by a method that calls {@link SecurityUtils#setAuthorizationObject()} directly
     */
    private static ArchCondition<JavaMethod> callSetAuthorizationObject() {
        return new ArchCondition<>("call SecurityUtils.setAuthorizationObject()") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean calls = method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().isEquivalentTo(SecurityUtils.class) && "setAuthorizationObject".equals(call.getName()));
                if (calls) {
                    events.add(SimpleConditionEvent.satisfied(method, method.getFullName() + " calls SecurityUtils.setAuthorizationObject()"));
                }
            }
        };
    }
}
