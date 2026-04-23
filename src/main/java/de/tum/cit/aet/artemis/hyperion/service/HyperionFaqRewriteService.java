package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.RewriteFaqResponseDTO;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Service to handle the rewriting of FAQs.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class HyperionFaqRewriteService {

    private static final String PROMPT_REWRITE_SYSTEM = "/prompts/hyperion/rewrite_faq_system.st";

    private static final String PROMPT_REWRITE_USER = "/prompts/hyperion/rewrite_faq_user.st";

    private static final String PROMPT_CONSISTENCY_SYSTEM = "/prompts/hyperion/faq_consistency_system.st";

    private static final String PROMPT_CONSISTENCY_USER = "/prompts/hyperion/faq_consistency_user.st";

    /**
     * Limits context to the 10 most recent FAQs to ensure the
     * model stays focused on current standards while controlling cost.
     */
    private static final int RECENT_FAQ_LIMIT = 10;

    private static final Logger log = LoggerFactory.getLogger(HyperionFaqRewriteService.class);

    private final FaqRepository faqRepository;

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final ObservationRegistry observationRegistry;

    private final ObjectMapper objectMapper;

    /**
     * Creates a new HyperionFaqRewriteService.
     *
     * @param faqRepository       repository for FAQ entities
     * @param chatClient          the AI chat client
     * @param templateService     prompt template service
     * @param observationRegistry observation registry for metrics
     * @param objectMapper        object mapper
     */
    public HyperionFaqRewriteService(FaqRepository faqRepository, ChatClient chatClient, HyperionPromptTemplateService templateService, ObservationRegistry observationRegistry,
            ObjectMapper objectMapper) {
        this.faqRepository = faqRepository;
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.observationRegistry = observationRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Rewrites the given FAQ for the provided course
     *
     * @param courseId the id of the course context
     * @param faqText  the FAQ to be rewritten
     * @return the rewrite result including inconsistencies, improvement, and suggestions
     */
    public RewriteFaqResponseDTO rewriteFaq(long courseId, String faqText) {
        if (faqText == null || faqText.isBlank()) {
            log.warn("Received blank FAQ text for course {}", courseId);
            return new RewriteFaqResponseDTO("", List.of(), List.of(), "");
        }

        String normalizedFaqText = faqText.trim();
        var observation = Observation.createNotStarted("hyperion.faq.rewrite", observationRegistry).lowCardinalityKeyValue("course.id", String.valueOf(courseId)).start();

        Map<String, String> input = Map.of("text_to_rewrite", normalizedFaqText);
        String systemPrompt = templateService.render(PROMPT_REWRITE_SYSTEM, Map.of());
        String userPrompt = templateService.render(PROMPT_REWRITE_USER, input);

        String rewrittenText;
        try (var _ = observation.openScope()) {
            // @formatter:off
            rewrittenText = chatClient
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
            // @formatter:on
            observation.event(Observation.Event.of("ai_rewrite_completed"));
        }
        catch (RuntimeException e) {
            log.error("Failed to process FAQ rewrite for course {}", courseId, e);
            observation.error(e);
            throw new InternalServerErrorAlertException("Failed to process FAQ rewrite: " + e.getMessage(), "FAQ", "faqRewriteFailed");
        }
        finally {
            observation.stop();
        }

        if (rewrittenText == null || rewrittenText.isBlank()) {
            log.warn("Received blank rewritten FAQ text for course {}", courseId);
            return new RewriteFaqResponseDTO("", List.of(), List.of(), "");
        }

        return checkFaqConsistency(courseId, rewrittenText);
    }

    private RewriteFaqResponseDTO checkFaqConsistency(long courseId, String rewrittenText) {
        List<Faq> faqs = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(courseId);
        if (faqs.isEmpty()) {
            return new RewriteFaqResponseDTO(rewrittenText, List.of(), List.of(), "");
        }

        List<ConsistencyIssue.Faq> faqData = faqs.stream().limit(RECENT_FAQ_LIMIT)
                .map(faq -> new ConsistencyIssue.Faq(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer())).toList();
        String faqsJson;
        try {
            faqsJson = objectMapper.writeValueAsString(faqData);
        }
        catch (Exception e) {
            log.error("Failed to serialize FAQ data", e);
            return new RewriteFaqResponseDTO(rewrittenText.trim(), List.of(), List.of(), "");
        }

        // The outputConverter handles the JSON parsing automatically
        var outputConverter = new BeanOutputConverter<>(ConsistencyIssue.class);
        String systemPrompt = templateService.render(PROMPT_CONSISTENCY_SYSTEM, Map.of());
        String userPrompt = templateService.renderObject(PROMPT_CONSISTENCY_USER, Map.of("faqs", faqsJson, "final_result", rewrittenText, "format", outputConverter.getFormat()));

        ConsistencyIssue consistencyIssue;
        try {
            // @formatter:off
            consistencyIssue = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(outputConverter);
            // @formatter:on
        }
        catch (Exception e) {
            log.error("Failed to process FAQ consistency for course {} - only returning rewritten text ", courseId, e);
            return new RewriteFaqResponseDTO(rewrittenText.trim(), List.of(), List.of(), "");
        }

        if (consistencyIssue == null || ConsistencyIssue.ConsistencyStatus.CONSISTENT.equals(consistencyIssue.type())) {
            return new RewriteFaqResponseDTO(rewrittenText.trim(), List.of(), List.of(), "");
        }
        return new RewriteFaqResponseDTO(rewrittenText.trim(), parseInconsistencies(consistencyIssue.faqs()), consistencyIssue.suggestions(), consistencyIssue.improvedVersion());
    }

    // See the prompt at src/main/resources/prompts/hyperion/faq_consistency_system.st for the definition of consistency
    private record ConsistencyIssue(ConsistencyStatus type, String message, List<Faq> faqs, List<String> suggestions, @JsonProperty("improved_version") String improvedVersion) {

        public record Faq(@JsonProperty("faq_id") long id, @JsonProperty("faq_question_title") String title, @JsonProperty("faq_question_answer") String answer) {
        }

        public enum ConsistencyStatus {
            CONSISTENT, INCONSISTENT
        }
    }

    private List<String> parseInconsistencies(List<ConsistencyIssue.Faq> faqs) {
        if (faqs == null) {
            return List.of();
        }
        return faqs.stream().map(f -> String.format("FAQ ID: %s, Title: %s, Answer: %s", f.id(), f.title(), f.answer())).toList();
    }
}
