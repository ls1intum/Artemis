package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisBuildLogEntryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisFeedbackDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * This service provides conversion methods for domain objects related to programming exercises into their DTO types.
 * These conversions require special handling too complex for constructors in the DTOs themselves.
 */
@Service
@Profile(PROFILE_IRIS)
public class PyrisProgrammingExerciseDTOService {

    private static final Logger log = LoggerFactory.getLogger(PyrisProgrammingExerciseDTOService.class);

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final ProfileService profileService;

    public PyrisProgrammingExerciseDTOService(GitService gitService, RepositoryService repositoryService, ProfileService profileService) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.profileService = profileService;
    }

    /**
     * Helper method to convert a ProgrammingExercise to a PyrisProgrammingExerciseDTO.
     * This notably includes fetching the contents of the template, solution and test repositories, if they exist.
     *
     * @param exercise the programming exercise to convert
     * @return the converted PyrisProgrammingExerciseDTO
     */
    public PyrisProgrammingExerciseDTO convertExercise(ProgrammingExercise exercise) {
        var templateRepositoryContents = getFilteredRepositoryContents(exercise.getTemplateParticipation());
        var solutionRepositoryContents = getFilteredRepositoryContents(exercise.getSolutionParticipation());
        Optional<Repository> testRepo = Optional.empty();
        try {
            testRepo = Optional.ofNullable(gitService.getOrCheckoutRepository(exercise.getVcsTestRepositoryUri(), true));
        }
        catch (GitAPIException e) {
            log.error("Could not fetch existing test repository", e);
        }
        var testsRepositoryContents = testRepo.map(repositoryService::getFilesContentFromWorkingCopy).orElse(Map.of());

        // @formatter:off
        return new PyrisProgrammingExerciseDTO(
                exercise.getId(),
                exercise.getTitle(),
                exercise.getProgrammingLanguage(),
                templateRepositoryContents,
                solutionRepositoryContents,
                testsRepositoryContents,
                exercise.getProblemStatement(),
                toInstant(exercise.getReleaseDate()),
                toInstant(exercise.getDueDate())
        );
        // @formatter:on
    }

    /**
     * Helper method to convert a ProgrammingSubmission to a PyrisSubmissionDTO.
     * This notably includes fetching the contents of the student repository, if it exists.
     *
     * @param submission the students submission
     * @return the converted PyrisSubmissionDTO
     */
    public PyrisSubmissionDTO convertSubmission(ProgrammingSubmission submission) {
        // @formatter:off
        var buildLogEntries = submission.getBuildLogEntries()
                .stream()
                .map(buildLogEntry -> new PyrisBuildLogEntryDTO(
                        toInstant(buildLogEntry.getTime()),
                        buildLogEntry.getLog()
                ))
                .toList();
        var studentRepositoryContents = getFilteredRepositoryContents((ProgrammingExerciseParticipation) submission.getParticipation());
        return new PyrisSubmissionDTO(
                submission.getId(),
                toInstant(submission.getSubmissionDate()),
                studentRepositoryContents,
                submission.getParticipation().isPracticeMode(),
                submission.isBuildFailed(),
                buildLogEntries,
                getLatestResult(submission)
        );
        // @formatter:on
    }

    /**
     * Helper method to convert the latest result of a submission to a PyrisResultDTO
     *
     * @param submission the submission
     * @return the PyrisResultDTO or null if the submission has no result
     */
    private PyrisResultDTO getLatestResult(ProgrammingSubmission submission) {
        var latestResult = submission.getLatestResult();
        if (latestResult == null) {
            return null;
        }
        var feedbacks = latestResult.getFeedbacks().stream().map(feedback -> {
            var text = feedback.getDetailText();
            if (feedback.getHasLongFeedbackText()) {
                text = feedback.getLongFeedback().orElseThrow().getText();
            }
            var testCaseName = feedback.getTestCase() == null ? feedback.getText() : feedback.getTestCase().getTestName();
            return new PyrisFeedbackDTO(text, testCaseName, Objects.requireNonNullElse(feedback.getCredits(), 0D));
        }).toList();

        return new PyrisResultDTO(toInstant(latestResult.getCompletionDate()), latestResult.isSuccessful(), feedbacks);
    }

    private Map<String, String> getFilteredRepositoryContents(ProgrammingExerciseParticipation participation) {
        var language = participation.getProgrammingExercise().getProgrammingLanguage();

        var repositoryContents = getRepositoryContents(participation);
        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Helper method to get & checkout the repository contents for a participation.
     * This is an exception safe way to fetch the repository, as it will return an empty map if the repository could not be fetched.
     * This is useful, as the Pyris call should not fail if the repository is not available.
     *
     * @param participation the participation
     * @return the repository or empty if it could not be fetched
     */
    private Map<String, String> getRepositoryContents(ProgrammingExerciseParticipation participation) {
        try {
            var repositoryUri = participation.getVcsRepositoryUri();
            if (profileService.isLocalVcsActive()) {
                return Optional.ofNullable(gitService.getBareRepository(repositoryUri)).map(bareRepository -> {
                    var lastCommitObjectId = gitService.getLastCommitHash(repositoryUri);
                    if (lastCommitObjectId == null) {
                        return null;
                    }
                    var lastCommitHash = lastCommitObjectId.getName();
                    try {
                        return repositoryService.getFilesContentFromBareRepository(bareRepository, lastCommitHash);
                    }
                    catch (IOException e) {
                        log.error("Could not fetch repository contents from bare repository", e);
                        return null;
                    }
                }).orElse(Map.of());
            }
            else {
                return Optional.ofNullable(gitService.getOrCheckoutRepository(repositoryUri, true)).map(repositoryService::getFilesContentFromWorkingCopy).orElse(Map.of());
            }
        }
        catch (GitAPIException e) {
            log.error("Could not fetch repository", e);
            return Map.of();
        }
    }
}
