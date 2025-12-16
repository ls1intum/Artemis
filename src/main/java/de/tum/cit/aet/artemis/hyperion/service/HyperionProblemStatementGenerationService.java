package de.tum.cit.aet.artemis.hyperion.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.util.LlmUsageHelper;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;

/**
 * Service for generating initial draft problem statements using Spring AI.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementGenerationService.class);

    /**
     * Maximum allowed length for generated problem statements (50,000 characters).
     * This prevents excessively long responses that could cause performance issues.
     */
    private static final int MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LlmUsageHelper llmUsageHelper;

    /**
     *
     *
     * @param chatClient      the AI chat client (optional)
     * @param templateService prompt template service
     */
    public HyperionProblemStatementGenerationService(ChatClient chatClient, HyperionPromptTemplateService templateService, LlmUsageHelper llmUsageHelper) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.llmUsageHelper = llmUsageHelper;
    }

    /**
     * Generate a problem statement for an exercise
     *
     * @param course     the course context for the problem statement
     * @param userPrompt the user's requirements and instructions for the problem statement
     * @return the generated problem statement response
     * @throws InternalServerErrorAlertException if generation fails or response is too long
     */
    public ProblemStatementGenerationResponseDTO generateProblemStatement(Course course, String userPrompt) {
        log.debug("Generating problem statement for course [{}]", course.getId());

        try {

            Map<String, String> templateVariables = Map.of("userPrompt", userPrompt != null ? userPrompt : "Generate a programming exercise problem statement", "courseTitle",
                    course.getTitle() != null ? course.getTitle() : "Programming Course", "courseDescription",
                    course.getDescription() != null ? course.getDescription() : "A programming course");

            String prompt = templateService.render("/prompts/hyperion/generate_draft_problem_statement.st", templateVariables);
            CallResponseSpec response = chatClient.prompt().user(prompt).call();
            ChatResponse chatResponse = response.chatResponse();
            String generatedProblemStatement = chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : response.content();
            String normalizedModel = extractModelName(chatResponse);

            // Validate response length
            if (generatedProblemStatement != null && generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH) {
                log.warn("Generated problem statement for course [{}] exceeds maximum length: {} characters", course.getId(), generatedProblemStatement.length());
                throw new InternalServerErrorAlertException(
                        "Generated problem statement is too long (" + generatedProblemStatement.length() + " characters). Maximum allowed: " + MAX_PROBLEM_STATEMENT_LENGTH,
                        "ProblemStatement", "problemStatementTooLong");
            }

            return new ProblemStatementGenerationResponseDTO(generatedProblemStatement, normalizedModel);
        }
        catch (InternalServerErrorAlertException e) {
            // Re-throw our own exceptions
            throw e;
        }
        catch (Exception e) {
            log.error("Error generating problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to generate problem statement: " + e.getMessage(), "ProblemStatement", "problemStatementGenerationFailed");
        }
    }

    private String extractModelName(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        String rawModel = chatResponse.getMetadata().getModel();
        if (rawModel == null) {
            return null;
        }
        String normalizedModel = llmUsageHelper.normalizeModelName(rawModel);
        return normalizedModel == null || normalizedModel.isBlank() ? null : normalizedModel;
    }
}
