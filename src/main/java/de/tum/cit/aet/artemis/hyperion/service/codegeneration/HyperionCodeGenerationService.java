package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
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

    public HyperionCodeGenerationService(ProgrammingExerciseRepository programmingExerciseRepository, @Autowired(required = false) ChatClient chatClient,
            HyperionPromptTemplateService templates) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
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
     * @param prompt            the prompt template path to render
     * @param templateVariables variables to substitute in the prompt template
     * @return the AI response containing generated content
     * @throws NetworkingException if AI service communication fails
     */
    protected CodeGenerationResponseDTO callChatClient(String prompt, Map<String, Object> templateVariables) throws NetworkingException {
        String rendered = templates.renderObject(prompt, templateVariables);
        try {
            CodeGenerationResponseDTO response = chatClient.prompt().user(rendered).call().entity(CodeGenerationResponseDTO.class);
            return response;
        }
        catch (TransientAiException e) {
            throw new NetworkingException("Temporary AI service issue. Please retry.", e);
        }
        catch (NonTransientAiException e) {
            throw new NetworkingException("AI request failed due to configuration or input. Check model and request.", e);
        }
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
