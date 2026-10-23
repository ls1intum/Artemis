package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Code generation strategy for creating solution code for programming exercises.
 * Generates complete, working implementations that solve the given programming problem
 * using AI-powered analysis of problem statements and requirements.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionSolutionRepositoryService extends HyperionCodeGenerationService {

    private final GitService gitService;

    private final HyperionProgrammingExerciseContextRendererService contextRenderer;

    public HyperionSolutionRepositoryService(ChatClient chatClient, HyperionPromptTemplateService templates, GitService gitService,
            HyperionProgrammingExerciseContextRendererService contextRenderer, LLMTokenUsageService llmTokenUsageService) {
        super(chatClient, templates, llmTokenUsageService);
        this.gitService = gitService;
        this.contextRenderer = contextRenderer;
    }

    @Override
    protected String testContext(ProgrammingExercise exercise) {
        return contextRenderer.getExistingTestCode(exercise, gitService);
    }

    @Override
    protected RepositoryType getRepositoryType() {
        return RepositoryType.SOLUTION;
    }
}
