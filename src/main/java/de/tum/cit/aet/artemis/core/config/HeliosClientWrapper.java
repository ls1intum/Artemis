package de.tum.cit.aet.artemis.core.config;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Reflection-only wrapper around the HeliosClient bean.
 * <p>
 * If the Helios starter is on the classpath, this wrapper will look up
 * the {@code HeliosClient} bean by name and invoke its migration-related
 * helper methods. If the starter is absent (or the API has changed),
 * all calls become no-ops and will not break Artemis’s startup.
 * <p>
 * You can add further Helios lifecycle methods here as needed.
 *
 * @see <a href="https://github.com/ls1intum/Helios/blob/staging/server/helios-status-spring-starter/PACKAGE_README.md"
 *      >Helios Status Spring Starter README</a>
 */
@Component
public class HeliosClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(HeliosClientWrapper.class);

    private final Object clientInstance;

    private final Method pushDbMigrationStartedMethod;

    private final Method pushDbMigrationFinishedMethod;

    private final Method pushDbMigrationFailedMethod;

    /**
     * Attempts to load &amp; wire the {@code de.tum.cit.aet.helios.HeliosClient} bean via reflection.
     * <p>
     * If the class is present and the Spring context contains a bean named {@code "heliosClient"},
     * its instance is captured along with the three migration-related methods:
     * {@code pushDbMigrationStarted()}, {@code pushDbMigrationFinished()}, and {@code pushDbMigrationFailed()}.
     * Otherwise, this wrapper will simply log a warning and all invocations become no-ops.
     *
     * @param ctx the current Spring {@link ApplicationContext} used to look up the Helios bean
     */
    public HeliosClientWrapper(ApplicationContext ctx) {
        Object client = null;
        Method started = null;
        Method finished = null;
        Method failed = null;

        try {
            // Load the client class by name
            Class<?> clientClass = Class.forName("de.tum.cit.aet.helios.HeliosClient");

            // Get the bean if it exists
            if (ctx.containsBean("heliosClient")) {
                client = ctx.getBean("heliosClient", clientClass);
                log.info("HeliosClient bean found: {}", clientClass.getName());

                // Look up each helper method
                started = clientClass.getMethod("pushDbMigrationStarted");
                finished = clientClass.getMethod("pushDbMigrationFinished");
                failed = clientClass.getMethod("pushDbMigrationFailed");
            }
            else {
                log.warn("HeliosClient bean not registered. All status update calls will be no-ops.");
            }
        }
        catch (ClassNotFoundException e) {
            log.warn("HeliosClient class not on classpath → all Helios calls will be no-ops");
        }
        catch (NoSuchMethodException e) {
            log.error("HeliosClient API changed, missing method: {}", e.getMessage(), e);
        }
        catch (Exception e) {
            log.error("Unexpected error initializing HeliosClientWrapper", e);
        }

        this.clientInstance = client;
        this.pushDbMigrationStartedMethod = started;
        this.pushDbMigrationFinishedMethod = finished;
        this.pushDbMigrationFailedMethod = failed;
    }

    /**
     * Notify Helios that a DB migration process has started.
     * <p>
     * If the HeliosClient is absent or the method lookup failed, this is a no-op.
     */
    public void pushDbMigrationStarted() {
        invoke(pushDbMigrationStartedMethod, "pushDbMigrationStarted");
    }

    /**
     * Notify Helios that a DB migration process has completed successfully.
     * <p>
     * If the HeliosClient is absent or the method lookup failed, this is a no-op.
     */
    public void pushDbMigrationFinished() {
        invoke(pushDbMigrationFinishedMethod, "pushDbMigrationFinished");
    }

    /**
     * Notify Helios that a DB migration process has failed.
     * <p>
     * If the HeliosClient is absent or the method lookup failed, this is a no-op.
     */
    public void pushDbMigrationFailed() {
        invoke(pushDbMigrationFailedMethod, "pushDbMigrationFailed");
    }

    /**
     * Internal helper: reflects and invokes the named method on the HeliosClient instance.
     *
     * @param method the {@link Method} to invoke; if null, no action is taken
     * @param name   the method name (used only for error logging)
     */
    private void invoke(Method method, String name) {
        if (clientInstance == null || method == null) {
            return;
        }

        try {
            method.invoke(clientInstance);
        }
        catch (Exception e) {
            log.error("Failed to invoke {} on HeliosClient: {}", name, e.getMessage(), e);
        }
    }
}
