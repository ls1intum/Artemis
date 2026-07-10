package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

/**
 * REST controller for managing {@link ExerciseVariantGroup}s, the course-owned groupings of interchangeable exercise
 * variants.
 * <p>
 * Authorization mirrors the rights for the exercises themselves: editors create, update and read groups (and assign
 * exercises to them), while only instructors may delete a group. Every endpoint additionally verifies that the targeted
 * group (and exercise) belongs to the course in the request path.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ExerciseVariantGroupResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantGroupResource.class);

    private static final String ENTITY_NAME = "exerciseVariantGroup";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseRepository courseRepository;

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    public ExerciseVariantGroupResource(CourseRepository courseRepository, ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseRepository exerciseRepository,
            ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService) {
        this.courseRepository = courseRepository;
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
    }

    /**
     * POST /courses/:courseId/exercise-variant-groups : Create a new exercise variant group in the given course.
     *
     * @param createDTO the settings of the group to create
     * @param courseId  the id of the course that will own the group
     * @return the ResponseEntity with status 201 (Created) and the created group in the body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exercise-variant-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> createExerciseVariantGroup(@Valid @RequestBody CreateExerciseVariantGroupDTO createDTO, @PathVariable Long courseId)
            throws URISyntaxException {
        log.debug("REST request to create ExerciseVariantGroup in course {} : {}", courseId, createDTO);
        ExerciseVariantGroup group = createDTO.toEntity();
        group.validateDates();
        // The course owns the unidirectional collection: save the group first to get an id, then attach it so the
        // course_id FK is written. Not wrapped in a transaction (this codebase avoids service-level @Transactional);
        // validation runs before the first save, so a failure between the two can only leave an orphan, course-less
        // group that no course-scoped query ever sees.
        group = exerciseVariantGroupRepository.save(group);
        Course course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(courseId);
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);
        return ResponseEntity.created(new URI("/api/exercise/courses/" + courseId + "/exercise-variant-groups/" + group.getId())).body(new ExerciseVariantGroupDTO(group));
    }

    /**
     * PUT /courses/:courseId/exercise-variant-groups/:groupId : Update an existing exercise variant group. The owning
     * course cannot be changed.
     *
     * @param updateDTO the new settings of the group
     * @param groupId   the id of the group to update
     * @param courseId  the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the updated group in the body
     */
    @PutMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> updateExerciseVariantGroup(@Valid @RequestBody UpdateExerciseVariantGroupDTO updateDTO, @PathVariable Long groupId,
            @PathVariable Long courseId) {
        log.debug("REST request to update ExerciseVariantGroup {} in course {} : {}", groupId, courseId, updateDTO);
        if (!Objects.equals(groupId, updateDTO.id())) {
            throw new BadRequestAlertException("The id in the path and the body must match", ENTITY_NAME, "idMismatch");
        }
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
        updateDTO.applyTo(group);
        group.validateDates();
        // Apply the group timeline to every member and validate the result BEFORE persisting anything: a timeline valid
        // at group level can still be rejected by a member (the group's example-solution-date rule is looser), so a bad
        // request must fail with 400 without mutating the stored group or exercise dates.
        List<Exercise> nonProgrammingExercises = new ArrayList<>();
        List<ProgrammingExercise> programmingExercises = new ArrayList<>();
        group.getExercises().forEach(exercise -> {
            applyGroupTimeline(group, exercise);
            validateDatesIfPossible(exercise);
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
        // Build the response from the loaded entity (its exercises were fetched); save()'s re-merged return value has a
        // lazy exercises collection that cannot initialize once the session closes (open-in-view is off).
        return ResponseEntity.ok(new ExerciseVariantGroupDTO(group));
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups : Get all exercise variant groups of a course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of groups in the body
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<ExerciseVariantGroupDTO>> getExerciseVariantGroupsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ExerciseVariantGroups for course {}", courseId);
        List<ExerciseVariantGroupDTO> groups = exerciseVariantGroupRepository.findAllByCourseId(courseId).stream().map(ExerciseVariantGroupDTO::new).toList();
        return ResponseEntity.ok(groups);
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups/:groupId : Get a single exercise variant group.
     *
     * @param groupId  the id of the group to retrieve
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the group in the body
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> getExerciseVariantGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to get ExerciseVariantGroup {} in course {}", groupId, courseId);
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
        return ResponseEntity.ok(new ExerciseVariantGroupDTO(group));
    }

    /**
     * DELETE /courses/:courseId/exercise-variant-groups/:groupId : Delete an exercise variant group. The aggregated
     * exercises survive and simply lose their group membership (the foreign key is set to null).
     *
     * @param groupId  the id of the group to delete
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteExerciseVariantGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to delete ExerciseVariantGroup {} in course {}", groupId, courseId);
        // Load the group without its members so the ON DELETE SET NULL foreign key (see the Liquibase changelog) can
        // ungroup them; loading them would make Hibernate's flush fail because managed exercises still reference the
        // removed group. The members survive, simply ungrouped.
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdWithoutExercisesElseThrow(groupId, courseId);
        exerciseVariantGroupRepository.delete(group);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, group.getTitle())).build();
    }

    /**
     * PUT /courses/:courseId/exercises/:exerciseId/variant-group : Assign an exercise to a variant group, or remove it
     * from its current group. Membership is edited from the exercise side, so moving an exercise between groups is a
     * single request.
     * <p>
     * A group may deliberately mix exercise types (the variants of one task can come in different formats, e.g. a text
     * and a modeling version). Scoring caps such a group once across all types in the overall course score; only the
     * per-exercise-type breakdown on the instructor scores page applies the cap per type bucket.
     *
     * @param assignmentDTO the target group ({@code groupId == null} removes the exercise from its group)
     * @param exerciseId    the id of the exercise to (re-)assign
     * @param courseId      the id of the course the exercise (and the target group) belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/exercises/{exerciseId}/variant-group")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> setExerciseVariantGroup(@RequestBody ExerciseVariantGroupAssignmentDTO assignmentDTO, @PathVariable Long exerciseId, @PathVariable Long courseId) {
        log.debug("REST request to assign exercise {} in course {} to variant group {}", exerciseId, courseId, assignmentDTO.groupId());
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            // An exam exercise reports its exam's course, so it would pass the ownership check below and be assigned to
            // a course-owned group, later breaking group timeline updates. Reject it up front.
            throw new BadRequestAlertException("Exam exercises cannot be assigned to a variant group", ENTITY_NAME, "examExerciseNotAllowed");
        }
        Course exerciseCourse = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (exerciseCourse == null || !Objects.equals(exerciseCourse.getId(), courseId)) {
            throw new BadRequestAlertException("The exercise does not belong to the course in the path", ENTITY_NAME, "courseIdMismatch");
        }
        ExerciseVariantGroup group = assignmentDTO.groupId() == null ? null : exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(assignmentDTO.groupId(), courseId);
        if (group != null && exercise instanceof QuizExercise quizExercise && quizExercise.getQuizMode() != QuizMode.INDIVIDUAL) {
            // Synchronized/batched quizzes have a single shared run and cannot reasonably share a group timeline with
            // other variants, so only individual-mode quizzes (which already support per-student dates) may join a group.
            throw new BadRequestAlertException("Only individual-mode quizzes can be added to an exercise group", ENTITY_NAME, "quizNotIndividual");
        }
        if (group != null) {
            // Let a brand-new, empty group adopt a timeline from its first exercise instead of forcing every
            // date to null until someone edits the group directly.
            adoptMissingDatesFromExercise(group, exercise);
        }
        exercise.setExerciseVariantGroup(group);
        if (group != null && exercise instanceof ProgrammingExercise programmingExercise) {
            // Persist the membership change, then route the timeline through the programming update flow so the
            // build-and-test date is recomputed and the scheduled build/test operations are refreshed (a plain save
            // would leave the old tasks scheduled).
            exerciseRepository.save(programmingExercise);
            updateProgrammingExerciseTimeline(programmingExercise, group);
            return ResponseEntity.ok().build();
        }
        if (group != null) {
            // Joining a group means adopting the group's shared timeline (even unset dates), so the variant's dates stay
            // consistent with its siblings. Removing an exercise (group == null) leaves its current dates untouched.
            applyGroupTimeline(group, exercise);
        }
        validateDatesIfPossible(exercise);
        exerciseRepository.save(exercise);
        return ResponseEntity.ok().build();
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
    private void validateDatesIfPossible(Exercise exercise) {
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
