package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.MAX_PROBLEM_STATEMENT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseDescription;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseTitle;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripLineNumbers;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripWrapperMarkers;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.validateUserPrompt;

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

        String sanitizedPrompt = sanitizeInput(userPrompt);
        validateUserPrompt(sanitizedPrompt, "ProblemStatementGeneration");

        String systemPrompt = templateService.render("/prompts/hyperion/generate_draft_problem_statement_system.st", Map.of());

        Map<String, String> userVariables = Map.of("userPrompt", sanitizedPrompt, "courseTitle", getSanitizedCourseTitle(course), "courseDescription",
                getSanitizedCourseDescription(course));
        String userMessage = templateService.render("/prompts/hyperion/generate_draft_problem_statement_user.st", userVariables);

        ChatResponse chatResponse;
        String generatedProblemStatement;
        try {
            chatResponse = chatClient.prompt().system(systemPrompt).user(userMessage).call().chatResponse();
            generatedProblemStatement = LLMTokenUsageService.extractResponseText(chatResponse);
        }
        catch (Exception e) {
            log.error("Error generating problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to generate problem statement", "ProblemStatement", "ProblemStatementGeneration.problemStatementGenerationFailed");
        }
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                builder -> builder.withCourse(course.getId()).withUser(userId));

        boolean isEmptyResponse = generatedProblemStatement == null || generatedProblemStatement.isBlank();
        if (isEmptyResponse) {
            throw new InternalServerErrorAlertException("Generated problem statement is null or empty", "ProblemStatement",
                    "ProblemStatementGeneration.problemStatementGenerationNull");
        }

        // Defensively strip artifacts the LLM may have copied from the prompt template
        generatedProblemStatement = stripLineNumbers(generatedProblemStatement);
        generatedProblemStatement = stripWrapperMarkers(generatedProblemStatement);

        generatedProblemStatement = generatedProblemStatement.trim();

        // Validate response length
        boolean exceedsMaxLength = generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH;
        if (exceedsMaxLength) {
            throw new InternalServerErrorAlertException("Generated problem statement exceeds the maximum allowed length", "ProblemStatement",
                    "ProblemStatementGeneration.generatedProblemStatementTooLong");
        }

        return new ProblemStatementGenerationResponseDTO(generatedProblemStatement);
    }

}
