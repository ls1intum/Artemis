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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisBuildLogEntryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisFeedbackDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageContentBaseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextMessageContentDTO;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisDTOService {

    private static final Logger log = LoggerFactory.getLogger(PyrisDTOService.class);

    private final RepositoryService repositoryService;

    private final ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService;

    private final IrisProactiveProperties proactiveProperties;

    public PyrisDTOService(RepositoryService repositoryService, ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService,
            IrisProactiveProperties proactiveProperties) {
        this.repositoryService = repositoryService;
        this.programmingFeedbackSynthesizerService = programmingFeedbackSynthesizerService;
        this.proactiveProperties = proactiveProperties;
    }

    /**
     * Helper method to convert a ProgrammingExercise to a PyrisProgrammingExerciseDTO.
     * This notably includes fetching the contents of the template, solution and test repositories, if they exist.
     *
     * @param exercise the programming exercise to convert
     * @return the converted PyrisProgrammingExerciseDTO
     */
    public PyrisProgrammingExerciseDTO toPyrisProgrammingExerciseDTO(ProgrammingExercise exercise) {
        var templateRepositoryContents = getFilteredRepositoryContents(exercise.getTemplateParticipation()).files();
        var solutionRepositoryContents = getFilteredRepositoryContents(exercise.getSolutionParticipation()).files();

        Map<String, String> testsRepositoryContents = Objects.requireNonNullElse(getRepositoryContents(exercise.getVcsTestRepositoryUri()), Map.of());

        return new PyrisProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getProgrammingLanguage(), templateRepositoryContents, solutionRepositoryContents,
                testsRepositoryContents, exercise.getProblemStatement(), toInstant(exercise.getReleaseDate()), toInstant(exercise.getDueDate()));
    }

    /**
     * The exercise as the struggle-intervention pipeline needs it: identity, language, problem statement and dates,
     * with all three repositories left empty. {@link #toPyrisProgrammingExerciseDTO} walks tens to hundreds of files
     * per call, while this pipeline only reads the problem statement and gets the student's code from the submission.
     * A struggle trigger fires by itself while a student works, so that payload would be paid for over and over.
     *
     * @param exercise the programming exercise to convert
     * @return the converted DTO without any repository contents
     */
    public PyrisProgrammingExerciseDTO toPyrisProgrammingExerciseMetadataDTO(ProgrammingExercise exercise) {
        return new PyrisProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getProgrammingLanguage(), Map.of(), Map.of(), Map.of(),
                exercise.getProblemStatement(), toInstant(exercise.getReleaseDate()), toInstant(exercise.getDueDate()));
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
    public PyrisSubmissionDTO toPyrisSubmissionDTO(@NonNull ProgrammingSubmission submission, Map<String, String> uncommittedFiles) {
        var buildLogEntries = submission.getBuildLogEntries().stream().map(buildLogEntry -> new PyrisBuildLogEntryDTO(toInstant(buildLogEntry.getTime()), buildLogEntry.getLog()))
                .toList();
        var participation = (ProgrammingExerciseParticipation) submission.getParticipation();
        var committed = getFilteredRepositoryContents(participation);
        Map<String, String> committedFiles = committed.files();
        Map<String, String> mergedRepository = new HashMap<>(committedFiles);
        mergedRepository.putAll(uncommittedFiles); // This overwrites any files with same path
        // getFilteredRepositoryContents tolerates a null participation, so every dereference below has to as well,
        // or a submission without one aborts the whole conversion instead of degrading to "nothing readable".
        var programmingLanguage = participation != null ? participation.getProgrammingExercise().getProgrammingLanguage() : null;
        var submittedRepository = buildSubmittedRepository(committedFiles, uncommittedFiles, programmingLanguage, committed.readable());
        // Lets Pyris tell "no code changed since the submission" apart from "the submitted code could not be read",
        // which would otherwise both be an empty map. Taken from the fetch rather than the file count, because a
        // repository holding no file of the exercise language is readable and empty.
        boolean submittedRepositoryAvailable = committed.readable();
        return new PyrisSubmissionDTO(submission.getId(), toInstant(submission.getSubmissionDate()), mergedRepository, submittedRepository, submittedRepositoryAvailable,
                submission.getParticipation() != null && submission.getParticipation().isPracticeMode(), submission.isBuildFailed(), buildLogEntries, getLatestResult(submission));
    }

    // The committed version of only the code files the client changed locally, so Pyris can diff live against submitted. A changed
    // existing file contributes its committed content, a genuinely new one contributes "", and unchanged files are skipped. An unreadable
    // committed set returns an empty map rather than fabricating an all-added diff. Readability is passed in rather than inferred from an
    // empty map, because a repository holding no file of the exercise language is readable and empty.
    static Map<String, String> buildSubmittedRepository(Map<String, String> committedFiles, Map<String, String> uncommittedFiles, ProgrammingLanguage language,
            boolean committedReadable) {
        Map<String, String> submittedRepository = new HashMap<>();
        for (var entry : uncommittedFiles.entrySet()) {
            var path = entry.getKey();
            if (language != null && !language.matchesFileExtension(path)) {
                continue; // non-language file (e.g. README): not part of the code diff
            }
            var committed = committedFiles.get(path);
            if (committed == null) {
                if (committedReadable) {
                    submittedRepository.put(path, ""); // genuinely new local code file
                }
                // else: committed set unavailable -> do not fabricate an all-added diff
            }
            else if (!committed.equals(entry.getValue())) {
                submittedRepository.put(path, committed); // changed existing code file
            }
            // unchanged code file: skip
        }
        return submittedRepository;
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
     * Like {@link #toPyrisMessageDTOList}, but tags each proactive message with how the student reacted, so the
     * struggle gate can avoid repeating a hint that was dismissed or ignored. Builds fresh DTOs and never mutates the
     * stored entities.
     *
     * <p>
     * A hint belonging to a different episode than the one running is marked as such. The session outlives any single
     * bout of being stuck and its proactive messages are never cleaned up, so without this a hint from days ago looks
     * exactly like one given a minute ago. The marker is added rather than substituted, because a rejection outlives
     * the episode it was given in. The tag text is the one channel that carries this, since {@code sent_at} is
     * dropped before the history reaches the model.
     *
     * @param messages         the chat-history messages, in chronological order
     * @param currentEpisodeId the episode this run belongs to, or null when the caller has none. Null marks
     *                             NOTHING as earlier: with the episode relation unknown, keeping every hint under
     *                             today's tags can only ever suppress a repeat, never license one.
     * @return the converted DTOs with proactive messages outcome-tagged
     */
    public List<PyrisMessageDTO> toPyrisMessageDTOListForStruggle(List<IrisMessage> messages, @Nullable String currentEpisodeId) {
        // One reverse pass instead of a forward scan per proactive message: "superseded" only asks whether a LATER
        // proactive message exists, so the index of the last one answers it for every message at once.
        int lastProactiveIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE) {
                lastProactiveIndex = i;
                break;
            }
        }
        var out = new ArrayList<PyrisMessageDTO>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            if (m.getOrigin() != IrisMessageOrigin.PROACTIVE_STRUGGLE) {
                out.add(PyrisMessageDTO.of(m));
            }
            else {
                out.add(annotatedProactiveDTO(m, proactiveOutcomeTag(m, messages, i, i < lastProactiveIndex, currentEpisodeId)));
            }
        }
        return out;
    }

    // The wire tag for a proactive message based on its persisted outcome, its neighbour, whether a later proactive message exists, and
    // whether it belongs to an episode other than the one running now.
    private String proactiveOutcomeTag(IrisMessage m, List<IrisMessage> all, int i, boolean superseded, @Nullable String currentEpisodeId) {
        // "from an earlier episode" is a qualifier on the reaction, not a replacement for it. Dropping the reaction
        // here would tell the gate that a hint the student explicitly rejected is merely old, which is the one thing
        // it must never conclude.
        String origin = isFromAnEarlierEpisode(m, currentEpisodeId) ? "proactive hint from an earlier episode" : "proactive hint";
        if (m.getProactiveOutcome() == IrisProactiveOutcome.DISMISSED) {
            return "(" + origin + ", dismissed) ";
        }
        // Engagement is attributed only when the IMMEDIATELY following message is a USER reply within the window:
        // if an assistant turn intervenes, a later user reply is more plausibly a response to that turn, not this hint.
        boolean replied = i + 1 < all.size() && all.get(i + 1).getSender() == IrisMessageSender.USER && isWithinEngagedWindow(m.getSentAt(), all.get(i + 1).getSentAt());
        if (m.getHelpful() != null || replied) {
            return "(" + origin + ", engaged) ";
        }
        return superseded ? "(" + origin + ", ignored) " : "(" + origin + ") ";
    }

    // Whether this hint was given during a different bout of being stuck than the one running now. Both ids have to be known for the
    // question to have an answer. A message from before episodes existed carries none, and a caller without one (an older client, or an
    // id too malformed to serve as an identity) cannot say what "earlier" would even be relative to. Both answer false, which leaves the
    // hint under the tags it has today: the fail-safe direction, since the gate then keeps treating it as a repeat.
    private static boolean isFromAnEarlierEpisode(IrisMessage m, @Nullable String currentEpisodeId) {
        return currentEpisodeId != null && m.getProactiveEpisodeId() != null && !currentEpisodeId.equals(m.getProactiveEpisodeId());
    }

    // True when the reply follows the hint within artemis.iris.proactive.engaged-reply-window (so a much-later manual message is not
    // misread as engagement with this hint).
    private boolean isWithinEngagedWindow(ZonedDateTime hintAt, ZonedDateTime replyAt) {
        if (hintAt == null || replyAt == null) {
            return false;
        }
        // The reply must come AT or AFTER the hint and within the window; a reply timestamped before the hint
        // (clock skew / reordering) is not engagement with it.
        var delta = Duration.between(hintAt, replyAt);
        return !delta.isNegative() && delta.compareTo(proactiveProperties.getEngagedReplyWindow()) <= 0;
    }

    // Build the wire DTO for a proactive message WITHOUT touching the stored entity: the message as PyrisMessageDTO#of maps it, with the
    // first text content prefixed by tag. Built ON TOP of that factory rather than beside it: a second copy of the content mapping is a
    // copy of the rule for which content subtypes travel at all, and the two would decide differently the day a third subtype exists.
    private static PyrisMessageDTO annotatedProactiveDTO(IrisMessage m, String tag) {
        var base = PyrisMessageDTO.of(m);
        List<PyrisMessageContentBaseDTO> contents = new ArrayList<>(base.contents());
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i) instanceof PyrisTextMessageContentDTO text) {
                contents.set(i, new PyrisTextMessageContentDTO(tag + text.textContent()));
                break;
            }
        }
        return new PyrisMessageDTO(base.id(), base.sentAt(), base.sender(), contents);
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
        if (submission.getParticipation() != null && submission.getParticipation().getExercise() instanceof ProgrammingExercise programmingExercise) {
            // the automatic test-case and SCA feedback lives in typed tables - attach the synthesized legacy
            // views so the Iris context keeps containing it (explicit exercise context, the graph is detached)
            programmingFeedbackSynthesizerService.attachSynthesizedFeedback(latestResult, programmingExercise, false);
        }
        var feedbacks = latestResult.getFeedbacks().stream().map(feedback -> {
            var text = feedback.getDetailText();
            if (feedback.getHasLongFeedbackText()) {
                text = feedback.getLongFeedback().orElseThrow().getText();
            }
            var testCase = feedback.getTestCase();
            var testCaseName = testCase == null ? feedback.getText() : testCase.getTestName();
            // positive is the authoritative outcome and is TRI-STATE: true=passed, false=failed,
            // null=not executed (Artemis semantics). Forward it verbatim so Pyris never shows an
            // unexecuted test as a failure. hasTestCase distinguishes a real test case from non-test
            // feedback (which is otherwise stuffed into testCaseName above).
            return new PyrisFeedbackDTO(text, testCaseName, Objects.requireNonNullElse(feedback.getCredits(), 0D), feedback.isPositive(), testCase != null);
        }).toList();

        return new PyrisResultDTO(toInstant(latestResult.getCompletionDate()), latestResult.isSuccessful(), feedbacks);
    }

    // A participation's language-filtered repository contents together with whether the repository could be read at all. The two are not
    // the same thing: a repository that checks out fine but holds no file of the exercise language filters down to an empty map while
    // still being perfectly readable. Deriving readability from the map being empty would report that as "the submitted code could not be
    // read" and suppress the all-added baseline for the student's new local files, so Pyris would see no diff for a valid repository
    private record RepositoryContents(boolean readable, Map<String, String> files) {

        private static final RepositoryContents UNREADABLE = new RepositoryContents(false, Map.of());
    }

    private RepositoryContents getFilteredRepositoryContents(ProgrammingExerciseParticipation participation) {
        if (participation == null) {
            return RepositoryContents.UNREADABLE;
        }
        var language = participation.getProgrammingExercise().getProgrammingLanguage();

        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());
        if (repositoryContents == null) {
            return RepositoryContents.UNREADABLE;
        }
        return new RepositoryContents(true, repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    // Helper method to get & checkout the repository contents for a given repository URI. This is an exception-safe way to fetch the
    // repository contents: it returns null if repositoryUri is null or if the repository could not be fetched. This is useful, as the
    // Pyris call should not fail if the repository is not available. null rather than an empty map so callers can tell an unreadable
    // repository apart from an empty one.
    private @Nullable Map<String, String> getRepositoryContents(LocalVCRepositoryUri repositoryUri) {
        if (repositoryUri == null) {
            return null;
        }
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);
        }
        catch (IOException e) {
            log.error("Could not get repository content", e);
            return null;
        }
    }
}
