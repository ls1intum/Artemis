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
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@Service("testRepositoryStrategy")
public class TestRepository extends CodeGenerationStrategy {

    private final SolutionRepository solutionRepository;

    public TestRepository(RepositoryService repositoryService, ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, PromptTemplateService templates,
            SolutionRepository solutionRepository, GitService gitService) {
        super(repositoryService, programmingExerciseRepository, chatClient, templates, gitService);
        this.solutionRepository = solutionRepository;
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise) throws NetworkingException {
        var solution = solutionRepository.generateCode(user, exercise);
        var templateVariables = Map.<String, Object>of("problemStatement", exercise.getProblemStatement(), "solutionCode", solution.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage());
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
