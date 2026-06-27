package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisBuildLogEntryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisFeedbackDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisJsonMessageContentDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageContentBaseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextMessageContentDTO;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@Lazy
@Service
@Conditional(IrisEnabled.class)
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
     * Uncommitted files override the committed files if they have the same path.
     *
     * @param submission       the student's submission
     * @param uncommittedFiles the uncommitted files from the client
     * @return the converted PyrisSubmissionDTO
     */
    public PyrisSubmissionDTO toPyrisSubmissionDTO(ProgrammingSubmission submission, Map<String, String> uncommittedFiles) {
        var buildLogEntries = submission.getBuildLogEntries().stream().map(buildLogEntry -> new PyrisBuildLogEntryDTO(toInstant(buildLogEntry.getTime()), buildLogEntry.getLog()))
                .toList();
        Map<String, String> committedFiles = getFilteredRepositoryContents((ProgrammingExerciseParticipation) submission.getParticipation());
        Map<String, String> mergedRepository = new HashMap<>(committedFiles);
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
     * Like {@link #toPyrisMessageDTOList}, but tags each proactive (origin PROACTIVE_STRUGGLE) message with how
     * the student reacted, so the struggle gate can avoid repeating a dismissed/ignored hint (spec §7.4). Builds
     * fresh DTOs — it never mutates the stored IrisMessage entities — and preserves id/sentAt/sender.
     *
     * @param messages the chat-history messages, in chronological order
     * @return the converted DTOs with proactive messages outcome-tagged
     */
    public List<PyrisMessageDTO> toPyrisMessageDTOListForStruggle(List<IrisMessage> messages) {
        var out = new ArrayList<PyrisMessageDTO>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            if (m.getOrigin() != IrisMessageOrigin.PROACTIVE_STRUGGLE) {
                out.add(PyrisMessageDTO.of(m));
            }
            else {
                out.add(annotatedProactiveDTO(m, proactiveOutcomeTag(m, messages, i)));
            }
        }
        return out;
    }

    /** The IMMEDIATELY following USER reply counts as engagement only if it lands within this window of the hint (spec §7.4; ENG). */
    private static final Duration ENGAGED_REPLY_WINDOW = Duration.ofMinutes(10);

    /** The wire tag for a proactive message based on its persisted outcome and surrounding messages. */
    private static String proactiveOutcomeTag(IrisMessage m, List<IrisMessage> all, int i) {
        if (m.getProactiveOutcome() == IrisProactiveOutcome.DISMISSED) {
            return "(proactive hint, dismissed) ";
        }
        // Engagement is attributed only when the IMMEDIATELY following message is a USER reply within the window:
        // if an assistant turn intervenes, a later user reply is more plausibly a response to that turn, not this hint.
        boolean replied = i + 1 < all.size() && all.get(i + 1).getSender() == IrisMessageSender.USER && isWithinEngagedWindow(m.getSentAt(), all.get(i + 1).getSentAt());
        if (m.getHelpful() != null || replied) {
            return "(proactive hint, engaged) ";
        }
        boolean superseded = all.subList(i + 1, all.size()).stream().anyMatch(x -> x.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE);
        return superseded ? "(proactive hint, ignored) " : "(proactive hint) ";
    }

    /**
     * True when the reply follows the hint within {@link #ENGAGED_REPLY_WINDOW} (so a much-later manual message is
     * not misread as engagement with this hint).
     */
    private static boolean isWithinEngagedWindow(ZonedDateTime hintAt, ZonedDateTime replyAt) {
        if (hintAt == null || replyAt == null) {
            return false;
        }
        // The reply must come AT or AFTER the hint and within the window; a reply timestamped before the hint
        // (clock skew / reordering) is not engagement with it.
        var delta = Duration.between(hintAt, replyAt);
        return !delta.isNegative() && delta.compareTo(ENGAGED_REPLY_WINDOW) <= 0;
    }

    /**
     * Build the wire DTO for a proactive message WITHOUT touching the stored entity: same id/sentAt/sender, the
     * first text content prefixed with {@code tag}, every other content mapped verbatim (mirrors PyrisMessageDTO.of).
     */
    private static PyrisMessageDTO annotatedProactiveDTO(IrisMessage m, String tag) {
        boolean[] prefixed = { false };
        var contents = m.getContent().stream().<PyrisMessageContentBaseDTO>map(c -> {
            if (c instanceof IrisTextMessageContent text) {
                String body = (!prefixed[0] ? tag : "") + text.getContentAsString();
                prefixed[0] = true;
                return new PyrisTextMessageContentDTO(body);
            }
            if (c instanceof IrisJsonMessageContent json) {
                return new PyrisJsonMessageContentDTO(json.getContentAsString());
            }
            return null;
        }).filter(Objects::nonNull).toList();
        return new PyrisMessageDTO(m.getId(), toInstant(m.getSentAt()), m.getSender(), contents);
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
        if (participation == null) {
            return Map.of();
        }
        var language = participation.getProgrammingExercise().getProgrammingLanguage();

        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());
        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Helper method to get & checkout the repository contents for a given repository URI.
     * This is an exception-safe way to fetch the repository contents: it returns an empty map
     * if {@code repositoryUri} is null or if the repository could not be fetched.
     * This is useful, as the Pyris call should not fail if the repository is not available.
     *
     * @param repositoryUri the repositoryUri of the repository
     * @return the repository contents, or an empty map if the URI is null or the fetch fails
     */
    private Map<String, String> getRepositoryContents(LocalVCRepositoryUri repositoryUri) {
        if (repositoryUri == null) {
            return Map.of();
        }
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);
        }
        catch (IOException e) {
            log.error("Could not get repository content", e);
            return Map.of();
        }
    }
}
