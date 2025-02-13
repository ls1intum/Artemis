package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.iris.service.IrisRewritingService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisRewriteTextRequestDTO;

/**
 * REST controller for managing Markdown Rewritings.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/")
public class IrisRewritingResource {

    private static final Logger log = LoggerFactory.getLogger(IrisRewritingResource.class);

    private static final String ENTITY_NAME = "rewriting";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final Optional<IrisRewritingService> irisRewritingService;

    public IrisRewritingResource(UserRepository userRepository, CourseRepository courseRepository, Optional<IrisRewritingService> irisRewritingService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.irisRewritingService = irisRewritingService;

    }

    /**
     * POST /courses/{courseId}/rewrite-text : Rewrite a given text.
     *
     * @param request  the request containing the text to be rewritten and the corresponding variant
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastTutorInCourse
    @PostMapping("courses/{courseId}/rewrite-text")
    public ResponseEntity<Void> rewriteText(@RequestBody PyrisRewriteTextRequestDTO request, @PathVariable Long courseId) {
        var rewritingService = irisRewritingService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        rewritingService.executeRewritingPipeline(user, course, request.variant(), request.toBeRewritten());
        log.debug("REST request to rewrite text: {}", request.toBeRewritten());
        return ResponseEntity.ok().build();
    }

}
