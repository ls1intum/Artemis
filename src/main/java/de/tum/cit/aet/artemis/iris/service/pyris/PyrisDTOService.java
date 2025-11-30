package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisBuildLogEntryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisFeedbackDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@Lazy
@Service
@Profile(PROFILE_IRIS)
public class PyrisDTOService {

    private static final Logger log = LoggerFactory.getLogger(PyrisDTOService.class);

    private final RepositoryService repositoryService;

    public PyrisDTOService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Helper method to convert a ProgrammingExercise to a PyrisProgrammingExerciseDTO.
     * This notably includes fetching the contents of the template, solution and test repositories, if they exist.
     *
     * @param exercise the programming exercise to convert
     * @return the converted PyrisProgrammingExerciseDTO
     */
    public PyrisProgrammingExerciseDTO toPyrisProgrammingExerciseDTO(ProgrammingExercise exercise) {
        var templateRepositoryContents = getFilteredRepositoryContents(exercise.getTemplateParticipation());
        var solutionRepositoryContents = getFilteredRepositoryContents(exercise.getSolutionParticipation());

        Map<String, String> testsRepositoryContents = getRepositoryContents(exercise.getVcsTestRepositoryUri());

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
    public PyrisSubmissionDTO toPyrisSubmissionDTO(ProgrammingSubmission submission) {
        return toPyrisSubmissionDTO(submission, Map.of());
    }

    /**
     * Helper method to convert a ProgrammingSubmission to a PyrisSubmissionDTO.
     * This notably includes fetching the contents of the student repository, if it exists.
     * Uncommitted files override the committed files if they have the same path.
     *
     * @param submission       the students submission
     * @param uncommittedFiles the uncommitted files from the client (working copy)
     * @return the converted PyrisSubmissionDTO
     */
    public PyrisSubmissionDTO toPyrisSubmissionDTO(ProgrammingSubmission submission, Map<String, String> uncommittedFiles) {
        var buildLogEntries = submission.getBuildLogEntries().stream().map(buildLogEntry -> new PyrisBuildLogEntryDTO(toInstant(buildLogEntry.getTime()), buildLogEntry.getLog()))
                .toList();
        var committedFiles = getFilteredRepositoryContents((ProgrammingExerciseParticipation) submission.getParticipation());
        var mergedRepository = new HashMap<>(committedFiles);
        mergedRepository.putAll(uncommittedFiles); // This overwrites any files with same path
        return new PyrisSubmissionDTO(submission.getId(), toInstant(submission.getSubmissionDate()), mergedRepository, submission.getParticipation().isPracticeMode(),
                submission.isBuildFailed(), buildLogEntries, getLatestResult(submission));
    }

    /**
     * Helper method to convert a list of IrisMessages to a list of PyrisMessageDTOs.
     * This needs separate handling for the different types of message content.
     *
     * @param messages the messages with contents to convert
     * @return the converted list of PyrisMessageDTOs
     */
    public List<PyrisMessageDTO> toPyrisMessageDTOList(List<IrisMessage> messages) {
        return messages.stream().map(PyrisMessageDTO::of).toList();
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

        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());
        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Helper method to get & checkout the repository contents for a participation.
     * This is an exception safe way to fetch the repository, as it will return an empty map if the repository could not be fetched.
     * This is useful, as the Pyris call should not fail if the repository is not available.
     *
     * @param repositoryUri the repositoryUri of the repository
     * @return the repository or empty if it could not be fetched
     */
    private Map<String, String> getRepositoryContents(LocalVCRepositoryUri repositoryUri) {
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);
        }
        catch (IOException e) {
            log.error("Could not get repository content", e);
            return Map.of();
        }
    }
}
