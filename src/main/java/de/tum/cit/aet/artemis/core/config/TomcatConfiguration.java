package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.apache.catalina.Context;
import org.apache.catalina.webresources.ExtractingRoot;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.config.valves.HealthCheckValve;
import de.tum.cit.aet.artemis.core.config.valves.PublicTimeValve;

@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class TomcatConfiguration {

    /**
     * The most locales Tomcat takes from the Accept-Language header of one request. Tomcat ignores the entries above the limit.
     * <p>
     * Tomcat 9.0.123, 10.1.61 and 11.0.27 introduced a default of 10 in a patch release. In those releases a request above the
     * limit made {@code getLocale()} throw, and Spring's {@code RequestContextFilter} calls it for every request, so such a client
     * got a 500 for every page and API call. Tomcat 11.0.28 reworked this to ignore the extra locales instead of throwing, and
     * the build uses a Tomcat that does. Browsers send one entry per configured language and RFC 9110 sets no maximum, so users
     * with many languages exceed 10. Do not lower the Tomcat version below 11.0.28 without restoring a test of the 500.
     * <p>
     * The limit stays to bound the locales that are processed for one request. Parsing costs about a microsecond per entry, and
     * 50 entries cost at most about 45 microseconds per request, an order of magnitude less than unlimited parsing at the proxy's
     * header size of up to 8 KB (roughly a thousand entries). These figures were measured when a header above the limit was
     * rejected at the limit, and not again for the Tomcat that ignores the extra entries.
     */
    static final int MAX_ACCEPT_LANGUAGE_LOCALES = 50;

    /**
     * Customize the Tomcat configuration to achieve performance improvements as outlined below
     *
     * @return the WebServerFactoryCustomizer with the customized options to improve the performance
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainerCustomizer() {
        return (TomcatServletWebServerFactory container) -> {
            // Add health check valve for ultra-fast /ping responses used by load balancers
            container.addEngineValves(new HealthCheckValve());
            // Add time valve for ultra-fast /time responses used by the webapp
            container.addEngineValves(new PublicTimeValve());
            container.addConnectorCustomizers(connector -> connector.setMaxLocaleCount(MAX_ACCEPT_LANGUAGE_LOCALES));

            container.addContextCustomizers((Context context) -> {

                /*
                 * This configuration addresses a performance issue related to the initial request
                 * to the Spring server taking significantly longer (8 seconds or more) than subsequent
                 * requests (around 100ms). In our case, this happened with the PostContextFilter class
                 * when fetching conversation messages.
                 * The reason for this delay is that Spring needs to introspect the class used as a parameter
                 * for the request handler method. It does so by scanning the classpath with multiple classloaders
                 * to find BeanInfo files.
                 * However, the Tomcat Embedded Classloader has two problems:
                 * 1. It is slow because it searches for JAR files inside other JAR files, which is a
                 * time-consuming operation.
                 * 2. By default, it discards its cache every 15 minutes, causing the performance issue
                 * to reappear later when the search is performed again.
                 * To address these issues, we've customized the TomcatServletWebServerFactory with two
                 * changes:
                 * - Set the context's reloadable attribute to false, preventing Tomcat from discarding
                 * the loaded classes and refreshing.
                 * - Use ExtractingRoot for the context's resources, instructing Tomcat to extract
                 * packages on startup (which requires more disk space) instead of scanning through
                 * un-extracted packages.
                 */

                context.setResources(new ExtractingRoot());
                context.setReloadable(false);
            });
        };
    }
}
