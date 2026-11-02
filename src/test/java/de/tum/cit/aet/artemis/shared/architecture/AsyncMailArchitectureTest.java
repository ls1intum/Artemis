package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Keeps mail sending off the shared task executor.
 *
 * <p>
 * The integration tests share one {@code JavaMailSender} spy per Spring context and stub and reset it around every test.
 * Mockito keeps the stubbing state of a mock in the mock itself, so a thread that calls the spy while a test stubs or
 * resets it corrupts that state, and the victim is whichever test happens to run next (a {@code MockitoException} in
 * {@code AbstractArtemisIntegrationTest.mockMailService}). The mail methods therefore run on the {@code mailTaskExecutor},
 * which is synchronous under the {@code test} profile and the shared pool otherwise.
 *
 * <p>
 * That only works if no other background task reaches the mail service on the shared pool. A task that is {@code @Async}
 * without a named executor runs on the shared pool in the tests as well, and the mail executor then runs on that pool
 * thread, because a synchronous executor sends on the thread that calls it. This rule rejects such a path.
 */
class AsyncMailArchitectureTest extends AbstractArchitectureTest {

    private static final String SHARED_EXECUTOR = "taskExecutor";

    @Test
    void testAsyncMethodsOnTheSharedExecutorDoNotSendMail() {
        ArchRule rule = methods().that().areAnnotatedWith(Async.class).and().areDeclaredInClassesThat().resideInAPackage("de.tum.cit.aet.artemis..")
                .should(notReachMailSendingOnTheSharedExecutor()).because("the tests would then send mail from a pooled thread, which races with the stubbing of the shared "
                        + "JavaMailSender spy in the next test. Use @Async(\"mailTaskExecutor\"), which is synchronous under the test profile.");
        rule.check(productionClasses);
    }

    private static ArchCondition<JavaMethod> notReachMailSendingOnTheSharedExecutor() {
        return new ArchCondition<>("not reach MailSendingService while running on the shared task executor") {

            @Override
            public void check(JavaMethod entry, ConditionEvents events) {
                String executor = entry.getAnnotationOfType(Async.class).value();
                if (!executor.isEmpty() && !SHARED_EXECUTOR.equals(executor)) {
                    return;
                }
                List<JavaMethod> path = findPathToMailSending(entry);
                if (path != null) {
                    String route = path.stream().map(JavaMethod::getFullName).reduce((left, right) -> left + " -> " + right).orElseThrow();
                    events.add(SimpleConditionEvent.violated(entry, entry.getFullName() + " runs on the shared task executor and reaches the mail service: " + route));
                }
            }
        };
    }

    /**
     * Breadth first search over the calls that production code makes, from the given method to a method of the mail
     * service. Calls are followed to their declared target (a call through an interface is not followed into the
     * implementations, which would reach every listener callback), and a lambda counts as part of the method that creates it.
     *
     * @param entry the method to start from
     * @return the methods from the entry to the first call of the mail service, or null if there is none
     */
    private static List<JavaMethod> findPathToMailSending(JavaMethod entry) {
        Map<JavaMethod, JavaMethod> cameFrom = new HashMap<>();
        Set<JavaMethod> seen = new HashSet<>();
        Deque<JavaMethod> queue = new ArrayDeque<>();
        queue.add(entry);
        seen.add(entry);
        while (!queue.isEmpty()) {
            JavaMethod current = queue.poll();
            for (JavaMethod callee : calleesOf(current)) {
                if (callee.getOwner().isEquivalentTo(MailSendingService.class)) {
                    List<JavaMethod> path = new ArrayList<>();
                    for (JavaMethod step = current; step != null; step = cameFrom.get(step)) {
                        path.add(0, step);
                    }
                    path.add(callee);
                    return path;
                }
                if (callee.getOwner().getPackageName().startsWith("de.tum.cit.aet.artemis") && seen.add(callee)) {
                    cameFrom.put(callee, current);
                    queue.add(callee);
                }
            }
        }
        return null;
    }

    private static Set<JavaMethod> calleesOf(JavaMethod method) {
        Set<JavaMethod> callees = new HashSet<>();
        Set<JavaMethod> bodies = new HashSet<>();
        bodies.add(method);
        method.getOwner().getMethods().stream().filter(candidate -> candidate.getName().startsWith("lambda$" + method.getName() + "$")).forEach(bodies::add);
        for (JavaMethod body : bodies) {
            for (JavaMethodCall call : body.getMethodCallsFromSelf()) {
                Optional<JavaMethod> resolved = call.getTarget().resolveMember();
                resolved.ifPresent(callees::add);
            }
        }
        return callees;
    }
}
