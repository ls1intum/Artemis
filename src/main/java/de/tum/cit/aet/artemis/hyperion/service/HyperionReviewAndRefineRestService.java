package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.client.model.ConsistencyCheckRequest;
import de.tum.cit.aet.artemis.hyperion.client.model.Repository;
import de.tum.cit.aet.artemis.hyperion.client.model.RepositoryFile;
import de.tum.cit.aet.artemis.hyperion.client.model.RewriteProblemStatementRequest;
import de.tum.cit.aet.artemis.hyperion.config.HyperionRestConfigurationProperties;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Service for reviewing and refining programming exercises using Hyperion REST API.
 *
 * Provides consistency checking and problem statement enhancement functionality
 * by integrating with external Hyperion service via REST endpoints.
 * Replaces the legacy gRPC-based implementation with modern HTTP communication.
 */
@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionReviewAndRefineRestService extends AbstractHyperionRestService {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineRestService.class);

    private final RepositoryService repositoryService;

    public HyperionReviewAndRefineRestService(@Qualifier("hyperionRestClient") RestClient restClient, RepositoryService repositoryService,
            HyperionRestConfigurationProperties hyperionProperties) {
        super(restClient, hyperionProperties);
        this.repositoryService = repositoryService;
    }

    /**
     * Performs consistency check on a programming exercise by analyzing the relationship
     * between problem statement, template code, solution code, and test cases.
     *
     * @param user     the user requesting the consistency check
     * @param exercise the programming exercise to analyze
     * @return formatted string containing identified inconsistencies and their descriptions
     * @throws NetworkingException if communication with Hyperion service fails
     */
    public String checkConsistency(User user, ProgrammingExercise exercise) throws NetworkingException {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());

        try {
            var request = buildConsistencyCheckRequest(exercise);
            var response = reviewAndRefineApi.consistencyCheckReviewAndRefineConsistencyCheckPost(request);

            // Extract issues and format them as a string
            String result = response.getIssues().stream().map(issue -> String.format("[%s] %s: %s", issue.getSeverity(), issue.getCategory(), issue.getDescription()))
                    .collect(Collectors.joining("\n"));

            log.info("Consistency check completed for exercise {}", exercise.getId());
            return result;

        }
        catch (Exception e) {
            handleRestException("consistency check for exercise " + exercise.getId(), e);
            return null; // unreachable due to exception
        }
    }

    /**
     * Enhances a problem statement by improving clarity, structure, and pedagogical value.
     * Uses Hyperion service to rewrite exercise descriptions with better formatting,
     * clearer instructions, and enhanced educational content.
     *
     * @param user             the user requesting the enhancement
     * @param problemStatement the original problem statement text to enhance
     * @return the enhanced problem statement with improved clarity and structure
     * @throws NetworkingException if communication with Hyperion service fails
     */
    public String rewriteProblemStatement(User user, String problemStatement) throws NetworkingException {
        log.info("Rewriting problem statement for user {}", user.getLogin());

        try {
            var request = new RewriteProblemStatementRequest().text(problemStatement);
            var response = reviewAndRefineApi.problemStatementRewriteReviewAndRefineProblemStatementRewritePost(request);

            log.info("Problem statement rewriting completed successfully for user {}", user.getLogin());
            return response.getRewrittenText();

        }
        catch (Exception e) {
            handleRestException("problem statement rewriting for user " + user.getLogin(), e);
            return null; // unreachable due to exception
        }
    }

    /**
     * Builds a consistency check request by extracting and structuring exercise data.
     * Collects exercise metadata, problem statement, and repository contents
     * into format expected by Hyperion API.
     */
    private ConsistencyCheckRequest buildConsistencyCheckRequest(ProgrammingExercise exercise) {
        try {
            var solutionRepo = buildRepository(exercise, RepositoryType.SOLUTION);
            var templateRepo = buildRepository(exercise, RepositoryType.TEMPLATE);
            var testRepo = buildRepository(exercise, RepositoryType.TESTS);

            return new ConsistencyCheckRequest().problemStatement(exercise.getProblemStatement() != null ? exercise.getProblemStatement() : "").solutionRepository(solutionRepo)
                    .templateRepository(templateRepo).testRepository(testRepo);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to build consistency check request", e);
        }
    }

    /**
     * Extracts repository contents and builds Repository object for API requests.
     * Retrieves all relevant files from the specified repository type,
     * applies content filtering, and structures data for Hyperion consumption.
     */
    private Repository buildRepository(ProgrammingExercise exercise, RepositoryType repositoryType) throws IOException {
        List<RepositoryFile> repositoryFiles;

        try {
            switch (repositoryType) {
                case TEMPLATE -> {
                    var templateParticipation = exercise.getTemplateParticipation();
                    repositoryFiles = getFilteredRepositoryFiles(templateParticipation);
                }
                case SOLUTION -> {
                    var solutionParticipation = exercise.getSolutionParticipation();
                    repositoryFiles = getFilteredRepositoryFiles(solutionParticipation);
                }
                case TESTS -> {
                    repositoryFiles = getRepositoryFiles(exercise.getVcsTestRepositoryUri());
                }
                default -> {
                    log.warn("Unknown repository type: {}", repositoryType);
                    repositoryFiles = List.of();
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to fetch repository contents for {} repository of exercise {}", repositoryType, exercise.getId(), e);
            repositoryFiles = List.of();
        }

        return new Repository().files(repositoryFiles);
    }

    /**
     * Helper method to get & checkout the repository contents for a participation.
     * Similar to PyrisDTOService implementation - filters by programming language if available.
     */
    private List<RepositoryFile> getFilteredRepositoryFiles(ProgrammingExerciseParticipation participation) {
        var language = participation.getProgrammingExercise().getProgrammingLanguage();
        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());

        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .map(entry -> new RepositoryFile().path(entry.getKey()).content(entry.getValue())).collect(Collectors.toList());
    }

    /**
     * Helper method to get repository files from VCS.
     */
    private List<RepositoryFile> getRepositoryFiles(VcsRepositoryUri repositoryUri) {
        var repositoryContents = getRepositoryContents(repositoryUri);

        return repositoryContents.entrySet().stream().map(entry -> new RepositoryFile().path(entry.getKey()).content(entry.getValue())).collect(Collectors.toList());
    }

    /**
     * Helper method to get repository contents from VCS.
     * Exception safe - returns empty map if repository could not be fetched.
     */
    private java.util.Map<String, String> getRepositoryContents(VcsRepositoryUri repositoryUri) {
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);
        }
        catch (IOException e) {
            log.error("Could not get repository content from {}", repositoryUri, e);
            return java.util.Map.of();
        }
    }
}
