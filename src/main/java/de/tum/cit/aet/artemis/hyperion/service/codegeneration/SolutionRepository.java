package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.Map;

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

@Service("solutionRepositoryStrategy")
public class SolutionRepository extends CodeGenerationStrategy {

    public SolutionRepository(RepositoryService repositoryService, ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient,
            PromptTemplateService templates, GitService gitService) {
        super(repositoryService, programmingExerciseRepository, chatClient, templates, gitService);
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise) throws NetworkingException {
        var templateVariables = Map.<String, Object>of("problemStatement", exercise.getProblemStatement(), "programmingLanguage", exercise.getProgrammingLanguage());
        return callChatClient("/prompts/hyperion/solution/1_plan.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "programmingLanguage", exercise.getProgrammingLanguage());
        return callChatClient("/prompts/hyperion/solution/2_file_structure.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        var fileStructure = defineFileStructure(user, exercise, solutionPlan);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "fileStructure", fileStructure.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage());
        return callChatClient("/prompts/hyperion/solution/3_headers.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        var headers = generateClassAndMethodHeaders(user, exercise, solutionPlan);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "filesWithHeaders", headers.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage());
        return callChatClient("/prompts/hyperion/solution/4_logic.st", templateVariables);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.SOLUTION;
    }
}
