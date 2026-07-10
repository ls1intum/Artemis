package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
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
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

/**
 * Service-level operations on {@link ExerciseVariantGroup}s: creating a group in a course and (re-)assigning
 * exercises to a group, including the shared-timeline semantics. Extracted from
 * {@link de.tum.cit.aet.artemis.exercise.web.ExerciseVariantGroupResource} so other server-side flows (e.g. the
 * AI variant-generation finalizer) can place exercises into groups without duplicating the timeline logic.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseVariantGroupService {

    private static final String ENTITY_NAME = "exerciseVariantGroup";

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
        // Course side). Persist the group first so it gets an id, then attach it to the course so the FK is written.
        group = exerciseVariantGroupRepository.save(group);
        Course course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(courseId);
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);
        return group;
    }

    /**
     * Assigns an exercise to a variant group, or removes it from its current group ({@code group == null}).
     * Joining a group means adopting the group's shared timeline; a brand-new empty group first adopts the joining
     * exercise's dates for any timeline field it doesn't define yet.
     *
     * @param exercise the exercise to (re-)assign; persisted by this method
     * @param group    the target group with its member exercises loaded, or {@code null} to ungroup
     */
    public void assignExerciseToGroup(Exercise exercise, @Nullable ExerciseVariantGroup group) {
        if (group != null && exercise instanceof QuizExercise quizExercise && quizExercise.getQuizMode() != QuizMode.INDIVIDUAL) {
            // Synchronized/batched quizzes have a single shared run and cannot reasonably share a group timeline with
            // other variants, so only individual-mode quizzes (which already support per-student dates) may join a group.
            throw new BadRequestAlertException("Only individual-mode quizzes can be added to an exercise group", ENTITY_NAME, "quizNotIndividual");
        }
        if (group != null) {
            // For a date the group doesn't define yet, and that none of its current members define either, adopt the
            // joining exercise's value instead of blanking it out. This makes empty (or partially configured) groups
            // adopt a sensible timeline from the first real exercise added to them, rather than forcing every date to
            // null until someone edits the group directly.
            adoptMissingDatesFromExercise(group, exercise);
        }
        exercise.setExerciseVariantGroup(group);
        if (group != null && exercise instanceof ProgrammingExercise programmingExercise) {
            // Persist the membership change first, then route the timeline through the programming update flow so the
            // build-and-test date is recomputed and the scheduled build/test operations are refreshed (a plain save
            // would leave the old due-date/build tasks scheduled). This exercise is fully loaded, so saving it is safe.
            exerciseRepository.save(programmingExercise);
            updateProgrammingExerciseTimeline(programmingExercise, group);
            return;
        }
        if (group != null) {
            // Joining a group means adopting the group's shared timeline (even unset dates), so the variant's dates stay
            // consistent with its siblings. Removing an exercise (group == null) leaves its current dates untouched.
            applyGroupTimeline(group, exercise);
        }
        validateDatesIfPossible(exercise);
        exerciseRepository.save(exercise);
    }

    /**
     * Copies the group's shared timeline onto every member exercise and persists them, keeping the variants' own dates in
     * sync with the group.
     *
     * @param group the group whose (already fetched) member exercises should adopt its timeline
     */
    public void applyGroupTimelineToMembers(ExerciseVariantGroup group) {
        List<Exercise> nonProgrammingExercises = new ArrayList<>();
        group.getExercises().forEach(exercise -> {
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                // Programming timeline changes recompute the build-and-test date and reschedule build/test operations, so
                // they go through the dedicated update flow (which reloads and saves the exercise itself) rather than a
                // plain saveAll.
                updateProgrammingExerciseTimeline(programmingExercise, group);
            }
            else {
                applyGroupTimeline(group, exercise);
                // Fail loudly (400) if the group's new timeline produces an invalid combination for a member exercise,
                // instead of silently persisting dates that the exercise's own update endpoint would have rejected.
                validateDatesIfPossible(exercise);
                nonProgrammingExercises.add(exercise);
            }
        });
        exerciseRepository.saveAll(nonProgrammingExercises);
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
            // Not a field on the base Exercise, so it needs its own ProgrammingExercise-only getter/setter pair instead
            // of going through the generic Exercise-typed helper above.
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
     * Copies the group's shared timeline (even unset dates) onto the exercise so the variant's dates stay
     * consistent with its siblings.
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

    /**
     * Validates the exercise's date combination via {@link Exercise#validateBaseDates()} rather than the polymorphic
     * {@link Exercise#validateDates()}, so that for a {@link QuizExercise} this runs only the base
     * release/start/due/assessmentDue/exampleSolutionPublication check and not {@link QuizExercise}'s additional
     * {@code quizBatches} loop: {@code quizBatches} is a lazy collection that is not initialized on the exercises loaded
     * here (no open Hibernate session outside the originating repository call), so iterating it would throw
     * {@code LazyInitializationException}. Only individual-mode quizzes can be group members (enforced in
     * {@link #assignExerciseToGroup}), so the batch-specific check this skips is not relevant for group timelines.
     *
     * @param exercise the exercise whose (already updated) dates should be validated
     */
    private void validateDatesIfPossible(Exercise exercise) {
        exercise.validateBaseDates();
    }
}
