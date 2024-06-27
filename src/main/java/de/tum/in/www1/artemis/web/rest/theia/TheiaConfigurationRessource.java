package de.tum.in.www1.artemis.web.rest.theia;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_THEIA;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.config.TheiaConfiguration;
import java.util.Map;
import java.util.Optional;

@Profile(PROFILE_THEIA)
@RestController
@RequestMapping("api/theia")
public class TheiaConfigurationRessource {

    private final TheiaConfiguration theiaConfiguration;

    @Autowired
    public TheiaConfigurationRessource(TheiaConfiguration theiaConfiguration) {
        this.theiaConfiguration = theiaConfiguration;
    }

    /**
     * GET /api/theia/images?language=<language>: Get the images for a specific language
     *
     * @param language the language for which the images should be retrieved
     * @return a map of flavor/name -> image-link
     */
    @GetMapping("images")
    public Optional<Map<String, String>> getImagesForLanguage(@RequestParam("language") String language) {
        language = language.toLowerCase();
        return Optional.ofNullable(this.theiaConfiguration.getImagesForLanguage(language));
    }


}
