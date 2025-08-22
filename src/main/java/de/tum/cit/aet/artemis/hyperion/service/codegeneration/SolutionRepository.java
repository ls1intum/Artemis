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
 * Code generation strategy for creating solution code for programming exercises.
 * Generates complete, working implementations that solve the given programming problem
 * using AI-powered analysis of problem statements and requirements.
 */
@Service("solutionRepositoryStrategy")
public class SolutionRepository extends CodeGenerationStrategy {

    public SolutionRepository(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, PromptTemplateService templates) {
        super(programmingExerciseRepository, chatClient, templates);
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs) throws NetworkingException {
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
