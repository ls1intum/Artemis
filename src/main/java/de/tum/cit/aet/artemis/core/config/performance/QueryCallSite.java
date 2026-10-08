package de.tum.cit.aet.artemis.core.config.performance;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.Set;

import org.springframework.data.repository.Repository;

/**
 * Where in the application a query was issued from, resolved by walking the current thread's
 * stack. Lets a runtime finding name the exact code that caused it, e.g. "repeated query from
 * {@code ParticipationRepository.findByExerciseId}, called by {@code CourseStatsService.calculate}"
 * -- an exact link to the static findings (which are keyed by class and method), instead of
 * guessing a connection from table names.
 *
 * @param repositoryMethod the outermost Spring Data repository method on the stack, e.g.
 *                             {@code ParticipationRepository.findByIdElseThrow}; {@code null} when the
 *                             query did not go through a repository (e.g. Hibernate lazily loading an
 *                             association).
 * @param callerMethod     the first application method below the repository (or, without one,
 *                             below Hibernate), e.g. {@code CourseStatsService.calculate}, or an entity
 *                             getter such as {@code Exercise.getTeams} for a lazy load; {@code null} if
 *                             no application frame is on the stack at all.
 */
public record QueryCallSite(String repositoryMethod, String callerMethod) {

    static final QueryCallSite UNKNOWN = new QueryCallSite(null, null);

    private static final String APP_PACKAGE = "de.tum.cit.aet.artemis.";

    /** The detector's own frames between the JDBC driver and the application code. */
    private static final Set<Class<?>> DETECTOR_CLASSES = Set.of(QueryCallSite.class, SlowQueryListener.class, SlowQueryCollector.class);

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Resolves the call site of the query currently executing on this thread. Must be called on
     * the thread that executes the query, since it inspects that thread's stack.
     *
     * @return the resolved call site; never {@code null}.
     */
    public static QueryCallSite resolve() {
        return WALKER.walk(frames -> {
            String repositoryMethod = null;
            for (Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext();) {
                StackWalker.StackFrame frame = it.next();
                Class<?> declaringClass = frame.getDeclaringClass();
                String repositoryName = repositoryName(declaringClass);
                if (repositoryName != null) {
                    // keep overwriting: the stack is walked innermost-first, and the outermost
                    // repository method (e.g. a default findByIdElseThrow wrapping findById) is the
                    // one application code actually calls, so the one static analysis also sees
                    repositoryMethod = repositoryName + "." + frame.getMethodName();
                    continue;
                }
                String className = declaringClass.getName();
                if (className.startsWith(APP_PACKAGE) && !DETECTOR_CLASSES.contains(declaringClass)) {
                    return new QueryCallSite(repositoryMethod, declaringClass.getSimpleName() + "." + frame.getMethodName());
                }
            }
            return new QueryCallSite(repositoryMethod, null);
        });
    }

    /**
     * The repository name for a stack frame's class, or {@code null} if the class is not part of
     * a Spring Data repository. Spring Data implements repository interfaces with JDK dynamic
     * proxies, whose generated class name ({@code jdk.proxy2.$Proxy123}) says nothing, so for a
     * proxy the application repository interface it implements is named instead.
     */
    static String repositoryName(Class<?> clazz) {
        if (!Repository.class.isAssignableFrom(clazz)) {
            return null;
        }
        if (Proxy.isProxyClass(clazz)) {
            for (Class<?> implemented : clazz.getInterfaces()) {
                if (Repository.class.isAssignableFrom(implemented) && implemented.getName().startsWith(APP_PACKAGE)) {
                    return implemented.getSimpleName();
                }
            }
        }
        return clazz.getName().startsWith(APP_PACKAGE) ? clazz.getSimpleName() : null;
    }
}
