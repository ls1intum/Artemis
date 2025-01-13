package de.tum.cit.aet.artemis.communication.web.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRephrasingService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing.RephrasingVariant;

/**
 * REST controller for managing Markdown Rephrasings.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/")
public class RephrasingResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final Logger log = LoggerFactory.getLogger(RephrasingResource.class);

    private static final String ENTITY_NAME = "rephrasing";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final Optional<IrisRephrasingService> irisRephrasingService;

    public RephrasingResource(UserRepository userRepository, CourseRepository courseRepository, Optional<IrisRephrasingService> irisRephrasingService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.irisRephrasingService = irisRephrasingService;

    }

    @GetMapping("courses/{courseId}/rephrase-text")
    public ResponseEntity<Map<String, String>> rephraseText(@RequestParam String toBeRephrased, @RequestParam RephrasingVariant variant, @PathVariable Long courseId) {
        var rephrasingService = irisRephrasingService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        rephrasingService.executeRephrasingPipeline(user, course, variant, toBeRephrased);
        log.debug("REST request to rephrase text: {}", toBeRephrased);
        // Hier könnte eine Umformulierung implementiert werden
        Map<String, String> response = Map.of("rephrasedText", "Rephrased: " + toBeRephrased);
        return ResponseEntity.ok(response);
    }

}
