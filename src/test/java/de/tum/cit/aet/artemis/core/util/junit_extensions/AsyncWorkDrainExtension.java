package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.async.ExceptionHandlingAsyncTaskExecutor;

/**
 * Lets the work that a test left on the shared {@code taskExecutor} finish before the test is torn down, so that it cannot run into the next test.
 * <p>
 * The integration tests of one Spring context share their spies ({@code JavaMailSender}, the notification and websocket services, ...) and stub and reset them
 * around every test. Mockito keeps the stubbing state in the mock itself, so a thread that calls a spy while another thread stubs or resets it corrupts that
 * state, and the victim is whichever test runs next, typically with a {@code MockitoException} in a {@code @BeforeEach}. Production code reaches those spies
 * from {@code @Async} methods, and a request that triggers one returns before the task has finished, so the test can end with a task still running.
 * <p>
 * This extension runs right after the test method and therefore before the {@code @AfterEach} methods that reset the spies, and waits until the pool has
 * neither running nor queued tasks. Tests of a context run one after the other, so what is in the pool belongs to the test that just ended. It waits for a
 * condition rather than for a fixed time and costs nothing when the pool is idle. If a task does not finish within the timeout, the extension gives up on that
 * context for the rest of the run instead of delaying every following test, and says so in the log.
 */
public class AsyncWorkDrainExtension implements AfterTestExecutionCallback {

    private static final Logger log = LoggerFactory.getLogger(AsyncWorkDrainExtension.class);

    private static final String SHARED_EXECUTOR_BEAN = "taskExecutor";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(2);

    /**
     * The pool is read twice in a row because a worker that has just taken a task off the queue is for a moment neither queued nor active.
     */
    private static final int IDLE_OBSERVATIONS_REQUIRED = 2;

    private static final Set<ApplicationContext> CONTEXTS_GIVEN_UP = ConcurrentHashMap.newKeySet();

    @Override
    public void afterTestExecution(ExtensionContext context) {
        drain(SpringExtension.getApplicationContext(context), TIMEOUT, context.getDisplayName());
    }

    /**
     * Waits until the shared task executor of the context has no running and no queued task.
     *
     * @param applicationContext the context whose shared task executor is awaited
     * @param timeout            how long to wait before giving up on the context
     * @param testName           the test the work belongs to, for the log
     */
    static void drain(ApplicationContext applicationContext, Duration timeout, String testName) {
        if (CONTEXTS_GIVEN_UP.contains(applicationContext) || !applicationContext.containsBean(SHARED_EXECUTOR_BEAN)) {
            return;
        }
        ThreadPoolExecutor pool = poolOf(applicationContext.getBean(SHARED_EXECUTOR_BEAN));
        if (pool == null) {
            return;
        }
        AtomicInteger idleObservations = new AtomicInteger();
        try {
            Awaitility.await().pollDelay(Duration.ZERO).pollInterval(POLL_INTERVAL).atMost(timeout).until(() -> {
                if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty()) {
                    return idleObservations.incrementAndGet() >= IDLE_OBSERVATIONS_REQUIRED;
                }
                idleObservations.set(0);
                return false;
            });
        }
        catch (ConditionTimeoutException e) {
            CONTEXTS_GIVEN_UP.add(applicationContext);
            log.warn("Async work of {} was still running {} after the test ended. Not waiting for the shared task executor in this context any more.", testName, timeout);
        }
    }

    static boolean hasGivenUp(ApplicationContext applicationContext) {
        return CONTEXTS_GIVEN_UP.contains(applicationContext);
    }

    private static ThreadPoolExecutor poolOf(Object executor) {
        if (executor instanceof ExceptionHandlingAsyncTaskExecutor) {
            executor = ReflectionTestUtils.getField(executor, "executor");
        }
        return executor instanceof ThreadPoolTaskExecutor threadPoolTaskExecutor ? threadPoolTaskExecutor.getThreadPoolExecutor() : null;
    }
}
