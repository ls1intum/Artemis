package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;

/**
 * Code generation strategy for creating template/starter code for programming exercises.
 * Generates scaffolding code that provides students with a structured starting point while
 * removing implementation details, based on complete solution code analysis.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionTemplateRepositoryService extends HyperionCodeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionTemplateRepositoryService.class);

    private final GitService gitService;

    private final HyperionProgrammingExerciseContextRendererService contextRenderer;

    /**
     * Creates a new TemplateRepository with required dependencies.
     *
     * @param programmingExerciseRepository repository for accessing programming exercise data
     * @param chatClient                    AI chat client for generating template code
     * @param templates                     service for rendering prompt templates
     * @param gitService                    service for Git operations to read solution code
     * @param contextRenderer               service for rendering programming exercise context
     */
    public HyperionTemplateRepositoryService(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, HyperionPromptTemplateService templates,
            GitService gitService, HyperionProgrammingExerciseContextRendererService contextRenderer) {
        super(programmingExerciseRepository, chatClient, templates);
        this.gitService = gitService;
        this.contextRenderer = contextRenderer;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     * @throws NetworkingException      if repository access fails or AI service communication fails
     */
    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure,
            String consistencyIssues) throws NetworkingException {
        String solutionCode = contextRenderer.getExistingSolutionCode(exercise, gitService);
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("problemStatement", exercise.getProblemStatement());
        templateVariables.put("solutionCode", solutionCode);
        templateVariables.put("previousBuildLogs", previousBuildLogs != null ? previousBuildLogs : "");
        return callChatClient("/prompts/hyperion/template/1_plan.st", templateVariables);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     * @throws NetworkingException      if AI service communication fails
     */
    @Override
    protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure, String consistencyIssues)
            throws NetworkingException {
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("solutionPlan", solutionPlan);
        return callChatClient("/prompts/hyperion/template/2_file_structure.st", templateVariables);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     * @throws NetworkingException      if AI service communication fails
     */
    @Override
    protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure,
            String consistencyIssues) throws NetworkingException {
        CodeGenerationResponseDTO fileStructure = defineFileStructure(user, exercise, solutionPlan, repositoryStructure, consistencyIssues);
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("solutionPlan", solutionPlan);
        templateVariables.put("fileStructure", fileStructure.getFiles());
        return callChatClient("/prompts/hyperion/template/3_headers.st", templateVariables);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     * @throws NetworkingException      if AI service communication fails
     */
    @Override
    protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure, String consistencyIssues)
            throws NetworkingException {
        CodeGenerationResponseDTO headers = generateClassAndMethodHeaders(user, exercise, solutionPlan, repositoryStructure, consistencyIssues);
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("solutionPlan", solutionPlan);
        templateVariables.put("filesWithHeaders", headers.getFiles());
        return callChatClient("/prompts/hyperion/template/4_logic.st", templateVariables);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.TEMPLATE;
    }

}
