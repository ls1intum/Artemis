package de.tum.cit.aet.artemis.core.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.theia.TheiaEnabled;

@Conditional(TheiaEnabled.class)
@Configuration
@Lazy
@ConfigurationProperties(prefix = "theia")
public class TheiaConfiguration {

    private Map<ProgrammingLanguage, Map<String, String>> images;

    public void setImages(final Map<ProgrammingLanguage, Map<String, String>> images) {
        this.images = images;
    }

    /**
     * Get the images for all languages
     *
     * @return a map of language -> [flavor/name -> image-link]
     */
    public Map<ProgrammingLanguage, Map<String, String>> getImagesForAllLanguages() {
        return images;
    }

    /**
     * Get the images for a specific language
     *
     * @param language the language for which the images should be retrieved
     * @return a map of flavor/name -> image-link
     */
    public Map<String, String> getImagesForLanguage(ProgrammingLanguage language) {
        return images.get(language);
    }

}
