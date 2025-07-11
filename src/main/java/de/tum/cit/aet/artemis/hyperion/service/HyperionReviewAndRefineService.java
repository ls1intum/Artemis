package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionConfigurationProperties;
import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckRequest;
import de.tum.cit.aet.artemis.hyperion.proto.Repository;
import de.tum.cit.aet.artemis.hyperion.proto.RepositoryFile;
import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc.ReviewAndRefineBlockingStub;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementRequest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Service for reviewing and refining programming exercises using Hyperion.
 * Provides consistency checks and problem statement rewriting.
 */
@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionReviewAndRefineService extends AbstractHyperionGrpcService {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineService.class);

    private final ReviewAndRefineBlockingStub reviewAndRefineStub;

    private final RepositoryService repositoryService;

    private final HyperionConfigurationProperties hyperionConfigurationProperties;

    public HyperionReviewAndRefineService(ReviewAndRefineBlockingStub reviewAndRefineStub, RepositoryService repositoryService,
            HyperionConfigurationProperties hyperionConfigurationProperties) {
        this.reviewAndRefineStub = reviewAndRefineStub;
        this.repositoryService = repositoryService;
        this.hyperionConfigurationProperties = hyperionConfigurationProperties;
    }

    /**
     * Performs a consistency check on a programming exercise.
     * Validates the consistency between problem statement, template, solution, and test repositories.
     *
     * @param user     the user executing the check
     * @param exercise the programming exercise to check
     * @return the consistency check result with any found inconsistencies
     * @throws HttpStatusException if the service call fails
     */
    public String checkConsistency(User user, ProgrammingExercise exercise) {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());

        try {
            var request = buildConsistencyCheckRequest(exercise);

            var response = reviewAndRefineStub.withDeadlineAfter(hyperionConfigurationProperties.getTimeouts().getConsistencyCheck().toSeconds(), TimeUnit.SECONDS)
                    .checkInconsistencies(request);

            log.info("Consistency check completed for exercise {}", exercise.getId());
            return response.getInconsistencies();

        }
        catch (Exception e) {
            handleGrpcException("consistency check for exercise " + exercise.getId(), e);
            return null; // unreachable due to exception
        }
    }

    /**
     * Rewrites a problem statement using Hyperion.
     * Improves clarity, structure, and pedagogical value of programming exercise descriptions.
     *
     * @param user             the user requesting the rewrite
     * @param problemStatement the problem statement text to be rewritten
     * @return the rewritten problem statement
     * @throws HttpStatusException if the service call fails
     */
    public String rewriteProblemStatement(User user, String problemStatement) {
        log.info("Rewriting problem statement for user {}", user.getLogin());

        try {
            var request = RewriteProblemStatementRequest.newBuilder().setText(problemStatement).build();

            var response = reviewAndRefineStub.withDeadlineAfter(hyperionConfigurationProperties.getTimeouts().getRewriteProblemStatement().toSeconds(), TimeUnit.SECONDS)
                    .rewriteProblemStatement(request);

            log.info("Problem statement rewriting completed successfully for user {}", user.getLogin());
            return response.getRewrittenText();

        }
        catch (Exception e) {
            handleGrpcException("problem statement rewriting for user " + user.getLogin(), e);
            return null; // unreachable due to exception
        }
    }

    /**
     * Builds the gRPC request from the programming exercise.
     *
     * @param exercise the programming exercise
     * @return the gRPC request
     */
    private InconsistencyCheckRequest buildConsistencyCheckRequest(ProgrammingExercise exercise) {
        var templateRepo = Repository.newBuilder().addAllFiles(getRepositoryFiles(exercise, RepositoryType.TEMPLATE)).build();

        var solutionRepo = Repository.newBuilder().addAllFiles(getRepositoryFiles(exercise, RepositoryType.SOLUTION)).build();

        var testRepo = Repository.newBuilder().addAllFiles(getRepositoryFiles(exercise, RepositoryType.TESTS)).build();

        return InconsistencyCheckRequest.newBuilder().setProblemStatement(exercise.getProblemStatement() != null ? exercise.getProblemStatement() : "")
                .setTemplateRepository(templateRepo).setSolutionRepository(solutionRepo).setTestRepository(testRepo).build();
    }

    /**
     * Retrieves repository files for the given repository type.
     * Based on the implementation of toPyrisProgrammingExerciseDTO in PyrisDTOService.
     *
     * @param exercise       the programming exercise
     * @param repositoryType the type of repository (TEMPLATE, SOLUTION, TESTS)
     * @return list of repository files
     */
    private List<RepositoryFile> getRepositoryFiles(ProgrammingExercise exercise, RepositoryType repositoryType) {
        Map<String, String> repositoryContents;

        try {
            switch (repositoryType) {
                case TEMPLATE -> {
                    var templateParticipation = exercise.getTemplateParticipation();
                    repositoryContents = getFilteredRepositoryContents(templateParticipation);
                }
                case SOLUTION -> {
                    var solutionParticipation = exercise.getSolutionParticipation();
                    repositoryContents = getFilteredRepositoryContents(solutionParticipation);
                }
                case TESTS -> {
                    repositoryContents = getRepositoryContents(exercise.getVcsTestRepositoryUri());
                }
                default -> {
                    log.warn("Unknown repository type: {}", repositoryType);
                    return List.of();
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to fetch repository contents for {} repository of exercise {}", repositoryType, exercise.getId(), e);
            return List.of();
        }

        return repositoryContents.entrySet().stream().map(entry -> RepositoryFile.newBuilder().setPath(entry.getKey()).setContent(entry.getValue()).build()).toList();
    }

    /**
     * Helper method to get & checkout the repository contents for a participation.
     * Similar to PyrisDTOService implementation - filters by programming language if available.
     */
    private Map<String, String> getFilteredRepositoryContents(ProgrammingExerciseParticipation participation) {
        var language = participation.getProgrammingExercise().getProgrammingLanguage();
        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());

        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Helper method to get repository contents from VCS.
     * Exception safe - returns empty map if repository could not be fetched.
     */
    private Map<String, String> getRepositoryContents(VcsRepositoryUri repositoryUri) {
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);
        }
        catch (IOException e) {
            log.error("Could not get repository content from {}", repositoryUri, e);
            return Map.of();
        }
    }
}
