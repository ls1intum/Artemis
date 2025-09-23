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
 * Code generation strategy for creating comprehensive test suites for programming exercises.
 * Generates unit tests, integration tests, and edge case scenarios by analyzing the solution code
 * and problem requirements to ensure thorough testing coverage.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionTestRepositoryService extends HyperionCodeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionTestRepositoryService.class);

    private final GitService gitService;

    private final HyperionProgrammingExerciseContextRendererService contextRenderer;

    /**
     * Creates a new TestRepository with required dependencies.
     *
     * @param programmingExerciseRepository repository for accessing programming exercise data
     * @param chatClient                    AI chat client for generating test code
     * @param templates                     service for rendering prompt templates
     * @param gitService                    service for Git operations
     * @param contextRenderer               service for rendering programming exercise context
     */
    public HyperionTestRepositoryService(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, HyperionPromptTemplateService templates,
            GitService gitService, HyperionProgrammingExerciseContextRendererService contextRenderer) {
        super(programmingExerciseRepository, chatClient, templates);
        this.gitService = gitService;
        this.contextRenderer = contextRenderer;
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure)
            throws NetworkingException {
        // Get existing solution code from repository instead of generating new code
        String solutionCode = contextRenderer.getExistingSolutionCode(exercise, gitService);
        var templateVariables = Map.<String, Object>of("problemStatement", exercise.getProblemStatement(), "solutionCode", solutionCode, "programmingLanguage",
                exercise.getProgrammingLanguage(), "previousBuildLogs", previousBuildLogs != null ? previousBuildLogs : "", "repositoryStructure",
                repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/test/1_plan.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure) throws NetworkingException {
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "programmingLanguage", exercise.getProgrammingLanguage(), "repositoryStructure",
                repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/test/2_file_structure.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure)
            throws NetworkingException {
        var fileStructure = defineFileStructure(user, exercise, solutionPlan, repositoryStructure);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "fileStructure", fileStructure.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage(), "repositoryStructure", repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/test/3_headers.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure) throws NetworkingException {
        var headers = generateClassAndMethodHeaders(user, exercise, solutionPlan, repositoryStructure);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "filesWithHeaders", headers.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage(), "repositoryStructure", repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/test/4_logic.st", templateVariables);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.TESTS;
    }
}
