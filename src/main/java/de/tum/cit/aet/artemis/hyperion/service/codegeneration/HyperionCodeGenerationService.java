package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

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
     * @return list of generated code files
     * @throws NetworkingException if AI service communication fails
     */
    public List<GeneratedFileDTO> generateCode(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure) throws NetworkingException {
        CodeGenerationResponseDTO solutionPlanResponse = generateSolutionPlan(user, exercise, previousBuildLogs, repositoryStructure);
        defineFileStructure(user, exercise, solutionPlanResponse.getSolutionPlan(), repositoryStructure);
        generateClassAndMethodHeaders(user, exercise, solutionPlanResponse.getSolutionPlan(), repositoryStructure);
        CodeGenerationResponseDTO coreLogicResponse = generateCoreLogic(user, exercise, solutionPlanResponse.getSolutionPlan(), repositoryStructure);

        return coreLogicResponse.getFiles();
    }

    /**
     * Handles communication with the AI chat client, including prompt rendering and error handling.
     * Renders template variables into prompts and manages AI service exceptions.
     * Uses {@link org.springframework.ai.chat.client.ChatClient.CallResponseSpec#responseEntity(Class)}
     * so parsed entity and raw {@link ChatResponse} are produced from the same terminal operation.
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
        try {
            ChatClient.CallResponseSpec responseSpec = chatClient.prompt().user(rendered).call();
            ResponseEntity<ChatResponse, CodeGenerationResponseDTO> responseEntity = responseSpec.responseEntity(CodeGenerationResponseDTO.class);
            ChatResponse chatResponse = responseEntity.getResponse();
            CodeGenerationResponseDTO response = responseEntity.getEntity();
            storeTokenUsage(user, exercise, prompt, chatResponse);
            return response;
        }
        catch (TransientAiException e) {
            throw new NetworkingException("Temporary AI service issue. Please retry.", e);
        }
        catch (NonTransientAiException e) {
            throw new NetworkingException("AI request failed due to configuration or input. Check model and request.", e);
        }
        catch (RuntimeException e) {
            throw new NetworkingException("AI response processing failed. Please retry.", e);
        }
    }

    /**
     * Persists token usage metadata for a successful generation call.
     * <p>
     * Metadata null checks are intentional: providers or proxies can return valid content while omitting
     * usage metadata, and telemetry absence must not fail an otherwise successful code-generation workflow.
     *
     * @param user         user initiating the generation request
     * @param exercise     programming exercise context for token-usage attribution
     * @param prompt       prompt template identifier used for pipeline id generation
     * @param chatResponse raw chat response that may contain usage metadata
     */
    private void storeTokenUsage(User user, ProgrammingExercise exercise, String prompt, ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return;
        }

        try {
            Usage usage = chatResponse.getMetadata().getUsage();
            int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
            LLMRequest llmRequest = llmTokenUsageService.buildLLMRequest(chatResponse.getMetadata().getModel(), promptTokens, completionTokens, buildPipelineId(prompt));
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
     * @return AI response containing the solution plan
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure)
            throws NetworkingException;

    /**
     * Defines the file structure and organization for the solution.
     * Second step in the 4-step generation pipeline.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to structure
     * @param solutionPlan        the high-level solution plan from step 1
     * @param repositoryStructure tree-format representation of current repository structure
     * @return AI response containing file structure definitions
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure)
            throws NetworkingException;

    /**
     * Generates class definitions and method signatures.
     * Third step in the 4-step generation pipeline.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to create headers for
     * @param solutionPlan        the high-level solution plan from step 1
     * @param repositoryStructure tree-format representation of current repository structure
     * @return AI response containing class and method headers
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure)
            throws NetworkingException;

    /**
     * Generates the core implementation logic for the solution.
     * Fourth and final step in the 4-step generation pipeline.
     *
     * @param user                the user requesting code generation
     * @param exercise            the programming exercise to implement
     * @param solutionPlan        the high-level solution plan from step 1
     * @param repositoryStructure tree-format representation of current repository structure
     * @return AI response containing complete implementation with generated files
     * @throws NetworkingException if AI service communication fails
     */
    protected abstract CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure)
            throws NetworkingException;

    /**
     * Returns the repository type that this strategy generates code for.
     *
     * @return the repository type (SOLUTION, TEMPLATE, or TESTS)
     */
    protected abstract RepositoryType getRepositoryType();
}
