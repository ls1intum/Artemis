package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    private static final String DEFAULT_FIX_BATCH_REVIEW_THREADS = "{\"threads\":[]}";

    private static final int MAX_FIX_BATCH_REVIEW_THREADS_LENGTH = 12000;

    /**
     * Regex that matches control characters except carriage return, line feed, and tab.
     * Used to sanitize consistency issue text before prompt rendering.
     */
    private static final String CONTROL_CHARS_PATTERN = "[\\p{Cntrl}&&[^\r\n\t]]";

    private static final String FIX_BATCH_REVIEW_THREADS_TEMPLATE_VARIABLE = "fixBatchReviewThreads";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
     * @param user                  the user requesting code generation
     * @param exercise              the programming exercise to generate code for
     * @param courseId              the resolved course id for telemetry attribution
     * @param previousBuildLogs     build failure logs from previous attempts for iterative improvement
     * @param repositoryStructure   tree-format representation of current repository structure
     * @param consistencyIssues     formatted consistency issues to inform the generation prompts
     * @param fixBatchReviewThreads selected review-thread payload to inform the generation prompts
     * @return list of generated code files
     * @throws NetworkingException if AI service communication fails
     */
    public List<GeneratedFileDTO> generateCode(User user, ProgrammingExercise exercise, Long courseId, String previousBuildLogs, String repositoryStructure,
            String consistencyIssues, String fixBatchReviewThreads) throws NetworkingException {
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
        String normalizedFixBatchReviewThreads = normalizeFixBatchReviewThreads(fixBatchReviewThreads);
        CodeGenerationResponseDTO solutionPlanResponse = generateSolutionPlan(user, exercise, courseId, previousBuildLogs, repositoryStructure, normalizedConsistencyIssues,
                normalizedFixBatchReviewThreads);
        CodeGenerationResponseDTO coreLogicResponse = generateCoreLogic(user, exercise, courseId, solutionPlanResponse.getSolutionPlan(), repositoryStructure,
                normalizedConsistencyIssues, normalizedFixBatchReviewThreads);

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

    private String normalizeFixBatchReviewThreads(String fixBatchReviewThreads) {
        if (fixBatchReviewThreads == null) {
            throw new IllegalArgumentException("fixBatchReviewThreads must not be null");
        }
        String trimmed = fixBatchReviewThreads.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_FIX_BATCH_REVIEW_THREADS;
        }
        String sanitized = trimmed.replaceAll(CONTROL_CHARS_PATTERN, "").trim();
        if (sanitized.length() > MAX_FIX_BATCH_REVIEW_THREADS_LENGTH) {
            return truncateFixBatchReviewThreadsSafely(sanitized);
        }
        return sanitized;
    }

    private String truncateFixBatchReviewThreadsSafely(String sanitized) {
        String normalizedJson = tryTrimParsedFixBatchReviewThreads(sanitized);
        if (normalizedJson != null) {
            return normalizedJson;
        }
        return truncateFixBatchReviewThreadsToValidatedObject(sanitized);
    }

    private String tryTrimParsedFixBatchReviewThreads(String sanitized) {
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(sanitized);
            if (!(parsed instanceof ObjectNode payload)) {
                return DEFAULT_FIX_BATCH_REVIEW_THREADS;
            }
            JsonNode threadsNode = payload.get("threads");
            if (!(threadsNode instanceof ArrayNode threads)) {
                return DEFAULT_FIX_BATCH_REVIEW_THREADS;
            }

            ObjectNode limitedPayload = payload.deepCopy();
            ArrayNode limitedThreads = threads.deepCopy();
            limitedPayload.set("threads", limitedThreads);

            while (true) {
                String serialized = serializeIfWithinFixBatchReviewThreadsLimit(limitedPayload);
                if (serialized != null) {
                    return serialized;
                }
                if (limitedThreads.isEmpty()) {
                    return DEFAULT_FIX_BATCH_REVIEW_THREADS;
                }
                limitedThreads.remove(limitedThreads.size() - 1);
            }
        }
        catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String truncateFixBatchReviewThreadsToValidatedObject(String sanitized) {
        int searchIndex = Math.min(sanitized.length(), MAX_FIX_BATCH_REVIEW_THREADS_LENGTH) - 1;
        while (searchIndex >= 0) {
            int closingBraceIndex = sanitized.lastIndexOf('}', searchIndex);
            if (closingBraceIndex < 0) {
                break;
            }

            String candidate = sanitized.substring(0, closingBraceIndex + 1).trim();
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(candidate);
                String serialized = serializeIfWithinFixBatchReviewThreadsLimit(parsed);
                if (serialized != null) {
                    return serialized;
                }
            }
            catch (JsonProcessingException ignored) {
                // Keep scanning backward for an earlier complete JSON object.
            }
            searchIndex = closingBraceIndex - 1;
        }
        return DEFAULT_FIX_BATCH_REVIEW_THREADS;
    }

    private String serializeIfWithinFixBatchReviewThreadsLimit(JsonNode payload) throws JsonProcessingException {
        String serialized = OBJECT_MAPPER.writeValueAsString(payload);
        return serialized.length() <= MAX_FIX_BATCH_REVIEW_THREADS_LENGTH ? serialized : null;
    }

    protected Map<String, Object> baseTemplateVariables(ProgrammingExercise exercise, String repositoryStructure, String consistencyIssues, String fixBatchReviewThreads) {
        if (exercise == null) {
            throw new IllegalArgumentException("exercise must not be null");
        }
        if (repositoryStructure == null) {
            throw new IllegalArgumentException("repositoryStructure must not be null");
        }
        if (consistencyIssues == null) {
            throw new IllegalArgumentException("consistencyIssues must not be null");
        }
        if (fixBatchReviewThreads == null) {
            throw new IllegalArgumentException("fixBatchReviewThreads must not be null");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("programmingLanguage", exercise.getProgrammingLanguage());
        variables.put("repositoryStructure", repositoryStructure);
        variables.put("consistencyIssues", consistencyIssues);
        variables.put(FIX_BATCH_REVIEW_THREADS_TEMPLATE_VARIABLE, fixBatchReviewThreads);
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
            ResponseEntity<ChatResponse, CodeGenerationResponseDTO> responseResult = chatClient.prompt().user(rendered).call().responseEntity(CodeGenerationResponseDTO.class);
            ChatResponse chatResponse = responseResult.getResponse();
            storeTokenUsage(user, exercise, courseId, prompt, chatResponse);
            CodeGenerationResponseDTO response = responseResult.entity();
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
        return false;
    }

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

    private static int sanitizeTokenCount(Integer tokenCount) {
        if (tokenCount == null) {
            return 0;
        }
        return Math.max(tokenCount, 0);
    }

    private String buildPipelineId(String prompt) {
        String promptId = extractPromptId(prompt);
        return String.join("_", "HYPERION", "CODE", "GENERATION", getRepositoryType().name(), promptId);
    }

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
     * @param user                  the user requesting code generation
     * @param exercise              the programming exercise to analyze
     * @param courseId              the resolved course id for telemetry attribution
     * @param previousBuildLogs     build failure logs from previous attempts for correction
     * @param repositoryStructure   tree-format representation of current repository structure
     * @param consistencyIssues     formatted consistency issues to inform the generation prompts
     * @param fixBatchReviewThreads selected review-thread payload to inform the generation prompts
     * @return AI response containing the solution plan
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, Long courseId, String previousBuildLogs, String repositoryStructure,
            String consistencyIssues, String fixBatchReviewThreads) throws NetworkingException;

    /**
     * Defines the file structure and organization for the solution.
     * Second step in the 4-step generation pipeline.
     *
     * @param user                  the user requesting code generation
     * @param exercise              the programming exercise to structure
     * @param courseId              the resolved course id for telemetry attribution
     * @param solutionPlan          the high-level solution plan from step 1
     * @param repositoryStructure   tree-format representation of current repository structure
     * @param consistencyIssues     formatted consistency issues to inform the generation prompts
     * @param fixBatchReviewThreads selected review-thread payload to inform the generation prompts
     * @return AI response containing file structure definitions
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan, String repositoryStructure,
            String consistencyIssues, String fixBatchReviewThreads) throws NetworkingException;

    /**
     * Generates class definitions and method signatures.
     * Third step in the 4-step generation pipeline.
     *
     * @param user                  the user requesting code generation
     * @param exercise              the programming exercise to create headers for
     * @param courseId              the resolved course id for telemetry attribution
     * @param solutionPlan          the high-level solution plan from step 1
     * @param repositoryStructure   tree-format representation of current repository structure
     * @param consistencyIssues     formatted consistency issues to inform the generation prompts
     * @param fixBatchReviewThreads selected review-thread payload to inform the generation prompts
     * @return AI response containing class and method headers
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan,
            String repositoryStructure, String consistencyIssues, String fixBatchReviewThreads) throws NetworkingException;

    /**
     * Generates the core implementation logic for the solution.
     * Fourth and final step in the 4-step generation pipeline.
     *
     * @param user                  the user requesting code generation
     * @param exercise              the programming exercise to implement
     * @param courseId              the resolved course id for telemetry attribution
     * @param solutionPlan          the high-level solution plan from step 1
     * @param repositoryStructure   tree-format representation of current repository structure
     * @param consistencyIssues     formatted consistency issues to inform the generation prompts
     * @param fixBatchReviewThreads selected review-thread payload to inform the generation prompts
     * @return AI response containing complete implementation with generated files
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan, String repositoryStructure,
            String consistencyIssues, String fixBatchReviewThreads) throws NetworkingException;

    /**
     * Returns the repository type that this strategy generates code for.
     *
     * @return the repository type (SOLUTION, TEMPLATE, or TESTS)
     */
    protected abstract RepositoryType getRepositoryType();
}
