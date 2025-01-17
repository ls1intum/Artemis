package de.tum.cit.aet.artemis.communication.web.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRewritingService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;

/**
 * REST controller for managing Markdown Rewritings.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/")
public class RewritingResource {

    private static final Logger log = LoggerFactory.getLogger(RewritingResource.class);

    private static final String ENTITY_NAME = "rewriting";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final Optional<IrisRewritingService> irisRewritingService;

    public RewritingResource(UserRepository userRepository, CourseRepository courseRepository, Optional<IrisRewritingService> irisRewritingService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.irisRewritingService = irisRewritingService;

    }

    @PostMapping("courses/{courseId}/rewrite-text")
    public ResponseEntity<Void> rewriteText(@RequestParam String toBeRewritten, @RequestParam RewritingVariant variant, @PathVariable Long courseId) {
        var rewritingService = irisRewritingService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        rewritingService.executeRewritingPipeline(user, course, variant, toBeRewritten);
        log.debug("REST request to rewrite text: {}", toBeRewritten);
        return ResponseEntity.ok().build();
    }

}
