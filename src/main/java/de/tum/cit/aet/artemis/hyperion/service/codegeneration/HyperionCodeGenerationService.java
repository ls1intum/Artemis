package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Abstract base class for AI-powered code generation strategies.
 * Provides a standardized 4-step generation pipeline: solution planning, file structure definition,
 * class/method header generation, and core logic implementation. Supports iterative improvement
 * through build feedback integration.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public abstract class HyperionCodeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionCodeGenerationService.class);

    /**
     * Maximum number of characters kept when passing consistency issues into AI prompts.
     */
    private static final int MAX_CONSISTENCY_ISSUES_LENGTH = 10000;

    /**
     * Regex that matches control characters except carriage return, line feed, and tab.
     * Used to sanitize consistency issue text before prompt rendering.
     */
    private static final String CONTROL_CHARS_PATTERN = "[\\p{Cntrl}&&[^\r\n\t]]";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final LLMTokenUsageService llmTokenUsageService;

    public HyperionCodeGenerationService(ProgrammingExerciseRepository programmingExerciseRepository, @Autowired(required = false) ChatClient chatClient,
            HyperionPromptTemplateService templates, LLMTokenUsageService llmTokenUsageService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
        this.llmTokenUsageService = llmTokenUsageService;
    }

    /**
     * Generates code files using the 4-step AI generation pipeline.
     * Orchestrates solution planning, file structure definition, header generation, and core logic implementation.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to generate code for
     * @param previousBuildLogs   build failure logs from previous attempts for iterative improvement
     * @param repositoryStructure tree-format representation of current repository structure
     * @param consistencyIssues   formatted consistency issues to inform the generation prompts
     * @return list of generated code files
     * @throws NetworkingException if AI service communication fails
     */
    public List<GeneratedFileDTO> generateCode(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure, String consistencyIssues)
            throws NetworkingException {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (exercise == null) {
            throw new IllegalArgumentException("exercise must not be null");
        }
        if (repositoryStructure == null) {
            throw new IllegalArgumentException("repositoryStructure must not be null");
        }
        String normalizedConsistencyIssues = normalizeConsistencyIssues(consistencyIssues);
        CodeGenerationResponseDTO solutionPlanResponse = generateSolutionPlan(user, exercise, previousBuildLogs, repositoryStructure, normalizedConsistencyIssues);
        defineFileStructure(user, exercise, solutionPlanResponse.getSolutionPlan(), repositoryStructure, normalizedConsistencyIssues);
        generateClassAndMethodHeaders(user, exercise, solutionPlanResponse.getSolutionPlan(), repositoryStructure, normalizedConsistencyIssues);
        CodeGenerationResponseDTO coreLogicResponse = generateCoreLogic(user, exercise, solutionPlanResponse.getSolutionPlan(), repositoryStructure, normalizedConsistencyIssues);

        return coreLogicResponse.getFiles();
    }

    private String normalizeConsistencyIssues(String consistencyIssues) {
        if (consistencyIssues == null) {
            throw new IllegalArgumentException("consistencyIssues must not be null");
        }
        String trimmed = consistencyIssues.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String sanitized = trimmed.replaceAll(CONTROL_CHARS_PATTERN, "").trim();
        if (sanitized.length() > MAX_CONSISTENCY_ISSUES_LENGTH) {
            return sanitized.substring(0, MAX_CONSISTENCY_ISSUES_LENGTH);
        }
        return sanitized;
    }

    protected Map<String, Object> baseTemplateVariables(ProgrammingExercise exercise, String repositoryStructure, String consistencyIssues) {
        if (exercise == null) {
            throw new IllegalArgumentException("exercise must not be null");
        }
        if (repositoryStructure == null) {
            throw new IllegalArgumentException("repositoryStructure must not be null");
        }
        if (consistencyIssues == null) {
            throw new IllegalArgumentException("consistencyIssues must not be null");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("programmingLanguage", exercise.getProgrammingLanguage());
        variables.put("repositoryStructure", repositoryStructure);
        variables.put("consistencyIssues", consistencyIssues);
        return variables;
    }

    /**
     * Handles communication with the AI chat client, including prompt rendering and error handling.
     * Renders template variables into prompts and manages AI service exceptions.
     *
     * @param user              user initiating the generation request
     * @param exercise          programming exercise context for token-usage trace attribution
     * @param prompt            the prompt template path to render
     * @param templateVariables variables to substitute in the prompt template
     * @return the AI response containing generated content
     * @throws NetworkingException if AI service communication fails
     */
    protected CodeGenerationResponseDTO callChatClient(User user, ProgrammingExercise exercise, String prompt, Map<String, Object> templateVariables) throws NetworkingException {
        String rendered = templates.renderObject(prompt, templateVariables);
        BeanOutputConverter<CodeGenerationResponseDTO> outputConverter = new BeanOutputConverter<>(CodeGenerationResponseDTO.class);
        try {
            ChatClient.CallResponseSpec responseSpec = chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatClientAttributes.OUTPUT_FORMAT.getKey(), outputConverter.getFormat())
                            .param(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey(), outputConverter.getJsonSchema()))
                    .user(rendered).call();
            ChatResponse chatResponse = responseSpec.chatResponse();
            storeTokenUsage(user, exercise, prompt, chatResponse);
            String responseContent = extractResponseContent(chatResponse);
            CodeGenerationResponseDTO response = outputConverter.convert(responseContent);
            if (response == null) {
                throw new IllegalArgumentException("AI response content is missing");
            }
            return response;
        }
        catch (TransientAiException e) {
            throw new NetworkingException("Temporary AI service issue. Please retry.", e);
        }
        catch (NonTransientAiException e) {
            throw new NetworkingException("AI request failed due to configuration or input. Check model and request.", e);
        }
        catch (IllegalArgumentException e) {
            throw new NetworkingException("AI response processing failed. Please retry.", e);
        }
        catch (RuntimeException e) {
            if (isResponseProcessingException(e)) {
                throw new NetworkingException("AI response processing failed. Please retry.", e);
            }
            throw new NetworkingException("AI request failed due to an internal processing error. Please contact support.", e);
        }
    }

    private static boolean isResponseProcessingException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JsonProcessingException) {
                return true;
            }
            current = current.getCause();
        }
        for (StackTraceElement element : throwable.getStackTrace()) {
            if ("org.springframework.ai.converter.BeanOutputConverter".equals(element.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static String extractResponseContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            throw new IllegalArgumentException("AI response content is missing");
        }
        String responseContent = chatResponse.getResult().getOutput().getText();
        if (responseContent == null || responseContent.isBlank()) {
            throw new IllegalArgumentException("AI response content is empty");
        }
        return responseContent;
    }

    private void storeTokenUsage(User user, ProgrammingExercise exercise, String prompt, ChatResponse chatResponse) {
        if (llmTokenUsageService == null || chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return;
        }
        try {
            Usage usage = chatResponse.getMetadata().getUsage();
            String model = chatResponse.getMetadata().getModel();
            if (model == null || model.isBlank()) {
                return;
            }
            Integer promptTokenCount = usage.getPromptTokens();
            Integer completionTokenCount = usage.getCompletionTokens();
            if (promptTokenCount == null && completionTokenCount == null) {
                return;
            }
            int promptTokens = promptTokenCount != null ? promptTokenCount : 0;
            int completionTokens = completionTokenCount != null ? completionTokenCount : 0;
            LLMRequest llmRequest = llmTokenUsageService.buildLLMRequest(model, promptTokens, completionTokens, buildPipelineId(prompt));
            if (llmRequest == null) {
                return;
            }
            Long courseId = exercise != null && exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
            Long exerciseId = exercise != null ? exercise.getId() : null;
            Long userId = user != null ? user.getId() : null;
            llmTokenUsageService.saveLLMTokenUsage(List.of(llmRequest), LLMServiceType.HYPERION, builder -> builder.withCourse(courseId).withExercise(exerciseId).withUser(userId));
        }
        catch (RuntimeException e) {
            Long exerciseId = exercise != null ? exercise.getId() : null;
            log.warn("Failed to store token usage for Hyperion code generation (exerciseId={}, prompt={})", exerciseId, prompt, e);
        }
    }

    private String buildPipelineId(String prompt) {
        String promptId = prompt != null ? prompt.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_") : "UNKNOWN";
        return String.join("_", "HYPERION", "CODE", "GENERATION", getRepositoryType().name(), promptId);
    }

    /**
     * Generates a high-level solution plan for the programming exercise.
     * First step in the 4-step generation pipeline.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to analyze
     * @param previousBuildLogs   build failure logs from previous attempts for correction
     * @param repositoryStructure tree-format representation of current repository structure
     * @param consistencyIssues   formatted consistency issues to inform the generation prompts
     * @return AI response containing the solution plan
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure,
            String consistencyIssues) throws NetworkingException;

    /**
     * Defines the file structure and organization for the solution.
     * Second step in the 4-step generation pipeline.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to structure
     * @param solutionPlan        the high-level solution plan from step 1
     * @param repositoryStructure tree-format representation of current repository structure
     * @param consistencyIssues   formatted consistency issues to inform the generation prompts
     * @return AI response containing file structure definitions
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure,
            String consistencyIssues) throws NetworkingException;

    /**
     * Generates class definitions and method signatures.
     * Third step in the 4-step generation pipeline.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to create headers for
     * @param solutionPlan        the high-level solution plan from step 1
     * @param repositoryStructure tree-format representation of current repository structure
     * @param consistencyIssues   formatted consistency issues to inform the generation prompts
     * @return AI response containing class and method headers
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure,
            String consistencyIssues) throws NetworkingException;

    /**
     * Generates the core implementation logic for the solution.
     * Fourth and final step in the 4-step generation pipeline.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to implement
     * @param solutionPlan        the high-level solution plan from step 1
     * @param repositoryStructure tree-format representation of current repository structure
     * @param consistencyIssues   formatted consistency issues to inform the generation prompts
     * @return AI response containing complete implementation with generated files
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure,
            String consistencyIssues) throws NetworkingException;

    /**
     * Returns the repository type that this strategy generates code for.
     *
     * @return the repository type (SOLUTION, TEMPLATE, or TESTS)
     */
    protected abstract RepositoryType getRepositoryType();
}
