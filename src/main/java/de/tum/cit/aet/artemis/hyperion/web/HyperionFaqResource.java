package de.tum.cit.aet.artemis.hyperion.web;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.RewriteFaqRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.RewriteFaqResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionFaqRewriteService;

/**
 * REST controller for managing Markdown Rewritings.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionFaqResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionFaqResource.class);

    private final HyperionFaqRewriteService hyperionFaqRewriteService;

    public HyperionFaqResource(HyperionFaqRewriteService hyperionFaqRewriteService) {
        this.hyperionFaqRewriteService = hyperionFaqRewriteService;
    }

    /**
     * POST /courses/{courseId}/faq/rewrite: Rewrite a faq for a course context
     *
     * @param courseId the id of the course
     * @param request  the id of the course the FAQ belongs to
     * @return the ResponseEntity with status 200 (OK), rewritten faq with inconsistencies, suggestions and improvement
     */
    @EnforceAtLeastTutorInCourse
    @PostMapping("courses/{courseId}/faq/rewrite")
    public ResponseEntity<RewriteFaqResponseDTO> rewriteFaq(@PathVariable long courseId, @Valid @RequestBody RewriteFaqRequestDTO request) {
        log.debug("REST request to Hyperion FAQ for course [{}]", courseId);
        var response = hyperionFaqRewriteService.rewriteFaq(courseId, request.faqText());
        return ResponseEntity.ok(response);
    }
}
