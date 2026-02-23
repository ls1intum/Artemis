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

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     */
    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure,
            String consistencyIssues) throws NetworkingException {
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("problemStatement", exercise.getProblemStatement());
        templateVariables.put("previousBuildLogs", previousBuildLogs != null ? previousBuildLogs : "");
        return callChatClient("/prompts/hyperion/solution/1_plan.st", templateVariables);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     */
    @Override
    protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure, String consistencyIssues)
            throws NetworkingException {
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("solutionPlan", solutionPlan);
        return callChatClient("/prompts/hyperion/solution/2_file_structure.st", templateVariables);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     */
    @Override
    protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure,
            String consistencyIssues) throws NetworkingException {
        CodeGenerationResponseDTO fileStructure = defineFileStructure(user, exercise, solutionPlan, repositoryStructure, consistencyIssues);
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("solutionPlan", solutionPlan);
        templateVariables.put("fileStructure", fileStructure.getFiles());
        return callChatClient("/prompts/hyperion/solution/3_headers.st", templateVariables);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code exercise}, {@code repositoryStructure}, or {@code consistencyIssues} is {@code null}
     */
    @Override
    protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure, String consistencyIssues)
            throws NetworkingException {
        CodeGenerationResponseDTO headers = generateClassAndMethodHeaders(user, exercise, solutionPlan, repositoryStructure, consistencyIssues);
        Map<String, Object> templateVariables = baseTemplateVariables(exercise, repositoryStructure, consistencyIssues);
        templateVariables.put("solutionPlan", solutionPlan);
        templateVariables.put("filesWithHeaders", headers.getFiles());
        return callChatClient("/prompts/hyperion/solution/4_logic.st", templateVariables);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.SOLUTION;
    }
}
