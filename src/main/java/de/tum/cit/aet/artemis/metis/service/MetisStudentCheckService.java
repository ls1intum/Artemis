package de.tum.cit.aet.artemis.metis.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseStatusDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisOwnParticipationDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisStudentCheckDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisAnswerRatingService.Rating;
import de.tum.cit.aet.artemis.metis.service.MetisWindowService.EffectiveWindow;

/**
 * The comprehension check of the requesting student: what the student's tab shows, starting the check, and answering its questions.
 * <p>
 * Every method acts for the current user and finds the user's graded participation through the exercise alone, so a student never names a participation and
 * can never reach another student's check. The timer runs on the server: a question is open from the moment it is shown for its time limit, cut to the end of
 * the student's window, and an answer that arrives later than a short grace after that is stored empty at the deadline.
 * <p>
 * Every state change is a guarded statement of the repositories, so a repeated or parallel request changes nothing twice. An answer is rated with one LLM
 * call while the student waits; a weak rating may bring a follow-up question, up to the configured number per main question, and otherwise the next main
 * question is shown or the check is completed. No new question is shown once the window leaves less time than one question needs. Reading the check repairs a
 * transition that a crash interrupted, but only once the latest answer is old enough that the request which stored it cannot still be waiting for its rating.
 * <p>
 * What the student sees is a presented status that can differ from the stored one: a check in review counts as ready once the student's window has opened
 * (unreviewed questions are released at the window start), a check whose effective due date lies in the future again counts as pending, and the reasons
 * {@code NOT_ENABLED}, {@code NOT_ELIGIBLE} and {@code CLOUD_AI_REQUIRED} are never stored. Unshown main questions, the review state and the generation
 * snapshot never reach the student; the ratings only once the window has ended, or once the check is completed when the window has no end.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisStudentCheckService {

    private static final Logger log = LoggerFactory.getLogger(MetisStudentCheckService.class);

    private static final String ENTITY_NAME = "metis";

    /** The longest text a follow-up question keeps, the same as a generated question. */
    static final int MAX_FOLLOW_UP_CHARACTERS = 2000;

    /** The course roles that make a participant staff rather than a student, whatever other role they hold. */
    private static final Set<CourseRole> STAFF_ROLES = Set.of(CourseRole.TEACHING_ASSISTANT, CourseRole.EDITOR, CourseRole.INSTRUCTOR);

    /**
     * The stored states in which a check has no generated questions to count: they are not generated yet, are being generated, or never will be. A check in
     * review has its questions and is counted by them, like a ready one.
     */
    private static final Set<MetisCheckStatus> WITHOUT_GENERATED_QUESTIONS = Set.of(MetisCheckStatus.PENDING, MetisCheckStatus.GENERATING, MetisCheckStatus.SKIPPED,
            MetisCheckStatus.FAILED);

    /** Prefers a participation that has a check, then the newest one. */
    private static final Comparator<MetisOwnParticipationDTO> OWN_PARTICIPATION_PREFERENCE = Comparator
            .comparing((MetisOwnParticipationDTO participation) -> participation.checkId() != null).thenComparingLong(MetisOwnParticipationDTO::participationId);

    private final MetisProperties properties;

    private final UserRepository userRepository;

    private final UserCourseRoleRepository userCourseRoleRepository;

    private final MetisExerciseConfigRepository configRepository;

    private final MetisCheckRepository checkRepository;

    private final MetisQuestionRepository questionRepository;

    private final MetisWindowService windowService;

    private final MetisAiConsentService consentService;

    private final MetisAnswerRatingService ratingService;

    public MetisStudentCheckService(MetisProperties properties, UserRepository userRepository, UserCourseRoleRepository userCourseRoleRepository,
            MetisExerciseConfigRepository configRepository, MetisCheckRepository checkRepository, MetisQuestionRepository questionRepository, MetisWindowService windowService,
            MetisAiConsentService consentService, MetisAnswerRatingService ratingService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.configRepository = configRepository;
        this.checkRepository = checkRepository;
        this.questionRepository = questionRepository;
        this.windowService = windowService;
        this.consentService = consentService;
        this.ratingService = ratingService;
    }

    /**
     * Whether an exercise has an enabled comprehension check, which decides whether the exercise page shows the check tab. A check can only be enabled for an
     * individual programming exercise of a course.
     *
     * @param exerciseId the exercise
     * @return the status with {@code enabled} true if the exercise has an enabled configuration
     */
    public MetisExerciseStatusDTO getExerciseStatus(long exerciseId) {
        return new MetisExerciseStatusDTO(configRepository.existsByExerciseIdAndEnabledTrue(exerciseId));
    }

    /**
     * The comprehension check of the current user on an exercise, as the student's tab shows it.
     * <p>
     * Reading writes in two cases only, both guarded and idempotent: a check skipped for missing consent goes back into the queue once the student consents,
     * and a check whose last transition a crash interrupted is repaired, by showing the next main question or completing the check.
     *
     * @param exerciseId the exercise
     * @return the presented check of the current user
     */
    public MetisStudentCheckDTO getOwnCheck(long exerciseId) {
        ZonedDateTime now = TimeUtil.now();
        Optional<MetisExerciseConfig> storedConfig = configRepository.findById(exerciseId);
        if (storedConfig.isEmpty() || !storedConfig.get().isEnabled()) {
            return notEnabled(storedConfig.orElseGet(MetisExerciseConfig::new));
        }
        MetisExerciseConfig config = storedConfig.get();
        Optional<MetisExerciseFactsDTO> facts = configRepository.findExerciseFacts(exerciseId).filter(found -> found.courseId() != null);
        if (facts.isEmpty()) {
            return notEnabled(config);
        }
        Optional<MetisOwnParticipationDTO> own = findOwnParticipation(exerciseId);
        long studentId = own.map(MetisOwnParticipationDTO::studentId).orElseGet(userRepository::getUserIdElseThrow);
        ZonedDateTime effectiveDueDate = own.map(MetisOwnParticipationDTO::effectiveDueDate).orElse(facts.get().dueDate());
        EffectiveWindow window = windowService.resolveForStudent(config, effectiveDueDate, exerciseId, studentId);
        if (own.isEmpty()) {
            return isPast(facts.get().dueDate(), now) ? withoutQuestions(MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION.name(), config, window)
                    : withoutQuestions(MetisCheckStatus.PENDING, null, config, window);
        }
        Optional<MetisCheck> check = checkRepository.findByParticipationId(own.get().participationId());
        if (check.isEmpty()) {
            return withoutCheck(own.get(), facts.get().courseId(), config, window, now);
        }
        return present(check.get(), config, window, effectiveDueDate, studentId, now);
    }

    /**
     * Starts the comprehension check of the current user and shows the first question. Starting a check that is in progress already, from a second click or a
     * second tab, changes nothing and returns it.
     *
     * @param exerciseId the exercise
     * @return the presented check with its first question open
     * @throws ConflictException             {@code metis.notEnabled} if the check is not enabled for the exercise, {@code metis.noCheck} if the user's participation has
     *                                           no check, {@code metis.notReady} if its questions are not ready or the effective due date lies in the future,
     *                                           {@code metis.windowNotOpen} before the student's window, {@code metis.windowClosed} after it or when it leaves less
     *                                           time than one question needs
     * @throws AccessForbiddenAlertException {@code metis.aiConsentRequired}, without an alert, if the user has not agreed to the AI processing the check needs
     */
    public MetisStudentCheckDTO startOwnCheck(long exerciseId) {
        ZonedDateTime now = TimeUtil.now();
        OwnCheck own = findOwnCheckElseThrow(exerciseId);
        MetisCheck check = own.check();
        if (check.getStatus() == MetisCheckStatus.IN_PROGRESS) {
            return getOwnCheck(exerciseId);
        }
        if (isFuture(own.participation().effectiveDueDate(), now)) {
            throw notReady();
        }
        MetisExerciseConfig config = own.config();
        EffectiveWindow window = windowService.resolveForStudent(config, own.participation().effectiveDueDate(), exerciseId, own.studentId());
        if (!window.hasOpened(now)) {
            throw new ConflictException("The comprehension check has not opened yet.", ENTITY_NAME, "metis.windowNotOpen");
        }
        if (!window.acceptsNewQuestion(now, config.getMinSecondsPerQuestion())) {
            throw windowClosed();
        }
        // A check in review counts as ready once the student's window has opened: unreviewed questions are released at the window start
        if (check.getStatus() != MetisCheckStatus.READY && check.getStatus() != MetisCheckStatus.IN_REVIEW) {
            throw notReady();
        }
        if (checkRepository.markStarted(check.getId(), now) == 0) {
            // A parallel request started the check first, or it left the ready states meanwhile
            if (checkRepository.findByIdElseThrow(check.getId()).getStatus() == MetisCheckStatus.IN_PROGRESS) {
                return getOwnCheck(exerciseId);
            }
            throw notReady();
        }
        List<MetisQuestion> questions = questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
        // Always the first main question, not the next unshown one: a parallel read may already have repaired the started check by showing the first question
        MetisQuestionThreads.mainQuestions(questions).stream().findFirst().ifPresent(first -> questionRepository.markShown(first.getId(), now));
        log.debug("The student {} started the Metis check {}", own.studentId(), check.getId());
        return getOwnCheck(exerciseId);
    }

    /**
     * Stores the current user's answer to a shown question of their comprehension check, rates it and moves the conversation on: a follow-up question when the
     * rating is at or below the threshold and the cap and the window allow one, else the next main question when the window leaves time for it, else the
     * completion of the check when no main question is left.
     * <p>
     * The answer is cut to the configured maximum, never rejected for its length. An answer that arrives more than the grace after the question's deadline is
     * stored empty at the deadline and rated 1 without the LLM. A repeated submission of an answered question changes nothing. When the rating fails, the
     * answer stays unrated for the rating backfill and the conversation moves on without a follow-up.
     *
     * @param exerciseId the exercise
     * @param questionId the question that is answered, which must belong to the user's own check
     * @param answerText the answer, possibly empty or missing (the client sends its draft when the timer expires)
     * @return the presented check after the answer
     * @throws ConflictException             {@code metis.notEnabled}, {@code metis.noCheck}, {@code metis.notReady} if the check is not in progress,
     *                                           {@code metis.notCurrentQuestion} if the question has not been shown, {@code metis.windowClosed} if the window
     *                                           ended more than the grace ago
     * @throws AccessForbiddenAlertException {@code metis.aiConsentRequired}, without an alert, if the user has not agreed to the AI processing the check needs
     * @throws EntityNotFoundException       if the question does not belong to the user's check
     */
    public MetisStudentCheckDTO submitAnswer(long exerciseId, long questionId, @Nullable String answerText) {
        ZonedDateTime now = TimeUtil.now();
        String text = MetisPromptTemplateService.truncate(Objects.requireNonNullElse(answerText, ""), properties.conversation().maxAnswerCharacters());
        OwnCheck own = findOwnCheckElseThrow(exerciseId);
        MetisCheck check = own.check();
        if (check.getStatus() == MetisCheckStatus.COMPLETED) {
            return getOwnCheck(exerciseId);
        }
        if (check.getStatus() != MetisCheckStatus.IN_PROGRESS) {
            throw notReady();
        }
        List<MetisQuestion> questions = questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
        MetisQuestion question = questions.stream().filter(candidate -> Objects.equals(candidate.getId(), questionId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("MetisQuestion", questionId));
        if (question.getAnsweredAt() != null) {
            return getOwnCheck(exerciseId);
        }
        ZonedDateTime shownAt = question.getShownAt();
        if (shownAt == null) {
            throw new ConflictException("This question is not the one currently open.", ENTITY_NAME, "metis.notCurrentQuestion");
        }
        MetisExerciseConfig config = own.config();
        EffectiveWindow window = windowService.resolveForStudent(config, own.participation().effectiveDueDate(), exerciseId, own.studentId());
        Duration grace = Duration.ofSeconds(properties.conversation().answerGraceSeconds());
        if (window.end() != null && now.isAfter(window.end().plus(grace))) {
            throw windowClosed();
        }
        ZonedDateTime deadline = deadlineOf(shownAt, question.getTimeLimitSeconds(), window);
        boolean late = now.isAfter(deadline.plus(grace));
        String storedAnswer = late ? "" : text;
        if (questionRepository.saveAnswer(questionId, storedAnswer, late ? deadline : now) == 0) {
            // A parallel request stored an answer first
            return getOwnCheck(exerciseId);
        }

        MetisQuestion mainQuestion = MetisAnswerRatingService.mainQuestionOf(question, questions);
        int minSeconds = config.getMinSecondsPerQuestion();
        boolean followUpAllowed = MetisQuestionThreads.followUpCount(questions, mainQuestion.getId()) < config.getMaxFollowUpsPerQuestion()
                && window.acceptsNewQuestion(now, minSeconds);
        Optional<Rating> rating = storedAnswer.isBlank() ? Optional.of(MetisAnswerRatingService.emptyAnswerRating())
                : rateLive(own, questions, mainQuestion, question, storedAnswer, followUpAllowed);
        // Zero rows: another writer rated the answer meanwhile; its score stands, and this request asks no follow-up on a rating that was not stored
        boolean rated = rating.isPresent() && questionRepository.saveRating(questionId, rating.get().score(), rating.get().rationale(), TimeUtil.now()) == 1;
        // The rating can take a minute, so a new question starts now rather than at the start of the request
        ZonedDateTime after = TimeUtil.now();

        // Nothing holds the check while the rating runs: a request that outlived the repair wait, or a parallel one, may have moved it on meanwhile
        MetisCheck currentCheck = checkRepository.findByIdElseThrow(check.getId());
        List<MetisQuestion> currentQuestions = questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
        if (currentCheck.getStatus() != MetisCheckStatus.IN_PROGRESS || MetisQuestionThreads.currentQuestion(currentQuestions).isPresent()) {
            return getOwnCheck(exerciseId);
        }
        String followUpQuestion = rating.map(Rating::followUpQuestion).orElse(null);
        boolean followUpDue = rated && rating.get().score() <= config.getFollowUpThreshold() && followUpAllowed && followUpQuestion != null && !followUpQuestion.isBlank()
                && window.acceptsNewQuestion(after, minSeconds);
        if (followUpDue) {
            askFollowUp(check.getId(), mainQuestion, followUpQuestion, rating.get().followUpTimeLimitSeconds(), currentQuestions, minSeconds, after);
        }
        else {
            showNextMainQuestionOrComplete(check.getId(), currentQuestions, window, minSeconds, after);
        }
        return getOwnCheck(exerciseId);
    }

    /**
     * The check of a participation that has none: the participation never gets one, because its user is no plain student of the course or because it has no
     * repository after the due date, or the poller has not created it yet.
     */
    private MetisStudentCheckDTO withoutCheck(MetisOwnParticipationDTO own, long courseId, MetisExerciseConfig config, EffectiveWindow window, ZonedDateTime now) {
        // The same rule as the selection of the participants: course roles add up, so a tutor can keep an old student role
        boolean student = userCourseRoleRepository.existsByUser_IdAndCourse_IdAndRole(own.studentId(), courseId, CourseRole.STUDENT);
        if (!student || userCourseRoleRepository.existsByUser_IdAndCourse_IdAndRoleIn(own.studentId(), courseId, STAFF_ROLES)) {
            return withoutQuestions(MetisCheckStatus.SKIPPED, MetisFailureReason.NOT_ELIGIBLE.name(), config, window);
        }
        if (own.repositoryUri() == null && isPast(own.effectiveDueDate(), now)) {
            return withoutQuestions(MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION.name(), config, window);
        }
        return withoutQuestions(MetisCheckStatus.PENDING, null, config, window);
    }

    /**
     * Presents an existing check: the status the student sees, and the questions once they may see them.
     */
    private MetisStudentCheckDTO present(MetisCheck check, MetisExerciseConfig config, EffectiveWindow window, @Nullable ZonedDateTime effectiveDueDate, long studentId,
            ZonedDateTime now) {
        MetisCheckStatus status = check.getStatus();
        if (check.getStartedAt() == null && isFuture(effectiveDueDate, now)) {
            // An extension moved the due date into the future again: the questions are generated again after it
            return withoutQuestions(MetisCheckStatus.PENDING, null, config, window);
        }
        if (status == MetisCheckStatus.SKIPPED && MetisFailureReason.NO_AI_CONSENT.name().equals(check.getFailureReason())) {
            return presentSkippedForMissingConsent(check, config, window, studentId);
        }
        if (WITHOUT_GENERATED_QUESTIONS.contains(status)) {
            boolean unavailable = status == MetisCheckStatus.SKIPPED || status == MetisCheckStatus.FAILED;
            return withoutQuestions(status, unavailable ? check.getFailureReason() : null, config, window);
        }
        List<MetisQuestion> questions = questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
        MetisCheck current = check;
        if (status == MetisCheckStatus.IN_PROGRESS && repairInterruptedTransition(check, questions, config, window, now)) {
            current = checkRepository.findByIdElseThrow(check.getId());
            questions = questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
        }
        // A check in review counts as ready once the student's window has opened: unreviewed questions are released at the window start
        boolean releasedAtWindowStart = status == MetisCheckStatus.IN_REVIEW && window.hasOpened(now);
        MetisCheckStatus presented = releasedAtWindowStart ? MetisCheckStatus.READY : current.getStatus();
        return withQuestions(presented, current.getStatus(), questions, config, window, now);
    }

    /**
     * A check skipped because the student had not agreed to AI processing: it goes back into the queue if the student agrees now, else the student sees why it
     * is skipped, with the reason that tells a student who allowed local AI only that this endpoint needs cloud AI consent.
     */
    private MetisStudentCheckDTO presentSkippedForMissingConsent(MetisCheck check, MetisExerciseConfig config, EffectiveWindow window, long studentId) {
        AiSelectionDecision decision = consentService.decisionOf(studentId);
        if (consentService.allows(decision)) {
            checkRepository.requeueSkippedChecks(List.of(check.getId()), MetisFailureReason.NO_AI_CONSENT.name());
            log.debug("The student {} consents to AI processing now, so the Metis check {} is queued again", studentId, check.getId());
            return withoutQuestions(MetisCheckStatus.PENDING, null, config, window);
        }
        return withoutQuestions(MetisCheckStatus.SKIPPED, consentService.skipReasonFor(decision), config, window);
    }

    /**
     * Repairs a started check that has no open question, because a crash interrupted a transition between two statements: shows the next main question if the
     * window leaves time for it, or completes the check if no main question is left. A check whose latest answer is younger than the longest an answer request
     * can take is left alone, because that request may still be waiting for its rating and will move the check on itself.
     *
     * @return whether anything was written
     */
    private boolean repairInterruptedTransition(MetisCheck check, List<MetisQuestion> questions, MetisExerciseConfig config, EffectiveWindow window, ZonedDateTime now) {
        if (MetisQuestionThreads.currentQuestion(questions).isPresent()) {
            return false;
        }
        Optional<ZonedDateTime> latestAnswer = questions.stream().map(MetisQuestion::getAnsweredAt).filter(Objects::nonNull).max(Comparator.comparing(ZonedDateTime::toInstant));
        if (latestAnswer.isPresent() && latestAnswer.get().plus(properties.answerSettleTime()).isAfter(now)) {
            return false;
        }
        Optional<MetisQuestion> next = MetisQuestionThreads.nextUnshownMain(questions);
        if (next.isPresent()) {
            if (!window.acceptsNewQuestion(now, config.getMinSecondsPerQuestion())) {
                return false;
            }
            questionRepository.markShown(next.get().getId(), now);
            log.info("Repaired the Metis check {}: showed the main question {} that an interrupted transition left unshown", check.getId(), next.get().getId());
            return true;
        }
        // No question is open and no main question is left, so every shown question is answered
        checkRepository.markCompleted(check.getId(), now);
        log.info("Repaired the Metis check {}: completed it after an interrupted transition", check.getId());
        return true;
    }

    /**
     * Rates an answer while the student waits, with the interactive client and without a retry.
     *
     * @return the rating, or empty if it failed; the answer then stays unrated for the rating backfill
     */
    private Optional<Rating> rateLive(OwnCheck own, List<MetisQuestion> questions, MetisQuestion mainQuestion, MetisQuestion question, String answer, boolean followUpAllowed) {
        var request = new MetisAnswerRatingService.RatingRequest(own.courseId(), own.config().getExerciseId(), own.studentId(), own.participation().repositoryUri(),
                own.check().getCommitHash(), own.config(), mainQuestion, MetisAnswerRatingService.threadBefore(questions, mainQuestion, question), question, answer,
                followUpAllowed, MetisLlmService.Mode.INTERACTIVE);
        try {
            return Optional.of(ratingService.rate(request));
        }
        catch (MetisLlmException e) {
            log.info("The live rating of the answer to Metis question {} failed as {}; the answer stays unrated until the backfill rates it", question.getId(), e.getKind());
        }
        catch (RuntimeException e) {
            log.error("The live rating of the answer to Metis question {} failed unexpectedly; the answer stays unrated until the backfill rates it", question.getId(), e);
        }
        return Optional.empty();
    }

    /**
     * Asks a follow-up question on the main question of the thread. It is shown at once, with the time limit the model proposed, or half the main question's
     * limit rounded to ten seconds, kept between the configuration's shortest limit (or the main question's limit if that is shorter) and the main question's
     * limit. A parallel request that asked a follow-up in the same order first wins.
     */
    private void askFollowUp(long checkId, MetisQuestion mainQuestion, String text, @Nullable Integer proposedTimeLimit, List<MetisQuestion> questions, int minSeconds,
            ZonedDateTime shownAt) {
        MetisQuestion followUp = new MetisQuestion();
        followUp.setCheckId(checkId);
        followUp.setQuestionOrder(MetisQuestionThreads.nextOrder(questions));
        followUp.setParentQuestionId(mainQuestion.getId());
        followUp.setQuestionText(MetisPromptTemplateService.truncate(text.strip(), MAX_FOLLOW_UP_CHARACTERS));
        followUp.setTimeLimitSeconds(followUpTimeLimit(proposedTimeLimit, mainQuestion.getTimeLimitSeconds(), minSeconds));
        followUp.setShownAt(shownAt);
        try {
            questionRepository.save(followUp);
        }
        catch (DataIntegrityViolationException e) {
            log.info("A parallel request asked a follow-up question in the Metis check {} first", checkId);
        }
    }

    /**
     * Shows the next main question if the window leaves time for it, or completes the check if no main question is left. When a main question is left but the
     * window has ended or leaves too little time, the check stays in progress without an open question.
     */
    private void showNextMainQuestionOrComplete(long checkId, List<MetisQuestion> questions, EffectiveWindow window, int minSeconds, ZonedDateTime now) {
        Optional<MetisQuestion> next = MetisQuestionThreads.nextUnshownMain(questions);
        if (next.isEmpty()) {
            checkRepository.markCompleted(checkId, now);
            return;
        }
        if (window.acceptsNewQuestion(now, minSeconds)) {
            questionRepository.markShown(next.get().getId(), now);
        }
    }

    /**
     * The time limit of a follow-up question: the model's proposal, or half the main question's limit rounded to ten seconds, kept between the shortest limit of
     * the configuration (or the main question's limit if that is shorter) and the main question's limit.
     *
     * @param proposedSeconds  the limit the model proposed, or null if it proposed none
     * @param mainTimeLimit    the limit of the main question
     * @param minSecondsConfig the shortest limit of the configuration
     * @return the limit of the follow-up question in seconds
     */
    static int followUpTimeLimit(@Nullable Integer proposedSeconds, int mainTimeLimit, int minSecondsConfig) {
        int requested = proposedSeconds != null ? proposedSeconds : (int) Math.round(mainTimeLimit / 20.0) * 10;
        return Math.clamp(requested, Math.min(minSecondsConfig, mainTimeLimit), mainTimeLimit);
    }

    /**
     * The deadline of a shown question: its time limit after it was shown, cut to the end of the window.
     */
    private static ZonedDateTime deadlineOf(ZonedDateTime shownAt, int timeLimitSeconds, EffectiveWindow window) {
        ZonedDateTime deadline = shownAt.plusSeconds(timeLimitSeconds);
        return window.end() != null && window.end().isBefore(deadline) ? window.end() : deadline;
    }

    /**
     * The check of the current user that start and answer act on, or the conflict that prevents them.
     */
    private OwnCheck findOwnCheckElseThrow(long exerciseId) {
        MetisExerciseConfig config = configRepository.findById(exerciseId).filter(MetisExerciseConfig::isEnabled).orElseThrow(MetisStudentCheckService::notEnabled);
        long courseId = configRepository.findExerciseFacts(exerciseId).map(MetisExerciseFactsDTO::courseId).orElseThrow(MetisStudentCheckService::notEnabled);
        MetisOwnParticipationDTO participation = findOwnParticipation(exerciseId).filter(own -> own.checkId() != null).orElseThrow(MetisStudentCheckService::noCheck);
        MetisCheck check = checkRepository.findByParticipationId(participation.participationId()).orElseThrow(MetisStudentCheckService::noCheck);
        if (!consentService.hasConsent(participation.studentId())) {
            // The panel explains the refusal itself and asks for the consent, so the client shows no alert for it
            throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "The comprehension check needs your consent to the processing of your code by an AI model.",
                    ENTITY_NAME, "metis.aiConsentRequired", true);
        }
        return new OwnCheck(config, courseId, participation, check);
    }

    /**
     * The graded participation of the current user in an exercise: the one with a check if there is one, else the newest.
     */
    private Optional<MetisOwnParticipationDTO> findOwnParticipation(long exerciseId) {
        String login = userRepository.getCurrentUserLogin();
        return checkRepository.findOwnGradedParticipations(exerciseId, login).stream().max(OWN_PARTICIPATION_PREFERENCE);
    }

    /**
     * A check without generated questions, counted by the configuration: pending, being generated, skipped or failed, or presented as such.
     */
    private static MetisStudentCheckDTO withoutQuestions(MetisCheckStatus status, @Nullable String failureReason, MetisExerciseConfig config, EffectiveWindow window) {
        return new MetisStudentCheckDTO(status, window.start(), window.end(), window.tutorialGroupTitle(), config.getMainQuestionCount(), config.getLikertScalePoints(), null,
                List.of(), false, failureReason);
    }

    /**
     * The check of an exercise that has no enabled comprehension check, with the counts of its configuration or the defaults.
     */
    private static MetisStudentCheckDTO notEnabled(MetisExerciseConfig config) {
        return new MetisStudentCheckDTO(MetisCheckStatus.SKIPPED, null, null, null, config.getMainQuestionCount(), config.getLikertScalePoints(), null, List.of(), false,
                MetisFailureReason.NOT_ENABLED.name());
    }

    /**
     * A check with generated questions, counted by its main questions: the open question while the check is in progress and the window has not ended, and the
     * answered questions in thread order, with their ratings once the ratings are visible. A check in review or ready has neither yet. Unshown main questions
     * are never included.
     */
    private static MetisStudentCheckDTO withQuestions(MetisCheckStatus presented, MetisCheckStatus stored, List<MetisQuestion> questions, MetisExerciseConfig config,
            EffectiveWindow window, ZonedDateTime now) {
        boolean ratingsVisible = window.end() != null ? window.hasEnded(now) : stored == MetisCheckStatus.COMPLETED;
        MetisQuestionDTO currentQuestion = stored == MetisCheckStatus.IN_PROGRESS && !window.hasEnded(now)
                ? MetisQuestionThreads.currentQuestion(questions).map(question -> MetisQuestionDTO.ofStudent(question, false)).orElse(null)
                : null;
        List<MetisQuestionDTO> answeredQuestions = MetisQuestionThreads.inThreadOrder(questions).stream().filter(question -> question.getAnsweredAt() != null)
                .map(question -> MetisQuestionDTO.ofStudent(question, ratingsVisible)).toList();
        int mainQuestionCount = MetisQuestionThreads.mainQuestions(questions).size();
        return new MetisStudentCheckDTO(presented, window.start(), window.end(), window.tutorialGroupTitle(), mainQuestionCount, config.getLikertScalePoints(), currentQuestion,
                answeredQuestions, ratingsVisible, null);
    }

    private static boolean isPast(@Nullable ZonedDateTime time, ZonedDateTime now) {
        return time != null && !time.isAfter(now);
    }

    private static boolean isFuture(@Nullable ZonedDateTime time, ZonedDateTime now) {
        return time != null && time.isAfter(now);
    }

    private static ConflictException notEnabled() {
        return new ConflictException("The comprehension check is not enabled for this exercise.", ENTITY_NAME, "metis.notEnabled");
    }

    private static ConflictException noCheck() {
        return new ConflictException("There is no comprehension check for your participation yet.", ENTITY_NAME, "metis.noCheck");
    }

    private static ConflictException notReady() {
        return new ConflictException("Your questions are not ready yet.", ENTITY_NAME, "metis.notReady");
    }

    private static ConflictException windowClosed() {
        return new ConflictException("The comprehension check has closed, or too little time is left to answer a question.", ENTITY_NAME, "metis.windowClosed");
    }

    /**
     * The check of the current user together with what start and answer need to act on it.
     *
     * @param config        the enabled configuration of the exercise
     * @param courseId      the course of the exercise
     * @param participation the user's graded participation
     * @param check         the check of that participation
     */
    private record OwnCheck(MetisExerciseConfig config, long courseId, MetisOwnParticipationDTO participation, MetisCheck check) {

        private long studentId() {
            return participation.studentId();
        }
    }
}
