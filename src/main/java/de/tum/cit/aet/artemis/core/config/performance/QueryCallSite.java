package de.tum.cit.aet.artemis.core.config.performance;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.repository.Repository;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

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
 *                             no application frame is on the stack at all. Module API delegates
 *                             ({@link AbstractApi}) are skipped: they only forward a call from another
 *                             module, so the code that decided to issue the query is their caller.
 * @param sourceFile       repository-relative path of the source file of {@code callerMethod}, e.g.
 *                             {@code src/main/java/de/tum/cit/aet/artemis/core/service/CourseService.java};
 *                             {@code null} if unknown. Not part of the call site's identity.
 * @param line             the line in {@code sourceFile} that issued the call; {@code null} if unknown.
 *                             Not part of the call site's identity.
 */
public record QueryCallSite(String repositoryMethod, String callerMethod, String sourceFile, Integer line) {

    static final QueryCallSite UNKNOWN = new QueryCallSite(null, null);

    private static final String SOURCE_ROOT = "src/main/java/";

    /**
     * A call site without a source location.
     *
     * @param repositoryMethod see the record component
     * @param callerMethod     see the record component
     */
    public QueryCallSite(String repositoryMethod, String callerMethod) {
        this(repositoryMethod, callerMethod, null, null);
    }

    /**
     * Two call sites are the same piece of code when they name the same repository and caller
     * method; the line only says where that code was seen first, so it must not split a count.
     *
     * @param other the object to compare with
     * @return whether {@code other} is a call site of the same repository and caller method
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof QueryCallSite site && Objects.equals(repositoryMethod, site.repositoryMethod) && Objects.equals(callerMethod, site.callerMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryMethod, callerMethod);
    }

    private static final String APP_PACKAGE = "de.tum.cit.aet.artemis.";

    /** The detector's own frames between the JDBC driver and the application code. */
    private static final Set<Class<?>> DETECTOR_CLASSES = Set.of(QueryCallSite.class, QueryListener.class, QueryCollector.class);

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
                    repositoryMethod = repositoryName + "." + sourceMethodName(frame.getMethodName());
                    continue;
                }
                String className = declaringClass.getName();
                if (className.startsWith(APP_PACKAGE) && !DETECTOR_CLASSES.contains(declaringClass) && !AbstractApi.class.isAssignableFrom(declaringClass)) {
                    return new QueryCallSite(repositoryMethod, declaringClass.getSimpleName() + "." + sourceMethodName(frame.getMethodName()), sourcePath(declaringClass, frame),
                            frame.getLineNumber() > 0 ? frame.getLineNumber() : null);
                }
            }
            return new QueryCallSite(repositoryMethod, null);
        });
    }

    /**
     * The repository-relative path of the file a frame's class is declared in, or {@code null}
     * when the frame has no file name (compiled without debug information).
     */
    private static String sourcePath(Class<?> declaringClass, StackWalker.StackFrame frame) {
        String fileName = frame.getFileName();
        return fileName == null ? null : SOURCE_ROOT + declaringClass.getPackageName().replace('.', '/') + "/" + fileName;
    }

    /**
     * The method name as written in the source. A lambda body compiles to a synthetic method named
     * after its enclosing method ({@code lambda$index$0}); this returns that enclosing method
     * ({@code index}), which is the name static analysis reports and which does not change when
     * the compiler renumbers lambdas.
     */
    static String sourceMethodName(String methodName) {
        String prefix = "lambda$";
        int end = methodName.lastIndexOf('$');
        if (methodName.startsWith(prefix) && end > prefix.length()) {
            return methodName.substring(prefix.length(), end);
        }
        return methodName;
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
