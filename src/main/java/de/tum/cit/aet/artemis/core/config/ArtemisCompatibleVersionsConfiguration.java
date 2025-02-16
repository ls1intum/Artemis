package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile(PROFILE_CORE)
@Configuration
@ConfigurationProperties(prefix = "artemis.compatible-versions")
public class ArtemisCompatibleVersionsConfiguration {

    private Platform android;

    private Platform ios;

    public Platform getAndroid() {
        return android;
    }

    public void setAndroid(Platform android) {
        this.android = android;
    }

    public Platform getIos() {
        return ios;
    }

    public void setIos(Platform ios) {
        this.ios = ios;
    }

    public static class Platform {

        private String min;

        private String recommended;

        // No-args constructor eklemek, property binding için faydalı olabilir
        public Platform() {
        }

        public String getMin() {
            return min;
        }

        public void setMin(String min) {
            this.min = min;
        }

        public String getRecommended() {
            return recommended;
        }

        public void setRecommended(String recommended) {
            this.recommended = recommended;
        }
    }
}
