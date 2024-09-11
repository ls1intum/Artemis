package de.tum.cit.aet.artemis.config;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.apache.catalina.Context;
import org.apache.catalina.webresources.ExtractingRoot;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile(PROFILE_CORE)
@Configuration
public class TomcatConfiguration {

    /**
     * Customize the Tomcat configuration to achieve performance improvements as outlined below
     *
     * @return the WebServerFactoryCustomizer with the customized options to improve the performance
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainerCustomizer() {
        return (TomcatServletWebServerFactory container) -> container.addContextCustomizers((Context context) -> {

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
    }
}
