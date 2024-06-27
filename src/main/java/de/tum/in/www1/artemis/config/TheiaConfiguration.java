package de.tum.in.www1.artemis.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "theia")
public class TheiaConfiguration {

    private Map<String, Map<String, String>> images;

    public void setImages(final Map<String, Map<String, String>> images) {
        this.images = images;
    }

    /**
     * Get the images for all languages
     *
     * @return a map of language -> [flavor/name -> image-link]
     */
    public Map<String, Map<String, String>> getImagesForAllLanguages() {
        return images;
    }

    /**
     * Get the images for a specific language
     *
     * @param language the language for which the images should be retrieved
     * @return a map of flavor/name -> image-link
     */
    public Map<String, String> getImagesForLanguage(String language) {
        return images.get(language);
    }

}
