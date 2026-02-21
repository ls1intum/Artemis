package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;

/**
 * Service for rewriting problem statements using Spring AI.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementRewriteService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementRewriteService.class);

    private static final String REWRITE_PIPELINE_ID = "HYPERION_PROBLEM_REWRITE";

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final ObservationRegistry observationRegistry;

    /**
     * Creates a new ProblemStatementRewriteService.
     *
     * @param chatClient           the AI chat client (optional)
     * @param templateService      prompt template service
     * @param observationRegistry  the observation registry for metrics
     * @param llmTokenUsageService service for tracking LLM token usage
     * @param userRepository       repository for resolving current user
     */
    public HyperionProblemStatementRewriteService(ChatClient chatClient, HyperionPromptTemplateService templateService, ObservationRegistry observationRegistry,
            LLMTokenUsageService llmTokenUsageService, UserRepository userRepository) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.observationRegistry = observationRegistry;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
    }

    /**
     * Rewrites the given problem statement text for the provided course and user.
     *
     * @param course               the course context
     * @param problemStatementText the original problem statement
     * @return the rewrite result including whether it was improved
     */
    @Observed(name = "hyperion.rewrite", contextualName = "problem statement rewrite", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementRewriteResponseDTO rewriteProblemStatement(Course course, String problemStatementText) {
        log.debug("Rewriting problem statement for course {}", course.getId());

        var current = observationRegistry.getCurrentObservation();
        if (current != null) {
            String ctx = "rewrite problem statement for course id: " + course.getId();
            current.contextualName(ctx);
            current.highCardinalityKeyValue(io.micrometer.common.KeyValue.of("lf.trace.name", ctx));
        }

        String resourcePath = "/prompts/hyperion/rewrite_problem_statement.st";
        Map<String, String> input = Map.of("text", problemStatementText.trim());
        String renderedPrompt = templateService.render(resourcePath, input);
        ChatResponse chatResponse;
        String responseContent;
        try {
            chatResponse = chatClient.prompt()
                    .system("You are an expert technical writing assistant for programming exercise problem statements. Return only the rewritten statement, no explanations.")
                    .user(renderedPrompt).call().chatResponse();
            responseContent = LLMTokenUsageService.extractResponseText(chatResponse);
        }
        catch (RuntimeException e) {
            log.warn("Failed to obtain or parse AI response for {} - returning original text", resourcePath, e);
            return new ProblemStatementRewriteResponseDTO(problemStatementText.trim(), false);
        }
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, REWRITE_PIPELINE_ID,
                builder -> builder.withCourse(course.getId()).withUser(userId));

        if (responseContent == null) {
            log.warn("AI returned null response for {} - returning original text", resourcePath);
            return new ProblemStatementRewriteResponseDTO(problemStatementText.trim(), false);
        }
        String result = responseContent.trim();
        boolean improved = !result.equals(problemStatementText.trim());
        return new ProblemStatementRewriteResponseDTO(result, improved);
    }

}
