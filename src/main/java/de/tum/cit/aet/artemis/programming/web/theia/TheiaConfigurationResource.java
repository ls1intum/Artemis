package de.tum.cit.aet.artemis.programming.web.theia;

import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.TheiaConfiguration;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.theia.TheiaEnabled;

@Conditional(TheiaEnabled.class)
@Lazy
@RestController
@RequestMapping("api/programming/theia/")
public class TheiaConfigurationResource {

    private final TheiaConfiguration theiaConfiguration;

    public TheiaConfigurationResource(TheiaConfiguration theiaConfiguration) {
        this.theiaConfiguration = theiaConfiguration;
    }

    /**
     * GET programming/theia/images?language=<language>: Get the images for a specific language
     *
     * @param language the language for which the images should be retrieved
     * @return a map of flavor/name -> image-link
     */
    @GetMapping("images")
    @EnforceAtLeastInstructor
    public ResponseEntity<Map<String, String>> getImagesForLanguage(@RequestParam("language") ProgrammingLanguage language) {
        return ResponseEntity.ok(this.theiaConfiguration.getImagesForLanguage(language));
    }

}
