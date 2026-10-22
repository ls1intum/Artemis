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
 * Code generation strategy for creating template/starter code for programming exercises.
 * Generates scaffolding code that provides students with a structured starting point while
 * removing implementation details, based on complete solution code analysis.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionTemplateRepositoryService extends HyperionCodeGenerationService {

    private final GitService gitService;

    private final HyperionProgrammingExerciseContextRendererService contextRenderer;

    /**
     * Creates a new TemplateRepository with required dependencies.
     *
     * @param chatClient           AI chat client for generating template code
     * @param templates            service for rendering prompt templates
     * @param gitService           service for Git operations to read solution code
     * @param contextRenderer      service for rendering programming exercise context
     * @param llmTokenUsageService service for persisting LLM token usage telemetry
     */
    public HyperionTemplateRepositoryService(ChatClient chatClient, HyperionPromptTemplateService templates, GitService gitService,
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
        return RepositoryType.TEMPLATE;
    }

}
