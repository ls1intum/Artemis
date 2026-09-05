package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
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
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Creates and keeps an {@link ExerciseVariantGroup} and its members on one shared timeline. Group creation is shared by
 * the {@link de.tum.cit.aet.artemis.exercise.web.ExerciseVariantGroupResource} and the AI variant-generation finalizer, so
 * both place exercises into groups without duplicating the logic. Joining a group adopts its dates, and editing the
 * group's dates pushes them onto every member. Programming members use a dedicated flow that also recomputes the
 * build-and-test date and reschedules build/test jobs. Group edits run the same post-save work a member's own update
 * endpoint would, so a group edit is never a weaker operation — see {@link #runPostTimelineUpdateSideEffects}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseVariantGroupService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantGroupService.class);

    private static final String ENTITY_NAME = "exerciseVariantGroup";

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ParticipationRepository participationRepository;

    private final ExerciseService exerciseService;

    private final ExerciseVersionService exerciseVersionService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final QuizExerciseService quizExerciseService;

    private final Optional<SlideApi> slideApi;

    public ExerciseVariantGroupService(ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository,
            ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService, ParticipationRepository participationRepository, ExerciseService exerciseService,
            ExerciseVersionService exerciseVersionService, InstanceMessageSendService instanceMessageSendService, QuizExerciseService quizExerciseService,
            Optional<SlideApi> slideApi) {
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.participationRepository = participationRepository;
        this.exerciseService = exerciseService;
        this.exerciseVersionService = exerciseVersionService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.quizExerciseService = quizExerciseService;
        this.slideApi = slideApi;
    }

    /**
     * Creates a new variant group and attaches it to the given course.
     *
     * @param courseId the id of the course that will own the group
     * @param group    the new, unsaved group entity
     * @return the persisted group
     */
    public ExerciseVariantGroup createGroup(Long courseId, ExerciseVariantGroup group) {
        group.validateDates();
        // The course_id FK lives on this table but the Course side owns the mapping, so the group has to exist before
        // it can be attached. Not wrapped in a transaction (this codebase avoids service-level @Transactional), so the
        // two writes are made safe by hand: the course is resolved BEFORE the first save, which leaves an unknown id
        // persisting nothing, and a failed attachment takes the row with it — a course-less group is invisible to
        // every course query and would linger forever. The attachment writes the new row's FK directly instead of
        // saving the course: that collection is orphanRemoval, so merging a snapshot taken before a concurrent
        // creation would delete the group that creation had just attached.
        courseRepository.findByIdElseThrow(courseId);
        ExerciseVariantGroup savedGroup = exerciseVariantGroupRepository.save(group);
        try {
            if (exerciseVariantGroupRepository.attachToCourse(savedGroup.getId(), courseId) != 1) {
                // The row is gone (a concurrent deletion between the save and this update), so nothing was attached.
                throw new IllegalStateException("Could not attach variant group " + savedGroup.getId() + " to course " + courseId);
            }
        }
        catch (RuntimeException attachmentFailed) {
            try {
                exerciseVariantGroupRepository.delete(savedGroup);
            }
            catch (RuntimeException cleanupFailed) {
                // Never let the cleanup hide why the attachment failed; log the row that has to go manually and
                // carry the cleanup error along as a suppressed exception.
                log.error("Could not delete variant group {} after it could not be attached to course {}", savedGroup.getId(), courseId, cleanupFailed);
                attachmentFailed.addSuppressed(cleanupFailed);
            }
            throw attachmentFailed;
        }
        return savedGroup;
    }

    /**
     * Applies the group's timeline to every member and persists both. All members are validated before anything is saved
     * (a timeline valid at group level can still be rejected by a member), so an invalid request mutates nothing.
     *
     * @param group the group whose updated, validated timeline should be pushed onto its members
     */
    public void saveWithTimelineAppliedToMembers(ExerciseVariantGroup group) {
        List<Exercise> nonProgrammingExercises = new ArrayList<>();
        List<ProgrammingExercise> programmingExercises = new ArrayList<>();
        // Snapshot each member's old dates: the post-update side effects below compare against them.
        Map<Long, TimelineSnapshot> snapshotsByExerciseId = new HashMap<>();
        group.getExercises().forEach(exercise -> {
            // Don't overwrite a started/ended quiz's dates (mirrors QuizExerciseService.checkQuizEditable). Guard only a
            // real timeline change, so a metadata-only group edit stays allowed while a member quiz is live.
            if (timelineDiffers(group, exercise)) {
                rejectIfQuizMemberNotEditable(exercise);
            }
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
        // All members valid: persist the group and the member updates together.
        exerciseVariantGroupRepository.save(group);
        exerciseRepository.saveAll(nonProgrammingExercises);
        nonProgrammingExercises.forEach(exercise -> runPostTimelineUpdateSideEffects(exercise, snapshotsByExerciseId.get(exercise.getId())));
        // Programming timeline changes recompute the build-and-test date and reschedule jobs, so they go through the
        // dedicated update flow (which reloads and saves the exercise itself) rather than a plain saveAll.
        programmingExercises.forEach(programmingExercise -> {
            ProgrammingExercise saved = updateProgrammingExerciseTimeline(programmingExercise, group);
            runProgrammingPostTimelineUpdateSideEffects(saved);
        });
    }

    /**
     * Moves the exercise into {@code group}, or out of its current group when {@code group} is {@code null}, and persists
     * it. Joining adopts the group's shared timeline (including its unset dates); unassignment keeps the current dates.
     *
     * @param exercise the exercise to (re-)assign; its type and course have already been validated by the caller
     * @param group    the target group, or {@code null} to remove the exercise from its current group
     */
    public void assignToGroup(Exercise exercise, @Nullable ExerciseVariantGroup group) {
        if (group != null) {
            // Joining stamps the group's timeline onto the exercise, so a started/ended quiz can't be added at all — there
            // is no "no-op" case to allow through here, unlike a group update.
            rejectIfQuizMemberNotEditable(exercise);
            // Let a brand-new, empty group adopt its first exercise's dates instead of forcing everything to null.
            adoptMissingDatesFromExercise(group, exercise);
        }
        // Joining changes the dates as much as a group edit, so snapshot here too; unassignment makes the side effects no-ops.
        TimelineSnapshot snapshot = TimelineSnapshot.of(exercise);
        exercise.setExerciseVariantGroup(group);
        if (group != null && exercise instanceof ProgrammingExercise programmingExercise) {
            // Validate the adopted timeline before persisting membership (programming validation is stricter, and a rejected
            // assignment must not leave the exercise grouped). Membership is saved first because the programming update flow
            // reloads by id; that flow is required to recompute the build-and-test date and reschedule the build/test jobs.
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
     * Re-pins an about-to-be-updated exercise to its owning group's timeline, so a member is never saved with dates that
     * differ from its group. The group (resolved by id, not the lazy association) is authoritative, so incoming dates are
     * silently overwritten — idempotent, self-healing, and it lets an unrelated field edit still succeed. Call after
     * applying the request DTO and before validating; the caller persists. Timeline-only endpoints reject members instead.
     *
     * @param exercise the exercise to pin to its group's timeline; unsaved or ungrouped exercises are left untouched
     */
    public void applyOwningGroupTimeline(Exercise exercise) {
        if (exercise.getId() == null) {
            // A brand-new exercise cannot be a group member yet.
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
     * Applies the group's timeline to a programming member via {@link ProgrammingExerciseCreationUpdateService#updateTimeline},
     * which recomputes the build-and-test date, validates, and reschedules the build/test jobs (a plain save would leave the
     * old jobs scheduled). The build-and-test date passed is the exercise's own current value — the group doesn't own it, so
     * the flow re-derives it from the new shared due date using the exercise's existing offset.
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
     * Runs the post-save work a non-programming member's own update endpoint does after a date change (drop stale
     * individual due dates, refresh the scheduler, reschedule notifications, unlock slides, snapshot a version), so a group
     * edit isn't a weaker operation. The exercise's own problem statement is passed as "old" because a timeline update never
     * changes it, so {@link ExerciseService#notifyAboutExerciseChanges} reports it unchanged.
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
     * Programming counterpart of {@link #runPostTimelineUpdateSideEffects}: {@code updateTimeline} already reschedules the
     * jobs and sends notifications, but version creation lives in the REST layer and would otherwise be skipped here.
     * Individual-due-date cleanup and slide handling are intentionally omitted, matching that endpoint.
     *
     * @param savedProgrammingExercise the exercise as returned by the programming timeline update flow
     */
    private void runProgrammingPostTimelineUpdateSideEffects(ProgrammingExercise savedProgrammingExercise) {
        exerciseVersionService.createExerciseVersion(savedProgrammingExercise);
    }

    /**
     * Refreshes the scheduler for the exercise type's date-driven jobs. Modeling and file-upload have none; only course
     * quizzes are scheduled (exam quizzes are driven by the exam).
     */
    private void scheduleTypeSpecificOperations(Exercise exercise) {
        switch (exercise) {
            case TextExercise textExercise -> instanceMessageSendService.sendTextExerciseSchedule(textExercise.getId());
            case QuizExercise quizExercise when quizExercise.isCourseExercise() -> instanceMessageSendService.sendQuizExerciseStartSchedule(quizExercise.getId());
            default -> {
                // Modeling, file upload and exam quizzes have no timeline-driven scheduling.
            }
        }
    }

    /** A member's pre-update timeline dates, kept so the post-save side effects can compare against them. */
    private record TimelineSnapshot(@Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate) {

        static TimelineSnapshot of(Exercise exercise) {
            return new TimelineSnapshot(exercise.getReleaseDate(), exercise.getDueDate(), exercise.getAssessmentDueDate());
        }
    }

    /**
     * For a still-empty group, adopts the joining exercise's dates for any shared field the group doesn't define yet, and
     * persists if anything changed. Groups that already have members keep their existing timeline. {@link #assignToGroup}
     * calls this for the exercise that joins first; public for a caller that has to persist membership in a different
     * order than the dates should be adopted in (see {@code VariantPlacementService}: the generated variant is placed
     * first, but its source is the exercise whose dates the group should take).
     *
     * @param group    the group the exercise is joining (its current members must already be loaded)
     * @param exercise the exercise joining the group, whose dates are the source to adopt from
     */
    public void adoptMissingDatesFromExercise(ExerciseVariantGroup group, Exercise exercise) {
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
     * Adopts the exercise's value for one field onto the group, only if the group doesn't define it yet and the result
     * stays a valid group timeline (see {@link ExerciseVariantGroup#areDatesValid()}) — guarding against stale leftover dates.
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
     * Validates via {@link Exercise#validateBaseDates()} rather than the polymorphic {@link Exercise#validateDates()}, which
     * for a {@link QuizExercise} would iterate the uninitialized lazy {@code quizBatches} and throw. Group quizzes are always
     * individual-mode, so the skipped batch check is irrelevant here.
     */
    private void validateDates(Exercise exercise) {
        exercise.validateBaseDates();
    }

    /**
     * Rejects the operation if the exercise is an individual quiz that is no longer editable (started batch or ended),
     * using the same conditions as {@link QuizExerciseService#checkQuizEditable}. Stops a group operation from overwriting a
     * live or finished quiz's dates. Non-quiz members and editable quizzes pass through.
     *
     * @param exercise the member exercise about to receive the group's timeline
     */
    private void rejectIfQuizMemberNotEditable(Exercise exercise) {
        if (!canJoinGroup(exercise)) {
            throw new BadRequestAlertException("The timeline of a variant group cannot be changed while a member quiz has started or has ended", ENTITY_NAME,
                    "quizMemberNotEditable");
        }
    }

    /**
     * Whether the exercise may join a variant group at all, so a caller that would have to undo persisted work after a
     * rejected {@link #assignToGroup} can ask first. Same condition as {@link #rejectIfQuizMemberNotEditable}.
     *
     * @param exercise the candidate member
     * @return false only for a quiz that has started or ended — every other exercise can join
     */
    public boolean canJoinGroup(Exercise exercise) {
        return !(exercise instanceof QuizExercise quizExercise) || quizExerciseService.isEditable(quizExercise);
    }

    /**
     * Whether applying the group's timeline would actually change any shared date {@link #applyGroupTimeline} writes,
     * compared as instants and tolerating nulls, so a re-applied identical timeline is detected as a no-op.
     *
     * @param group    the group providing the shared timeline
     * @param exercise the member whose current dates are compared against the group's
     * @return {@code true} if any shared date differs
     */
    private boolean timelineDiffers(ExerciseVariantGroup group, Exercise exercise) {
        return datesDiffer(group.getReleaseDate(), exercise.getReleaseDate()) || datesDiffer(group.getStartDate(), exercise.getStartDate())
                || datesDiffer(group.getDueDate(), exercise.getDueDate()) || datesDiffer(group.getAssessmentDueDate(), exercise.getAssessmentDueDate())
                || datesDiffer(group.getExampleSolutionPublicationDate(), exercise.getExampleSolutionPublicationDate());
    }

    /** Null-tolerant instant comparison, so the same moment carrying a different zone offset compares equal. */
    private static boolean datesDiffer(@Nullable ZonedDateTime first, @Nullable ZonedDateTime second) {
        Instant firstInstant = first != null ? first.toInstant() : null;
        Instant secondInstant = second != null ? second.toInstant() : null;
        return !Objects.equals(firstInstant, secondInstant);
    }

    /**
     * Overwrites the exercise's timeline (including unset dates) with the group's, so all variants share one timeline. A
     * programming member's {@code buildAndTestStudentSubmissionsAfterDueDate} is left alone — it's derived per exercise, not
     * shared (see {@link ExerciseVariantGroup}), and {@link #updateProgrammingExerciseTimeline} re-derives it.
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
