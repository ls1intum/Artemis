package de.tum.in.www1.artemis.service.connectors.pyris;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.iris.message.IrisJsonMessageContent;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisBuildLogEntryDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisFeedbackDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisJsonMessageContentDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisMessageContentDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisMessageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisResultDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisTextMessageContentDTOPyris;

@Service
public class PyrisDTOService {

    private static final Logger log = LoggerFactory.getLogger(PyrisDTOService.class);

    private final GitService gitService;

    private final RepositoryService repositoryService;

    public PyrisDTOService(GitService gitService, RepositoryService repositoryService) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    public PyrisProgrammingExerciseDTO toPyrisDTO(ProgrammingExercise exercise) {
        var templateRepositoryContents = getRepository(exercise.getTemplateParticipation()).map(repositoryService::getFilesWithContent).orElse(Map.of());
        var solutionRepositoryContents = getRepository(exercise.getSolutionParticipation()).map(repositoryService::getFilesWithContent).orElse(Map.of());
        Optional<Repository> testRepo = Optional.empty();
        try {
            testRepo = Optional.ofNullable(gitService.getOrCheckoutRepository(exercise.getVcsTestRepositoryUri(), true));
        }
        catch (GitAPIException e) {
            log.error("Could not fetch existing test repository", e);
        }
        var testsRepositoryContents = testRepo.map(repositoryService::getFilesWithContent).orElse(Map.of());

        return new PyrisProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getProgrammingLanguage(), templateRepositoryContents, solutionRepositoryContents,
                testsRepositoryContents, exercise.getProblemStatement(), exercise.getReleaseDate(), exercise.getDueDate());
    }

    public PyrisSubmissionDTO toPyrisDTO(ProgrammingSubmission submission) {
        var buildLogEntries = submission.getBuildLogEntries().stream().map(buildLogEntry -> new PyrisBuildLogEntryDTO(buildLogEntry.getTime(), buildLogEntry.getLog())).toList();
        var studentRepositoryContents = getRepository((ProgrammingExerciseParticipation) submission.getParticipation()).map(repositoryService::getFilesWithContent)
                .orElse(Map.of());
        return new PyrisSubmissionDTO(submission.getId(), submission.getSubmissionDate(), studentRepositoryContents, submission.getParticipation().isPracticeMode(),
                submission.isBuildFailed(), buildLogEntries, getLatestResult(submission));
    }

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
            return new PyrisFeedbackDTO(text, feedback.getTestCase().getTestName(), feedback.getCredits());
        }).toList();

        return new PyrisResultDTO(latestResult.getCompletionDate(), latestResult.isSuccessful(), feedbacks);
    }

    public List<PyrisMessageDTO> toPyrisDTO(List<IrisMessage> messages) {
        return messages.stream().map(message -> {
            var content = message.getContent().stream().map(messageContent -> {
                PyrisMessageContentDTO result = null;
                if (messageContent.getClass().equals(IrisTextMessageContent.class)) {
                    result = new PyrisTextMessageContentDTOPyris(messageContent.getContentAsString());
                }
                else if (messageContent.getClass().equals(IrisJsonMessageContent.class)) {
                    result = new PyrisJsonMessageContentDTO(messageContent.getContentAsString());
                }
                return result;
            }).filter(Objects::nonNull).toList();
            return new PyrisMessageDTO(message.getSentAt(), message.getSender(), content);
        }).toList();
    }

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
