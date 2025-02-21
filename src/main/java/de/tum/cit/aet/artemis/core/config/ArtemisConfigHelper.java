package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ATLAS_ENABLED_PROPERTY_NAME;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

public class ArtemisConfigHelper {

    private static final Logger log = LoggerFactory.getLogger(ArtemisConfigHelper.class);

    public boolean isAtlasEnabled(Environment environment) {
        return getPropertyOrExitArtemis(ATLAS_ENABLED_PROPERTY_NAME, environment);
    }

    private boolean getPropertyOrExitArtemis(String key, Environment environment) {
        Boolean value = environment.getProperty(key, Boolean.class);
        if (value == null) {
            log.error("Property {} not found in Artemis configuration. Make sure to add it to your application.yml-file. Allowed values: true, false", key);
            System.exit(1);
        }
        return value;
    }
}
