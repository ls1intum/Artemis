package de.tum.in.www1.artemis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertiesConfigurationGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PropertiesConfigurationGuard.class);

    @Value("${info.universityName:#{null}}")
    private String universityName;

    public void afterPropertiesSet() {
        if (this.universityName == null || this.universityName.isEmpty()) {
            log.error(
                    "The name of the university and the name of the main admin are not configured in the application-prod.yml! These are needed to be displayed in the /about page, and for the telemetry service.");
            throw new IllegalArgumentException("The name of the University, and the name of the main admin must be configured! "); // exists the application
        }
    }
}
