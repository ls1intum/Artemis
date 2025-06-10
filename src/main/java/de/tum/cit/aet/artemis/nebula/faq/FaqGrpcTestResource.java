package de.tum.cit.aet.artemis.nebula.faq;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nebula")
public class FaqGrpcTestResource {

    private final FaqGrpcIngestService faqGrpcIngestService;

    public FaqGrpcTestResource(FaqGrpcIngestService faqGrpcIngestService) {
        this.faqGrpcIngestService = faqGrpcIngestService;
    }

    @PostMapping("/ingest-faqs/{courseId}")
    public ResponseEntity<String> ingestFaqsToNebula(@PathVariable Long courseId, @RequestBody String inputText) {
        String result = faqGrpcIngestService.ingestAcceptedFaqsToNebula(courseId, inputText);
        return ResponseEntity.ok(result);
    }
}
