package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.client.api.ReviewAndRefineApi;
import de.tum.cit.aet.artemis.hyperion.client.model.ConsistencyCheckRequest;
import de.tum.cit.aet.artemis.hyperion.client.model.ConsistencyCheckResponse;
import de.tum.cit.aet.artemis.hyperion.client.model.ConsistencyIssueSeverity;
import de.tum.cit.aet.artemis.hyperion.client.model.Repository;
import de.tum.cit.aet.artemis.hyperion.client.model.RepositoryFile;
import de.tum.cit.aet.artemis.hyperion.client.model.RewriteProblemStatementRequest;
import de.tum.cit.aet.artemis.hyperion.client.model.RewriteProblemStatementResponse;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.Severity;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Service for reviewing and refining programming exercises using the Hyperion service.
 */
@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionReviewAndRefineRestService {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineRestService.class);

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ReviewAndRefineApi reviewAndRefineApi;

    public HyperionReviewAndRefineRestService(RepositoryService repositoryService, ProgrammingExerciseRepository programmingExerciseRepository,
            ReviewAndRefineApi reviewAndRefineApi) {
        this.repositoryService = repositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.reviewAndRefineApi = reviewAndRefineApi;
    }

    /**
     * Performs a consistency check on a programming exercise using the Hyperion service.
     * Analyzes the relationship between problem statement, template code, solution code, and test cases.
     *
     * @param user     the user requesting the consistency check
     * @param exercise the programming exercise to analyze
     * @return DTO containing identified inconsistencies and their descriptions
     * @throws NetworkingException if communication with Hyperion service fails
     */
    public ConsistencyCheckResponseDTO checkConsistency(User user, ProgrammingExercise exercise) throws NetworkingException {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());

        try {
            // Load the exercise with participations to avoid lazy loading issues
            var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
            var request = buildConsistencyCheckRequest(exerciseWithParticipations);

            // Use the generated API client to perform the consistency check
            ConsistencyCheckResponse response = reviewAndRefineApi.consistencyCheckReviewAndRefineConsistencyCheckPost(request);

            // Convert to DTO
            List<ConsistencyIssueDTO> issueDTOs = response.getIssues().stream()
                    .map(issue -> new ConsistencyIssueDTO(mapHyperionSeverity(issue.getSeverity()), issue.getCategory(), issue.getDescription(), issue.getSuggestedFix(),
                            issue.getRelatedLocations().stream().map(location -> new ArtifactLocationDTO(mapHyperionArtifactType(location.getType()), location.getFilePath(),
                                    location.getStartLine(), location.getEndLine())).collect(Collectors.toList())))
                    .collect(Collectors.toList());

            boolean hasIssues = !issueDTOs.isEmpty();
            String summary = hasIssues ? String.format("Found %d consistency issue(s)", issueDTOs.size()) : "No issues found";

            log.info("Consistency check completed for exercise {}", exercise.getId());
            return new ConsistencyCheckResponseDTO(issueDTOs, hasIssues, summary);

        }
        catch (RestClientException e) {
            log.error("Failed to perform consistency check for exercise {} by user {}: {}", exercise.getId(), user.getLogin(), e.getMessage(), e);
            throw new NetworkingException("An error occurred while calling Hyperion: " + e.getMessage(), e);
        }
        catch (Exception e) {
            log.error("Failed to perform consistency check for exercise {} by user {}", exercise.getId(), user.getLogin(), e);
            throw new NetworkingException("An error occurred while performing consistency check", e);
        }
    }

    /**
     * Rewrites and improves a problem statement using the Hyperion service.
     *
     * @param user                 the user requesting the problem statement rewrite
     * @param course               the course context (used for logging and tracking)
     * @param problemStatementText the original problem statement text to improve
     * @return DTO containing the improved problem statement text
     * @throws NetworkingException      if communication with the Hyperion service fails
     * @throws IllegalArgumentException if any parameter is null or problemStatementText is empty
     */
    public ProblemStatementRewriteResponseDTO rewriteProblemStatement(User user, Course course, String problemStatementText) throws NetworkingException {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (course == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        if (problemStatementText == null || problemStatementText.trim().isEmpty()) {
            throw new IllegalArgumentException("Problem statement text must not be null or empty");
        }

        log.info("Rewriting problem statement for course {} by user {}", course.getId(), user.getLogin());

        try {
            var request = new RewriteProblemStatementRequest();
            request.setText(problemStatementText.trim());

            // Use the generated API client to perform the problem statement rewrite
            RewriteProblemStatementResponse response = reviewAndRefineApi.problemStatementRewriteReviewAndRefineProblemStatementRewritePost(request);

            String result = response.getRewrittenText();

            if (result == null || result.trim().isEmpty()) {
                log.warn("Hyperion service returned empty rewritten text for course {}", course.getId());
                throw new NetworkingException("Hyperion service returned empty response");
            }

            boolean improved = !result.trim().equals(problemStatementText.trim());

            log.info("Problem statement rewrite completed for course {}", course.getId());
            return new ProblemStatementRewriteResponseDTO(result.trim(), improved);
        }
        catch (RestClientException e) {
            log.error("Failed to rewrite problem statement for course {} by user {}: {}", course.getId(), user.getLogin(), e.getMessage(), e);
            throw new NetworkingException("An error occurred while calling Hyperion: " + e.getMessage(), e);
        }
        catch (Exception e) {
            log.error("Failed to rewrite problem statement for course {} by user {}", course.getId(), user.getLogin(), e);
            throw new NetworkingException("An error occurred while rewriting problem statement", e);
        }
    }

    /**
     * Builds a consistency check request by extracting and structuring exercise data for the Hyperion API.
     *
     * @param exercise the programming exercise to extract data from
     * @return a properly structured ConsistencyCheckRequest for the Hyperion API
     * @throws RuntimeException if repository access fails or data extraction encounters errors
     */
    private ConsistencyCheckRequest buildConsistencyCheckRequest(ProgrammingExercise exercise) {
        try {
            var solutionRepo = buildRepository(exercise, RepositoryType.SOLUTION);
            var templateRepo = buildRepository(exercise, RepositoryType.TEMPLATE);
            var testRepo = buildRepository(exercise, RepositoryType.TESTS);

            var request = new ConsistencyCheckRequest();
            request.setProblemStatement(exercise.getProblemStatement() != null ? exercise.getProblemStatement() : "");
            request.setSolutionRepository(solutionRepo);
            request.setTemplateRepository(templateRepo);
            request.setTestRepository(testRepo);

            // Set programming language based on exercise
            if (exercise.getProgrammingLanguage() != null) {
                request.setProgrammingLanguage(mapProgrammingLanguage(exercise.getProgrammingLanguage()));
            }

            return request;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to build consistency check request", e);
        }
    }

    /**
     * Maps Artemis programming language enumeration to Hyperion API programming language enumeration.
     *
     * @param artemisLanguage the Artemis programming language to map
     * @return the corresponding Hyperion API programming language enumeration
     */
    private de.tum.cit.aet.artemis.hyperion.client.model.AppCreationStepsStep8ReviewAndRefineConsistencyCheckModelsProgrammingLanguage mapProgrammingLanguage(
            ProgrammingLanguage artemisLanguage) {
        return switch (artemisLanguage) {
            case JAVA -> de.tum.cit.aet.artemis.hyperion.client.model.AppCreationStepsStep8ReviewAndRefineConsistencyCheckModelsProgrammingLanguage.JAVA;
            case PYTHON -> de.tum.cit.aet.artemis.hyperion.client.model.AppCreationStepsStep8ReviewAndRefineConsistencyCheckModelsProgrammingLanguage.PYTHON;
            default -> de.tum.cit.aet.artemis.hyperion.client.model.AppCreationStepsStep8ReviewAndRefineConsistencyCheckModelsProgrammingLanguage.JAVA;
        };
    }

    /**
     * Extracts repository contents and builds a Repository object for Hyperion API requests.
     *
     * @param exercise       the programming exercise containing repository information
     * @param repositoryType the type of repository to extract (TEMPLATE, SOLUTION, or TESTS)
     * @return a Repository object containing filtered file contents, or empty Repository if access fails
     * @throws IOException if repository operations encounter unrecoverable errors
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

        var repository = new Repository();
        repository.setFiles(repositoryFiles);
        return repository;
    }

    /**
     * Retrieves and filters repository contents for a programming exercise participation.
     * Filters by programming language to include only relevant source files.
     *
     * @param participation the programming exercise participation with VCS repository access
     * @return list of RepositoryFile objects containing filtered source files
     */
    private List<RepositoryFile> getFilteredRepositoryFiles(ProgrammingExerciseParticipation participation) {
        var language = participation.getProgrammingExercise().getProgrammingLanguage();
        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());

        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey())).map(entry -> {
            var file = new RepositoryFile();
            file.setPath(entry.getKey());
            file.setContent(entry.getValue());
            return file;
        }).collect(Collectors.toList());
    }

    /**
     * Retrieves all files from a VCS repository and converts them to RepositoryFile objects.
     * This method is used primarily for test repositories where no programming language
     * filtering is applied, as test files may include various formats and configurations.
     *
     * @param repositoryUri the VCS repository URI to access
     * @return list of RepositoryFile objects containing all repository files
     */
    private List<RepositoryFile> getRepositoryFiles(VcsRepositoryUri repositoryUri) {
        var repositoryContents = getRepositoryContents(repositoryUri);

        return repositoryContents.entrySet().stream().map(entry -> {
            var file = new RepositoryFile();
            file.setPath(entry.getKey());
            file.setContent(entry.getValue());
            return file;
        }).collect(Collectors.toList());
    }

    /**
     * Safely retrieves repository contents from a VCS repository with error handling.
     * Returns an empty map if the repository cannot be accessed rather than propagating exceptions.
     * This allows consistency checks to proceed with partial data when some repositories are unavailable.
     *
     * @param repositoryUri the VCS repository URI to access
     * @return map of file paths to file contents, or empty map if repository access fails
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

    /**
     * Maps Hyperion's ArtifactType enum to Artemis's ArtifactType enum.
     *
     * @param hyperionType the artifact type from Hyperion
     * @return the corresponding Artemis artifact type
     */
    private ArtifactType mapHyperionArtifactType(de.tum.cit.aet.artemis.hyperion.client.model.ArtifactType hyperionType) {
        return switch (hyperionType) {
            case PROBLEM_STATEMENT -> ArtifactType.PROBLEM_STATEMENT;
            case TEMPLATE_REPOSITORY -> ArtifactType.TEMPLATE_REPOSITORY;
            case SOLUTION_REPOSITORY -> ArtifactType.SOLUTION_REPOSITORY;
        };
    }

    /**
     * Maps Hyperion's ConsistencyIssueSeverity enum to Artemis's Severity enum.
     *
     * @param hyperionSeverity the severity from Hyperion
     * @return the corresponding Artemis severity
     */
    private Severity mapHyperionSeverity(ConsistencyIssueSeverity hyperionSeverity) {
        return switch (hyperionSeverity) {
            case LOW -> Severity.LOW;
            case MEDIUM -> Severity.MEDIUM;
            case HIGH -> Severity.HIGH;
        };
    }
}
