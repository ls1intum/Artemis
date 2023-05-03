package de.tum.in.www1.artemis.config;

import org.apache.catalina.Context;
import org.apache.catalina.webresources.ExtractingRoot;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfiguration {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainerCustomizer() {
        return (TomcatServletWebServerFactory container) -> {
            container.addContextCustomizers((Context context) -> {
                // This configuration is used to improve initialization performance.
                // It prevents slow initial REST calls due to Spring Boot searching endlessly for BeanInfos

                context.setResources(new ExtractingRoot());
                context.setReloadable(false);
            });
        };
    }
}
