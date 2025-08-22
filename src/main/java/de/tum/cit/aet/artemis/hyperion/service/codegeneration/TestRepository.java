package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.PromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Code generation strategy for creating comprehensive test suites for programming exercises.
 * Generates unit tests, integration tests, and edge case scenarios by analyzing the solution code
 * and problem requirements to ensure thorough testing coverage.
 */
@Service("testRepositoryStrategy")
public class TestRepository extends CodeGenerationStrategy {

    private final SolutionRepository solutionRepository;

    /**
     * Creates a new TestRepository with required dependencies.
     *
     * @param programmingExerciseRepository repository for accessing programming exercise data
     * @param chatClient                    AI chat client for generating test code
     * @param templates                     service for rendering prompt templates
     * @param solutionRepository            solution repository for generating reference solution code
     */
    public TestRepository(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, PromptTemplateService templates,
            SolutionRepository solutionRepository) {
        super(programmingExerciseRepository, chatClient, templates);
        this.solutionRepository = solutionRepository;
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs) throws NetworkingException {
        var solution = solutionRepository.generateCode(user, exercise, previousBuildLogs);
        var templateVariables = Map.<String, Object>of("problemStatement", exercise.getProblemStatement(), "solutionCode", solution, "programmingLanguage",
                exercise.getProgrammingLanguage(), "previousBuildLogs", previousBuildLogs != null ? previousBuildLogs : "");
        return callChatClient("/prompts/hyperion/test/1_plan.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "programmingLanguage", exercise.getProgrammingLanguage());
        return callChatClient("/prompts/hyperion/test/2_file_structure.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        var fileStructure = defineFileStructure(user, exercise, solutionPlan);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "fileStructure", fileStructure.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage());
        return callChatClient("/prompts/hyperion/test/3_headers.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        var headers = generateClassAndMethodHeaders(user, exercise, solutionPlan);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "filesWithHeaders", headers.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage());
        return callChatClient("/prompts/hyperion/test/4_logic.st", templateVariables);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.TESTS;
    }
}
