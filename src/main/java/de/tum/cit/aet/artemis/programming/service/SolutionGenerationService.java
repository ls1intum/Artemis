package de.tum.cit.aet.artemis.programming.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.hyperion.HyperionConnectorService;
import de.tum.cit.aet.artemis.programming.service.hyperion.dto.HyperionRepositoryFile;
import de.tum.cit.aet.artemis.programming.service.hyperion.dto.HyperionSolutionGenerationRequest;
import de.tum.cit.aet.artemis.programming.service.hyperion.dto.HyperionSolutionGenerationResponse;

/**
 * Service for generating solution repositories using AI
 */
@Service
@ConditionalOnProperty(name = "artemis.hyperion.enabled", havingValue = "true")
@Lazy
public class SolutionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SolutionGenerationService.class);

    private final HyperionConnectorService hyperionConnectorService;

    private final GitService gitService;

    private final ProgrammingExerciseRepositoryService repositoryService;

    public SolutionGenerationService(HyperionConnectorService hyperionConnectorService, GitService gitService, ProgrammingExerciseRepositoryService repositoryService) {
        this.hyperionConnectorService = hyperionConnectorService;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    /**
     * Generates a solution repository for the given programming exercise using AI
     */
    public void generateSolutionRepository(ProgrammingExercise programmingExercise, User user) throws SolutionGenerationException {
        try {
            log.info("Starting solution generation for exercise: {}", programmingExercise.getTitle());

            HyperionSolutionGenerationRequest request = createRequestFromExercise(programmingExercise);

            HyperionSolutionGenerationResponse response = hyperionConnectorService.generateSolution(request);

            updateSolutionRepository(programmingExercise, response.repository().files(), user);

            log.info("Successfully generated solution for exercise: {}", programmingExercise.getTitle());

        }
        catch (HyperionConnectorService.HyperionConnectorException e) {
            log.error("Failed to generate solution for exercise {}: {}", programmingExercise.getTitle(), e.getMessage());
            throw new SolutionGenerationException("Failed to generate solution from Hyperion service", e);
        }
        catch (IOException | GitAPIException e) {
            log.error("Failed to update solution repository for exercise {}: {}", programmingExercise.getTitle(), e.getMessage());
            throw new SolutionGenerationException("Failed to update solution repository", e);
        }
    }

    /**
     * Creates a Hyperion request from the programming exercise
     */
    private HyperionSolutionGenerationRequest createRequestFromExercise(ProgrammingExercise programmingExercise) {
        var boundaryConditions = new HyperionSolutionGenerationRequest.BoundaryConditions(programmingExercise.getProgrammingLanguage().toString().toLowerCase(),
                programmingExercise.getProjectType() != null ? programmingExercise.getProjectType().toString().toLowerCase() : "plain", "medium", // Default difficulty - could be
                                                                                                                                                  // extracted from exercise
                                                                                                                                                  // settings
                programmingExercise.getMaxPoints() != null ? programmingExercise.getMaxPoints().intValue() : 10,
                programmingExercise.getBonusPoints() != null ? programmingExercise.getBonusPoints().intValue() : 0,
                List.of("Use only standard library functions", "Follow clean code principles"));

        var problemStatement = new HyperionSolutionGenerationRequest.ProblemStatement(programmingExercise.getTitle(), programmingExercise.getShortName(),
                programmingExercise.getProblemStatement());

        return new HyperionSolutionGenerationRequest(boundaryConditions, problemStatement);
    }

    /**
     * Updates the solution repository with generated files
     */
    private void updateSolutionRepository(ProgrammingExercise programmingExercise, List<HyperionRepositoryFile> files, User user) throws IOException, GitAPIException {
        Repository solutionRepo = gitService.getOrCheckoutRepository(programmingExercise.getVcsSolutionRepositoryUri(), true);

        gitService.resetToOriginHead(solutionRepo);
        gitService.pullIgnoreConflicts(solutionRepo);

        Path repoPath = solutionRepo.getLocalPath().toRealPath();

        // Write all generated files
        for (HyperionRepositoryFile file : files) {
            Path filePath = repoPath.resolve(file.path());

            // Create parent directories if they don't exist
            Files.createDirectories(filePath.getParent());

            // Write file content
            Files.writeString(filePath, file.content(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("Generated file: {}", file.path());
        }

        repositoryService.commitAndPushRepository(solutionRepo, "AI-generated solution repository", false, user);

        log.info("Updated solution repository with {} generated files", files.size());
    }

    /**
     * Exception thrown when solution generation fails
     */
    public static class SolutionGenerationException extends Exception {

        public SolutionGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
