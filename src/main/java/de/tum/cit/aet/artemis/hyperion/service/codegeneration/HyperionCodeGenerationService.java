package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import tools.jackson.core.JacksonException;

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

    private static final ObjectMapper OBJECT_MAPPER = JsonObjectMapper.get();

    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private static final Pattern CHANNEL_TIMEOUT_MESSAGE_PATTERN = Pattern.compile("Channel response timed out after \\d+ milliseconds");

    private static final String USER_FRIENDLY_CHANNEL_TIMEOUT_MESSAGE = "The AI took too long to respond and this generation request timed out after 5 minutes. Please refresh first to check whether any files were already created or updated. If nothing changed, start the generation again.";

    /**
     * Maximum number of characters kept when passing consistency issues into AI prompts.
     */
    private static final int MAX_CONSISTENCY_ISSUES_LENGTH = 10000;

    private static final String DEFAULT_BUILD_ENVIRONMENT_CONTEXT = "No build environment files found.";

    private static final int MAX_BUILD_ENVIRONMENT_CONTEXT_LENGTH = 12000;

    /**
     * Regex that matches control characters except carriage return, line feed, and tab.
     * Used to sanitize consistency issue text before prompt rendering.
     */
    private static final String CONTROL_CHARS_PATTERN = "[\\p{Cntrl}&&[^\r\n\t]]";

    private static final String BUILD_ENVIRONMENT_CONTEXT_TEMPLATE_VARIABLE = "buildEnvironmentContext";

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final LLMTokenUsageService llmTokenUsageService;

    public HyperionCodeGenerationService(@Autowired(required = false) ChatClient chatClient, HyperionPromptTemplateService templates, LLMTokenUsageService llmTokenUsageService) {
        this.chatClient = chatClient;
        this.templates = templates;
        this.llmTokenUsageService = llmTokenUsageService;
    }

    /**
     * Generates code files using the 4-step AI generation pipeline.
     * Orchestrates solution planning, file structure definition, header generation, and core logic implementation.
     *
     * @param user                    the user requesting code generation
     * @param exercise                the programming exercise to generate code for
     * @param courseId                the resolved course id for telemetry attribution
     * @param previousBuildLogs       build failure logs from previous attempts for iterative improvement
     * @param repositoryStructure     tree-format representation of current repository structure
     * @param buildEnvironmentContext rendered build-file context for dependency and toolchain alignment
     * @param consistencyIssues       formatted consistency issues to inform the generation prompts
     * @return list of generated code files
     * @throws NetworkingException if AI service communication fails
     */
    public List<GeneratedFileDTO> generateCode(User user, ProgrammingExercise exercise, Long courseId, String previousBuildLogs, String repositoryStructure,
            String buildEnvironmentContext, String consistencyIssues) throws NetworkingException {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (exercise == null) {
            throw new IllegalArgumentException("exercise must not be null");
        }
        if (repositoryStructure == null) {
            throw new IllegalArgumentException("repositoryStructure must not be null");
        }
        String normalizedBuildEnvironmentContext = normalizeBuildEnvironmentContext(buildEnvironmentContext);
        String normalizedConsistencyIssues = normalizeConsistencyIssues(consistencyIssues);
        CodeGenerationResponseDTO solutionPlanResponse = generateSolutionPlan(user, exercise, courseId, previousBuildLogs, repositoryStructure, normalizedBuildEnvironmentContext,
                normalizedConsistencyIssues);
        CodeGenerationResponseDTO coreLogicResponse = generateCoreLogic(user, exercise, courseId, solutionPlanResponse.getSolutionPlan(), repositoryStructure,
                normalizedBuildEnvironmentContext, normalizedConsistencyIssues);

        return coreLogicResponse.getFiles();
    }

    private String normalizeBuildEnvironmentContext(String buildEnvironmentContext) {
        if (buildEnvironmentContext == null) {
            throw new IllegalArgumentException("buildEnvironmentContext must not be null");
        }
        String trimmed = buildEnvironmentContext.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_BUILD_ENVIRONMENT_CONTEXT;
        }
        String sanitized = trimmed.replaceAll(CONTROL_CHARS_PATTERN, "").trim();
        if (sanitized.length() > MAX_BUILD_ENVIRONMENT_CONTEXT_LENGTH) {
            return sanitized.substring(0, MAX_BUILD_ENVIRONMENT_CONTEXT_LENGTH);
        }
        return sanitized;
    }

    /**
     * Sanitizes consistency issue text before it is embedded into prompts.
     *
     * @param consistencyIssues raw consistency issue text
     * @return trimmed, control-character-free text truncated to the configured limit
     */
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

    /**
     * Creates the common template variable set shared by the different generation stages.
     *
     * @param exercise            programming exercise context
     * @param repositoryStructure tree-format representation of the repository
     * @param consistencyIssues   sanitized consistency issue text
     * @return mutable template variable map populated with the shared values
     */
    protected Map<String, Object> baseTemplateVariables(ProgrammingExercise exercise, String repositoryStructure, String buildEnvironmentContext, String consistencyIssues) {
        if (exercise == null) {
            throw new IllegalArgumentException("exercise must not be null");
        }
        if (repositoryStructure == null) {
            throw new IllegalArgumentException("repositoryStructure must not be null");
        }
        if (buildEnvironmentContext == null) {
            throw new IllegalArgumentException("buildEnvironmentContext must not be null");
        }
        if (consistencyIssues == null) {
            throw new IllegalArgumentException("consistencyIssues must not be null");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("programmingLanguage", exercise.getProgrammingLanguage());
        variables.put("repositoryStructure", repositoryStructure);
        variables.put(BUILD_ENVIRONMENT_CONTEXT_TEMPLATE_VARIABLE, buildEnvironmentContext);
        variables.put("consistencyIssues", consistencyIssues);
        return variables;
    }

    /**
     * Handles communication with the AI chat client, including prompt rendering and error handling.
     * Renders template variables into prompts and manages AI service exceptions.
     *
     * @param user              user initiating the generation request
     * @param exercise          programming exercise context for token-usage trace attribution
     * @param courseId          resolved course id for token-usage trace attribution
     * @param prompt            the prompt template path to render
     * @param templateVariables variables to substitute in the prompt template
     * @return the AI response containing generated content
     * @throws NetworkingException if AI service communication fails
     */
    protected CodeGenerationResponseDTO callChatClient(User user, ProgrammingExercise exercise, Long courseId, String prompt, Map<String, Object> templateVariables)
            throws NetworkingException {
        String rendered = templates.renderObject(prompt, templateVariables);
        try {
            ChatResponse chatResponse = chatClient.prompt().user(rendered).call().chatResponse();
            CodeGenerationResponseDTO response = parseCodeGenerationResponse(LLMTokenUsageService.extractResponseText(chatResponse));
            storeTokenUsage(user, exercise, courseId, prompt, chatResponse);
            return response;
        }
        catch (TransientAiException e) {
            throw new NetworkingException("Temporary AI service issue. Please retry.", e);
        }
        catch (NonTransientAiException e) {
            throw new NetworkingException("AI request failed due to configuration or input. Check model and request.", e);
        }
        catch (IllegalArgumentException e) {
            throw new NetworkingException("AI response processing failed due to illegal argument. Please retry.", e);
        }
        catch (RuntimeException e) {
            if (isResponseProcessingException(e)) {
                throw new NetworkingException("AI response processing failed. Please retry.", e);
            }
            if (isChannelResponseTimeout(e)) {
                throw new NetworkingException(USER_FRIENDLY_CHANNEL_TIMEOUT_MESSAGE, e);
            }
            throw new NetworkingException("AI request failed due to an internal processing error.", e);
        }
    }

    /**
     * Parses the LLM response into the DTO expected by the generation pipeline.
     *
     * @param responseText raw text returned by the model
     * @return parsed code generation response
     */
    private CodeGenerationResponseDTO parseCodeGenerationResponse(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalArgumentException("AI response content is missing");
        }

        String trimmed = responseText.trim();
        List<String> candidates = extractJsonCandidates(trimmed);
        for (int index = 0; index < candidates.size(); index++) {
            String candidate = candidates.get(index);
            try {
                CodeGenerationResponseDTO response = OBJECT_MAPPER.readValue(candidate, CodeGenerationResponseDTO.class);
                if (response != null) {
                    return response;
                }
            }
            catch (JsonProcessingException e) {
                log.debug("Failed to parse AI response candidate {}/{} as code generation JSON", index + 1, candidates.size(), e);
                // Try the next candidate.
            }
        }
        throw new IllegalArgumentException("AI response content could not be parsed");
    }

    /**
     * Extracts likely JSON payload candidates from raw LLM output.
     *
     * @param responseText raw LLM output
     * @return ordered immutable JSON candidates to try parsing, from most specific to fallback;
     *         callers must treat the returned list as read-only
     */
    private List<String> extractJsonCandidates(String responseText) {
        Matcher codeBlockMatcher = JSON_CODE_BLOCK_PATTERN.matcher(responseText);
        if (codeBlockMatcher.find()) {
            return List.of(codeBlockMatcher.group(1).trim(), responseText);
        }

        int firstBrace = responseText.indexOf('{');
        int lastBrace = responseText.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String embeddedJson = responseText.substring(firstBrace, lastBrace + 1).trim();
            if (!embeddedJson.equals(responseText)) {
                return List.of(embeddedJson, responseText);
            }
        }

        return List.of(responseText);
    }

    /**
     * Detects whether a runtime exception originated from response parsing or DTO mapping.
     *
     * @param throwable exception to inspect
     * @return true if the causal chain contains a response-processing exception
     */
    private static boolean isResponseProcessingException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            // Check for both Jackson 2 (com.fasterxml) and Jackson 3 (tools.jackson) exceptions,
            // since Spring AI's BeanOutputConverter uses Jackson 3 internally.
            if (current instanceof JsonProcessingException || current instanceof JacksonException || current instanceof IllegalArgumentException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Detects a channel timeout surfaced by the Spring AI client.
     *
     * @param throwable exception to inspect
     * @return true if the causal chain indicates the channel response timed out
     */
    private static boolean isChannelResponseTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && CHANNEL_TIMEOUT_MESSAGE_PATTERN.matcher(message).find()) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Persists token-usage telemetry when usage metadata is available from the model response.
     *
     * @param user         user who triggered the generation
     * @param exercise     exercise being generated
     * @param courseId     resolved course id for attribution
     * @param prompt       prompt identifier used for the call
     * @param chatResponse model response containing usage metadata
     */
    private void storeTokenUsage(User user, ProgrammingExercise exercise, Long courseId, String prompt, ChatResponse chatResponse) {
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
            Long exerciseId = exercise != null ? exercise.getId() : null;
            if (courseId == null) {
                log.warn("Skipping token usage persistence for Hyperion code generation due to missing courseId (exerciseId={}, prompt={})", exerciseId, prompt);
                return;
            }
            int promptTokens = sanitizeTokenCount(promptTokenCount);
            int completionTokens = sanitizeTokenCount(completionTokenCount);
            LLMRequest llmRequest = llmTokenUsageService.buildLLMRequest(model, promptTokens, completionTokens, buildPipelineId(prompt));
            if (llmRequest == null) {
                return;
            }
            Long userId = user != null ? user.getId() : null;
            llmTokenUsageService.saveLLMTokenUsage(List.of(llmRequest), LLMServiceType.HYPERION, builder -> builder.withCourse(courseId).withExercise(exerciseId).withUser(userId));
        }
        catch (RuntimeException e) {
            Long exerciseId = exercise != null ? exercise.getId() : null;
            log.warn("Failed to store token usage for Hyperion code generation (exerciseId={}, prompt={})", exerciseId, prompt, e);
        }
    }

    /**
     * Normalizes nullable or negative token counts before persistence.
     *
     * @param tokenCount token count reported by the model metadata
     * @return non-negative token count
     */
    private static int sanitizeTokenCount(Integer tokenCount) {
        if (tokenCount == null) {
            return 0;
        }
        return Math.max(tokenCount, 0);
    }

    /**
     * Builds the telemetry pipeline id for a prompt invocation.
     *
     * @param prompt prompt path or identifier
     * @return pipeline id used for token-usage attribution
     */
    private String buildPipelineId(String prompt) {
        String promptId = extractPromptId(prompt);
        return String.join("_", "HYPERION", "CODE", "GENERATION", getRepositoryType().name(), promptId);
    }

    /**
     * Extracts a stable uppercase prompt id from the prompt file name.
     *
     * @param prompt prompt path or identifier
     * @return normalized prompt id, or {@code UNKNOWN} when none can be derived
     */
    private static String extractPromptId(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "UNKNOWN";
        }

        String normalizedPath = prompt.trim().replace('\\', '/');
        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        String fileName = lastSlashIndex >= 0 ? normalizedPath.substring(lastSlashIndex + 1) : normalizedPath;

        int extensionSeparatorIndex = fileName.lastIndexOf('.');
        String fileNameWithoutExtension = extensionSeparatorIndex > 0 ? fileName.substring(0, extensionSeparatorIndex) : fileName;

        String sanitized = fileNameWithoutExtension.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "UNKNOWN" : sanitized;
    }

    /**
     * Generates a high-level solution plan for the programming exercise.
     * First step in the 4-step generation pipeline.
     *
     * @param user                    the user requesting code generation
     * @param exercise                the programming exercise to analyze
     * @param courseId                the resolved course id for telemetry attribution
     * @param previousBuildLogs       build failure logs from previous attempts for correction
     * @param repositoryStructure     tree-format representation of current repository structure
     * @param buildEnvironmentContext rendered build-file context for dependency and toolchain alignment
     * @param consistencyIssues       formatted consistency issues to inform the generation prompts
     * @return AI response containing the solution plan
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, Long courseId, String previousBuildLogs, String repositoryStructure,
            String buildEnvironmentContext, String consistencyIssues) throws NetworkingException;

    /**
     * Defines the file structure and organization for the solution.
     * Second step in the 4-step generation pipeline.
     *
     * @param user                    the user requesting code generation
     * @param exercise                the programming exercise to structure
     * @param courseId                the resolved course id for telemetry attribution
     * @param solutionPlan            the high-level solution plan from step 1
     * @param repositoryStructure     tree-format representation of current repository structure
     * @param buildEnvironmentContext rendered build-file context for dependency and toolchain alignment
     * @param consistencyIssues       formatted consistency issues to inform the generation prompts
     * @return AI response containing file structure definitions
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan, String repositoryStructure,
            String buildEnvironmentContext, String consistencyIssues) throws NetworkingException;

    /**
     * Generates class definitions and method signatures.
     * Third step in the 4-step generation pipeline.
     *
     * @param user                    the user requesting code generation
     * @param exercise                the programming exercise to create headers for
     * @param courseId                the resolved course id for telemetry attribution
     * @param solutionPlan            the high-level solution plan from step 1
     * @param repositoryStructure     tree-format representation of current repository structure
     * @param buildEnvironmentContext rendered build-file context for dependency and toolchain alignment
     * @param consistencyIssues       formatted consistency issues to inform the generation prompts
     * @return AI response containing class and method headers
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan,
            String repositoryStructure, String buildEnvironmentContext, String consistencyIssues) throws NetworkingException;

    /**
     * Generates the core implementation logic for the solution.
     * Fourth and final step in the 4-step generation pipeline.
     *
     * @param user                    the user requesting code generation
     * @param exercise                the programming exercise to implement
     * @param courseId                the resolved course id for telemetry attribution
     * @param solutionPlan            the high-level solution plan from step 1
     * @param repositoryStructure     tree-format representation of current repository structure
     * @param buildEnvironmentContext rendered build-file context for dependency and toolchain alignment
     * @param consistencyIssues       formatted consistency issues to inform the generation prompts
     * @return AI response containing complete implementation with generated files
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan, String repositoryStructure,
            String buildEnvironmentContext, String consistencyIssues) throws NetworkingException;

    /**
     * Returns the repository type that this strategy generates code for.
     *
     * @return the repository type (SOLUTION, TEMPLATE, or TESTS)
     */
    protected abstract RepositoryType getRepositoryType();
}
