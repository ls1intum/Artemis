package de.tum.cit.aet.artemis.nebula.faq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;

@RestController
@RequestMapping("/api/nebula/")
public class FaqGrpcTestResource {

    private static final Logger log = LoggerFactory.getLogger(FaqGrpcTestResource.class);

    private static final String ENTITY_NAME = "faq_GRPC";

    private final FaqGrpcService faqGrpcService;

    public FaqGrpcTestResource(FaqGrpcService faqGrpcService) {
        this.faqGrpcService = faqGrpcService;
    }

    @EnforceAtLeastTutorInCourse
    @PostMapping("rewrite-faq/{courseId}")
    public ResponseEntity<String> rewriteFaqInNebula(@PathVariable Long courseId, @RequestBody String inputText) {

        log.debug("REST request to rewrite the following FAQ input text : {}", inputText);
        String result = faqGrpcService.ingestAcceptedFaqsToNebula(courseId, inputText);
        log.debug("Result from the rewriting : {} See the result: {}", inputText, result);
        return ResponseEntity.ok(result);
    }
}
