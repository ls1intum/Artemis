package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.MAX_PROBLEM_STATEMENT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripLineNumbers;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripWrapperMarkers;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
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

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    /**
     * Creates a new ProblemStatementRewriteService.
     *
     * @param chatClient           the AI chat client, may be null if AI is not configured
     * @param templateService      prompt template service
     * @param llmTokenUsageService service for tracking LLM token usage
     * @param userRepository       repository for resolving current user
     */
    public HyperionProblemStatementRewriteService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService, LLMTokenUsageService llmTokenUsageService,
            UserRepository userRepository) {
        this.chatClient = chatClient;
        this.templateService = templateService;
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
        log.debug("Rewriting problem statement for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ProblemStatement", "ProblemStatementRewrite.chatClientNotConfigured");
        }

        String sanitizedProblemStatement = sanitizeInput(problemStatementText);
        if (sanitizedProblemStatement.isBlank()) {
            throw new BadRequestAlertException("Problem statement cannot be empty", "ProblemStatement", "ProblemStatementRewrite.problemStatementEmpty");
        }

        String resourcePath = "/prompts/hyperion/rewrite_problem_statement.st";
        Map<String, String> input = Map.of("text", sanitizedProblemStatement);
        String renderedPrompt = templateService.render(resourcePath, input);
        ChatResponse chatResponse;
        String responseContent;
        try {
            chatResponse = chatClient.prompt()
                    .system("You are an expert technical writing assistant for programming exercise problem statements. Return only the rewritten statement, no explanations.")
                    .user(renderedPrompt).call().chatResponse();
            responseContent = LLMTokenUsageService.extractResponseText(chatResponse);
        }
        catch (Exception e) {
            log.error("Error rewriting problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to rewrite problem statement", "ProblemStatement", "ProblemStatementRewrite.problemStatementRewriteFailed");
        }
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, REWRITE_PIPELINE_ID,
                builder -> builder.withCourse(course.getId()).withUser(userId));

        boolean isEmptyResponse = responseContent == null || responseContent.isBlank();
        if (isEmptyResponse) {
            throw new InternalServerErrorAlertException("Rewritten problem statement is null or empty", "ProblemStatement", "ProblemStatementRewrite.problemStatementRewriteNull");
        }

        // Defensively strip artifacts the LLM may have copied from the prompt template
        responseContent = stripLineNumbers(responseContent);
        responseContent = stripWrapperMarkers(responseContent);

        String result = responseContent.trim();

        // Validate response length
        boolean exceedsMaxLength = result.length() > MAX_PROBLEM_STATEMENT_LENGTH;
        if (exceedsMaxLength) {
            throw new InternalServerErrorAlertException("Rewritten problem statement exceeds the maximum allowed length", "ProblemStatement",
                    "ProblemStatementRewrite.rewrittenProblemStatementTooLong");
        }

        boolean improved = !result.equals(sanitizedProblemStatement);
        return new ProblemStatementRewriteResponseDTO(result, improved);
    }

}
