package de.tum.cit.aet.artemis.nebula.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_NEBULA;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.service.FaqService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisRewriteTextRequestDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingResponse;
import de.tum.cit.aet.artemis.nebula.service.FaqProcessingService;

/**
 * REST controller for managing Faqs.
 */
@Profile(PROFILE_NEBULA)
@Lazy
@RestController
@RequestMapping("api/nebula/")
public class FaqProcessingResource {

    private static final Logger log = LoggerFactory.getLogger(FaqProcessingResource.class);

    private static final String ENTITY_NAME = "faq";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final FaqProcessingService faqProcessingService;

    public FaqProcessingResource(CourseRepository courseRepository, UserRepository userRepository, FaqRepository faqRepository, FaqService faqService,
            FaqProcessingService faqProcessingService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.faqProcessingService = faqProcessingService;
    }

    /**
     * POST /courses/{courseId}/rewrite-text : Rewrite a given text.
     *
     * @param request  the request containing the text to be rewritten and the corresponding variant
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastTutorInCourse
    @PostMapping("/courses/{courseId}/rewrite-text")
    public ResponseEntity<FaqRewritingResponse> rewriteText(@RequestBody PyrisRewriteTextRequestDTO request, @PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        FaqRewritingResponse response = faqProcessingService.executeRewriting(user, course, request.toBeRewritten());
        return ResponseEntity.ok(response);
    }
}
