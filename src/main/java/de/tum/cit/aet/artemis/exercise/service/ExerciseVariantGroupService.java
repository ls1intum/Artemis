package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Keeps the timeline of an {@link ExerciseVariantGroup} and its member exercises in sync, and creates groups in a
 * course. Shared by the {@link de.tum.cit.aet.artemis.exercise.web.ExerciseVariantGroupResource} and the AI
 * variant-generation finalizer, so both place exercises into groups without duplicating the timeline logic.
 * <p>
 * All variants of a group share one timeline: joining a group means adopting the group's dates, and editing the group's
 * dates pushes them onto every member. Programming members take a dedicated route, because changing their timeline also
 * has to recompute the build-and-test date and reschedule the build/test operations.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseVariantGroupService {

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    public ExerciseVariantGroupService(ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository,
            ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService) {
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
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
        // The course owns the unidirectional collection (the course_id FK lives on this table but is managed from the
        // Course side). Persist the group first so it gets an id, then attach it so the course_id FK is written. Not
        // wrapped in a transaction (this codebase avoids service-level @Transactional); validation runs before the
        // first save, so a failure between the two can only leave an orphan, course-less group no course query sees.
        group = exerciseVariantGroupRepository.save(group);
        Course course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(courseId);
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);
        return group;
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
        group.getExercises().forEach(exercise -> {
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
        // Programming timeline changes recompute the build-and-test date and reschedule build/test operations, so they go
        // through the dedicated update flow (which reloads and saves the exercise itself) rather than a plain saveAll.
        programmingExercises.forEach(programmingExercise -> updateProgrammingExerciseTimeline(programmingExercise, group));
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
            updateProgrammingExerciseTimeline(programmingExercise, group);
            return;
        }
        if (group != null) {
            applyGroupTimeline(group, exercise);
        }
        validateDates(exercise);
        exerciseRepository.save(exercise);
    }

    /**
     * Applies the group's shared timeline to a programming member exercise through
     * {@link ProgrammingExerciseCreationUpdateService#updateTimeline}, which recomputes the build-and-test date,
     * validates the resulting dates, and reschedules the build/test operations. A plain repository save would persist
     * the new dates but leave the old build tasks scheduled. The exercise's current assessment type is preserved.
     *
     * @param programmingExercise the programming member whose timeline should adopt the group's
     * @param group               the group providing the shared timeline
     */
    private void updateProgrammingExerciseTimeline(ProgrammingExercise programmingExercise, ExerciseVariantGroup group) {
        ProgrammingExerciseTimelineUpdateDTO timelineUpdate = new ProgrammingExerciseTimelineUpdateDTO(programmingExercise.getId(), group.getReleaseDate(), group.getStartDate(),
                group.getDueDate(), programmingExercise.getAssessmentType(), group.getAssessmentDueDate(), group.getExampleSolutionPublicationDate(),
                group.getBuildAndTestStudentSubmissionsAfterDueDate());
        programmingExerciseCreationUpdateService.updateTimeline(timelineUpdate, null);
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
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            // Not a base Exercise field, so it needs the ProgrammingExercise-only getter/setter pair.
            changed |= adoptMissingDate(group, programmingExercise, ProgrammingExercise::getBuildAndTestStudentSubmissionsAfterDueDate,
                    ExerciseVariantGroup::getBuildAndTestStudentSubmissionsAfterDueDate, ExerciseVariantGroup::setBuildAndTestStudentSubmissionsAfterDueDate);
        }
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
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(group.getBuildAndTestStudentSubmissionsAfterDueDate());
        }
    }
}
