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

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
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

    private static final String HYGIENE_REPAIR_INSTRUCTION = """

            The previous draft was rejected by automated hygiene checks. Rewrite it from scratch.
            Keep it student-facing and behavioral. Use only these headings: title, Introduction, Required Behaviors, Boundary Cases, and Worked Examples.
            Do not include task markers, test names, grader internals, UML, optional extras, benchmarks, student test-suite work, or any checklist item about tests unless the instructor explicitly requested them.
            Do not include optional/removable side features, resource-limit discussions, thread-safety/concurrency requirements, or examples that say both conflict and no conflict.
            Do not invent files, standard input, command-line interfaces, CSV, JSON, databases, or web interfaces unless the instructor requested them. Do not add submission or
            deliverable sections, code-comment requirements, style advice, or specific library/framework/type choices unless the instructor requested them as a learning objective.
            If the instructor asked to avoid exact names, do not use the words API, operation, method, class, unit test, tests, test suite, optional, benchmark, UML, repository, grader, or hidden. Remove every API heading, operation table, method-like operation name, exact class name, and Java code example; use neutral prose or input/output tables instead.
            Do not include instructor decisions, open questions, drafting notes, or other authoring-process sections. Resolve reasonable defaults directly in the requirements.
            """;

    @Nullable
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
     * @param userPrompt the user's requirements and instructions for the problem statement
     * @return the generated problem statement response
     * @throws InternalServerErrorAlertException if generation fails or response is too long
     */
    @Observed(name = "hyperion.generate", contextualName = "problem statement generation", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementGenerationResponseDTO generateProblemStatement(Course course, String userPrompt) {
        log.debug("Generating problem statement for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ProblemStatement", "ProblemStatementGeneration.chatClientNotConfigured");
        }

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

        // Defensively strip artifacts the LLM may have copied from the prompt template
        generatedProblemStatement = cleanGeneratedProblemStatement(generatedProblemStatement);

        boolean isEmptyResponse = generatedProblemStatement == null || generatedProblemStatement.isBlank();
        if (isEmptyResponse) {
            throw new InternalServerErrorAlertException("Generated problem statement is null or empty", "ProblemStatement",
                    "ProblemStatementGeneration.problemStatementGenerationNull");
        }

        try {
            HyperionUtils.validateDraftProblemStatementHygiene(generatedProblemStatement, sanitizedPrompt, "ProblemStatementGeneration");
        }
        catch (InternalServerErrorAlertException hygieneFailure) {
            log.info("Generated draft problem statement failed hygiene checks for course [{}]; retrying once with repair instructions", course.getId());
            try {
                chatResponse = chatClient.prompt().system(systemPrompt + HYGIENE_REPAIR_INSTRUCTION).user(userMessage).call().chatResponse();
                generatedProblemStatement = cleanGeneratedProblemStatement(LLMTokenUsageService.extractResponseText(chatResponse));
            }
            catch (Exception e) {
                log.error("Error repairing generated problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
                throw hygieneFailure;
            }
            llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                    builder -> builder.withCourse(course.getId()).withUser(userId));
            if (generatedProblemStatement == null || generatedProblemStatement.isBlank()) {
                throw new InternalServerErrorAlertException("Generated problem statement is null or empty", "ProblemStatement",
                        "ProblemStatementGeneration.problemStatementGenerationNull");
            }
            HyperionUtils.validateDraftProblemStatementHygiene(generatedProblemStatement, sanitizedPrompt, "ProblemStatementGeneration");
        }

        // Validate response length
        boolean exceedsMaxLength = generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH;
        if (exceedsMaxLength) {
            throw new InternalServerErrorAlertException("Generated problem statement exceeds the maximum allowed length", "ProblemStatement",
                    "ProblemStatementGeneration.generatedProblemStatementTooLong");
        }

        return new ProblemStatementGenerationResponseDTO(generatedProblemStatement);
    }

    @Nullable
    private static String cleanGeneratedProblemStatement(@Nullable String generatedProblemStatement) {
        if (generatedProblemStatement == null) {
            return null;
        }
        return stripWrapperMarkers(stripLineNumbers(generatedProblemStatement)).trim();
    }

}
