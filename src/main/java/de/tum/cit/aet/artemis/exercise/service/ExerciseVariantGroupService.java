package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Keeps the timeline of an {@link ExerciseVariantGroup} and its member exercises in sync.
 * <p>
 * All variants of a group share one timeline: joining a group means adopting the group's dates, and editing the group's
 * dates pushes them onto every member. Programming members take a dedicated route, because changing their timeline also
 * has to recompute the build-and-test date and reschedule the build/test operations.
 * <p>
 * Changing dates through a group must not be a weaker operation than editing each member directly, so every member also
 * gets the post-save work its own update endpoint performs — see {@link #runPostTimelineUpdateSideEffects}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseVariantGroupService {

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ParticipationRepository participationRepository;

    private final ExerciseService exerciseService;

    private final ExerciseVersionService exerciseVersionService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final Optional<SlideApi> slideApi;

    public ExerciseVariantGroupService(ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseRepository exerciseRepository,
            ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService, ParticipationRepository participationRepository, ExerciseService exerciseService,
            ExerciseVersionService exerciseVersionService, InstanceMessageSendService instanceMessageSendService, Optional<SlideApi> slideApi) {
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.participationRepository = participationRepository;
        this.exerciseService = exerciseService;
        this.exerciseVersionService = exerciseVersionService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.slideApi = slideApi;
    }

    /**
     * Persists the group together with its members, after pushing the group's timeline onto each of them.
     * <p>
     * The timeline is applied and validated for every member BEFORE anything is persisted: a timeline that is valid at
     * group level can still be rejected by a member (the group's example-solution-date rule is looser), so an invalid
     * request must fail without mutating the stored group or exercise dates.
     *
     * @param group the group whose (already updated and validated) timeline should be pushed onto its members
     */
    public void saveWithTimelineAppliedToMembers(ExerciseVariantGroup group) {
        List<Exercise> nonProgrammingExercises = new ArrayList<>();
        List<ProgrammingExercise> programmingExercises = new ArrayList<>();
        // Snapshot every member's dates before applyGroupTimeline overwrites them: the post-update side effects below
        // (notifications, slide unlocking, stale individual due dates) all compare against the previous values.
        Map<Long, TimelineSnapshot> snapshotsByExerciseId = new HashMap<>();
        group.getExercises().forEach(exercise -> {
            snapshotsByExerciseId.put(exercise.getId(), TimelineSnapshot.of(exercise));
            applyGroupTimeline(group, exercise);
            validateDates(exercise);
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                programmingExercises.add(programmingExercise);
            }
            else {
                nonProgrammingExercises.add(exercise);
            }
        });
        // All members validated: persist the group and the member updates, keeping every member's dates in sync.
        exerciseVariantGroupRepository.save(group);
        exerciseRepository.saveAll(nonProgrammingExercises);
        nonProgrammingExercises.forEach(exercise -> runPostTimelineUpdateSideEffects(exercise, snapshotsByExerciseId.get(exercise.getId())));
        // Programming timeline changes recompute the build-and-test date and reschedule build/test operations, so they go
        // through the dedicated update flow (which reloads and saves the exercise itself) rather than a plain saveAll.
        programmingExercises.forEach(programmingExercise -> {
            ProgrammingExercise saved = updateProgrammingExerciseTimeline(programmingExercise, group);
            runProgrammingPostTimelineUpdateSideEffects(saved);
        });
    }

    /**
     * Moves the exercise into the given group, or out of its current group when {@code group} is {@code null}, and
     * persists it.
     * <p>
     * Joining a group means adopting the group's shared timeline (including its unset dates), so the variant's dates stay
     * consistent with its siblings. Removing an exercise leaves its current dates untouched — the server keeps them on
     * unassignment.
     *
     * @param exercise the exercise to (re-)assign; its type and course have already been validated by the caller
     * @param group    the target group, or {@code null} to remove the exercise from its current group
     */
    public void assignToGroup(Exercise exercise, @Nullable ExerciseVariantGroup group) {
        if (group != null) {
            // Let a brand-new, empty group adopt a timeline from its first exercise instead of forcing every
            // date to null until someone edits the group directly.
            adoptMissingDatesFromExercise(group, exercise);
        }
        // Joining a group changes the exercise's dates just as much as editing the group does, so the same snapshot is
        // needed here. Unassignment keeps the exercise's dates, which simply makes the side effects below no-ops.
        TimelineSnapshot snapshot = TimelineSnapshot.of(exercise);
        exercise.setExerciseVariantGroup(group);
        if (group != null && exercise instanceof ProgrammingExercise programmingExercise) {
            // Validate the prospective timeline BEFORE persisting the membership: the group's timeline can be legal at
            // group level but rejected by the programming validation (e.g. its example-solution-date rule is stricter),
            // and a rejected assignment must not leave the exercise grouped. The membership save must still happen
            // before the timeline update, because the programming update flow reloads the exercise by id. That flow is
            // required so the build-and-test date is recomputed and the scheduled build/test operations are refreshed
            // (a plain save would leave the old tasks scheduled).
            applyGroupTimeline(group, programmingExercise);
            validateDates(programmingExercise);
            exerciseRepository.save(programmingExercise);
            runProgrammingPostTimelineUpdateSideEffects(updateProgrammingExerciseTimeline(programmingExercise, group));
            return;
        }
        if (group != null) {
            applyGroupTimeline(group, exercise);
        }
        validateDates(exercise);
        Exercise saved = exerciseRepository.save(exercise);
        runPostTimelineUpdateSideEffects(saved, snapshot);
    }

    /**
     * Re-applies the owning group's shared timeline onto an exercise that is about to be updated, so a member can never
     * be persisted with dates that differ from its group.
     * <p>
     * The group — not the request — is the source of truth for a member's timeline, so the incoming dates are silently
     * overwritten rather than rejected: this is idempotent, keeps a stale client from failing an otherwise valid edit of
     * some unrelated field, and lets the member's dates self-heal. The exercise's own (lazy, likely detached)
     * {@code exerciseVariantGroup} is deliberately not read; the group is resolved by id instead. Callers must invoke
     * this after applying their request DTO and before validating, and are responsible for persisting the exercise.
     * <p>
     * Endpoints whose <em>only</em> purpose is changing the timeline reject group members outright instead of calling
     * this, because silently ignoring such a request would be misleading.
     *
     * @param exercise the exercise to pin to its group's timeline; unsaved or ungrouped exercises are left untouched
     */
    public void applyOwningGroupTimeline(Exercise exercise) {
        if (exercise.getId() == null) {
            // A brand-new exercise cannot be a group member yet, so there is no timeline to inherit.
            return;
        }
        exerciseVariantGroupRepository.findByExerciseId(exercise.getId()).ifPresent(group -> applyGroupTimeline(group, exercise));
    }

    /**
     * Resolves the group owning the given exercise, for callers that reject rather than overwrite timeline changes.
     *
     * @param exerciseId the id of the exercise to look up
     * @return the owning group, or empty if the exercise is not a variant
     */
    public Optional<ExerciseVariantGroup> findOwningGroup(long exerciseId) {
        return exerciseVariantGroupRepository.findByExerciseId(exerciseId);
    }

    /**
     * Applies the group's shared timeline to a programming member exercise through
     * {@link ProgrammingExerciseCreationUpdateService#updateTimeline}, which recomputes the build-and-test date,
     * validates the resulting dates, and reschedules the build/test operations. A plain repository save would persist
     * the new dates but leave the old build tasks scheduled. The exercise's current assessment type is preserved.
     * <p>
     * The build-and-test date passed in is the exercise's <em>own</em> current value, not a group value: the group does
     * not own that date (see the class Javadoc of {@link ExerciseVariantGroup}). Handing the current value to the regular
     * update flow makes it re-derive the date from the new shared due date using this exercise's existing offset —
     * exactly what the per-exercise timeline endpoint does when only the due date changes.
     *
     * @param programmingExercise the programming member whose timeline should adopt the group's
     * @param group               the group providing the shared timeline
     */
    private ProgrammingExercise updateProgrammingExerciseTimeline(ProgrammingExercise programmingExercise, ExerciseVariantGroup group) {
        ProgrammingExerciseTimelineUpdateDTO timelineUpdate = new ProgrammingExerciseTimelineUpdateDTO(programmingExercise.getId(), group.getReleaseDate(), group.getStartDate(),
                group.getDueDate(), programmingExercise.getAssessmentType(), group.getAssessmentDueDate(), group.getExampleSolutionPublicationDate(),
                programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        return programmingExerciseCreationUpdateService.updateTimeline(timelineUpdate, null);
    }

    /**
     * Runs the post-save work a non-programming member's own update endpoint performs after a date change, so a group
     * timeline edit is not a silently weaker version of editing the exercise directly. Mirrors the tail of e.g.
     * {@code TextExerciseCreationUpdateResource#updateTextExercise}: stale individual due dates are dropped, the
     * type-specific scheduler is refreshed, release/assessment notifications are rescheduled, slides tied to the old due
     * date are unlocked, and a version snapshot is recorded.
     * <p>
     * The exercise's own problem statement is passed as the "old" one because a timeline update never changes it, which
     * makes {@link ExerciseService#notifyAboutExerciseChanges} correctly report it as unchanged.
     *
     * @param exercise the saved member exercise
     * @param snapshot its dates from before the group timeline was applied
     */
    private void runPostTimelineUpdateSideEffects(Exercise exercise, TimelineSnapshot snapshot) {
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(exercise, snapshot.dueDate());
        scheduleTypeSpecificOperations(exercise);
        exerciseService.notifyAboutExerciseChanges(snapshot.releaseDate(), snapshot.assessmentDueDate(), exercise.getProblemStatement(), exercise, null);
        slideApi.ifPresent(api -> api.handleDueDateChange(snapshot.dueDate(), exercise));
        exerciseVersionService.createExerciseVersion(exercise);
    }

    /**
     * The programming counterpart of {@link #runPostTimelineUpdateSideEffects}. {@code updateTimeline} already reschedules
     * the build/test operations and sends the notifications itself, but version creation sits in the REST layer
     * ({@code ProgrammingExercisePartialUpdateResource#updateProgrammingExerciseTimeline}) and would otherwise be skipped
     * on this path. Individual-due-date cleanup and slide handling are deliberately not run here either, matching what
     * that endpoint does.
     *
     * @param savedProgrammingExercise the exercise as returned by the programming timeline update flow
     */
    private void runProgrammingPostTimelineUpdateSideEffects(ProgrammingExercise savedProgrammingExercise) {
        exerciseVersionService.createExerciseVersion(savedProgrammingExercise);
    }

    /**
     * Refreshes the scheduler that owns the exercise type's date-driven jobs. Modeling and file-upload exercises have no
     * dedicated scheduler, and only course quizzes are scheduled (exam quizzes are driven by the exam).
     */
    private void scheduleTypeSpecificOperations(Exercise exercise) {
        switch (exercise) {
            case TextExercise textExercise -> instanceMessageSendService.sendTextExerciseSchedule(textExercise.getId());
            case QuizExercise quizExercise when quizExercise.isCourseExercise() -> instanceMessageSendService.sendQuizExerciseStartSchedule(quizExercise.getId());
            default -> {
                // Modeling, file upload and exam quizzes have no timeline-driven scheduling of their own.
            }
        }
    }

    /**
     * An exercise's shared timeline dates as they were before a group update, captured so the post-save side effects can
     * compare against them. Only the fields those side effects actually read are kept.
     */
    private record TimelineSnapshot(@Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate) {

        static TimelineSnapshot of(Exercise exercise) {
            return new TimelineSnapshot(exercise.getReleaseDate(), exercise.getDueDate(), exercise.getAssessmentDueDate());
        }
    }

    /**
     * If the group has no members yet, adopts the joining exercise's dates for whichever shared timeline fields the
     * group doesn't already define. Persists the group if anything changed. Groups that already have members keep
     * their existing timeline untouched: only a brand-new, empty group adopts a sensible starting timeline.
     *
     * @param group    the group the exercise is joining (its current members must already be loaded)
     * @param exercise the exercise joining the group, whose dates are used as the source to adopt from
     */
    private void adoptMissingDatesFromExercise(ExerciseVariantGroup group, Exercise exercise) {
        if (!group.getExercises().isEmpty()) {
            return;
        }
        boolean changed = false;
        changed |= adoptMissingDate(group, exercise, Exercise::getReleaseDate, ExerciseVariantGroup::getReleaseDate, ExerciseVariantGroup::setReleaseDate);
        changed |= adoptMissingDate(group, exercise, Exercise::getStartDate, ExerciseVariantGroup::getStartDate, ExerciseVariantGroup::setStartDate);
        changed |= adoptMissingDate(group, exercise, Exercise::getDueDate, ExerciseVariantGroup::getDueDate, ExerciseVariantGroup::setDueDate);
        changed |= adoptMissingDate(group, exercise, Exercise::getAssessmentDueDate, ExerciseVariantGroup::getAssessmentDueDate, ExerciseVariantGroup::setAssessmentDueDate);
        changed |= adoptMissingDate(group, exercise, Exercise::getExampleSolutionPublicationDate, ExerciseVariantGroup::getExampleSolutionPublicationDate,
                ExerciseVariantGroup::setExampleSolutionPublicationDate);
        if (changed) {
            exerciseVariantGroupRepository.save(group);
        }
    }

    /**
     * Adopts the exercise's value for one field onto the group, but only if the group doesn't already define that
     * field and the resulting group timeline stays internally consistent (see {@link ExerciseVariantGroup#areDatesValid()}).
     * This guards against an exercise with stale/inconsistent leftover dates corrupting an otherwise valid group.
     */
    private <T extends Exercise> boolean adoptMissingDate(ExerciseVariantGroup group, T exercise, Function<T, ZonedDateTime> exerciseGetter,
            Function<ExerciseVariantGroup, ZonedDateTime> groupGetter, BiConsumer<ExerciseVariantGroup, ZonedDateTime> groupSetter) {
        ZonedDateTime exerciseDate = exerciseGetter.apply(exercise);
        if (exerciseDate != null && groupGetter.apply(group) == null) {
            groupSetter.accept(group, exerciseDate);
            if (group.areDatesValid()) {
                return true;
            }
            groupSetter.accept(group, null);
        }
        return false;
    }

    /**
     * Validates the exercise's dates via {@link Exercise#validateBaseDates()} rather than the polymorphic
     * {@link Exercise#validateDates()}: for a {@link QuizExercise} the latter also iterates the lazy {@code quizBatches}
     * collection, which is not initialized here and would throw {@code LazyInitializationException}. Only individual-mode
     * quizzes can be group members, so the skipped batch check is irrelevant for group timelines.
     *
     * @param exercise the exercise whose (already updated) dates should be validated
     */
    private void validateDates(Exercise exercise) {
        exercise.validateBaseDates();
    }

    /**
     * Overwrites the exercise's timeline fields with the group's, including unset (null) dates, so that all variants in a
     * group share one timeline.
     * <p>
     * A programming member's {@code buildAndTestStudentSubmissionsAfterDueDate} is deliberately left alone: it is derived
     * per exercise from its own build plan rather than shared (see the class Javadoc of {@link ExerciseVariantGroup}), and
     * {@link #updateProgrammingExerciseTimeline} lets the regular update flow re-derive it from the new shared due date.
     *
     * @param group    the group providing the shared timeline
     * @param exercise the member exercise to update in place (not persisted here)
     */
    private void applyGroupTimeline(ExerciseVariantGroup group, Exercise exercise) {
        exercise.setReleaseDate(group.getReleaseDate());
        exercise.setStartDate(group.getStartDate());
        exercise.setDueDate(group.getDueDate());
        exercise.setAssessmentDueDate(group.getAssessmentDueDate());
        exercise.setExampleSolutionPublicationDate(group.getExampleSolutionPublicationDate());
    }
}
