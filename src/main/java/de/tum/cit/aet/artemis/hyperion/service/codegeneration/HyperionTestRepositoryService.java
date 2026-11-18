package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Code generation strategy for creating comprehensive test suites for programming exercises.
 * Generates unit tests, integration tests, and edge case scenarios by analyzing the solution code
 * and problem requirements to ensure thorough testing coverage.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionTestRepositoryService extends HyperionCodeGenerationService {

    private final GitService gitService;

    private final HyperionProgrammingExerciseContextRendererService contextRenderer;

    /**
     * Creates a new TestRepository with required dependencies.
     *
     * @param chatClient           AI chat client for generating test code
     * @param templates            service for rendering prompt templates
     * @param gitService           service for Git operations
     * @param contextRenderer      service for rendering programming exercise context
     * @param llmTokenUsageService service for persisting LLM token usage telemetry
     */
    public HyperionTestRepositoryService(ChatClient chatClient, HyperionPromptTemplateService templates, GitService gitService,
            HyperionProgrammingExerciseContextRendererService contextRenderer, LLMTokenUsageService llmTokenUsageService) {
        super(chatClient, templates, llmTokenUsageService);
        this.gitService = gitService;
        this.contextRenderer = contextRenderer;
    }

    @Override
    protected String solutionContext(ProgrammingExercise exercise) throws NetworkingException {
        return contextRenderer.getExistingSolutionCode(exercise, gitService);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.TESTS;
    }
}
