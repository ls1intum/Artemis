package de.tum.cit.aet.artemis.iris.service.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

public abstract class AbstractIrisChatSessionService<S extends IrisSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface<S> {

    private static final Logger log = LoggerFactory.getLogger(AbstractIrisChatSessionService.class);

    private static final int MAX_SESSION_TITLE_LENGTH = 255;

    protected final IrisSessionRepository irisSessionRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    protected final IrisMessageService irisMessageService;

    private final IrisMessageRepository irisMessageRepository;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final ObjectMapper objectMapper;

    private final Optional<IrisCitationService> irisCitationService;

    private final PyrisJobService pyrisJobService;

    /**
     * Constructor with citation service support.
     * Use this for chat sessions that can have citations (Course, Lecture, Exercise chats).
     */
    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService,
            Optional<IrisCitationService> irisCitationService, PyrisJobService pyrisJobService) {
        this.irisSessionRepository = irisSessionRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.objectMapper = objectMapper;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.irisCitationService = irisCitationService;
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * Constructor without citation service support.
     * Use this for sessions that don't support citations (e.g., Tutor Suggestions).
     */
    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService,
            PyrisJobService pyrisJobService) {
        this.irisSessionRepository = irisSessionRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.objectMapper = objectMapper;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.irisCitationService = Optional.empty();
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * Updates the latest suggestions of the session.
     * Converts the list of latest suggestions to a JSON string.
     * The updated suggestions are then saved to the session in the database.
     *
     * @param session           The session to update
     * @param latestSuggestions The latest suggestions to set
     */
    protected void updateLatestSuggestions(S session, List<String> latestSuggestions) {
        if (latestSuggestions == null || latestSuggestions.isEmpty()) {
            return;
        }
        try {
            var suggestions = objectMapper.writeValueAsString(latestSuggestions);
            session.setLatestSuggestions(suggestions);
            irisSessionRepository.save(session);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Could not update latest suggestions for session " + session.getId(), e);
        }
    }

    /**
     * Sets and saves the session title if provided.
     * Truncates the title to 255 characters if necessary.
     * This is a utility method that can be used by services that don't extend this class.
     *
     * @param <T>               The type of session (must extend IrisSession)
     * @param session           The session to update
     * @param sessionTitle      The title to set (can be null or blank)
     * @param sessionRepository The repository to save the session
     * @return The truncated session title if it was set, null otherwise
     */
    public static <T extends IrisSession> String setSessionTitle(T session, String sessionTitle, IrisSessionRepository sessionRepository) {
        if (sessionTitle != null && !sessionTitle.isBlank()) {
            String truncatedTitle = sessionTitle.length() > MAX_SESSION_TITLE_LENGTH ? sessionTitle.substring(0, MAX_SESSION_TITLE_LENGTH) : sessionTitle;
            session.setTitle(truncatedTitle);
            sessionRepository.save(session);
            return truncatedTitle;
        }
        return null;
    }

    /**
     * Return a localized "New chat" title based on the user's language key.
     *
     * @param langKey       The language key of the user
     * @param messageSource The message source to resolve titles from
     * @return the localized title
     */
    public static String getLocalizedNewChatTitle(String langKey, MessageSource messageSource) {
        Locale locale = langKey == null || langKey.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(langKey);
        return messageSource.getMessage("iris.chat.session.newChatTitle", null, "New Chat", locale);
    }

    /**
     * Handles the status update of an ExerciseChatJob by sending the result to the student via the Websocket.
     *
     * @param job          The job that was executed
     * @param statusUpdate The status update of the job
     * @return the same job record or a new job record with the same job id if changes were made
     */
    public TrackedSessionBasedPyrisJob handleStatusUpdate(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        long handlingStart = System.nanoTime();
        // Only the result branch (saving the assistant message) needs the messages and contents;
        // the frequent intermediate status updates get away with a plain session load. Pyris blocks
        // its pipeline thread on each callback, so this reload is on the chat latency critical path.
        // noinspection unchecked
        var session = statusUpdate.result() != null ? (S) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId())
                : (S) irisSessionRepository.findByIdElseThrow(job.sessionId());

        String sessionTitle = AbstractIrisChatSessionService.setSessionTitle(session, statusUpdate.sessionTitle(), irisSessionRepository);
        TrackedSessionBasedPyrisJob updatedJob;
        if (statusUpdate.result() != null) {
            updatedJob = Boolean.FALSE.equals(statusUpdate.finalResult()) ? handleIntermediateResultStatusUpdate(job, statusUpdate, session, sessionTitle)
                    : handleResultStatusUpdate(job, statusUpdate, session, sessionTitle);
        }
        else {
            applyNonResultSideEffects(session, job, statusUpdate, sessionTitle, false);
            updatedJob = recordTokenUsage(session, job, statusUpdate, null);
        }

        updateLatestSuggestions(session, statusUpdate.suggestions());

        log.debug("Iris status update handled in {} ms (result present: {})", (System.nanoTime() - handlingStart) / 1_000_000, statusUpdate.result() != null);

        return updatedJob;
    }

    private TrackedSessionBasedPyrisJob handleIntermediateResultStatusUpdate(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate, S session,
            String sessionTitle) {
        // Intermediate messages are persisted and, once the session is reloaded, get their citation metadata resolved just like final ones - so their citations become
        // clickable and have to be pinned too. The extra lookup only happens for a message that actually contains a lecture citation; stampCitationVersions returns
        // immediately otherwise, which keeps the frequent citation-free intermediate updates off the database.
        var result = irisCitationService.map(service -> service.stampCitationVersions(statusUpdate.result())).orElse(statusUpdate.result());

        var message = new IrisMessage();
        for (var content : parseResultContents(result)) {
            message.addContent(content);
        }
        message.setIntermediate(true);
        var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
        irisChatWebsocketService.sendMessage(session, savedMessage, PyrisRunState.RUNNING, statusUpdate.error(), sessionTitle, List.of(), job.jobId(), null, null, false);
        return recordTokenUsage(session, job, statusUpdate, null);
    }

    private TrackedSessionBasedPyrisJob handleResultStatusUpdate(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate, S session, String sessionTitle) {
        return pyrisJobService.runWithJobLock(job.jobId(), () -> {
            var reloadedJob = pyrisJobService.getJob(job.jobId());
            if (!(reloadedJob instanceof TrackedSessionBasedPyrisJob trackedJob)) {
                log.debug("Skipping already processed result status update for Iris job {} because the job is no longer tracked", job.jobId());
                applyNonResultSideEffects(session, job, statusUpdate, sessionTitle, true);
                return job;
            }
            if (trackedJob.assistantMessageId() != null) {
                applyNonResultSideEffects(session, trackedJob, statusUpdate, sessionTitle, true);
                return trackedJob;
            }

            // Pin every citation to the version of the material it was generated from, before the text is persisted. Once stored, the marker keeps that version forever,
            // so a later re-upload of the PDF or a re-transcribed video can be detected when the citation is clicked.
            var result = irisCitationService.map(service -> service.stampCitationVersions(statusUpdate.result())).orElse(statusUpdate.result());

            var message = new IrisMessage();
            for (var content : parseResultContents(result)) {
                message.addContent(content);
            }
            var citationInfo = irisCitationService.map(service -> service.resolveCitationInfo(result)).orElse(List.of());
            message.setAccessedMemories(statusUpdate.accessedMemories());
            message.setCreatedMemories(statusUpdate.createdMemories());
            message.setToolActivity(statusUpdate.activities());
            var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            var updatedJob = trackedJob.withAssistantMessageId(savedMessage.getId());
            pyrisJobService.updateJob(updatedJob);
            irisChatWebsocketService.sendMessage(session, savedMessage, PyrisRunState.RUNNING, statusUpdate.error(), sessionTitle, citationInfo, job.jobId(),
                    statusUpdate.activities(), statusUpdate.activitySeq(), null);
            updatedJob = recordTokenUsage(session, updatedJob, statusUpdate, savedMessage);
            pyrisJobService.updateJob(updatedJob);
            return updatedJob;
        });
    }

    private void applyNonResultSideEffects(S session, TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate, String sessionTitle,
            boolean suppressMessageWebsocket) {
        if (statusUpdate.accessedMemories() != null && !statusUpdate.accessedMemories().isEmpty() && job.userMessageId() != null) {
            irisMessageRepository.findById(job.userMessageId()).ifPresent(message -> {
                message.setAccessedMemories(statusUpdate.accessedMemories());
                irisMessageRepository.save(message);
                if (!suppressMessageWebsocket) {
                    irisChatWebsocketService.sendMessage(session, message, statusUpdate.runState(), statusUpdate.error());
                }
            });
        }
        if (statusUpdate.createdMemories() != null && !statusUpdate.createdMemories().isEmpty() && job.assistantMessageId() != null) {
            irisMessageRepository.findById(job.assistantMessageId()).ifPresent(message -> {
                message.setCreatedMemories(statusUpdate.createdMemories());
                irisMessageRepository.save(message);
                if (!suppressMessageWebsocket) {
                    irisChatWebsocketService.sendMessage(session, message, statusUpdate.runState(), statusUpdate.error());
                }
            });
        }
        irisChatWebsocketService.sendStatusUpdate(session, job.jobId(), statusUpdate.runState(), statusUpdate.error(), sessionTitle, statusUpdate.suggestions(),
                statusUpdate.tokens(), statusUpdate.activities(), statusUpdate.activitySeq());
    }

    private TrackedSessionBasedPyrisJob recordTokenUsage(S session, TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate, IrisMessage savedMessage) {
        if (statusUpdate.tokens() == null || statusUpdate.tokens().isEmpty()) {
            return job;
        }
        if (savedMessage != null) {
            // generated message is first sent and generated trace is saved
            var llmTokenUsageTrace = llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
                builder.withIrisMessageID(savedMessage.getId()).withUser(session.getUserId());
                this.setLLMTokenUsageParameters(builder, session);
                return builder;
            });

            return job.withTraceId(llmTokenUsageTrace.getId());
        }

        // interaction suggestion is sent and appended to the generated trace if it exists
        var existingTrace = Optional.ofNullable(job.traceId()).flatMap(llmTokenUsageService::findLLMTokenUsageTraceById);
        if (existingTrace.isPresent()) {
            llmTokenUsageService.appendRequestsToTrace(statusUpdate.tokens(), existingTrace.get());
            return job;
        }
        var llmTokenUsage = llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
            builder.withUser(session.getUserId());
            this.setLLMTokenUsageParameters(builder, session);
            return builder;
        });
        return job.withTraceId(llmTokenUsage.getId());
    }

    /**
     * Handles a partial chat status update by relaying the partial response over the websocket.
     * The partial response is ephemeral and is not persisted.
     *
     * @param job          The job that is currently executed
     * @param statusUpdate The partial status update of the job
     */
    public void handlePartialStatusUpdate(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        // noinspection unchecked
        var session = (S) irisSessionRepository.findByIdElseThrow(job.sessionId());
        irisChatWebsocketService.sendPartialUpdate(session, statusUpdate.partialResult(), statusUpdate.partialSeq(), job.jobId());
    }

    private static final String MALFORMED_MCQ_ERROR_MESSAGE = "Sorry, I tried to generate a quiz question but the response was malformed. Please try again.";

    private static final Set<String> MCQ_CONTENT_TYPES = Set.of("mcq", "mcq-set");

    /**
     * Pattern to find embedded MCQ JSON blocks within mixed text+JSON responses.
     */
    private static final Pattern MCQ_JSON_PATTERN = Pattern.compile("\\{\\s*\"type\"\\s*:\\s*\"mcq(?:-set)?\"\\s*,");

    /**
     * Parses the result string from the LLM into a list of IrisMessageContent.
     * Handles three cases:
     * 1. Entire string is valid MCQ JSON -> single IrisJsonMessageContent
     * 2. Mixed text + embedded JSON -> list of text and JSON content segments
     * 3. Plain text -> single IrisTextMessageContent
     *
     * @param result The result string from the LLM
     * @return A list of IrisMessageContent (text and/or JSON)
     */
    private List<IrisMessageContent> parseResultContents(String result) {
        String trimmed = result.strip();

        // Case 1: entire string is valid MCQ JSON
        if (trimmed.startsWith("{")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(trimmed);
                if (jsonNode.has("type") && MCQ_CONTENT_TYPES.contains(jsonNode.get("type").asText())) {
                    if (isValidMcqNode(jsonNode)) {
                        return List.of(new IrisJsonMessageContent(jsonNode));
                    }
                    return List.of(new IrisTextMessageContent(MALFORMED_MCQ_ERROR_MESSAGE));
                }
            }
            catch (JsonProcessingException e) {
                // Not valid JSON as a whole, try mixed content below
            }
        }

        // Case 2: scan for embedded MCQ JSON blocks
        Matcher matcher = MCQ_JSON_PATTERN.matcher(trimmed);
        List<IrisMessageContent> contents = new ArrayList<>();
        int lastEnd = 0;
        boolean foundMcq = false;

        while (matcher.find()) {
            int jsonStart = matcher.start();
            // Skip matches that fall inside an already-extracted JSON block
            // (e.g. inner {"type":"mcq",...} objects inside an mcq-set)
            if (jsonStart < lastEnd) {
                continue;
            }
            String jsonCandidate = extractJsonObject(trimmed, jsonStart);
            if (jsonCandidate == null) {
                continue;
            }

            try {
                JsonNode jsonNode = objectMapper.readTree(jsonCandidate);
                if (!jsonNode.has("type") || !MCQ_CONTENT_TYPES.contains(jsonNode.get("type").asText())) {
                    continue;
                }
                if (!isValidMcqNode(jsonNode)) {
                    return List.of(new IrisTextMessageContent(MALFORMED_MCQ_ERROR_MESSAGE));
                }

                // Add text before this JSON block
                if (jsonStart > lastEnd) {
                    String textBefore = trimmed.substring(lastEnd, jsonStart).strip();
                    if (!textBefore.isEmpty()) {
                        contents.add(new IrisTextMessageContent(textBefore));
                    }
                }

                contents.add(new IrisJsonMessageContent(jsonNode));
                lastEnd = jsonStart + jsonCandidate.length();
                foundMcq = true;
            }
            catch (JsonProcessingException e) {
                // Invalid JSON, skip this match
            }
        }

        if (foundMcq) {
            // Add any remaining text after the last JSON block
            if (lastEnd < trimmed.length()) {
                String textAfter = trimmed.substring(lastEnd).strip();
                if (!textAfter.isEmpty()) {
                    contents.add(new IrisTextMessageContent(textAfter));
                }
            }
            return contents;
        }

        // Case 3: plain text
        return List.of(new IrisTextMessageContent(trimmed));
    }

    /**
     * Extracts a complete JSON object from a string starting at the given position by counting matching braces.
     *
     * @param text  The text containing the JSON object
     * @param start The position of the opening brace
     * @return The extracted JSON string, or null if no valid object could be extracted
     */
    private static String extractJsonObject(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '{') {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') {
                    depth++;
                }
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Validates that a JSON node has the required MCQ shape expected by the client:
     * a non-empty "question" string, an "options" array with at least two entries
     * (each having a "text" string and a "correct" boolean) where exactly one option
     * is marked correct, and an "explanation" string.
     *
     * @param node the JSON node to validate
     * @return true if the node represents a valid MCQ
     */
    private boolean isValidMcqNode(JsonNode node) {
        String type = node.has("type") ? node.get("type").asText() : "";

        if ("mcq-set".equals(type)) {
            JsonNode questions = node.get("questions");
            if (questions == null || !questions.isArray() || questions.isEmpty()) {
                return false;
            }
            for (JsonNode q : questions) {
                if (!isValidSingleMcqNode(q)) {
                    return false;
                }
            }
            return true;
        }

        // Single MCQ ("mcq" type or fallback)
        return isValidSingleMcqNode(node);
    }

    /**
     * Validates that a JSON node has the required single MCQ shape: a non-empty "question" string,
     * an "options" array with at least two entries (each having a "text" string and a "correct" boolean)
     * where exactly one option is marked correct, and an "explanation" string.
     *
     * @param node the JSON node to validate
     * @return true if the node represents a valid single MCQ question
     */
    private boolean isValidSingleMcqNode(JsonNode node) {
        JsonNode question = node.get("question");
        if (question == null || !question.isTextual() || question.asText().isBlank()) {
            return false;
        }

        JsonNode options = node.get("options");
        if (options == null || !options.isArray() || options.size() < 2) {
            return false;
        }
        int correctCount = 0;
        for (JsonNode option : options) {
            if (!option.isObject()) {
                return false;
            }
            JsonNode text = option.get("text");
            JsonNode correct = option.get("correct");
            if (text == null || !text.isTextual() || text.asText().isBlank()) {
                return false;
            }
            if (correct == null || !correct.isBoolean()) {
                return false;
            }
            if (correct.asBoolean()) {
                correctCount++;
            }
        }
        if (correctCount != 1) {
            return false;
        }

        JsonNode explanation = node.get("explanation");
        if (explanation == null || !explanation.isTextual() || explanation.asText().isBlank()) {
            return false;
        }

        return true;
    }

    Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        List<ProgrammingExerciseStudentParticipation> participations;
        if (exercise.isTeamMode()) {
            participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionByExerciseIdAndStudentLoginInTeam(exercise.getId(), user.getLogin());
        }
        else {
            participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        }

        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return participations.getLast().getSubmissions().stream().max(Submission::compareTo)
                .flatMap(sub -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(sub.getId()));
    }

    protected abstract void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, S session);
}
