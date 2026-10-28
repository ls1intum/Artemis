package de.tum.cit.aet.artemis.metis.service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseConfigDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGroupWindowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisTutorialGroupDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;

/**
 * The Metis settings of one exercise as editors change them: the configuration with its validation, the windows of the tutorial groups, and the tutorial groups
 * of the course to choose them from.
 * <p>
 * The configuration is a permanent row. Every programming exercise owns one from the moment it is stored, switched off and with the defaults, whether or not
 * Metis is enabled on the server, so saving the settings changes that row in place with one guarded statement and never inserts or deletes it. Only a row that
 * a creation which failed half-way left out is added first, with the defaults, as the next update of the exercise would add it as well.
 * <p>
 * A saved setting can change what the existing checks of the exercise have to look like: without a review no check waits for one, with a review no ready check
 * that nobody has started keeps an unapproved main question, and a changed window gives the checks that were skipped for a closed window another chance. The
 * row is written first and the checks are moved afterwards with guarded bulk statements, while the generation worker writes the status of a check first and
 * reads the configuration afterwards. So whichever of the two comes second sees the write of the other, and a check that is generated while an editor switches
 * the review ends in the status the current setting demands.
 * <p>
 * The checks follow the review as it is stored after the write, not the change a request made: a parallel save can switch the review between the read and the
 * write of a request, so what the request read before is no reliable guide. Should two saves still interleave their statements so closely that the checks end
 * up behind the setting, the next save of the settings moves them.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisExerciseConfigService {

    private static final Logger log = LoggerFactory.getLogger(MetisExerciseConfigService.class);

    private static final String ENTITY_NAME = "metis";

    /** The reason of the checks that a changed window puts back into the queue. */
    private static final List<String> WINDOW_CLOSED_REASON = List.of(MetisFailureReason.WINDOW_CLOSED.name());

    private final MetisExerciseConfigRepository configRepository;

    private final MetisGroupWindowRepository groupWindowRepository;

    private final MetisCheckRepository checkRepository;

    public MetisExerciseConfigService(MetisExerciseConfigRepository configRepository, MetisGroupWindowRepository groupWindowRepository, MetisCheckRepository checkRepository) {
        this.configRepository = configRepository;
        this.groupWindowRepository = groupWindowRepository;
        this.checkRepository = checkRepository;
    }

    /**
     * The configuration of an exercise, read from the row the exercise owns. A programming exercise whose creation failed half-way lacks the row until a settings
     * request or the next update of the exercise adds it, and an exercise of another type never has one; both get the defaults the row starts with, switched
     * off. Nothing is written, because the exercise pages read the configuration of every programming exercise.
     *
     * @param exerciseId the exercise
     * @return the stored configuration, or the defaults when the exercise has no row
     */
    public MetisExerciseConfigDTO getConfig(long exerciseId) {
        return configRepository.findById(exerciseId).map(MetisExerciseConfigDTO::of).orElseGet(() -> MetisExerciseConfigDTO.defaults(exerciseId));
    }

    /**
     * Validates the requested configuration of an exercise and writes it into the row the exercise owns, then moves the existing checks to the stored settings:
     * without a review the checks in review are released, with a review the ready checks nobody has started and that still have an unapproved main question
     * return to it, and a changed window puts the checks skipped for a closed window back into the queue.
     * <p>
     * The review statement runs after every save, whether or not this request changed the review, because only the stored setting tells which way the checks
     * have to go: a parallel save may have switched the review and moved the checks after this request read the row. When the checks already follow the
     * setting, the statement changes nothing. The window, in contrast, is compared with the one read before the write, because putting the checks skipped for a
     * closed window back into the queue sends each of them through generation again, which only a changed window justifies.
     * <p>
     * One statement changes the row in place, and the same statement refuses a new rating scale once a student has started the check, so that no start can slip
     * in between a check and the write. A row that a failed creation left out is added with the defaults first. A missing due date is accepted, because the
     * settings form saves on every change; generation simply waits for one.
     *
     * @param exerciseId the exercise, which the path of the request names; the caller has checked that the body names the same one
     * @param config     the requested configuration, whose single fields bean validation has already checked
     * @return the configuration as it is stored afterwards
     * @throws BadRequestAlertException if the fields contradict each other, the check is switched on for an exercise that cannot have one, or the exercise is no
     *                                      programming exercise and therefore owns no configuration
     * @throws ConflictException        if the rating scale changes although a student has already started the check
     * @throws EntityNotFoundException  if the exercise is deleted while the request runs
     */
    public MetisExerciseConfigDTO updateConfig(long exerciseId, MetisExerciseConfigDTO config) {
        validateConfig(exerciseId, config);
        MetisExerciseConfig before = storedOrRepairedConfig(exerciseId);
        int updated = configRepository.updateSettings(exerciseId, config.enabled(), config.windowStart(), config.windowEnd(), config.mainQuestionCount(),
                config.maxFollowUpsPerQuestion(), config.minSecondsPerQuestion(), config.maxSecondsPerQuestion(), config.answerLength(), config.complexity(),
                config.likertScalePoints(), config.followUpThreshold(), config.reviewRequired(), config.instructorHint());
        if (updated == 0) {
            if (!configRepository.existsById(exerciseId)) {
                // The row was read a moment ago, and it only ever goes together with its exercise.
                throw new EntityNotFoundException("Exercise", exerciseId);
            }
            // The row exists, so the guard of the statement refused it: every score is read against the scale, which therefore stays as it is once the first
            // student has started the check on it.
            throw new ConflictException("The rating scale cannot change after students have started the check.", ENTITY_NAME, "metis.scaleLocked");
        }
        // The statement bypasses the persistence context, so the row is read again; what it holds now is what the checks have to follow.
        MetisExerciseConfig after = configRepository.findByIdElseThrow(exerciseId);
        moveChecksToTheReviewSetting(exerciseId, after.isReviewRequired());
        if (!isSameWindow(before, after)) {
            requeueChecksSkippedForAClosedWindow(exerciseId);
        }
        return MetisExerciseConfigDTO.of(after);
    }

    /**
     * The windows of the tutorial groups of an exercise.
     *
     * @param exerciseId the exercise
     * @return the windows in the order they were created, empty when every group uses the window of the configuration
     */
    public List<MetisGroupWindowDTO> getGroupWindows(long exerciseId) {
        return toDtos(groupWindowRepository.findAllByExerciseId(exerciseId));
    }

    /**
     * Replaces the windows of the tutorial groups of an exercise with the requested ones, keyed by the tutorial group: a stored window of a group that the
     * request names again is updated, a window of a group the request does not name is deleted, and a window of a new group is created. The {@code id} and
     * {@code exerciseId} of the requested windows are ignored; the path of the request decides the exercise.
     * <p>
     * The windows reference the configuration row the exercise owns, which this method never changes. Only a row that a creation which failed half-way left out
     * is added first, with the defaults and switched off. Afterwards the checks skipped for a closed window are put back into the queue, because one of the
     * windows changed. That happens on a conflict with a parallel request as well, because the windows this request deleted before the conflict stay deleted.
     *
     * @param exerciseId the exercise, which must be a programming exercise of a course
     * @param windows    the windows the exercise should have afterwards, each with a tutorial group, a start and an end
     * @return the stored windows in the order they were created
     * @throws BadRequestAlertException if the exercise is no programming exercise of a course, a group appears twice or belongs to another course, or a window
     *                                      does not end after it starts
     * @throws ConflictException        if another request changed the windows of the exercise at the same time
     */
    public List<MetisGroupWindowDTO> replaceGroupWindows(long exerciseId, List<MetisGroupWindowDTO> windows) {
        long courseId = configRepository.findExerciseFacts(exerciseId).map(MetisExerciseFactsDTO::courseId).orElseThrow(MetisExerciseConfigService::unsupportedExercise);
        Set<Long> tutorialGroupIds = new HashSet<>();
        for (MetisGroupWindowDTO window : windows) {
            if (!tutorialGroupIds.add(window.tutorialGroupId())) {
                throw new BadRequestAlertException("Each tutorial group can have only one window.", ENTITY_NAME, "metis.duplicateTutorialGroup");
            }
            if (!window.windowEnd().isAfter(window.windowStart())) {
                throw windowOrderViolated();
            }
        }
        if (!tutorialGroupIds.isEmpty() && groupWindowRepository.countTutorialGroupsOfCourse(courseId, tutorialGroupIds) != tutorialGroupIds.size()) {
            throw new BadRequestAlertException("The tutorial group does not belong to this course.", ENTITY_NAME, "metis.tutorialGroupNotInCourse");
        }
        if (!windows.isEmpty()) {
            // The key of a window references the configuration row; the facts above show that the exercise is a programming exercise, which owns one.
            configRepository.ensureExistsFor(exerciseId);
        }

        List<MetisGroupWindow> storedWindows = groupWindowRepository.findAllByExerciseId(exerciseId);
        Map<Long, MetisGroupWindow> storedByGroup = storedWindows.stream().collect(Collectors.toMap(MetisGroupWindow::getTutorialGroupId, Function.identity()));
        List<MetisGroupWindow> removed = storedByGroup.values().stream().filter(window -> !tutorialGroupIds.contains(window.getTutorialGroupId())).toList();
        List<MetisGroupWindow> requested = new ArrayList<>(windows.size());
        for (MetisGroupWindowDTO window : windows) {
            MetisGroupWindow row = storedByGroup.get(window.tutorialGroupId());
            if (row == null) {
                row = new MetisGroupWindow();
                row.setExerciseId(exerciseId);
                row.setTutorialGroupId(window.tutorialGroupId());
            }
            row.setWindowStart(window.windowStart());
            row.setWindowEnd(window.windowEnd());
            requested.add(row);
        }

        List<MetisGroupWindow> stored;
        try {
            groupWindowRepository.deleteAllInBatch(removed);
            stored = groupWindowRepository.saveAll(requested);
        }
        catch (DataIntegrityViolationException | ConcurrencyFailureException concurrentChange) {
            // A parallel request inserted a window for the same group, or deleted one this request updates. Its result stands; the editor reloads. The deletion
            // ran in a transaction of its own and stays, so the students of a deleted window may have a new one, and the client sends no further request after
            // the reload: the checks skipped for a closed window are judged again here, which is harmless when nothing changed.
            log.info("The group windows of exercise {} were changed by another request at the same time: {}", exerciseId, concurrentChange.getMessage());
            requeueChecksSkippedForAClosedWindow(exerciseId);
            throw new ConflictException("The windows were changed elsewhere at the same time. Reload the page.", ENTITY_NAME, "metis.concurrentModification");
        }
        requeueChecksSkippedForAClosedWindow(exerciseId);
        return toDtos(stored);
    }

    /**
     * The tutorial groups of a course, for which an editor can set a window.
     *
     * @param courseId the course
     * @return the groups of the course, ordered by title
     */
    public List<MetisTutorialGroupDTO> getTutorialGroups(long courseId) {
        return groupWindowRepository.findTutorialGroupsByCourseId(courseId);
    }

    /**
     * Checks the rules that relate the fields of a configuration to each other or to the exercise; bean validation has checked each field on its own.
     */
    private void validateConfig(long exerciseId, MetisExerciseConfigDTO config) {
        if (config.minSecondsPerQuestion() > config.maxSecondsPerQuestion()) {
            throw new BadRequestAlertException("The shortest time limit must not exceed the longest one.", ENTITY_NAME, "metis.timeLimitOrder");
        }
        int likertScalePoints = config.likertScalePoints();
        if (likertScalePoints != MetisExerciseConfig.MIN_LIKERT_POINTS && likertScalePoints != MetisExerciseConfig.MAX_LIKERT_POINTS) {
            throw new BadRequestAlertException("The rating scale must have 5 or 7 points.", ENTITY_NAME, "metis.likertScale");
        }
        if (config.followUpThreshold() < 1 || config.followUpThreshold() >= likertScalePoints) {
            throw new BadRequestAlertException("The follow-up threshold must be below the highest rating.", ENTITY_NAME, "metis.followUpThreshold");
        }
        if (config.windowStart() != null && config.windowEnd() != null && !config.windowEnd().isAfter(config.windowStart())) {
            throw windowOrderViolated();
        }
        if (Boolean.TRUE.equals(config.enabled()) && !canHaveChecks(exerciseId)) {
            throw unsupportedExercise();
        }
    }

    /**
     * Whether the check can run for an exercise: a programming exercise of a course, not of an exam, worked on by single students rather than teams.
     */
    private boolean canHaveChecks(long exerciseId) {
        return configRepository.findExerciseFacts(exerciseId).filter(facts -> facts.courseId() != null && facts.mode() == ExerciseMode.INDIVIDUAL).isPresent();
    }

    /**
     * The configuration row of an exercise as it is stored before a request changes it. A programming exercise whose creation failed between storing the
     * exercise and inserting its row gets the row with the defaults first; an exercise of another type owns no configuration and never gets one.
     */
    private MetisExerciseConfig storedOrRepairedConfig(long exerciseId) {
        Optional<MetisExerciseConfig> stored = configRepository.findById(exerciseId);
        if (stored.isPresent()) {
            return stored.get();
        }
        if (configRepository.findExerciseFacts(exerciseId).isEmpty()) {
            throw unsupportedExercise();
        }
        configRepository.ensureExistsFor(exerciseId);
        return configRepository.findByIdElseThrow(exerciseId);
    }

    /**
     * Moves the checks of an exercise that nobody has started to the stored review setting: without a review the checks in review are released, with a review
     * the ready checks that still have an unapproved main question return to it. Both statements are guarded and change nothing when the checks already follow
     * the setting, which they do unless the setting has just changed.
     */
    private void moveChecksToTheReviewSetting(long exerciseId, boolean reviewRequired) {
        if (reviewRequired) {
            int returned = checkRepository.returnUnstartedToReviewOfExercise(exerciseId);
            if (returned > 0) {
                log.debug("Returned {} ready Metis checks of exercise {} to review, because the review is required", returned, exerciseId);
            }
        }
        else {
            int released = checkRepository.releaseAllInReviewOfExercise(exerciseId);
            if (released > 0) {
                log.debug("Released {} Metis checks of exercise {} from review, because no review is required", released, exerciseId);
            }
        }
    }

    /**
     * Puts the checks of an exercise that were skipped for a closed window back into the queue after a window of the exercise changed. Their generation
     * judges them against the new window, without an LLM call if that one has closed as well.
     */
    private void requeueChecksSkippedForAClosedWindow(long exerciseId) {
        int requeued = checkRepository.requeueChecksOfExercise(exerciseId, WINDOW_CLOSED_REASON);
        log.debug("Requeued {} Metis checks of exercise {} that were skipped for a closed window, because a window changed", requeued, exerciseId);
    }

    /**
     * Maps windows in the order they were created, so that a window keeps its place in the settings form after a reload.
     */
    private static List<MetisGroupWindowDTO> toDtos(List<MetisGroupWindow> windows) {
        return windows.stream().sorted(Comparator.comparing(MetisGroupWindow::getId)).map(MetisGroupWindowDTO::of).toList();
    }

    /**
     * Whether two configurations have the same window, at the precision the database stores.
     */
    private static boolean isSameWindow(MetisExerciseConfig first, MetisExerciseConfig second) {
        return isSameInstant(first.getWindowStart(), second.getWindowStart()) && isSameInstant(first.getWindowEnd(), second.getWindowEnd());
    }

    /**
     * Whether two optional times denote the same instant at the precision the database stores, the millisecond. The zone does not matter: a client sends its
     * own offset, the database returns another one.
     */
    private static boolean isSameInstant(@Nullable ZonedDateTime first, @Nullable ZonedDateTime second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.toInstant().truncatedTo(ChronoUnit.MILLIS).equals(second.toInstant().truncatedTo(ChronoUnit.MILLIS));
    }

    private static BadRequestAlertException windowOrderViolated() {
        return new BadRequestAlertException("The window must end after it starts.", ENTITY_NAME, "metis.windowOrder");
    }

    private static BadRequestAlertException unsupportedExercise() {
        String message = "The comprehension check is available for individual programming exercises in courses only.";
        return new BadRequestAlertException(message, ENTITY_NAME, "metis.unsupportedExercise");
    }
}
