package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(PROFILE_SCHEDULING)
public class PropertiesConfigurationGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PropertiesConfigurationGuard.class);

    @Value("${info.operatorName:#{null}}")
    private String operatorName;

    public void afterPropertiesSet() {
        if (this.operatorName == null || this.operatorName.isEmpty()) {
            log.error(
                    "The name of the operator (University) is not configured in the application-prod.yml! It is needed to be displayed in the /about page, and for the telemetry service.");
            throw new IllegalArgumentException("The name of the operator (university) must be configured, but is not!"); // exits the application
        }
    }
}
