package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.PromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;

/**
 * Code generation strategy for creating comprehensive test suites for programming exercises.
 * Generates unit tests, integration tests, and edge case scenarios by analyzing the solution code
 * and problem requirements to ensure thorough testing coverage.
 */
@Service("testRepositoryStrategy")
@Lazy
@Conditional(HyperionEnabled.class)
public class TestRepository extends CodeGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(TestRepository.class);

    private final GitService gitService;

    /**
     * Creates a new TestRepository with required dependencies.
     *
     * @param programmingExerciseRepository repository for accessing programming exercise data
     * @param chatClient                    AI chat client for generating test code
     * @param templates                     service for rendering prompt templates
     * @param gitService                    service for Git operations
     */
    public TestRepository(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, PromptTemplateService templates, GitService gitService) {
        super(programmingExerciseRepository, chatClient, templates);
        this.gitService = gitService;
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure)
            throws NetworkingException {
        // Get existing solution code from repository instead of generating new code
        String solutionCode = getExistingSolutionCode(exercise);
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

    /**
     * Reads existing solution code from the solution repository.
     * This method retrieves source files from the solution repository
     * and concatenates their content as a string for test generation.
     *
     * @param exercise the programming exercise
     * @return concatenated content of all solution source files
     * @throws NetworkingException if repository access fails
     */
    private String getExistingSolutionCode(ProgrammingExercise exercise) throws NetworkingException {
        try {
            var solutionRepositoryUri = exercise.getVcsSolutionRepositoryUri();
            if (solutionRepositoryUri == null) {
                log.warn("No solution repository URI found for exercise {}, using problem statement only", exercise.getId());
                return "No solution code available. Please refer to the problem statement.";
            }

            Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepositoryUri, true, "main", false);
            if (solutionRepository == null) {
                log.warn("Failed to access solution repository for exercise {}", exercise.getId());
                return "Solution repository not accessible. Please refer to the problem statement.";
            }

            // Read all Java files from the solution repository
            Path repositoryPath = solutionRepository.getLocalPath();
            StringBuilder solutionCode = new StringBuilder();

            try {
                Files.walk(repositoryPath).filter(path -> path.toString().endsWith(".java")).filter(path -> path.toString().contains("src/")).filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path);
                                solutionCode.append("// File: ").append(repositoryPath.relativize(path)).append("\n");
                                solutionCode.append(content).append("\n\n");
                            }
                            catch (IOException e) {
                                log.warn("Failed to read file {}: {}", path, e.getMessage());
                            }
                        });
            }
            catch (IOException e) {
                log.error("Failed to scan solution repository for exercise {}: {}", exercise.getId(), e.getMessage());
                return "Failed to read solution code. Please refer to the problem statement.";
            }

            return solutionCode.length() > 0 ? solutionCode.toString() : "No solution code found. Please refer to the problem statement.";

        }
        catch (Exception e) {
            log.error("Error accessing solution repository for exercise {}: {}", exercise.getId(), e.getMessage(), e);
            throw new NetworkingException("Failed to access solution repository", e);
        }
    }
}
