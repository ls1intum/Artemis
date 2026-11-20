package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * Reproduces the lock-order inversion of Spring Framework 7.1.0 (spring-framework#37516) deterministically and checks that
 * {@link BeanNamesByTypeCacheWorkaround} removes it.
 * <p>
 * Thread one looks a bean up by type. Its cache computation is paused while it holds the cache lock, then it has to instantiate a
 * {@code FactoryBean} to learn the type, which needs the singleton lock. Thread two creates a lazy singleton, so it holds the
 * singleton lock, and looks a bean up by type from inside the creation, which needs the cache lock.
 */
class BeanNamesByTypeCacheWorkaroundTest {

    /**
     * The names say what these threads are: the vanilla test leaves its two threads deadlocked for the rest of the test JVM, and
     * a thread dump taken while triaging another problem would otherwise show an unexplained "Found one Java-level deadlock".
     */
    private static final String LOOKUP_THREAD_NAME = "bean-factory-deadlock-test-type-lookup";

    private static final String CREATION_THREAD_NAME = "bean-factory-deadlock-test-bean-creation";

    /** Generous, so that a loaded CI runner cannot fail the case that is expected to finish. */
    private static final long EXPECTED_TO_FINISH_TIMEOUT_SECONDS = 60;

    /** Short, because the case that is expected to hang always runs into this timeout. */
    private static final long EXPECTED_TO_HANG_TIMEOUT_SECONDS = 5;

    private static final long LATCH_TIMEOUT_SECONDS = 60;

    /**
     * A raw {@link FactoryBean}: its object type can only be found by instantiating it, which type matching does under the singleton lock.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static class RawFactoryBean implements FactoryBean {

        @Override
        public Object getObject() {
            return "product";
        }

        @Override
        public Class<?> getObjectType() {
            return String.class;
        }
    }

    @Test
    void shouldNotDeadlockBetweenTypeLookupAndBeanCreationWithTheWorkaround() {
        var beanFactory = new DefaultListableBeanFactory();

        assertThat(BeanNamesByTypeCacheWorkaround.apply(beanFactory)).isTrue();

        assertThat(bothThreadsFinish(beanFactory, EXPECTED_TO_FINISH_TIMEOUT_SECONDS)).as("the type lookup and the bean creation both finish").isTrue();
    }

    @Test
    void shouldReplaceTheCachesOnlyOnce() {
        var beanFactory = new DefaultListableBeanFactory();

        assertThat(BeanNamesByTypeCacheWorkaround.apply(beanFactory)).isTrue();
        assertThat(BeanNamesByTypeCacheWorkaround.apply(beanFactory)).isFalse();
    }

    /**
     * Fails once the Spring Framework in use no longer deadlocks. Then remove {@link BeanNamesByTypeCacheWorkaround}, its entry in
     * {@code META-INF/spring.factories}, and this test class.
     */
    @Test
    void vanillaBeanFactoryStillDeadlocks_removeTheWorkaroundWhenThisFails() {
        assertThat(bothThreadsFinish(new DefaultListableBeanFactory(), EXPECTED_TO_HANG_TIMEOUT_SECONDS))
                .as("the vanilla bean factory still deadlocks, so the workaround is still needed").isFalse();
    }

    @Test
    void shouldBeRegisteredAsApplicationContextInitializerInSpringFactories() {
        List<ApplicationContextInitializer> initializers = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader()).load(ApplicationContextInitializer.class);

        assertThat(initializers).anyMatch(BeanNamesByTypeCacheWorkaround.class::isInstance);
    }

    /**
     * Runs the interleaving described in the class comment.
     *
     * @param beanFactory    the bean factory, which must be empty
     * @param timeoutSeconds how long each thread may take to finish
     * @return true if both threads finished within the timeout, false if they are stuck
     */
    private static boolean bothThreadsFinish(DefaultListableBeanFactory beanFactory, long timeoutSeconds) {
        var lookupHoldsCacheLock = new CountDownLatch(1);
        var creationHoldsSingletonLock = new CountDownLatch(1);

        var rawFactory = new RootBeanDefinition(RawFactoryBean.class);
        rawFactory.setLazyInit(true);
        beanFactory.registerBeanDefinition("rawFactory", rawFactory);

        var lazyBean = new RootBeanDefinition(Object.class, () -> {
            creationHoldsSingletonLock.countDown();
            beanFactory.getBeanNamesForType(Runnable.class);
            return new Object();
        });
        lazyBean.setLazyInit(true);
        beanFactory.registerBeanDefinition("lazyBean", lazyBean);

        // Pauses the type lookup while it computes the cache entry, until the bean creation holds the singleton lock.
        beanFactory.addBeanPostProcessor(new SmartInstantiationAwareBeanPostProcessor() {

            @Override
            public Class<?> predictBeanType(Class<?> beanClass, String beanName) {
                if ("rawFactory".equals(beanName) && LOOKUP_THREAD_NAME.equals(Thread.currentThread().getName())) {
                    lookupHoldsCacheLock.countDown();
                    await(creationHoldsSingletonLock);
                }
                return null;
            }
        });
        beanFactory.freezeConfiguration();

        var lookup = new FutureTask<>(() -> beanFactory.getBeanNamesForType(Runnable.class));
        startDaemon(lookup, LOOKUP_THREAD_NAME);
        await(lookupHoldsCacheLock);
        var creation = new FutureTask<>(() -> beanFactory.getBean("lazyBean"));
        startDaemon(creation, CREATION_THREAD_NAME);

        try {
            lookup.get(timeoutSeconds, TimeUnit.SECONDS);
            creation.get(timeoutSeconds, TimeUnit.SECONDS);
            return true;
        }
        catch (TimeoutException ex) {
            return false;
        }
        catch (ExecutionException ex) {
            throw new AssertionError("The lookup or the creation failed instead of finishing or hanging", ex);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the threads", ex);
        }
    }

    /**
     * Daemon threads, so that the threads of the deadlocked vanilla bean factory cannot keep the test JVM alive.
     */
    private static void startDaemon(Runnable task, String name) {
        var thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("The test thread interleaving was not reached");
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the other thread", ex);
        }
    }
}
