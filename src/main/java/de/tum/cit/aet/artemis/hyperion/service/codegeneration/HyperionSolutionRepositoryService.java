package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Code generation strategy for creating solution code for programming exercises.
 * Generates complete, working implementations that solve the given programming problem
 * using AI-powered analysis of problem statements and requirements.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionSolutionRepositoryService extends HyperionCodeGenerationService {

    public HyperionSolutionRepositoryService(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, HyperionPromptTemplateService templates) {
        super(programmingExerciseRepository, chatClient, templates);
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure)
            throws NetworkingException {
        var templateVariables = Map.<String, Object>of("problemStatement", exercise.getProblemStatement(), "programmingLanguage", exercise.getProgrammingLanguage(),
                "previousBuildLogs", previousBuildLogs != null ? previousBuildLogs : "", "repositoryStructure", repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/solution/1_plan.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure) throws NetworkingException {
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "programmingLanguage", exercise.getProgrammingLanguage(), "repositoryStructure",
                repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/solution/2_file_structure.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure)
            throws NetworkingException {
        var fileStructure = defineFileStructure(user, exercise, solutionPlan, repositoryStructure);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "fileStructure", fileStructure.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage(), "repositoryStructure", repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/solution/3_headers.st", templateVariables);
    }

    @Override
    protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure) throws NetworkingException {
        var headers = generateClassAndMethodHeaders(user, exercise, solutionPlan, repositoryStructure);
        var templateVariables = Map.<String, Object>of("solutionPlan", solutionPlan, "filesWithHeaders", headers.getFiles(), "programmingLanguage",
                exercise.getProgrammingLanguage(), "repositoryStructure", repositoryStructure != null ? repositoryStructure : "");
        return callChatClient("/prompts/hyperion/solution/4_logic.st", templateVariables);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.SOLUTION;
    }
}
