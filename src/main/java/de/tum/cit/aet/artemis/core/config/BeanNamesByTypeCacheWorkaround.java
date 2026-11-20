package de.tum.cit.aet.artemis.core.config;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Works around a lock-order inversion in Spring Framework 7.1.0 that can hang the whole bean factory.
 * <p>
 * Since spring-framework gh-37382, {@code DefaultListableBeanFactory#getBeanNamesForType} fills its type cache through
 * {@link ConcurrentReferenceHashMap#computeIfAbsent}, which runs the type matching while a segment lock of the cache is held.
 * Type matching may have to instantiate a {@code FactoryBean}, which takes the singleton lock. A thread that creates a bean holds
 * the singleton lock and, in {@code addSingleton}, needs a segment lock of the same cache. Two threads then wait for each other
 * forever, and every later bean creation waits behind them. Artemis is exposed in two ways: lazy singletons are created after
 * startup by {@link DeferredEagerBeanInitializer}, and any thread (for example the JVM's GC notification thread, through the
 * Prometheus exemplar sampler) may look a bean up by type at any time.
 * <p>
 * The workaround gives both caches the behaviour that upstream proposes in spring-framework#37517 and that 7.0.x had: look up,
 * compute <em>outside</em> any lock, then store. The cost is that two threads may compute the same entry concurrently, which
 * normally yields the same result. As in 7.0.x, {@code clear()} no longer waits for a computation that is in progress, so a result
 * computed while the bean definitions change may be stored afterwards. Artemis registers no bean definitions and no singletons at
 * runtime, so this cannot happen here.
 * <p>
 * The cache fields are private, so they are replaced reflectively before the context is refreshed and therefore before they are
 * used. The replacement only happens while the fields hold a plain {@link ConcurrentReferenceHashMap}: that keeps it from running
 * twice and from touching a Spring version that changed the field type. Only contexts that a {@code SpringApplication} creates are
 * covered, which includes the integration tests.
 * <p>
 * <strong>Remove this class, its {@code spring.factories} entry, {@code BeanNamesByTypeCacheWorkaroundTest} and
 * {@code BeanNamesByTypeCacheWorkaroundContextTest} once the Spring Framework release in use contains the fix.</strong> The fix is
 * tracked in the pull request spring-framework#37517 (the issue #37516 was closed as its duplicate). The proposed fix keeps the cache
 * type, so this class does not switch itself off. {@code vanillaBeanFactoryStillDeadlocks_removeTheWorkaroundWhenThisFails} fails as soon
 * as the release is on the classpath. The reflective write to a final field works on JDK 25, but JDK 26 warns about it (JEP 500), and
 * a later JDK refuses it. The workaround then logs a warning and leaves the bean factory unchanged.
 */
public class BeanNamesByTypeCacheWorkaround implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(BeanNamesByTypeCacheWorkaround.class);

    private static final List<String> CACHE_FIELD_NAMES = List.of("allBeanNamesByType", "singletonBeanNamesByType");

    /** The initial capacity that {@code DefaultListableBeanFactory} uses for both caches. */
    private static final int INITIAL_CAPACITY = 64;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (context instanceof GenericApplicationContext genericContext) {
            apply(genericContext.getDefaultListableBeanFactory());
        }
    }

    /**
     * Replaces the bean names by type caches of the given bean factory.
     *
     * @param beanFactory the bean factory, which must not have looked up a bean by type yet
     * @return true if the caches were replaced, false if they already were, or if this Spring version has no cache that needs it
     */
    static boolean apply(DefaultListableBeanFactory beanFactory) {
        try {
            boolean replaced = false;
            for (String fieldName : CACHE_FIELD_NAMES) {
                Field field = DefaultListableBeanFactory.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                // Only the cache of the affected Spring versions is replaced: a fixed version uses another type, and a repeated call finds ours.
                if (field.get(beanFactory).getClass() == ConcurrentReferenceHashMap.class) {
                    field.set(beanFactory, new ComputeOutsideLockCache());
                    replaced = true;
                }
            }
            return replaced;
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Could not apply the workaround for the bean factory deadlock of Spring Framework 7.1.0, the bean factory keeps its type caches", ex);
            return false;
        }
    }

    /**
     * A type cache that computes a missing entry without holding a lock of the map.
     * <p>
     * The computation that finishes last wins, as in Spring Framework 7.0.x. A lookup that another lookup interrupts, because it creates
     * a {@code FactoryBean} that looks up beans by type itself, finishes after the nested one, which could not see the bean in
     * creation and found fewer names. Keeping the first result would return and cache that incomplete answer.
     */
    static final class ComputeOutsideLockCache extends ConcurrentReferenceHashMap<Object, String[]> {

        ComputeOutsideLockCache() {
            super(INITIAL_CAPACITY);
        }

        @Override
        public String[] computeIfAbsent(Object key, Function<? super Object, ? extends String[]> mappingFunction) {
            String[] cached = get(key);
            if (cached != null) {
                return cached;
            }
            String[] computed = mappingFunction.apply(key);
            put(key, computed);
            return computed;
        }
    }
}
