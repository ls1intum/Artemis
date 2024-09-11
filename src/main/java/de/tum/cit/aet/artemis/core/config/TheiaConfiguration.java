package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_THEIA;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;

@Profile(PROFILE_THEIA)
@Configuration
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
