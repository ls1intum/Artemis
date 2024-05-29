package de.tum.in.www1.artemis.service.connectors.pyris;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.iris.message.IrisJsonMessageContent;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisBuildLogEntryDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisFeedbackDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisJsonMessageContentDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisMessageContentBaseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisMessageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisResultDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisTextMessageContentDTO;
import de.tum.in.www1.artemis.service.programming.RepositoryService;

@Service
@Profile("iris")
public class PyrisDTOService {

    private static final Logger log = LoggerFactory.getLogger(PyrisDTOService.class);

    private final GitService gitService;

    private final RepositoryService repositoryService;

    public PyrisDTOService(GitService gitService, RepositoryService repositoryService) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    /**
     * Helper method to convert a ProgrammingExercise to a PyrisProgrammingExerciseDTO.
     * This notably includes fetching the contents of the template, solution and test repositories, if they exist.
     *
     * @param exercise the programming exercise to convert
     * @return the converted PyrisProgrammingExerciseDTO
     */
    public PyrisProgrammingExerciseDTO toPyrisDTO(ProgrammingExercise exercise) {
        var templateRepositoryContents = getRepository(exercise.getTemplateParticipation()).map(repositoryService::getFilesContentFromWorkingCopy).orElse(Map.of());
        var solutionRepositoryContents = getRepository(exercise.getSolutionParticipation()).map(repositoryService::getFilesContentFromWorkingCopy).orElse(Map.of());
        Optional<Repository> testRepo = Optional.empty();
        try {
            testRepo = Optional.ofNullable(gitService.getOrCheckoutRepository(exercise.getVcsTestRepositoryUri(), true));
        }
        catch (GitAPIException e) {
            log.error("Could not fetch existing test repository", e);
        }
        var testsRepositoryContents = testRepo.map(repositoryService::getFilesContentFromWorkingCopy).orElse(Map.of());

        return new PyrisProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getProgrammingLanguage(), templateRepositoryContents, solutionRepositoryContents,
                testsRepositoryContents, exercise.getProblemStatement(), toInstant(exercise.getReleaseDate()), toInstant(exercise.getDueDate()));
    }

    /**
     * Helper method to convert a ProgrammingSubmission to a PyrisSubmissionDTO.
     * This notably includes fetching the contents of the student repository, if it exists.
     *
     * @param submission the students submission
     * @return the converted PyrisSubmissionDTO
     */
    public PyrisSubmissionDTO toPyrisDTO(ProgrammingSubmission submission) {
        var buildLogEntries = submission.getBuildLogEntries().stream().map(buildLogEntry -> new PyrisBuildLogEntryDTO(toInstant(buildLogEntry.getTime()), buildLogEntry.getLog()))
                .toList();
        var studentRepositoryContents = getRepository((ProgrammingExerciseParticipation) submission.getParticipation()).map(repositoryService::getFilesContentFromWorkingCopy)
                .orElse(Map.of());
        return new PyrisSubmissionDTO(submission.getId(), toInstant(submission.getSubmissionDate()), studentRepositoryContents, submission.getParticipation().isPracticeMode(),
                submission.isBuildFailed(), buildLogEntries, getLatestResult(submission));
    }

    /**
     * Helper method to convert a list of IrisMessages to a list of PyrisMessageDTOs.
     * This needs separate handling for the different types of message content.
     *
     * @param messages the messages with contents to convert
     * @return the converted list of PyrisMessageDTOs
     */
    public List<PyrisMessageDTO> toPyrisDTO(List<IrisMessage> messages) {
        return messages.stream().map(message -> {
            var content = message.getContent().stream().map(messageContent -> {
                PyrisMessageContentBaseDTO result = null;
                if (messageContent.getClass().equals(IrisTextMessageContent.class)) {
                    result = new PyrisTextMessageContentDTO(messageContent.getContentAsString());
                }
                else if (messageContent.getClass().equals(IrisJsonMessageContent.class)) {
                    result = new PyrisJsonMessageContentDTO(messageContent.getContentAsString());
                }
                return result;
            }).filter(Objects::nonNull).toList();
            return new PyrisMessageDTO(toInstant(message.getSentAt()), message.getSender(), content);
        }).toList();
    }

    /**
     * Null safe conversion of ZonedDateTime to Instant
     *
     * @param zonedDateTime the ZonedDateTime to convert
     * @return the Instant or null if the input was null
     */
    @Nullable
    private Instant toInstant(@Nullable ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        return zonedDateTime.toInstant();
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
                text = feedback.getLongFeedback().get().getText();
            }
            var testCaseName = feedback.getTestCase() == null ? feedback.getText() : feedback.getTestCase().getTestName();
            return new PyrisFeedbackDTO(text, testCaseName, Objects.requireNonNullElse(feedback.getCredits(), 0D));
        }).toList();

        return new PyrisResultDTO(toInstant(latestResult.getCompletionDate()), latestResult.isSuccessful(), feedbacks);
    }

    /**
     * Helper method to get & checkout the repository for a participation.
     * This is an exception safe way to fetch the repository, as it will return an empty optional if the repository could not be fetched.
     * This is useful, as the Pyris call should not fail if the repository is not available.
     *
     * @param participation the participation
     * @return the repository or empty if it could not be fetched
     */
    private Optional<Repository> getRepository(ProgrammingExerciseParticipation participation) {
        try {
            return Optional.ofNullable(gitService.getOrCheckoutRepository(participation.getVcsRepositoryUri(), true));
        }
        catch (GitAPIException e) {
            log.error("Could not fetch repository", e);
            return Optional.empty();
        }
    }
}
