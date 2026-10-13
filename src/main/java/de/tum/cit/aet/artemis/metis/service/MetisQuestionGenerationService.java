package de.tum.cit.aet.artemis.metis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROGRAMMING_GRACE_PERIOD_SECONDS;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseContextDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerationTargetDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisDiffService.FileChange;
import de.tum.cit.aet.artemis.metis.service.MetisDiffService.FocusFileContext;
import de.tum.cit.aet.artemis.metis.service.MetisDiffService.OwnCodeDiff;
import de.tum.cit.aet.artemis.metis.service.MetisDiffService.RepositoryUnavailableException;
import de.tum.cit.aet.artemis.metis.service.MetisWindowService.EffectiveWindow;

/**
 * Generates the questions of a student's comprehension check from the code the student wrote, and replaces single questions during the review.
 * <p>
 * The generation of a check ({@link #generate(long, boolean)}) runs on the Metis executor of the scheduling node, right after the dispatcher claimed the check.
 * It reads the check again, makes sure the configuration is still enabled, the student's effective due date has passed, the student's window leaves time for
 * a question and the student still consents to AI processing, all before any repository is read. Then it finds the commit of the student's last submission up to
 * the effective due date, computes the student's own code against the template, picks one random focus file per question (seeded with the check id, so a run
 * is reproducible) and asks the LLM for the questions in one batch call. Unusable output is asked for once more. The questions are stored with their focus
 * file, and the check records the commit, the model and the prompt version of the run. Every write to the check is guarded by its {@code GENERATING} state, so
 * a check that was reset or deleted meanwhile is left alone, and the outcome tells the dispatcher what happened.
 * <p>
 * The regeneration of one question ({@link #regenerateQuestion(long, long)}) runs on the request of an editor with the interactive client, without a retry.
 * While a review is required, the check stays in review for the whole call, so nobody sees a text that nobody approved; questions of a check the student has
 * started are frozen.
 * <p>
 * Student content is sanitized before it reaches a prompt, the prompts never contain names, logins or e-mail addresses, and no prompt content is logged.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisQuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(MetisQuestionGenerationService.class);

    /** The version of the generation prompts, stored with every generated check; bump it whenever a prompt file changes (the column holds 32 characters). */
    public static final String PROMPT_VERSION = "metis-v1";

    /** The system prompt of a generation and of a regeneration. */
    static final String SYSTEM_PROMPT_TEMPLATE = "prompts/metis/generate_questions_system.st";

    /** The user prompt of a generation. */
    static final String USER_PROMPT_TEMPLATE = "prompts/metis/generate_questions_user.st";

    /** The user prompt of a regeneration. */
    static final String REGENERATION_PROMPT_TEMPLATE = "prompts/metis/regenerate_question_user.st";

    /** The longest question text that is stored. */
    static final int MAX_QUESTION_CHARACTERS = 2000;

    /** How often one question may be regenerated; the column is a tinyint. */
    static final int MAX_REGENERATIONS = 20;

    /** Stands for the full content of a focus file that is too large for the prompt; its diff follows. */
    static final String CONTENT_OMITTED = "(full file omitted, too large)";

    /** Stands for an empty list or a missing value in a prompt. */
    static final String NONE = "(none)";

    /** Stands for a programming language the exercise does not name. */
    private static final String UNKNOWN_LANGUAGE = "(unknown)";

    /** The list of changed files takes at most this part of {@code max-prompt-characters}: a quarter. */
    private static final int CHANGED_FILES_BUDGET_DIVISOR = 4;

    /** The shortest a diff of a focus file is cut to for the prompt budget, about fifty lines of code; a shorter diff stays whole. */
    static final int MIN_FOCUS_DIFF_CHARACTERS = 2000;

    /** The last line of a list of changed files that leaves files out, filled with their number. */
    private static final String MORE_CHANGED_FILES = "... and %d more changed files";

    private static final String ENTITY_NAME = "metis";

    /** The heading of a focus file block, filled with the question number, the path and the added and removed lines. */
    private static final String FOCUS_FILE_HEADING = "### Question %d: focus file %s (%d lines added, %d removed)";

    /** A focus file that an earlier question has already, filled with the question number, the number of the earlier question and the path. */
    private static final String REPEATED_FOCUS_FILE = "### Question %d: same focus file as question %d (%s); ask about a different part of it.";

    /** The numbered content of a focus file, fenced as data; filled with the question number, the content and the question number. */
    private static final String STUDENT_FILE_BLOCK = """
            --- BEGIN STUDENT FILE %d ---
            %s
            --- END STUDENT FILE %d ---""";

    /** The diff of a focus file against the template, fenced as data; filled with the question number, the diff and the question number. */
    private static final String DIFF_BLOCK = """
            --- BEGIN DIFF %d (+ marks lines the student added, - lines the student removed) ---
            %s
            --- END DIFF %d ---""";

    /** One other question of the student in a regeneration prompt, fenced as data; filled with its number, its text and its number. */
    private static final String OTHER_QUESTION_BLOCK = """
            --- BEGIN OTHER QUESTION %d ---
            %s
            --- END OTHER QUESTION %d ---""";

    /** The difficulty of the questions, for the five steps of the complexity setting. */
    private static final List<String> COMPLEXITY_INSTRUCTIONS = List.of("Ask what one specific statement, condition or variable in the student's code does.",
            "Ask what a small block of the student's code does and why.",
            "Ask about the behaviour of the student's code, for example its result for a concrete small input or the purpose of a loop or method.",
            "Ask for reasoning across several lines: an edge case, an invariant, or what would change if a specific line changed.",
            "Ask about design decisions and their consequences: correctness arguments, alternatives, complexity or the effect of a non-trivial change.");

    /** The stored states in which an editor may still change the questions of a check: generated and not started. */
    private static final Set<MetisCheckStatus> EDITABLE_STATES = Set.of(MetisCheckStatus.IN_REVIEW, MetisCheckStatus.READY);

    /** Line breaks, which a value that is shown on one line of a prompt (the title of the exercise) must not contain. */
    private static final Pattern LINE_BREAKS = Pattern.compile("\\R");

    /** Control characters other than line feed, carriage return and tab, which a stored question text must not contain. */
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");

    private final MetisProperties properties;

    private final MetisCheckRepository checkRepository;

    private final MetisQuestionRepository questionRepository;

    private final MetisExerciseConfigRepository configRepository;

    private final MetisWindowService windowService;

    private final MetisAiConsentService consentService;

    private final MetisDiffService diffService;

    private final MetisPromptTemplateService templateService;

    private final MetisLlmService llmService;

    private final UserRepository userRepository;

    private final BeanOutputConverter<GeneratedQuestionsOutput> outputConverter = new BeanOutputConverter<>(GeneratedQuestionsOutput.class);

    public MetisQuestionGenerationService(MetisProperties properties, MetisCheckRepository checkRepository, MetisQuestionRepository questionRepository,
            MetisExerciseConfigRepository configRepository, MetisWindowService windowService, MetisAiConsentService consentService, MetisDiffService diffService,
            MetisPromptTemplateService templateService, MetisLlmService llmService, UserRepository userRepository) {
        this.properties = properties;
        this.checkRepository = checkRepository;
        this.questionRepository = questionRepository;
        this.configRepository = configRepository;
        this.windowService = windowService;
        this.consentService = consentService;
        this.diffService = diffService;
        this.templateService = templateService;
        this.llmService = llmService;
        this.userRepository = userRepository;
    }

    /**
     * What the generation of one check achieved, which the dispatcher acts on.
     */
    public enum Outcome {
        /** The questions are stored and the check is ready or in review. */
        GENERATED,
        /** The check is skipped for a reason that is no failure: a closed window, missing consent, no submission or no own code. */
        SKIPPED,
        /** The check failed: the repository, the LLM or the server could not produce questions. */
        FAILED,
        /** Nothing was decided: the check left the generation meanwhile, or it went back into the queue because it cannot be generated yet. */
        DEFERRED,
        /**
         * The LLM endpoint was unavailable: the check went back into the queue for a later attempt, or it failed if this was its last attempt. Either way the
         * next check would meet the same endpoint.
         */
        LLM_UNAVAILABLE
    }

    /**
     * The output the model is asked for: one question per focus file.
     *
     * @param questions the questions in the order of the question numbers of the prompt
     */
    record GeneratedQuestionsOutput(@JsonPropertyDescription("Exactly one entry per question number in the prompt, in that order") List<GeneratedQuestionOutput> questions) {
    }

    /**
     * One question the model wrote.
     *
     * @param number           the question number of the prompt, starting at 1
     * @param question         the question for the student
     * @param timeLimitSeconds the seconds the student gets for it
     */
    record GeneratedQuestionOutput(@JsonPropertyDescription("The question number from the prompt, starting at 1") int number,
            @JsonPropertyDescription("The question for the student; Markdown allowed; at most 1000 characters") String question,
            @JsonPropertyDescription("Seconds the student gets for this question, within the range given in the instructions") int timeLimitSeconds) {
    }

    /**
     * The questions of one usable output together with the model that wrote them.
     *
     * @param questions the questions in the order of the question numbers
     * @param model     the model the endpoint reported
     */
    private record GeneratedQuestions(List<GeneratedQuestionOutput> questions, String model) {
    }

    /**
     * Generates the questions of a check that the dispatcher has claimed. Nothing is read from the student's repository and the LLM is not called when the
     * check is skipped for a closed window or missing consent. The claim is already stored when this runs, so an unexpected exception fails the check with
     * {@link MetisFailureReason#INTERNAL_ERROR} even when the first read of the check throws; otherwise the check would stay in generation until the
     * scheduling node restarts.
     *
     * @param checkId     the check, claimed for generation
     * @param lastAttempt whether this is the last attempt the configuration allows when the LLM is unavailable; the check then fails instead of going back
     *                        into the queue, and the outcome is still {@link Outcome#LLM_UNAVAILABLE}
     * @return what the generation achieved
     */
    public Outcome generate(long checkId, boolean lastAttempt) {
        try {
            Optional<MetisGenerationTargetDTO> target = checkRepository.findGenerationTarget(checkId);
            if (target.isEmpty() || target.get().status() != MetisCheckStatus.GENERATING) {
                log.debug("The Metis check {} is not being generated (any more), so it is left alone", checkId);
                return Outcome.DEFERRED;
            }
            return generateIfPossible(target.get(), lastAttempt);
        }
        catch (RuntimeException e) {
            log.error("The generation of the Metis check {} failed unexpectedly", checkId, e);
            return end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.INTERNAL_ERROR, null);
        }
    }

    /**
     * Replaces one main question of a check during the review with a new one about the same focus file, using the interactive client without a retry. The new
     * text is not approved, and while the exercise requires a review the check stays in review during the call and afterwards, so the student never sees a text
     * nobody approved. When the call fails or is refused, a check that was ready before goes back to ready once all its main questions are approved.
     *
     * @param exerciseId the exercise the editor is authorized for
     * @param questionId the main question to replace
     * @return the replaced question as staff see it
     * @throws EntityNotFoundException           if the question does not belong to a check of the exercise
     * @throws BadRequestAlertException          {@code metis.notMainQuestion} for a follow-up question
     * @throws ConflictException                 {@code metis.questionShown}, {@code metis.regenerationLimit}, {@code metis.notEditable} if the student has started
     *                                               the check or it is neither ready nor in review, {@code metis.aiConsentMissing},
     *                                               {@code metis.repositoryUnavailable}, or {@code metis.questionChanged} if another editor changed the question
     *                                               meanwhile
     * @throws InternalServerErrorAlertException {@code metis.llmUnavailable} if the LLM is unavailable or refused the request, {@code metis.invalidLlmResponse}
     *                                               if its output is unusable
     */
    public MetisQuestionDTO regenerateQuestion(long exerciseId, long questionId) {
        MetisQuestion question = questionRepository.findByIdAndExerciseIdElseThrow(questionId, exerciseId);
        if (question.isFollowUp()) {
            throw new BadRequestAlertException("Follow-up questions cannot be reviewed.", ENTITY_NAME, "metis.notMainQuestion");
        }
        if (question.getShownAt() != null) {
            throw new ConflictException("The question has already been shown and can no longer be changed.", ENTITY_NAME, "metis.questionShown");
        }
        if (question.getRegenerationCount() >= MAX_REGENERATIONS) {
            throw new ConflictException("This question has been regenerated too often.", ENTITY_NAME, "metis.regenerationLimit");
        }
        MetisGenerationTargetDTO target = checkRepository.findGenerationTarget(question.getCheckId())
                .orElseThrow(() -> new EntityNotFoundException("MetisCheck", question.getCheckId()));
        if (!EDITABLE_STATES.contains(target.status())) {
            throw notEditable();
        }
        if (!consentService.hasConsent(target.studentId())) {
            throw new ConflictException("The student has not agreed to AI processing, so no new question can be generated.", ENTITY_NAME, "metis.aiConsentMissing");
        }
        // Every programming exercise has its configuration row; the defaults only stand in for an inconsistent database
        MetisExerciseConfig config = configRepository.findById(exerciseId).orElseGet(MetisExerciseConfig::new);
        FocusFileContext focusFile = diffService.readFocusFileContext(target.repositoryUri(), target.commitHash(), question.getFocusFilePath())
                .orElseThrow(() -> new ConflictException("The student's repository could not be read.", ENTITY_NAME, "metis.repositoryUnavailable"));
        // Keeps the student out while the model works, until their window opens (a check in review counts as ready from then on)
        boolean movedToReview = config.isReviewRequired() && moveToReview(target.checkId());
        boolean replaced = false;
        try {
            replace(question, target, config, focusFile);
            replaced = true;
            if (config.isReviewRequired()) {
                // Again: another editor's approval may have released the check while the model was working
                checkRepository.returnToReview(target.checkId());
            }
        }
        finally {
            if (movedToReview && !replaced) {
                // Nothing changed, so a check that was ready before is ready again
                checkRepository.releaseIfAllMainQuestionsApproved(target.checkId());
            }
        }
        return MetisQuestionDTO.ofStaff(questionRepository.findByIdElseThrow(questionId));
    }

    /**
     * Generates the questions of a claimed check once the preconditions are checked: the configuration is still enabled, the effective due date has passed,
     * and the window and the consent allow a check.
     */
    private Outcome generateIfPossible(MetisGenerationTargetDTO target, boolean lastAttempt) {
        long checkId = target.checkId();
        ZonedDateTime now = TimeUtil.now();
        Optional<MetisExerciseConfig> enabledConfig = configRepository.findById(target.exerciseId()).filter(MetisExerciseConfig::isEnabled);
        ZonedDateTime effectiveDueDate = target.effectiveDueDate();
        if (enabledConfig.isEmpty() || effectiveDueDate == null || effectiveDueDate.isAfter(now)) {
            // Switched off meanwhile, or an extension moved the due date into the future: the check is claimed again once it can be generated
            checkRepository.releaseGeneration(checkId);
            log.debug("The Metis check {} cannot be generated yet and goes back into the queue", checkId);
            return Outcome.DEFERRED;
        }
        MetisExerciseConfig config = enabledConfig.get();
        EffectiveWindow window = windowService.resolveForStudent(config, effectiveDueDate, target.exerciseId(), target.studentId());
        if (!window.hasTimeLeft(now, config.getMinSecondsPerQuestion())) {
            // Ended, empty (an extension past its end) or too short for one question: nobody could answer the questions
            return end(checkId, MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED, null);
        }
        if (!consentService.hasConsent(target.studentId())) {
            return end(checkId, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT, null);
        }
        // Leftovers of an interrupted earlier run; the check was never started, so they have no follow-ups
        questionRepository.deleteAllOfCheck(checkId);
        ZonedDateTime deadline = effectiveDueDate.plusSeconds(PROGRAMMING_GRACE_PERIOD_SECONDS);
        Optional<String> commitHash = checkRepository.findLatestCommitHashesUpTo(target.participationId(), deadline, PageRequest.of(0, 1)).stream().findFirst();
        if (commitHash.isEmpty()) {
            return end(checkId, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION, null);
        }
        return generateFromCommit(target, config, commitHash.get(), lastAttempt);
    }

    /**
     * Generates the questions from the student's own code at the submitted commit.
     */
    private Outcome generateFromCommit(MetisGenerationTargetDTO target, MetisExerciseConfig config, String commitHash, boolean lastAttempt) {
        long checkId = target.checkId();
        if (target.repositoryUri() == null) {
            return end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.REPOSITORY_UNAVAILABLE, commitHash);
        }
        OwnCodeDiff diff;
        try {
            diff = diffService.computeOwnCodeDiff(target.repositoryUri(), commitHash);
        }
        catch (RepositoryUnavailableException e) {
            return end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.REPOSITORY_UNAVAILABLE, commitHash);
        }
        if (diff.focusCandidates().isEmpty()) {
            return end(checkId, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_OWN_CODE, commitHash);
        }
        // Seeded with the check id, so that a run is reproducible
        List<FileChange> focusFiles = MetisDiffService.pickFocusFiles(diff, config.getMainQuestionCount(), new SplittableRandom(checkId));
        MetisExerciseContextDTO context = configRepository.findExerciseContext(target.exerciseId())
                .orElseThrow(() -> new IllegalStateException("The exercise " + target.exerciseId() + " of the Metis check " + checkId + " does not exist"));
        Optional<String> userPrompt = generationUserPrompt(config, context, diff, focusFiles);
        if (userPrompt.isEmpty()) {
            // The configured budget leaves no room for the student's code, and questions asked without it could not be about the student's own code
            return end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.INTERNAL_ERROR, commitHash);
        }
        String systemPrompt = systemPrompt(config, focusFiles.size());
        var attribution = new MetisLlmService.Attribution(target.courseId(), target.exerciseId(), target.studentId());
        GeneratedQuestions generated;
        try {
            generated = callWithOneRetry(systemPrompt, userPrompt.get(), focusFiles.size(), attribution, checkId);
        }
        catch (MetisLlmException e) {
            return onLlmFailure(checkId, e, commitHash, lastAttempt);
        }
        return store(target, config, commitHash, focusFiles, generated);
    }

    /**
     * Stores the generated questions and the snapshot of the run, and moves the check to ready or into review. A review that was switched on or off while the
     * model was working applies to this check as well.
     */
    private Outcome store(MetisGenerationTargetDTO target, MetisExerciseConfig config, String commitHash, List<FileChange> focusFiles, GeneratedQuestions generated) {
        long checkId = target.checkId();
        try {
            questionRepository.saveAll(toQuestions(checkId, generated.questions(), focusFiles, config));
        }
        catch (DataIntegrityViolationException e) {
            if (!questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(checkId).isEmpty()) {
                // For example a run of a node that stopped during a rolling restart; that run also moves the check on
                log.info("Another run stored the questions of the Metis check {} first", checkId);
                return Outcome.DEFERRED;
            }
            // The database refused the rows themselves, and no other run would ever move the check out of the generation
            log.error("The database refused the generated questions of the Metis check {}", checkId, e);
            return end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.INTERNAL_ERROR, null);
        }
        MetisCheckStatus nextStatus = config.isReviewRequired() ? MetisCheckStatus.IN_REVIEW : MetisCheckStatus.READY;
        if (checkRepository.markGenerated(checkId, nextStatus, commitHash, generated.model(), PROMPT_VERSION, TimeUtil.now()) == 0) {
            log.warn("The Metis check {} left the generation while its questions were generated; the next claim deletes them", checkId);
            return Outcome.DEFERRED;
        }
        // The configuration service saves its row first and moves the checks afterwards, while this check was written first: reading the configuration again
        // means that whichever of the two comes second sees the write of the other
        boolean reviewNow = configRepository.findById(target.exerciseId()).map(MetisExerciseConfig::isReviewRequired).orElse(config.isReviewRequired());
        if (reviewNow != config.isReviewRequired()) {
            if (reviewNow) {
                checkRepository.returnUnstartedToReviewOfExercise(target.exerciseId());
            }
            else {
                checkRepository.releaseAllInReviewOfExercise(target.exerciseId());
            }
        }
        log.debug("Generated {} questions for the Metis check {}", focusFiles.size(), checkId);
        return Outcome.GENERATED;
    }

    /**
     * Ends a generation after a failed LLM call. An unavailable endpoint puts the check back into the queue, unless this was the last attempt, which fails the
     * check; both report the unavailable endpoint, so that the dispatcher pauses before it claims the next check.
     */
    private Outcome onLlmFailure(long checkId, MetisLlmException failure, String commitHash, boolean lastAttempt) {
        return switch (failure.getKind()) {
            case UNAVAILABLE -> {
                if (lastAttempt) {
                    end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.LLM_UNAVAILABLE, commitHash);
                }
                else {
                    checkRepository.releaseGeneration(checkId);
                    log.info("The LLM endpoint was unavailable for the Metis check {}, which goes back into the queue", checkId);
                }
                yield Outcome.LLM_UNAVAILABLE;
            }
            case REJECTED -> end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.LLM_REJECTED, commitHash);
            case INVALID_OUTPUT -> end(checkId, MetisCheckStatus.FAILED, MetisFailureReason.INVALID_LLM_RESPONSE, commitHash);
        };
    }

    /**
     * Ends a generation without questions. The write is guarded by the generating state, so a check that was reset or deleted meanwhile stays as it is.
     */
    private Outcome end(long checkId, MetisCheckStatus status, MetisFailureReason reason, @Nullable String commitHash) {
        checkRepository.markUnavailable(checkId, status, reason.name(), commitHash);
        if (status == MetisCheckStatus.SKIPPED) {
            log.debug("The Metis check {} is skipped ({})", checkId, reason);
            return Outcome.SKIPPED;
        }
        log.info("The Metis check {} failed ({})", checkId, reason);
        return Outcome.FAILED;
    }

    /**
     * Asks the model for the questions with the batch client, and once more when its output is unusable.
     *
     * @throws MetisLlmException {@code INVALID_OUTPUT} after two unusable outputs, {@code UNAVAILABLE} or {@code REJECTED} if a call failed
     */
    private GeneratedQuestions callWithOneRetry(String systemPrompt, String userPrompt, int questionCount, MetisLlmService.Attribution attribution, long checkId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                MetisLlmService.Completion completion = llmService.complete(MetisLlmService.Pipeline.QUESTION_GENERATION, MetisLlmService.Mode.BATCH, systemPrompt, userPrompt,
                        attribution);
                Optional<List<GeneratedQuestionOutput>> questions = parseQuestions(completion.text(), questionCount);
                if (questions.isPresent()) {
                    return new GeneratedQuestions(questions.get(), completion.model());
                }
            }
            catch (MetisLlmException e) {
                // An empty answer is unusable output like any other; every other failure ends the attempt at once
                if (e.getKind() != MetisLlmException.Kind.INVALID_OUTPUT) {
                    throw e;
                }
            }
            log.debug("Attempt {} to generate the questions of the Metis check {} returned unusable output", attempt, checkId);
        }
        throw new MetisLlmException(MetisLlmException.Kind.INVALID_OUTPUT, "The LLM returned no usable questions for the Metis check " + checkId);
    }

    /**
     * Parses the output of the model: a leading reasoning block and anything around the JSON object are dropped. The output is unusable if it holds no JSON of
     * the expected shape, fewer questions than asked for, or a question among them that would be stored empty. Of more questions than asked for, the first
     * ones count; they are mapped by their numbers if these are exactly 1 to the count, otherwise by their position.
     *
     * @param text          the raw output of the model
     * @param questionCount the number of questions asked for
     * @return the questions in the order of their numbers, or empty if the output is unusable
     */
    Optional<List<GeneratedQuestionOutput>> parseQuestions(String text, int questionCount) {
        String json = MetisAnswerRatingService.extractJson(text);
        if (json == null) {
            return Optional.empty();
        }
        GeneratedQuestionsOutput output;
        try {
            output = outputConverter.convert(json);
        }
        catch (RuntimeException e) {
            // The message of the parser can quote the output, which may quote the student's code, so it is not logged
            log.debug("The generated questions are no valid JSON of the expected shape");
            return Optional.empty();
        }
        if (output == null || output.questions() == null || output.questions().size() < questionCount) {
            return Optional.empty();
        }
        List<GeneratedQuestionOutput> asked = output.questions().subList(0, questionCount);
        if (asked.stream().anyMatch(question -> question == null || question.question() == null || questionText(question.question()).isEmpty())) {
            return Optional.empty();
        }
        Set<Integer> numbers = asked.stream().map(GeneratedQuestionOutput::number).collect(Collectors.toSet());
        boolean numberedOneToCount = numbers.size() == questionCount && IntStream.rangeClosed(1, questionCount).allMatch(numbers::contains);
        return Optional.of(numberedOneToCount ? asked.stream().sorted(Comparator.comparingInt(GeneratedQuestionOutput::number)).toList() : List.copyOf(asked));
    }

    /**
     * Turns the generated questions into question rows: question n is about focus file n.
     */
    private static List<MetisQuestion> toQuestions(long checkId, List<GeneratedQuestionOutput> generated, List<FileChange> focusFiles, MetisExerciseConfig config) {
        List<MetisQuestion> questions = new ArrayList<>(focusFiles.size());
        for (int index = 0; index < focusFiles.size(); index++) {
            FileChange focusFile = focusFiles.get(index);
            GeneratedQuestionOutput output = generated.get(index);
            MetisQuestion question = new MetisQuestion();
            question.setCheckId(checkId);
            question.setQuestionOrder(index + 1);
            question.setFocusFilePath(focusFile.path());
            question.setFocusFileChangedLines(focusFile.changedLines());
            question.setQuestionText(questionText(output.question()));
            question.setTimeLimitSeconds(timeLimit(output.timeLimitSeconds(), config));
            question.setRegenerationCount(0);
            questions.add(question);
        }
        return questions;
    }

    /**
     * The text of a generated question as it is stored: without control characters other than line breaks and tabs (the model can write U+0000 as a JSON
     * escape, and PostgreSQL refuses to store that character), without surrounding whitespace, cut to {@link #MAX_QUESTION_CHARACTERS}.
     *
     * @param text the text the model wrote
     * @return the stored text, empty if nothing but whitespace and control characters remains
     */
    static String questionText(String text) {
        return MetisPromptTemplateService.truncate(CONTROL_CHARACTERS.matcher(text).replaceAll("").strip(), MAX_QUESTION_CHARACTERS);
    }

    /**
     * The time limit of a generated question as it is stored: clamped to the configured range; a missing or non-positive limit becomes the middle of the range,
     * rounded to ten seconds.
     *
     * @param requestedSeconds the limit the model proposed
     * @param config           the configuration with the shortest and the longest limit
     * @return the stored limit in seconds
     */
    static int timeLimit(int requestedSeconds, MetisExerciseConfig config) {
        int lower = Math.min(config.getMinSecondsPerQuestion(), config.getMaxSecondsPerQuestion());
        int upper = Math.max(config.getMinSecondsPerQuestion(), config.getMaxSecondsPerQuestion());
        int seconds = requestedSeconds > 0 ? requestedSeconds : (int) Math.round((lower + upper) / 20.0) * 10;
        return Math.clamp(seconds, lower, upper);
    }

    /**
     * The system prompt of a generation or a regeneration: how many questions, how demanding, how long the answers and the time range.
     */
    private String systemPrompt(MetisExerciseConfig config, int questionCount) {
        return templateService.render(SYSTEM_PROMPT_TEMPLATE,
                Map.of("questionCount", questionCount, "complexityInstruction", complexityInstruction(config.getComplexity()), "answerLengthInstruction",
                        MetisAnswerRatingService.answerLengthInstruction(config.getAnswerLength()), "minSeconds", config.getMinSecondsPerQuestion(), "maxSeconds",
                        config.getMaxSecondsPerQuestion()));
    }

    /**
     * The difficulty of the questions for the prompt.
     *
     * @param complexity the step of the complexity setting, from 1 to 5; values outside are clamped
     * @return the instruction for that step
     */
    static String complexityInstruction(int complexity) {
        return COMPLEXITY_INSTRUCTIONS.get(Math.clamp(complexity, 1, COMPLEXITY_INSTRUCTIONS.size()) - 1);
    }

    /**
     * The user prompt of a generation, kept within {@code max-prompt-characters}. The list of changed files takes at most a quarter of the budget. When the
     * prompt is too long, the full contents of the focus files are left out, the largest first; when that is not enough, the part of every diff above
     * {@link #MIN_FOCUS_DIFF_CHARACTERS} is cut by the same share, so that every question keeps code the student wrote. The block of every question stays.
     *
     * @return the prompt, or empty if the rest of the prompt leaves less room than the shortest diffs need, which only a small configured budget can cause
     */
    private Optional<String> generationUserPrompt(MetisExerciseConfig config, MetisExerciseContextDTO context, OwnCodeDiff diff, List<FileChange> focusFiles) {
        int budget = properties.generation().maxPromptCharacters();
        Map<String, Object> variables = new HashMap<>(headerVariables(config, context));
        variables.put("changedFiles", changedFiles(diff.changedFiles(), focusFiles, budget / CHANGED_FILES_BUDGET_DIVISOR));
        variables.put("format", outputConverter.getFormat());
        // The prompt shows each file once, keyed by its path; a file that two questions share appears in the block of the first one
        Map<String, String> contents = new HashMap<>();
        Map<String, String> diffs = new HashMap<>();
        for (FileChange focusFile : focusFiles) {
            if (focusFile.content() != null) {
                contents.putIfAbsent(focusFile.path(), MetisPromptTemplateService.numberLines(MetisPromptTemplateService.sanitize(focusFile.content())));
            }
            diffs.putIfAbsent(focusFile.path(), MetisPromptTemplateService.sanitize(focusFile.unifiedDiff()).stripTrailing());
        }
        String prompt = renderGenerationUserPrompt(variables, focusFiles, contents, diffs);
        List<String> largestContentFirst = contents.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getValue().length()).reversed().thenComparing(Map.Entry::getKey)).map(Map.Entry::getKey)
                .toList();
        for (String path : largestContentFirst) {
            if (prompt.length() <= budget) {
                return Optional.of(prompt);
            }
            contents.remove(path);
            prompt = renderGenerationUserPrompt(variables, focusFiles, contents, diffs);
        }
        if (prompt.length() <= budget) {
            return Optional.of(prompt);
        }
        // Every diff appears once in the prompt, so the rest of the prompt keeps its length when the diffs are cut
        int diffCharacters = diffs.values().stream().mapToInt(String::length).sum();
        int available = budget - (prompt.length() - diffCharacters);
        Map<String, Integer> minimumLengths = new HashMap<>();
        diffs.forEach((path, fileDiff) -> minimumLengths.put(path, Math.min(fileDiff.length(), MIN_FOCUS_DIFF_CHARACTERS)));
        int minimumCharacters = minimumLengths.values().stream().mapToInt(Integer::intValue).sum();
        if (available < minimumCharacters) {
            log.error("The Metis generation prompt leaves {} of the {} characters of max-prompt-characters for the diffs of the focus files, which need at least {}",
                    Math.max(available, 0), budget, minimumCharacters);
            return Optional.empty();
        }
        // The prompt is over the budget, so the diffs are longer than the room left, and their parts above the minimum are not empty: the share is below 1
        double share = (available - minimumCharacters) / (double) (diffCharacters - minimumCharacters);
        diffs.replaceAll((path, fileDiff) -> {
            int minimumLength = minimumLengths.get(path);
            return cutDiff(fileDiff, minimumLength + (int) Math.floor((fileDiff.length() - minimumLength) * share));
        });
        return Optional.of(renderGenerationUserPrompt(variables, focusFiles, contents, diffs));
    }

    /**
     * Cuts a sanitized diff to at most the given number of characters. The cut can leave a last line that looks like a delimiter, so the result is sanitized
     * again; the replacement can be a few characters longer than that line, so it is cut once more, which only shortens the replacement.
     */
    private static String cutDiff(String diff, int maxCharacters) {
        String cut = MetisPromptTemplateService.sanitize(MetisPromptTemplateService.truncate(diff, maxCharacters));
        return MetisPromptTemplateService.truncate(cut, maxCharacters);
    }

    private String renderGenerationUserPrompt(Map<String, Object> variables, List<FileChange> focusFiles, Map<String, String> contents, Map<String, String> diffs) {
        Map<String, Object> withFocusFiles = new HashMap<>(variables);
        withFocusFiles.put("focusFiles", focusFileBlocks(focusFiles, contents, diffs));
        return templateService.render(USER_PROMPT_TEMPLATE, withFocusFiles);
    }

    /**
     * The block of every question: its focus file with the numbered content, unless it is too large or was left out for the budget, and its diff. A file that
     * an earlier question has already only names that question.
     */
    private static String focusFileBlocks(List<FileChange> focusFiles, Map<String, String> contents, Map<String, String> diffs) {
        List<String> blocks = new ArrayList<>(focusFiles.size());
        Map<String, Integer> firstQuestionOfPath = new HashMap<>();
        for (int index = 0; index < focusFiles.size(); index++) {
            int number = index + 1;
            FileChange focusFile = focusFiles.get(index);
            String path = MetisPromptTemplateService.sanitize(focusFile.path());
            Integer firstQuestion = firstQuestionOfPath.putIfAbsent(focusFile.path(), number);
            if (firstQuestion != null) {
                blocks.add(String.format(Locale.ROOT, REPEATED_FOCUS_FILE, number, firstQuestion, path));
                continue;
            }
            String heading = String.format(Locale.ROOT, FOCUS_FILE_HEADING, number, path, focusFile.insertedLines(), focusFile.deletedLines());
            String content = contents.get(focusFile.path());
            String contentBlock = content != null ? String.format(Locale.ROOT, STUDENT_FILE_BLOCK, number, content, number) : CONTENT_OMITTED;
            String diffBlock = String.format(Locale.ROOT, DIFF_BLOCK, number, diffs.getOrDefault(focusFile.path(), ""), number);
            blocks.add(heading + "\n" + contentBlock + "\n" + diffBlock);
        }
        return String.join("\n\n", blocks);
    }

    /**
     * One line per changed file of the student's own code, {@code path: +inserted -deleted}, in the order of the paths. A list that would be longer than the
     * given number of characters names the focus files and, as far as they fit, the files with the most changed lines, and ends with the number of the files
     * it leaves out.
     *
     * @param changedFiles  the changed files, sorted by path
     * @param focusFiles    the focus files of the questions, which the list always names
     * @param maxCharacters the longest the list may be, unless the lines of the focus files alone are longer
     * @return the list, or {@link #NONE} without changed files
     */
    private static String changedFiles(List<FileChange> changedFiles, List<FileChange> focusFiles, int maxCharacters) {
        if (changedFiles.isEmpty()) {
            return NONE;
        }
        Map<String, String> lineOfPath = new HashMap<>();
        changedFiles.forEach(file -> lineOfPath.put(file.path(), changedFileLine(file)));
        String allFiles = changedFiles.stream().map(file -> lineOfPath.get(file.path())).collect(Collectors.joining("\n"));
        if (allFiles.length() <= maxCharacters) {
            return allFiles;
        }
        Set<String> focusPaths = focusFiles.stream().map(FileChange::path).collect(Collectors.toSet());
        Comparator<FileChange> mostRelevantFirst = Comparator.comparing((FileChange file) -> !focusPaths.contains(file.path()))
                .thenComparing(Comparator.comparingInt(FileChange::changedLines).reversed()).thenComparing(FileChange::path);
        int reserved = String.format(Locale.ROOT, MORE_CHANGED_FILES, changedFiles.size()).length();
        Set<String> listedPaths = new HashSet<>();
        int length = 0;
        for (FileChange file : changedFiles.stream().sorted(mostRelevantFirst).toList()) {
            // A line with the line break that follows it
            int lineLength = lineOfPath.get(file.path()).length() + 1;
            if (focusPaths.contains(file.path()) || length + lineLength + reserved <= maxCharacters) {
                listedPaths.add(file.path());
                length += lineLength;
            }
        }
        List<String> lines = new ArrayList<>(changedFiles.stream().filter(file -> listedPaths.contains(file.path())).map(file -> lineOfPath.get(file.path())).toList());
        if (listedPaths.size() < changedFiles.size()) {
            lines.add(String.format(Locale.ROOT, MORE_CHANGED_FILES, changedFiles.size() - listedPaths.size()));
        }
        return String.join("\n", lines);
    }

    private static String changedFileLine(FileChange file) {
        return String.format(Locale.ROOT, "%s: +%d -%d", MetisPromptTemplateService.sanitize(file.path()), file.insertedLines(), file.deletedLines());
    }

    /**
     * The variables both user prompts start with: the title, the programming language, the problem statement and the instructor's hint, each sanitized.
     */
    private Map<String, Object> headerVariables(MetisExerciseConfig config, MetisExerciseContextDTO context) {
        String title = LINE_BREAKS.matcher(MetisPromptTemplateService.sanitize(context.title())).replaceAll(" ");
        String language = context.programmingLanguage() != null ? context.programmingLanguage().name() : UNKNOWN_LANGUAGE;
        // Cut before sanitizing, so that the cut cannot produce a line that looks like a delimiter
        String problemStatement = MetisPromptTemplateService
                .sanitize(MetisPromptTemplateService.truncate(context.problemStatement(), properties.generation().maxProblemStatementCharacters()));
        String hint = MetisPromptTemplateService.sanitize(config.getInstructorHint());
        return Map.of("exerciseTitle", title.isBlank() ? NONE : title, "programmingLanguage", language, "problemStatement", problemStatement.isBlank() ? NONE : problemStatement,
                "instructorHint", hint.isBlank() ? NONE : hint);
    }

    /**
     * Moves a check into review while one of its questions is regenerated.
     *
     * @return true; the check is in review now
     * @throws ConflictException {@code metis.notEditable} if the check is not in review afterwards, because its student started it meanwhile
     */
    private boolean moveToReview(long checkId) {
        checkRepository.returnToReview(checkId);
        if (checkRepository.findByIdElseThrow(checkId).getStatus() != MetisCheckStatus.IN_REVIEW) {
            throw notEditable();
        }
        return true;
    }

    /**
     * Asks the model for a new question about the focus file and stores it, provided nobody changed the question or started the check meanwhile.
     */
    private void replace(MetisQuestion question, MetisGenerationTargetDTO target, MetisExerciseConfig config, FocusFileContext focusFile) {
        List<MetisQuestion> questions = questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(target.checkId());
        List<String> otherQuestions = MetisQuestionThreads.mainQuestions(questions).stream().filter(other -> !Objects.equals(other.getId(), question.getId()))
                .map(MetisQuestion::getQuestionText).toList();
        MetisExerciseContextDTO context = configRepository.findExerciseContext(target.exerciseId())
                .orElseThrow(() -> new EntityNotFoundException("ProgrammingExercise", target.exerciseId()));
        String systemPrompt = systemPrompt(config, 1);
        String userPrompt = regenerationUserPrompt(config, context, question, otherQuestions, focusFile);
        // A regeneration is billed to the editor who asked for it
        var attribution = new MetisLlmService.Attribution(target.courseId(), target.exerciseId(), userRepository.getUserIdElseThrow());
        GeneratedQuestionOutput output = regenerate(systemPrompt, userPrompt, attribution, question.getId());
        int replaced = questionRepository.replaceAfterRegeneration(question.getId(), questionText(output.question()), timeLimit(output.timeLimitSeconds(), config),
                question.getRegenerationCount());
        if (replaced == 0) {
            MetisCheck check = checkRepository.findByIdElseThrow(target.checkId());
            if (check.getStartedAt() != null || !EDITABLE_STATES.contains(check.getStatus())) {
                throw notEditable();
            }
            throw new ConflictException("The question was changed in the meantime. Reload and try again.", ENTITY_NAME, "metis.questionChanged");
        }
        log.debug("Regenerated the Metis question {}", question.getId());
    }

    /**
     * Makes the one interactive call of a regeneration; it is never retried, because an editor waits for it.
     *
     * @throws InternalServerErrorAlertException {@code metis.llmUnavailable} or {@code metis.invalidLlmResponse}
     */
    private GeneratedQuestionOutput regenerate(String systemPrompt, String userPrompt, MetisLlmService.Attribution attribution, long questionId) {
        try {
            MetisLlmService.Completion completion = llmService.complete(MetisLlmService.Pipeline.QUESTION_REGENERATION, MetisLlmService.Mode.INTERACTIVE, systemPrompt, userPrompt,
                    attribution);
            return parseQuestions(completion.text(), 1).map(List::getFirst)
                    .orElseThrow(() -> new MetisLlmException(MetisLlmException.Kind.INVALID_OUTPUT, "The LLM returned no usable question"));
        }
        catch (MetisLlmException e) {
            log.info("The regeneration of the Metis question {} failed as {}", questionId, e.getKind());
            if (e.getKind() == MetisLlmException.Kind.INVALID_OUTPUT) {
                throw new InternalServerErrorAlertException("The language model returned an unusable answer. Try again.", ENTITY_NAME, "metis.invalidLlmResponse");
            }
            throw new InternalServerErrorAlertException("The language model is not reachable at the moment. Try again later.", ENTITY_NAME, "metis.llmUnavailable");
        }
    }

    /**
     * The user prompt of a regeneration: the exercise, the old question, the other questions of the student and the focus file.
     */
    private String regenerationUserPrompt(MetisExerciseConfig config, MetisExerciseContextDTO context, MetisQuestion question, List<String> otherQuestions,
            FocusFileContext focusFile) {
        Map<String, Object> variables = new HashMap<>(headerVariables(config, context));
        variables.put("oldQuestion", MetisPromptTemplateService.sanitize(question.getQuestionText()));
        variables.put("otherQuestions", otherQuestions(otherQuestions));
        variables.put("path", MetisPromptTemplateService.sanitize(focusFile.path()));
        variables.put("changedLines", focusFile.changedLines());
        variables.put("focusFileBlock", focusFileBlock(focusFile));
        variables.put("format", outputConverter.getFormat());
        return templateService.render(REGENERATION_PROMPT_TEMPLATE, variables);
    }

    /**
     * The focus file of a regeneration: its numbered content, unless it is too large, and its diff.
     */
    private static String focusFileBlock(FocusFileContext focusFile) {
        String content = focusFile.content() != null
                ? String.format(Locale.ROOT, STUDENT_FILE_BLOCK, 1, MetisPromptTemplateService.numberLines(MetisPromptTemplateService.sanitize(focusFile.content())), 1)
                : CONTENT_OMITTED;
        String diff = focusFile.unifiedDiff() != null ? MetisPromptTemplateService.sanitize(focusFile.unifiedDiff()).stripTrailing() : NONE;
        return content + "\n" + String.format(Locale.ROOT, DIFF_BLOCK, 1, diff, 1);
    }

    /**
     * The other main questions of the student, each fenced as data, or a note that there are none.
     */
    private static String otherQuestions(List<String> otherQuestions) {
        if (otherQuestions.isEmpty()) {
            return NONE;
        }
        List<String> blocks = new ArrayList<>(otherQuestions.size());
        for (int index = 0; index < otherQuestions.size(); index++) {
            int number = index + 1;
            blocks.add(String.format(Locale.ROOT, OTHER_QUESTION_BLOCK, number, MetisPromptTemplateService.sanitize(otherQuestions.get(index)), number));
        }
        return String.join("\n", blocks);
    }

    private static ConflictException notEditable() {
        return new ConflictException("The questions of this student can no longer be changed.", ENTITY_NAME, "metis.notEditable");
    }
}
