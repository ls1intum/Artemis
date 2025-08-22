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
 * Code generation strategy for creating template/starter code for programming exercises.
 * Generates scaffolding code that provides students with a structured starting point while
 * removing implementation details, based on complete solution code analysis.
 */
@Service("templateRepositoryStrategy")
public class TemplateRepository extends CodeGenerationStrategy {

    private final SolutionRepository solutionRepository;

    /**
     * Creates a new TemplateRepository with required dependencies.
     *
     * @param programmingExerciseRepository repository for accessing programming exercise data
     * @param chatClient                    AI chat client for generating template code
     * @param templates                     service for rendering prompt templates
     * @param solutionRepository            solution repository for generating reference solution code
     */
    public TemplateRepository(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, PromptTemplateService templates,
            SolutionRepository solutionRepository) {
        super(programmingExerciseRepository, chatClient, templates);
        this.solutionRepository = solutionRepository;
    }

    @Override
    protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs) throws NetworkingException {
        return solutionRepository.generateSolutionPlan(user, exercise, previousBuildLogs);
    }

    @Override
    protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        return solutionRepository.defineFileStructure(user, exercise, solutionPlan);
    }

    @Override
    protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        return solutionRepository.generateClassAndMethodHeaders(user, exercise, solutionPlan);
    }

    @Override
    protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException {
        var solutionCode = solutionRepository.generateCoreLogic(user, exercise, solutionPlan);
        var templateVariables = Map.<String, Object>of("solutionCode", solutionCode.getFiles());
        return callChatClient("/prompts/hyperion/template/4_logic.st", templateVariables);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.TEMPLATE;
    }
}
