package de.tum.in.www1.artemis;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import de.tum.in.www1.artemis.config.DefaultProfileUtil;

/**
 * This is a helper Java class that provides an alternative to creating a web.xml. This will be invoked only when the application is deployed to a Servlet container like Tomcat,
 * JBoss etc.
 */
public class ApplicationWebXml extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        /**
         * set a default to use when no profile is configured.
         */
        DefaultProfileUtil.addDefaultProfile(application.application());
        return application.sources(ArtemisApp.class);
    }
}
