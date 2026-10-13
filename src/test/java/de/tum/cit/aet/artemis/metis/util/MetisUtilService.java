package de.tum.cit.aet.artemis.metis.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;

/**
 * Writes Metis rows for integration tests: exercise configurations, group windows, checks and questions.
 * <p>
 * Each method writes the columns its parameters name, plus the columns every row of that kind has in production: a started check has its start time, a
 * completed check its completion time, a generated check its snapshot, and a follow-up is shown from the moment it exists. Timestamps the caller does not pass
 * come from {@link TimeUtil#now()}, so they follow a clock a test has frozen. Everything else keeps the entity's defaults; a test that needs more sets it on the
 * returned entity and saves it again.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
@Conditional(MetisEnabled.class)
public class MetisUtilService {

    /** The model a generated fixture check records, the same id the canned LLM responses of the tests report. */
    public static final String TEST_LLM_MODEL = "test-model";

    /** The prompt version a generated fixture check records. */
    public static final String TEST_PROMPT_VERSION = "metis-v1";

    private final MetisExerciseConfigRepository configRepository;

    private final MetisGroupWindowRepository groupWindowRepository;

    private final MetisCheckRepository checkRepository;

    private final MetisQuestionRepository questionRepository;

    public MetisUtilService(MetisExerciseConfigRepository configRepository, MetisGroupWindowRepository groupWindowRepository, MetisCheckRepository checkRepository,
            MetisQuestionRepository questionRepository) {
        this.configRepository = configRepository;
        this.groupWindowRepository = groupWindowRepository;
        this.checkRepository = checkRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * Creates the configuration of an exercise, or updates the one it has. Only the given settings change; an existing row keeps its other settings, a new row
     * gets the entity's defaults for them. Tests also use this to switch the check of an exercise off again after a method.
     *
     * @param exerciseId     the exercise
     * @param enabled        whether the check runs for the exercise
     * @param reviewRequired whether generated questions wait for an approval
     * @param windowStart    the start of the default window, or null to start at the due date
     * @param windowEnd      the end of the default window, or null for no end
     * @return the saved configuration
     */
    public MetisExerciseConfig saveConfig(long exerciseId, boolean enabled, boolean reviewRequired, @Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd) {
        MetisExerciseConfig config = configRepository.findById(exerciseId).orElseGet(MetisExerciseConfig::new);
        config.setExerciseId(exerciseId);
        config.setEnabled(enabled);
        config.setReviewRequired(reviewRequired);
        config.setWindowStart(windowStart);
        config.setWindowEnd(windowEnd);
        return configRepository.save(config);
    }

    /**
     * Creates a check that has not been generated: no commit, no model, no prompt version and no failure reason. A check in progress gets the current time as
     * its start, a completed check as its start and its completion.
     *
     * @param participationId the participation the check belongs to
     * @param status          the status of the check
     * @return the saved check
     */
    public MetisCheck saveCheck(long participationId, MetisCheckStatus status) {
        return checkRepository.save(newCheck(participationId, status));
    }

    /**
     * Creates a check whose questions have been generated: besides the status and the commit it records {@link #TEST_LLM_MODEL}, {@link #TEST_PROMPT_VERSION}
     * and the current time as its generation time. A check in progress gets the current time as its start, a completed check as its start and its completion.
     *
     * @param participationId the participation the check belongs to
     * @param status          the status of the check, usually ready, in review, in progress or completed
     * @param commitHash      the commit the questions were generated from
     * @return the saved check
     */
    public MetisCheck saveGeneratedCheck(long participationId, MetisCheckStatus status, String commitHash) {
        MetisCheck check = newCheck(participationId, status);
        check.setCommitHash(commitHash);
        check.setLlmModel(TEST_LLM_MODEL);
        check.setPromptVersion(TEST_PROMPT_VERSION);
        check.setGeneratedAt(TimeUtil.now());
        return checkRepository.save(check);
    }

    /**
     * Creates a main question that has been neither approved nor shown.
     *
     * @param checkId          the check the question belongs to
     * @param order            the order of the question inside the check
     * @param focusFilePath    the file the question is about
     * @param changedLines     the inserted plus deleted lines of that file
     * @param text             the question text
     * @param timeLimitSeconds the seconds the student gets once the question is shown
     * @return the saved question
     */
    public MetisQuestion saveMainQuestion(long checkId, int order, String focusFilePath, int changedLines, String text, int timeLimitSeconds) {
        MetisQuestion question = newQuestion(checkId, order, text, timeLimitSeconds);
        question.setFocusFilePath(focusFilePath);
        question.setFocusFileChangedLines(changedLines);
        return questionRepository.save(question);
    }

    /**
     * Creates a follow-up question. A follow-up is shown from the moment it is asked, so it gets the current time as its show time; it has no focus file of
     * its own.
     *
     * @param checkId          the check the question belongs to
     * @param order            the order of the question inside the check
     * @param parentQuestionId the main question it follows up on
     * @param text             the question text
     * @param timeLimitSeconds the seconds the student gets
     * @return the saved question
     */
    public MetisQuestion saveFollowUp(long checkId, int order, long parentQuestionId, String text, int timeLimitSeconds) {
        MetisQuestion question = newQuestion(checkId, order, text, timeLimitSeconds);
        question.setParentQuestionId(parentQuestionId);
        question.setShownAt(TimeUtil.now());
        return questionRepository.save(question);
    }

    /**
     * Creates the window of a tutorial group for an exercise. The window references the exercise's configuration, so a disabled configuration with default
     * settings is created first when the exercise has none.
     *
     * @param exerciseId      the exercise
     * @param tutorialGroupId the tutorial group
     * @param start           the start of the window
     * @param end             the end of the window
     * @return the saved window
     */
    public MetisGroupWindow saveGroupWindow(long exerciseId, long tutorialGroupId, ZonedDateTime start, ZonedDateTime end) {
        if (!configRepository.existsById(exerciseId)) {
            saveConfig(exerciseId, false, false, null, null);
        }
        MetisGroupWindow window = new MetisGroupWindow();
        window.setExerciseId(exerciseId);
        window.setTutorialGroupId(tutorialGroupId);
        window.setWindowStart(start);
        window.setWindowEnd(end);
        return groupWindowRepository.save(window);
    }

    private static MetisCheck newCheck(long participationId, MetisCheckStatus status) {
        MetisCheck check = new MetisCheck();
        check.setParticipationId(participationId);
        check.setStatus(status);
        if (status == MetisCheckStatus.IN_PROGRESS || status == MetisCheckStatus.COMPLETED) {
            check.setStartedAt(TimeUtil.now());
        }
        if (status == MetisCheckStatus.COMPLETED) {
            check.setCompletedAt(TimeUtil.now());
        }
        return check;
    }

    private static MetisQuestion newQuestion(long checkId, int order, String text, int timeLimitSeconds) {
        MetisQuestion question = new MetisQuestion();
        question.setCheckId(checkId);
        question.setQuestionOrder(order);
        question.setQuestionText(text);
        question.setTimeLimitSeconds(timeLimitSeconds);
        return question;
    }
}
