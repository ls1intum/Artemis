package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
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
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;
import io.micrometer.observation.annotation.Observed;

/**
 * Service for generating initial draft problem statements using Spring AI.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementGenerationService.class);

    private static final String GENERATION_PIPELINE_ID = "HYPERION_PROBLEM_GENERATION";

    /**
     * Maximum allowed length for generated problem statements (50,000 characters).
     * This prevents excessively long responses that could cause performance issues.
     */
    private static final int MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    /**
     * Creates a new HyperionProblemStatementGenerationService.
     *
     *
     * @param chatClient           the AI chat client (optional)
     * @param templateService      prompt template service
     * @param llmTokenUsageService service for tracking LLM token usage
     * @param userRepository       repository for resolving current user
     */
    public HyperionProblemStatementGenerationService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService, LLMTokenUsageService llmTokenUsageService,
            UserRepository userRepository) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
    }

    /**
     * Generate a problem statement for an exercise
     *
     * @param course     the course context for the problem statement
     * @param userPrompt the user's requirements and instructions for the problem
     *                       statement
     * @return the generated problem statement response
     * @throws InternalServerErrorAlertException if generation fails or response is
     *                                               too long
     */
    @Observed(name = "hyperion.generate", contextualName = "problem statement generation", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementGenerationResponseDTO generateProblemStatement(Course course, String userPrompt) {
        log.debug("Generating problem statement for course [{}]", course.getId());

        try {

            Map<String, String> templateVariables = Map.of("userPrompt", userPrompt != null ? userPrompt : "Generate a programming exercise problem statement", "courseTitle",
                    course.getTitle() != null ? course.getTitle() : "Programming Course", "courseDescription",
                    course.getDescription() != null ? course.getDescription() : "A programming course");

            String prompt = templateService.render("/prompts/hyperion/generate_draft_problem_statement.st", templateVariables);

            ChatResponse chatResponse = chatClient.prompt().user(prompt).call().chatResponse();
            String generatedProblemStatement = null;
            if (chatResponse != null) {
                generatedProblemStatement = chatResponse.getResult().getOutput().getText();
            }

            // Store token usage
            if (chatResponse != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                LLMRequest llmRequest = llmTokenUsageService.buildLLMRequest(chatResponse.getMetadata().getModel(), usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                        usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0, GENERATION_PIPELINE_ID);
                Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
                llmTokenUsageService.saveLLMTokenUsage(List.of(llmRequest), LLMServiceType.HYPERION, builder -> builder.withCourse(course.getId()).withUser(userId));
            }

            // Validate response length
            if (generatedProblemStatement != null && generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
                log.warn("Generated problem statement for course [{}] exceeds maximum length: {} characters", course.getId(), generatedProblemStatement.length());
                throw new InternalServerErrorAlertException(
                        "Generated problem statement is too long (" + generatedProblemStatement.length() + " characters). Maximum allowed: " + MAX_PROBLEM_STATEMENT_LENGTH,
                        "ProblemStatement", "problemStatementTooLong");
            }

            return new ProblemStatementGenerationResponseDTO(generatedProblemStatement);
        }

        String sanitizedPrompt = sanitizeInput(userPrompt);
        HyperionUtils.validateUserPrompt(sanitizedPrompt, "ProblemStatementGeneration");

        String systemPrompt = templateService.render("/prompts/hyperion/generate_draft_problem_statement_system.st", Map.of());

        Map<String, String> userVariables = Map.of("userPrompt", sanitizedPrompt, "courseTitle", getSanitizedCourseTitle(course), "courseDescription",
                getSanitizedCourseDescription(course));
        String userMessage = templateService.render("/prompts/hyperion/generate_draft_problem_statement_user.st", userVariables);

        String generatedProblemStatement;
        try {
            var chatResponse = chatClient.prompt().system(systemPrompt).user(userMessage).call().chatResponse();
            generatedProblemStatement = LLMTokenUsageService.extractResponseText(chatResponse);
            Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
            llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                    builder -> builder.withCourse(course.getId()).withUser(userId));
        }
        catch (Exception e) {
            log.error("Error generating problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to generate problem statement", "ProblemStatement", "ProblemStatementGeneration.problemStatementGenerationFailed");
        }

        if (generatedProblemStatement == null || generatedProblemStatement.isBlank()) {
            throw new InternalServerErrorAlertException("Generated problem statement is null or empty", "ProblemStatement",
                    "ProblemStatementGeneration.problemStatementGenerationNull");
        }

        // Defensively strip line-number prefixes the LLM may have included in its response
        generatedProblemStatement = stripLineNumbers(generatedProblemStatement);

        generatedProblemStatement = generatedProblemStatement.trim();

        // Validate response length
        if (generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
            log.warn("Generated problem statement for course [{}] exceeds maximum length: {} characters (max {})", course.getId(), generatedProblemStatement.length(),
                    MAX_PROBLEM_STATEMENT_LENGTH);
            throw new InternalServerErrorAlertException("Generated problem statement exceeds the maximum allowed length", "ProblemStatement",
                    "ProblemStatementGeneration.generatedProblemStatementTooLong");
        }

        return new ProblemStatementGenerationResponseDTO(generatedProblemStatement);
    }

}
