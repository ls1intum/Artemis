package de.tum.in.www1.artemis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "theia")
public class TheiaConfiguration {

    public TheiaConfiguration() {
        System.out.println("TheiaConfiguration constructor");
    }

    private String images;

    public String getImages() {
        System.out.println("Getting images: " + images);
        return images;
    }

    /*
    public Map<String, Map<String, String>> getImagesForAllLanguages() {
        return images;
    }

    public Map<String, String> getImagesForLanguage(String language) {
        return images.get(language);
    }
     */
}
