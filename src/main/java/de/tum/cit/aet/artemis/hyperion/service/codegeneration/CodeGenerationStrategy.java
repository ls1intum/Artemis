package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFile;
import de.tum.cit.aet.artemis.hyperion.service.PromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@Service("hyperionCodeGenerationService")
@Lazy
@Profile(PROFILE_HYPERION)
public abstract class CodeGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationStrategy.class);

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ChatClient chatClient;

    private final PromptTemplateService templates;

    private final GitService gitService;

    public CodeGenerationStrategy(RepositoryService repositoryService, ProgrammingExerciseRepository programmingExerciseRepository,
            @Autowired(required = false) ChatClient chatClient, PromptTemplateService templates, GitService gitService) {
        this.repositoryService = repositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
        this.gitService = gitService;
    }

    public CodeGenerationResponseDTO generateCode(User user, ProgrammingExercise exercise) throws NetworkingException {
        var solutionPlanResponse = generateSolutionPlan(user, exercise);
        var fileStructureResponse = defineFileStructure(user, exercise, solutionPlanResponse.getSolutionPlan());
        var classAndMethodHeadersResponse = generateClassAndMethodHeaders(user, exercise, solutionPlanResponse.getSolutionPlan());
        var coreLogicResponse = generateCoreLogic(user, exercise, solutionPlanResponse.getSolutionPlan());

        commitFilesToRepository(user, exercise, coreLogicResponse.getFiles());

        return coreLogicResponse;
    }

    private void commitFilesToRepository(User user, ProgrammingExercise exercise, List<GeneratedFile> files) {
        try {
            var repositoryUri = exercise.getRepositoryURL(getRepositoryType());
            String branch = gitService.getDefaultBranchOfRepository(repositoryUri);
            Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, branch, false);

            repositoryService.deleteAllContentInRepository(repository);

            for (GeneratedFile file : files) {
                InputStream inputStream = new ByteArrayInputStream(file.content().getBytes(StandardCharsets.UTF_8));
                repositoryService.createFile(repository, file.path(), inputStream);
            }
            repositoryService.commitChanges(repository, user);
        }
        catch (IOException | GitAPIException e) {
            log.error("Failed to commit generated files to repository for exercise {}", exercise.getId(), e);
            // Optionally rethrow as a custom exception
        }
    }

    protected CodeGenerationResponseDTO callChatClient(String prompt, Map<String, Object> templateVariables) throws NetworkingException {
        String rendered = templates.render(prompt, templateVariables);
        try {
            var response = chatClient.prompt().user(rendered).call().entity(CodeGenerationResponseDTO.class);
            return response;
        }
        catch (TransientAiException e) {
            throw new NetworkingException("Temporary AI service issue. Please retry.", e);
        }
        catch (NonTransientAiException e) {
            throw new NetworkingException("AI request failed due to configuration or input. Check model and request.", e);
        }
    }

    protected abstract CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise) throws NetworkingException;

    protected abstract CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException;

    protected abstract CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException;

    protected abstract CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan) throws NetworkingException;

    protected abstract RepositoryType getRepositoryType();
}
